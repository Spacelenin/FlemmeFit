package com.spacelenin.flemmefit.montre

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/**
 * Mises à jour de l'app montre : elle cherche elle-même la dernière version (tag `montre-N`) dans le repo public
 * des releases, la télécharge et l'installe avec l'installeur du système.
 *
 * Jamais en 4G : uniquement l'Internet partagé par le téléphone en Bluetooth, ou le Wi-Fi. Le réseau est
 * demandé explicitement et gardé pendant tout le téléchargement (sinon Wear OS le coupe dès que l'écran
 * s'éteint), le téléchargement tourne dans un service, et il reprend où il s'était arrêté.
 */
object Updater {
    private const val LIST_URL = "https://api.github.com/repos/Spacelenin/FlemmeFit-releases/releases?per_page=30"
    private val tag = Regex("""^montre-(\d+)$""")
    private const val RETRIES = 5

    data class Found(val version: Int, val url: String, val size: Long = 0)

    sealed interface State {
        data object Idle : State
        /** [percent] de 0 à 100, null tant que la taille est inconnue. */
        data class Downloading(val version: Int, val percent: Int?, val kbPerSecond: Int? = null) : State
        data class Installing(val version: Int) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    /** Journal (écran de test et diagnostic). */
    val log = MutableStateFlow(listOf<String>())

    fun note(line: String) {
        log.value = (log.value + line).takeLast(30)
        if (line.startsWith("❌")) _state.value = State.Failed(line.removePrefix("❌ "))
        if (line.startsWith("✅")) _state.value = State.Idle
    }

    fun reset() {
        _state.value = State.Idle
    }

    /**
     * Demande un réseau hors 4G et le garde tant que [block] tourne : Bluetooth (Internet du téléphone)
     * d'abord, sinon Wi-Fi.
     */
    private suspend fun <T> withNetwork(context: Context, block: suspend (Network) -> T): T {
        // On prend le réseau déjà en place (Internet du téléphone en Bluetooth, ou Wi-Fi), jamais la 4G.
        // Pas de demande de réseau : elle allumait le Wi-Fi de la montre. Le service au premier plan garde
        // la connexion ouverte pendant le téléchargement.
        val cm = context.getSystemService(ConnectivityManager::class.java)
        fun allowed(n: Network): Boolean {
            val caps = cm.getNetworkCapabilities(n) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        }
        val existing = cm.activeNetwork?.takeIf(::allowed) ?: cm.allNetworks.firstOrNull(::allowed)
            ?: throw IOException("Pas de réseau hors 4G : rapproche le téléphone ou passe en Wi-Fi.")
        return block(existing)
    }

    /** Vérification demandée à la main : le résultat est affiché. */
    sealed interface Check {
        data object Running : Check
        data object UpToDate : Check
        data class Error(val message: String) : Check
    }

    val manual = MutableStateFlow<Check?>(null)

    suspend fun checkNow(context: Context): Found? {
        manual.value = Check.Running
        return try {
            val installed = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
            val found = latest(context)?.takeIf { it.version > installed }
            manual.value = if (found == null) Check.UpToDate else null
            found
        } catch (e: Exception) {
            manual.value = Check.Error(e.message ?: e.javaClass.simpleName)
            null
        }
    }

    /** Version plus récente que celle installée, ou null. Silencieux en cas d'erreur. */
    suspend fun check(context: Context): Found? = try {
        val installed = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        latest(context)?.takeIf { it.version > installed }
    } catch (e: Exception) {
        null
    }

    suspend fun latest(context: Context): Found? = withContext(Dispatchers.IO) {
        withNetwork(context) { net ->
            val releases = JSONArray(get(net, LIST_URL))
            var best: Found? = null
            for (i in 0 until releases.length()) {
                val r = releases.getJSONObject(i)
                if (r.optBoolean("draft")) continue
                val v = tag.matchEntire(r.optString("tag_name"))?.groupValues?.get(1)?.toIntOrNull() ?: continue
                val assets = r.optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    val a = assets.getJSONObject(j)
                    if (a.optString("name").endsWith(".apk") && (best == null || v > best.version)) {
                        best = Found(v, a.optString("browser_download_url"), a.optLong("size"))
                    }
                }
            }
            best
        }
    }

