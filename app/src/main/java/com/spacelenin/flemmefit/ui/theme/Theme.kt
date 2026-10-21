package com.spacelenin.flemmefit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Thèmes au choix dans les réglages ; l'orange est celui par défaut. */
enum class AppTheme(
    val label: String,
    val accent: Color,
    val onAccent: Color,
    /** Fond teinté des cartes mises en avant. */
    val container: Color,
    val onContainer: Color,
    /** Fond teinté plus discret (bandeaux, puces). */
    val soft: Color,
) {
    ORANGE("Orange", Color(0xFFFF9E5E), Color(0xFF1A0E05), Color(0xFF3B2A1E), Color(0xFFFFDCC4), Color(0xFF2A1E16)),
    VIOLET("Violet sobre", Color(0xFFB4A2E0), Color(0xFF1B1430), Color(0xFF2E2640), Color(0xFFE6DDFB), Color(0xFF221C2E)),
    BLEU("Bleu glacier", Color(0xFF8CC8F0), Color(0xFF0A1A26), Color(0xFF1E2E3B), Color(0xFFD4ECFB), Color(0xFF16222C)),
    VERT("Vert sauge", Color(0xFF9ED9A8), Color(0xFF0E1F12), Color(0xFF203326), Color(0xFFD6F2DB), Color(0xFF172419)),
    ;

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: ORANGE
    }
}

private fun AppTheme.colors() = darkColorScheme(
    primary = accent,
    onPrimary = onAccent,
    primaryContainer = container,
    onPrimaryContainer = onContainer,
    secondary = onContainer,
    onSecondary = onAccent,
    secondaryContainer = soft,
    onSecondaryContainer = onContainer,
    tertiary = Color(0xFF8FE0A5),
    onTertiary = Color(0xFF0E1F12),
    background = Color(0xFF0F0F0F),
    onBackground = Color(0xFFF1F1F1),
    surface = Color(0xFF0F0F0F),
    onSurface = Color(0xFFF1F1F1),
    surfaceVariant = Color(0xFF272727),
    onSurfaceVariant = Color(0xFFAAAAAA),
    surfaceContainerLowest = Color(0xFF0A0A0A),
    surfaceContainerLow = Color(0xFF161616),
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF1E1E1E),
    surfaceContainerHighest = Color(0xFF262626),
    inverseSurface = Color(0xFFF1F1F1),
    inverseOnSurface = Color(0xFF0F0F0F),
    outline = Color(0xFF5A5A5A),
    outlineVariant = Color(0xFF2C2C2C),
    error = Color(0xFFE05252),
    onError = Color(0xFF1F0808),
    errorContainer = Color(0xFF3A1A1A),
    onErrorContainer = Color(0xFFF4C7C7),
)

/** Une couleur par programme : le premier prend celle du thème, les suivants s'en distinguent. */
private val OtherProgramColors = listOf(
    Color(0xFF7FD1FF),
    Color(0xFF9ED9A8),
    Color(0xFFF2C14E),
    Color(0xFFF08BB4),
    Color(0xFF6FD6CB),
)

/** Nombre de couleurs de programme au choix (la première suit le thème). */
val ProgramColorCount get() = OtherProgramColors.size + 1

@Composable
fun programColor(index: Int): Color {
    val i = Math.floorMod(index, OtherProgramColors.size + 1)
    return if (i == 0) MaterialTheme.colorScheme.primary else OtherProgramColors[i - 1]
}

@Composable
fun FlemmeFitTheme(theme: AppTheme = AppTheme.ORANGE, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = theme.colors(), content = content)
}
