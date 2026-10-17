package com.spacelenin.flemmefit.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.spacelenin.flemmefit.BuildConfig
import com.spacelenin.flemmefit.model.ReleaseInfo
import com.spacelenin.flemmefit.model.Releases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Mise à jour sans passer par GitHub : lit la dernière release du dépôt public,
 * télécharge l'APK et le confie à l'installeur Android (une confirmation à l'écran).
 * Android refuse d'office un APK signé avec une autre clé.
 */
class AppUpdater(private val context: Context) {

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val release: ReleaseInfo) : State
        data class Downloading(val release: ReleaseInfo, val progress: Float) : State
        data object Installing : State
        data class Failed(val message: String, val release: ReleaseInfo? = null) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private var job: Job? = null

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Le bandeau « plus tard » masque cette version jusqu'à la suivante. */
    private val _dismissed = MutableStateFlow(prefs.getInt(KEY_DISMISSED, 0))
    val dismissedVersion: StateFlow<Int> = _dismissed.asStateFlow()

    val currentVersion: Int get() = BuildConfig.VERSION_CODE

    /** Vérification automatique au plus toutes les [AUTO_CHECK_INTERVAL_MS], silencieuse en cas d'erreur. */
    fun autoCheck() {
        val last = prefs.getLong(KEY_LAST_CHECK, 0)
        if (System.currentTimeMillis() - last < AUTO_CHECK_INTERVAL_MS) return
        check(silent = true)
    }

    fun check(silent: Boolean = false) {
        if (job?.isActive == true) return
        job = scope.launch {
            if (!silent) _state.value = State.Checking
            _state.value = try {
                val release = Releases.parseLatest(httpGet(LATEST_URL))
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                when {
                    release == null -> if (silent) State.Idle else State.Failed("Aucune version publiée trouvée.")
                    release.versionCode > currentVersion -> State.Available(release)
                    else -> if (silent) State.Idle else State.UpToDate
                }
            } catch (e: IOException) {
                Log.w(TAG, "Vérification impossible", e)
                if (silent) State.Idle else State.Failed("Pas de connexion à GitHub (${e.message.orEmpty()}).")
            }
        }
    }

    fun clearMessage() {
        val s = _state.value
        if (s == State.UpToDate || (s is State.Failed && s.release == null)) _state.value = State.Idle
    }

    fun dismiss(release: ReleaseInfo) {
        prefs.edit().putInt(KEY_DISMISSED, release.versionCode).apply()
        _dismissed.value = release.versionCode
    }

    /** Android demande une autorisation « installer des applis inconnues » propre à FlemmeFit. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun install(release: ReleaseInfo) {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                _state.value = State.Downloading(release, 0f)
                val apk = download(release)
                _state.value = State.Installing
                commit(apk)
            } catch (e: Exception) {
                Log.e(TAG, "Mise à jour impossible", e)
                _state.value = State.Failed("Téléchargement impossible : ${e.message.orEmpty()}", release)
            }
        }
    }

    /** Appelé par [InstallResultReceiver] quand l'installeur a fini (ou a été annulé). */
    internal fun onInstallResult(success: Boolean, message: String?) {
        _state.value = if (success) State.Idle else State.Failed(message ?: "Installation annulée.", (_state.value as? State.Failed)?.release)
    }

    private fun download(release: ReleaseInfo): File {
        val target = File(context.cacheDir, "update.apk")
        val connection = open(release.apkUrl)
        try {
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        read += n
                        if (total > 0) _state.value = State.Downloading(release, (read.toFloat() / total).coerceAtMost(1f))
                    }
                }
            }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            if (release.sha256 != null && release.sha256 != sha) {
                target.delete()
                throw IOException("fichier corrompu (empreinte différente)")
            }
            return target
        } finally {
            connection.disconnect()
        }
    }

    private fun commit(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("flemmefit.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, InstallResultReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
        }
    }

    private fun httpGet(url: String): String {
        val connection = open(url)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "FlemmeFit/${BuildConfig.VERSION_NAME}")
        }

    companion object {
        private const val TAG = "AppUpdater"
        const val REPOSITORY = "Spacelenin/FlemmeFit"
        private const val LATEST_URL = "https://api.github.com/repos/$REPOSITORY/releases/latest"
        private const val KEY_LAST_CHECK = "lastCheck"
        private const val KEY_DISMISSED = "dismissedVersion"
        private const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}
