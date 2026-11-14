package com.spacelenin.flemmefit.montre

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
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

    suspend fun latest(): Found? = withContext(Dispatchers.IO) {
        val releases = JSONArray(get(LIST_URL))
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
            val connection = open(found.url)
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

    private fun get(url: String): String {
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
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "FlemmeFitMontre")
        }
}