    /** Lance la mise à jour dans un service : elle continue même écran éteint. */
    fun install(context: Context, found: Found) {
        _state.value = State.Downloading(found.version, null)
        context.startForegroundService(
            Intent(context, UpdateService::class.java)
                .putExtra(UpdateService.EXTRA_VERSION, found.version)
                .putExtra(UpdateService.EXTRA_URL, found.url)
                .putExtra(UpdateService.EXTRA_SIZE, found.size),
        )
    }

    /** Téléchargement avec reprise (en-tête Range) et quelques nouvelles tentatives, puis installation. */
    internal suspend fun download(context: Context, found: Found) = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "update-${found.version}.apk")
        // Les restes d'une autre version ne servent à rien.
        context.cacheDir.listFiles { f -> f.name.startsWith("update") && f.name != file.name }?.forEach { it.delete() }
        var attempt = 0
        while (true) {
            try {
                withNetwork(context) { net -> fetch(net, found, file) }
                break
            } catch (e: IOException) {
                attempt++
                note("Coupure (${e.message.orEmpty()}), reprise ${attempt}/$RETRIES…")
                if (attempt >= RETRIES) throw e
                delay(3000L * attempt)
            }
        }
        _state.value = State.Installing(found.version)
        note("Téléchargé : ${file.length() / 1024} Ko")
        installFile(context, file)
    }

    private fun fetch(net: Network, found: Found, file: File) {
        val already = if (file.exists()) file.length() else 0L
        if (found.size > 0 && already >= found.size) return
        val connection = open(net, found.url, accept = "application/octet-stream")
        if (already > 0) connection.setRequestProperty("Range", "bytes=$already-")
        try {
            val code = connection.responseCode
            val resumed = code == HttpURLConnection.HTTP_PARTIAL
            if (code != HttpURLConnection.HTTP_OK && !resumed) throw IOException("HTTP $code")
            val total = if (found.size > 0) found.size else (if (resumed) already else 0L) + connection.contentLengthLong
            var done = if (resumed) already else 0L
            connection.inputStream.use { input ->
                FileOutputStream(file, resumed).use { out ->
                    val buffer = ByteArray(32 * 1024)
                    var lastPercent = -1
                    // Débit mesuré sur la dernière seconde environ.
                    var windowStart = System.currentTimeMillis()
                    var windowBytes = 0L
                    var speed: Int? = null
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        windowBytes += n
                        val now = System.currentTimeMillis()
                        val speedChanged = now - windowStart >= 1000
                        if (speedChanged) {
                            speed = (windowBytes * 1000 / (now - windowStart) / 1024).toInt()
                            windowStart = now
                            windowBytes = 0
                        }
                        val percent = if (total > 0) (done * 100 / total).toInt() else null
                        if (percent != lastPercent || speedChanged) {
                            lastPercent = percent ?: -1
                            _state.value = State.Downloading(found.version, percent, speed)
                        }
                    }
                }
            }
            if (total > 0 && done < total) throw IOException("téléchargement incomplet")
        } finally {
            connection.disconnect()
        }
    }

    private fun installFile(context: Context, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            file.inputStream().use { input ->
                session.openWrite("update.apk", 0, file.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
            val pending = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pending.intentSender)
        }
        note("Installation envoyée au système…")
    }

    private fun get(net: Network, url: String): String {
        val connection = open(net, url)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(net: Network, url: String, accept: String = "application/vnd.github+json"): HttpURLConnection =
        (net.openConnection(URL(url)) as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "FlemmeFitMontre")
        }
}

/** Fait tourner le téléchargement et l'installation au premier plan : rien ne coupe quand l'écran s'éteint. */
class UpdateService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val version = intent?.getIntExtra(EXTRA_VERSION, 0) ?: 0
        val url = intent?.getStringExtra(EXTRA_URL)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Mise à jour", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("FlemmeFit")
            .setContentText("Mise à jour $version en cours")
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        if (url == null || running) return START_NOT_STICKY
        running = true
        scope.launch {
            try {
                Updater.download(applicationContext, Updater.Found(version, url, intent.getLongExtra(EXTRA_SIZE, 0)))
            } catch (e: Exception) {
                Updater.note("❌ ${e.message ?: e.javaClass.simpleName}")
            } finally {
                running = false
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_VERSION = "version"
        const val EXTRA_URL = "url"
        const val EXTRA_SIZE = "size"
        private const val CHANNEL = "maj"
        private const val NOTIFICATION_ID = 8
    }
}
