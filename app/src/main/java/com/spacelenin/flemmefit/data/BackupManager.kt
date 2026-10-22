package com.spacelenin.flemmefit.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.spacelenin.flemmefit.BuildConfig
import com.spacelenin.flemmefit.model.Backup
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Sauvegarde : un fichier JSON choisi par l'utilisateur, réécrit tout seul quand le suivi change.
 * Sans fichier choisi, rien n'est sauvegardé. Export et restauration à la main en plus.
 */
class BackupManager(
    private val context: Context,
    private val repository: ProgramRepository,
    private val settings: AppSettings,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> Log.e(TAG, "Erreur de sauvegarde", e) })

    /** Écoute le suivi : quelques secondes après le dernier changement, le fichier choisi est réécrit. */
    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            repository.state.drop(1).debounce(DEBOUNCE_MS).collect { autoSave() }
        }
        // Au lancement, si la dernière sauvegarde date (ou a échoué), on la refait.
        scope.launch {
            if (System.currentTimeMillis() - settings.backupLast.value > DAY_MS || settings.backupError.value != null) autoSave()
        }
    }

    /** Choix du fichier de sauvegarde automatique (créé par le sélecteur d'Android), puis première écriture. */
    fun chooseFile(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (e: SecurityException) {
            Log.w(TAG, "Accès durable refusé par le fournisseur", e)
        }
        settings.setBackupUri(uri.toString())
        scope.launch { autoSave() }
    }

    /** Arrête la sauvegarde automatique (le fichier existant reste où il est). */
    fun stopAuto() {
        val uri = settings.backupUri.value ?: return
        settings.setBackupUri(null)
        try {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: SecurityException) {
        }
    }

    suspend fun autoSave(): Boolean {
        val uri = settings.backupUri.value ?: return false
        return try {
            write(Uri.parse(uri))
            settings.markBackup(System.currentTimeMillis(), null)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Sauvegarde automatique impossible", e)
            settings.markBackup(null, e.message ?: e.javaClass.simpleName)
            false
        }
    }

    /** Écrit une sauvegarde complète dans [uri] (remplace le contenu). */
    suspend fun write(uri: Uri) = withContext(Dispatchers.IO) {
        val text = snapshot().encode()
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("fichier inaccessible")
        out.use { it.write(text.encodeToByteArray()) }
    }

    /** Lit une sauvegarde, ou null si le fichier n'en est pas une. */
    suspend fun read(uri: Uri): Backup? = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri) ?: return@withContext null
        Backup.decode(stream.bufferedReader().use { it.readText() })
    }

    /**
     * Remplace tout par la sauvegarde. Les réglages sont relus au démarrage :
     * l'appelant relance l'appli juste après.
     */
    suspend fun restore(backup: Backup) = withContext(Dispatchers.IO) {
        for ((file, entries) in backup.prefs) {
            if (file !in Backup.PREF_FILES) continue
            val prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE)
            val local = prefs.all.filterKeys { it in Backup.LOCAL_KEYS }
            val editor = prefs.edit().clear()
            // Ce téléphone garde son propre fichier de sauvegarde.
            for ((k, v) in local) putAny(editor, k, v)
            for ((k, v) in entries) if (k !in Backup.LOCAL_KEYS) putAny(editor, k, v.toAny())
            editor.commit()
        }
        repository.restore(backup.state)
    }

    private fun putAny(editor: android.content.SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is String -> editor.putString(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }

    private fun snapshot() = Backup(
        createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).toString(),
        appVersion = BuildConfig.VERSION_CODE,
        state = repository.state.value,
        prefs = Backup.prefsOf(Backup.PREF_FILES.associateWith { context.getSharedPreferences(it, Context.MODE_PRIVATE).all }),
    )

    private companion object {
        const val TAG = "FlemmeFitBackup"
        const val DEBOUNCE_MS = 5_000L
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
