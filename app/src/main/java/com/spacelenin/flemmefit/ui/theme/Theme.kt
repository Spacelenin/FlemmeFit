package com.spacelenin.flemmefit.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.R

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

/** Les mêmes, plus soutenues pour rester lisibles sur fond clair. */
private val FitProgramColors = listOf(
    Color(0xFF2A9FD8),
    Color(0xFF3FA34D),
    Color(0xFFF08A1C),
    Color(0xFFE0457B),
    Color(0xFF17A99A),
)

/** Le thème clair façon console de salon (bêta) est-il actif ? */
val LocalFitTheme = staticCompositionLocalOf { false }

/** Vert « fit », bleu ciel, orange : les couleurs du thème clair. */
object FitPalette {
    val Green = Color(0xFF5BB52A)
    val GreenDark = Color(0xFF3C8E17)
    val GreenPale = Color(0xFFE6F6D8)
    val Sky = Color(0xFF39ABE0)
    val SkyPale = Color(0xFFDDF1FB)
    val Orange = Color(0xFFF39A2B)
    val Text = Color(0xFF3B3F44)
    val TextSoft = Color(0xFF767C83)
    val Line = Color(0xFFD5DCE2)
    val Saturday = Color(0xFF1E9BB0)
    val Sunday = Color(0xFFE0383E)
    val Highlight = Color(0x66E8F5A0)
}

private val FitColors = lightColorScheme(
    primary = FitPalette.GreenDark,
    onPrimary = Color.White,
    primaryContainer = FitPalette.GreenPale,
    onPrimaryContainer = Color(0xFF1F4D0C),
    secondary = FitPalette.Sky,
    onSecondary = Color.White,
    secondaryContainer = FitPalette.SkyPale,
    onSecondaryContainer = Color(0xFF0B4A66),
    tertiary = FitPalette.Orange,
    onTertiary = Color.White,
    // Transparent : le fond (blanc, sol carrelé) est dessiné une fois sous tous les écrans.
    background = Color.Transparent,
    onBackground = FitPalette.Text,
    surface = Color.White,
    onSurface = FitPalette.Text,
    surfaceVariant = Color(0xFFEDF1F4),
    onSurfaceVariant = FitPalette.TextSoft,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFCFD),
    surfaceContainer = Color(0xFFF6F8FA),
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFEFF3F6),
    inverseSurface = FitPalette.Text,
    inverseOnSurface = Color.White,
    outline = FitPalette.Green,
    outlineVariant = FitPalette.Line,
    error = Color(0xFFD9363E),
    onError = Color.White,
    errorContainer = Color(0xFFFDE3E3),
    onErrorContainer = Color(0xFF7A1418),
)

private val Rounded = FontFamily(
    Font(R.font.nunito_semibold, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.Medium),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_extrabold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
    Font(R.font.nunito_black, FontWeight.Black),
)

private fun Typography.rounded(): Typography {
    fun TextStyle.r() = copy(fontFamily = Rounded)
    // Grands titres : vert, avec un léger relief.
    val title = Shadow(Color(0x403C8E17), Offset(0f, 3f), 4f)
    return Typography(
        displayLarge = displayLarge.r(), displayMedium = displayMedium.r(), displaySmall = displaySmall.r(),
        headlineLarge = headlineLarge.r().copy(fontWeight = FontWeight.Black, color = FitPalette.Green, shadow = title),
        headlineMedium = headlineMedium.r().copy(fontWeight = FontWeight.Black, color = FitPalette.Green, shadow = title),
        headlineSmall = headlineSmall.r(),
        titleLarge = titleLarge.r().copy(fontWeight = FontWeight.Black, color = FitPalette.Green, shadow = title),
        titleMedium = titleMedium.r(), titleSmall = titleSmall.r(),
        bodyLarge = bodyLarge.r(), bodyMedium = bodyMedium.r(), bodySmall = bodySmall.r(),
        labelLarge = labelLarge.r().copy(fontWeight = FontWeight.Bold), labelMedium = labelMedium.r(), labelSmall = labelSmall.r(),
    )
}

private val FitTypography = Typography().rounded()

private val FitShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Nombre de couleurs de programme au choix (la première suit le thème). */
val ProgramColorCount get() = OtherProgramColors.size + 1

@Composable
fun programColor(index: Int): Color {
    val i = Math.floorMod(index, OtherProgramColors.size + 1)
    val others = if (LocalFitTheme.current) FitProgramColors else OtherProgramColors
    return if (i == 0) MaterialTheme.colorScheme.primary else others[i - 1]
}

/** [fit] : thème clair façon console de salon (bêta), à la place des couleurs choisies. */
@Composable
fun FlemmeFitTheme(theme: AppTheme = AppTheme.ORANGE, fit: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFitTheme provides fit) {
        if (fit) {
            MaterialTheme(colorScheme = FitColors, typography = FitTypography, shapes = FitShapes, content = content)
        } else {
            MaterialTheme(colorScheme = theme.colors(), content = content)
        }
    }
}
