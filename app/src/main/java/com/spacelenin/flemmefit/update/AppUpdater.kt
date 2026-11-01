package com.spacelenin.flemmefit.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.spacelenin.flemmefit.BuildConfig
import com.spacelenin.flemmefit.model.ReleaseInfo
import com.spacelenin.flemmefit.model.Releases
import kotlinx.coroutines.CoroutineExceptionHandler
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

    // Filet de sécurité : une erreur imprévue en arrière-plan est journalisée au lieu de faire planter l'appli.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> Log.e(TAG, "Erreur en arrière-plan", e) })
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private var job: Job? = null
    /** Version en cours d'installation, pour « Réessayer ». */
    private var installing: ReleaseInfo? = null

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Le bandeau « plus tard » masque cette version jusqu'à la suivante. */
    private val _dismissed = MutableStateFlow(prefs.getInt(KEY_DISMISSED, 0))
    val dismissedVersion: StateFlow<Int> = _dismissed.asStateFlow()

    val currentVersion: Int get() = BuildConfig.VERSION_CODE

    private val _changelog = MutableStateFlow<ReleaseInfo?>(null)
    /** Nouveautés de la version tout juste installée, à montrer une fois. */
    val changelog: StateFlow<ReleaseInfo?> = _changelog.asStateFlow()

    /**
     * Premier lancement d'une nouvelle version : va chercher ses notes de version.
     * Une installation neuve (jamais vérifié de mise à jour) n'affiche rien.
     */
    fun loadChangelogIfUpdated() {
        val seen = prefs.getInt(KEY_SEEN_VERSION, 0)
        if (seen >= currentVersion) return
        val usedBefore = seen > 0 || prefs.contains(KEY_LAST_CHECK)
        if (!usedBefore) {
            markChangelogSeen()
            return
        }
        scope.launch {
            try {
                val release = Releases.parseLatest(httpGet("$TAG_URL$currentVersion"))
                // Versions sautées (mise à jour faite en retard) : leurs notes aussi.
                val notes = if (seen > 0 && seen < currentVersion - 1) {
                    Releases.combinedNotes(recentReleases(), after = seen, upTo = currentVersion) ?: release?.notes
                } else {
                    release?.notes
                }
                if (release != null && notes != null) _changelog.value = release.copy(notes = notes) else markChangelogSeen()
            } catch (e: Exception) {
                // Hors ligne : on retentera au prochain lancement.
                Log.w(TAG, "Notes de version indisponibles", e)
            }
        }
    }

    fun markChangelogSeen() {
        prefs.edit().putInt(KEY_SEEN_VERSION, currentVersion).apply()
        _changelog.value = null
    }

    /** Vérification automatique au plus toutes les [AUTO_CHECK_INTERVAL_MS], silencieuse en cas d'erreur. */
    fun autoCheck() {
        // Une version déjà trouvée reste affichée : inutile de redemander.
        if (_state.value !is State.Idle) return
        val last = prefs.getLong(KEY_LAST_CHECK, 0)
        if (System.currentTimeMillis() - last < AUTO_CHECK_INTERVAL_MS) return
        check(silent = true)
    }

    fun check(silent: Boolean = false) {
        if (job?.isActive == true) return
        job = scope.launch {
            if (!silent) _state.value = State.Checking
            // Noté avant la requête : hors ligne, on ne réessaie pas à chaque retour sur l'accueil.
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            _state.value = try {
                val release = Releases.parseLatest(httpGet(LATEST_URL))
                when {
                    release == null -> if (silent) State.Idle else State.Failed("Aucune version publiée trouvée.")
                    release.versionCode > currentVersion -> {
                        // Une vérification demandée à la main réaffiche une version mise de côté avec « Plus tard ».
                        if (!silent) undismiss()
                        State.Available(withSkippedNotes(release))
                    }
                    else -> if (silent) State.Idle else State.UpToDate
                }
            } catch (e: Exception) {
                // Une erreur non attrapée dans ce scope ferait planter l'app : tout est rattrapé ici.
                Log.w(TAG, "Vérification impossible", e)
                if (silent) State.Idle else State.Failed("Pas de connexion à GitHub (${e.message.orEmpty()}).")
            }
        }
    }

    /** Dernières versions publiées (vide si GitHub ne répond pas). */
    private fun recentReleases(): List<ReleaseInfo> = try {
        Releases.parseList(httpGet(LIST_URL))
    } catch (e: Exception) {
        Log.w(TAG, "Liste des versions indisponible", e)
        emptyList()
    }

    /**
     * Toutes les versions publiées, de la plus récente à la plus ancienne, pour l'écran des notes de version.
     * Lève une exception si GitHub ne répond pas. À appeler hors du fil principal.
     */
    fun allReleases(): List<ReleaseInfo> {
        val all = ArrayList<ReleaseInfo>()
        for (page in 1..MAX_PAGES) {
            val releases = Releases.parseList(httpGet("$ALL_URL$page"))
            if (releases.isEmpty()) break
            all += releases
        }
        return all.distinctBy { it.versionCode }.sortedByDescending { it.versionCode }
    }

    /** Plusieurs versions d'écart : les notes de chacune, pas seulement de la dernière. */
    private fun withSkippedNotes(release: ReleaseInfo): ReleaseInfo {
        if (release.versionCode <= currentVersion + 1) return release
        val notes = Releases.combinedNotes(recentReleases(), after = currentVersion, upTo = release.versionCode) ?: return release
        return release.copy(notes = notes)
    }

    fun clearMessage() {
        val s = _state.value
        if (s == State.UpToDate || (s is State.Failed && s.release == null)) _state.value = State.Idle
    }

    private fun undismiss() {
        prefs.edit().remove(KEY_DISMISSED).apply()
        _dismissed.value = 0
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
        installing = release
        job = scope.launch {
            try {
                _state.value = State.Downloading(release, 0f)
                val apk = download(release)
                _state.value = State.Installing
                commit(apk)
            } catch (e: Exception) {
                Log.e(TAG, "Mise à jour impossible", e)
                File(context.cacheDir, APK_FILE).delete()
                _state.value = State.Failed("Téléchargement impossible : ${e.message.orEmpty()}", release)
            }
        }
    }

    /** Appelé par [InstallResultReceiver] quand l'installeur a fini (ou a été annulé). */
    internal fun onInstallResult(success: Boolean, message: String?) {
        File(context.cacheDir, APK_FILE).delete()
        // On garde la version pour proposer « Réessayer » après une annulation.
        _state.value = if (success) State.Idle else State.Failed(message ?: "Installation annulée.", installing)
    }

    private fun download(release: ReleaseInfo): File {
        val target = File(context.cacheDir, APK_FILE)
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
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
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
        private const val APK_FILE = "update.apk"
        private const val KEY_LAST_CHECK = "lastCheck"
        private const val KEY_DISMISSED = "dismissedVersion"
        private const val KEY_SEEN_VERSION = "seenVersion"
        private const val LIST_URL = "https://api.github.com/repos/$REPOSITORY/releases?per_page=30"
        private const val ALL_URL = "https://api.github.com/repos/$REPOSITORY/releases?per_page=100&page="
        private const val MAX_PAGES = 5
        private const val TAG_URL = "https://api.github.com/repos/$REPOSITORY/releases/tags/build-"
        private const val AUTO_CHECK_INTERVAL_MS = 10 * 60 * 1000L
    }
}
