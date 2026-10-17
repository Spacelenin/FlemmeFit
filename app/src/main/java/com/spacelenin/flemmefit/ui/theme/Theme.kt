package com.spacelenin.flemmefit.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Green = Color(0xFF2E7D5B)

private val LightColors = lightColorScheme(primary = Green)
private val DarkColors = darkColorScheme(primary = Color(0xFF7FD8A8))

/** Une couleur par programme, pour les distinguer sur l'accueil. */
private val ProgramColors = listOf(
    Color(0xFF2E7D5B),
    Color(0xFF3F6FD8),
    Color(0xFFD9672E),
    Color(0xFF8E44AD),
    Color(0xFFC2185B),
    Color(0xFF00838F),
)

fun programColor(index: Int): Color = ProgramColors[Math.floorMod(index, ProgramColors.size)]

@Composable
fun FlemmeFitTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
