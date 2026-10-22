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

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_TUTORIAL = "tutorial_offered"
    }
}
