package com.spacelenin.flemmefit.montre

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Test de mise à jour autonome : la montre cherche elle-même la dernière version publiée (tag `montre-N`)
 * dans le repo public des releases, la télécharge et l'installe avec l'installeur du système.
 */
object Updater {
    private const val LIST_URL = "https://api.github.com/repos/Spacelenin/FlemmeFit-releases/releases?per_page=30"
    private val tag = Regex("""^montre-(\d+)$""")

    /** Journal affiché à l'écran : chaque étape du test, pour savoir précisément où ça bloque. */
    val log = MutableStateFlow(listOf<String>())

    fun note(line: String) {
        log.value = (log.value + line).takeLast(30)
    }

    data class Found(val version: Int, val url: String)

    /**
     * Réseau autorisé : tout sauf la 4G de la montre. En pratique l'Internet partagé par le téléphone en Bluetooth,
     * ou le Wi-Fi. Null si seule la 4G est disponible : on ne télécharge rien.
     */
    private fun network(context: Context): Network? {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        return cm.allNetworks.firstOrNull { n ->
            val caps = cm.getNetworkCapabilities(n) ?: return@firstOrNull false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
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
        val net = network(context) ?: throw IOException("Pas de réseau hors 4G (téléphone ou Wi-Fi)")
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
                    best = Found(v, a.optString("browser_download_url"))
                }
            }
        }
        best
    }

    /** Télécharge et lance l'installation ; le résultat arrive dans [InstallResultReceiver]. */
    suspend fun install(context: Context, found: Found) {
        val file = File(context.cacheDir, "update.apk")
        withContext(Dispatchers.IO) {
            val net = network(context) ?: throw IOException("Pas de réseau hors 4G (téléphone ou Wi-Fi)")
            val connection = open(net, found.url)
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
                connection.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
            } finally {
                connection.disconnect()
            }
        }
        note("Téléchargé : ${file.length() / 1024} Ko")
        withContext(Dispatchers.IO) {
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

    private fun open(net: Network, url: String): HttpURLConnection =
        (net.openConnection(URL(url)) as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "FlemmeFitMontre")
        }.also { it.instanceFollowRedirects = true }
}
