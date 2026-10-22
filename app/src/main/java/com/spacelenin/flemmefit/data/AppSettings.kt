package com.spacelenin.flemmefit.data

import android.content.Context
import com.spacelenin.flemmefit.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Préférences d'affichage, hors de l'état des programmes. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(AppTheme.of(prefs.getString(KEY_THEME, null)))
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    fun setTheme(theme: AppTheme) {
        prefs.edit().putString(KEY_THEME, theme.name).apply()
        _theme.value = theme
    }

    private val _tutorialOffered = MutableStateFlow(prefs.getBoolean(KEY_TUTORIAL, false))
    /** Le tuto a déjà été proposé (accepté ou non) : on ne le repropose plus d'office. */
    val tutorialOffered: StateFlow<Boolean> = _tutorialOffered.asStateFlow()

    fun markTutorialOffered() {
        prefs.edit().putBoolean(KEY_TUTORIAL, true).apply()
        _tutorialOffered.value = true
    }

    private val _backupUri = MutableStateFlow(prefs.getString(KEY_BACKUP_URI, null))
    /** Fichier de sauvegarde automatique choisi par l'utilisateur. Null : pas de sauvegarde automatique. */
    val backupUri: StateFlow<String?> = _backupUri.asStateFlow()

    private val _backupLast = MutableStateFlow(prefs.getLong(KEY_BACKUP_LAST, 0L))
    /** Heure (epoch ms) de la dernière sauvegarde réussie, 0 si aucune. */
    val backupLast: StateFlow<Long> = _backupLast.asStateFlow()

    private val _backupError = MutableStateFlow(prefs.getString(KEY_BACKUP_ERROR, null))
    /** Raison de l'échec de la dernière sauvegarde automatique, null si elle a réussi. */
    val backupError: StateFlow<String?> = _backupError.asStateFlow()

    fun setBackupUri(uri: String?) {
        prefs.edit().putString(KEY_BACKUP_URI, uri).remove(KEY_BACKUP_ERROR).apply()
        _backupUri.value = uri
        _backupError.value = null
    }

    fun markBackup(at: Long?, error: String?) {
        prefs.edit().apply { if (at != null) putLong(KEY_BACKUP_LAST, at) }.putString(KEY_BACKUP_ERROR, error).apply()
        if (at != null) _backupLast.value = at
        _backupError.value = error
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_TUTORIAL = "tutorial_offered"
        // Mêmes clés que Backup.LOCAL_KEYS : elles ne partent pas dans la sauvegarde.
        const val KEY_BACKUP_URI = "backup_uri"
        const val KEY_BACKUP_LAST = "backup_last"
        const val KEY_BACKUP_ERROR = "backup_error"
    }
}
