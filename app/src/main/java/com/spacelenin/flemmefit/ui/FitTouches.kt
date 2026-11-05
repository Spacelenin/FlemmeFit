package com.spacelenin.flemmefit.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Fines rayures horizontales par-dessus un fond : la texture des bandeaux et panneaux du thème fit. */
fun Modifier.fitStripes(color: Color = Color.White, alpha: Float = 0.16f, spacing: Dp = 3.dp): Modifier = drawBehind {
    val step = spacing.toPx().coerceAtLeast(2f)
    val stroke = (step / 3f).coerceAtLeast(1f)
    var y = step / 2
    while (y < size.height) {
        drawLine(color.copy(alpha = alpha), Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
        y += step
    }
}

/** Fond de panneau ligné, très pâle (sous le texte des fenêtres et des consignes). */
fun Modifier.linedPaper(): Modifier = fitStripes(Color(0xFF8C9AA6), alpha = 0.07f, spacing = 4.dp)

private val Red = Color(0xFFE0262B)
// Chiffres et leur unité (« 20 s », « 10 fois », « 2 × 12 »), et quelques mots de prudence.
private val Highlight = Regex(
    "\\b\\d+(?:[,.]\\d+)?(?:\\s?(?:×\\s?\\d+|s\\b|min\\b|fois\\b|reps?\\b|secondes?\\b|%))?|\\b(?:jamais|stop|sans douleur|sans à-coups|dos droit|corps gainé|lentement)\\b",
    RegexOption.IGNORE_CASE,
)

/** Les chiffres et les mots importants d'une consigne, en rouge. */
fun highlightKeywords(text: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    Highlight.findAll(text).forEach { m ->
        append(text.substring(last, m.range.first))
        withStyle(SpanStyle(color = Red, fontWeight = FontWeight.Bold)) { append(m.value) }
        last = m.range.last + 1
    }
    append(text.substring(last))
}

/** Étoiles de niveau : [filled] sur [total], pleines dorées et vides grises. */
@Composable
fun FitStars(filled: Int, total: Int = 4, modifier: Modifier = Modifier) {
    Row(modifier.semantics { contentDescription = "Niveau $filled sur $total" }) {
        repeat(total) { i ->
            Text(
                "★",
                style = MaterialTheme.typography.labelMedium.copy(shadow = null),
                color = if (i < filled) Color(0xFFF2B705) else Color(0xFFB9C1C8),
            )
        }
    }
}

/** Étoiles d'un exo d'après son palier : la première au départ, la dernière au palier le plus haut. Null sans paliers. */
fun levelStars(palier: Int?, levelCount: Int, total: Int = 4): Int? {
    if (palier == null || levelCount < 2) return null
    return 1 + (palier.coerceIn(0, levelCount - 1) * (total - 1) + (levelCount - 2)) / (levelCount - 1)
}

/**
 * Bannière de fête : une bande rose rayée qui traverse l'écran, un texte jaune cerclé de rouge. Elle entre, reste
 * un moment, puis repart ; [onDone] quand elle a fini.
 */
@Composable
fun RecordBanner(text: String, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val slide = remember(text) { Animatable(-1f) }
    LaunchedEffect(text) {
        slide.animateTo(0f, tween(450, easing = FastOutSlowInEasing))
        delay(1800)
        slide.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
        onDone()
    }
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val width = constraints.maxWidth.toFloat()
        val measurer = rememberTextMeasurer()
        val style = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .graphicsLayer { translationX = slide.value * width }
                .background(Brush.verticalGradient(listOf(Color(0xFFF07AA0), Color(0xFFD9406E), Color(0xFFF07AA0))))
                .fitStripes(Color.White, alpha = 0.18f)
                .semantics { contentDescription = text },
        ) {
            // Texte jaune à contour rouge : le contour d'abord, le texte par-dessus.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .drawWithContent {
                        val layout = measurer.measure(AnnotatedString(text), style)
                        val at = Offset((size.width - layout.size.width) / 2, (size.height - layout.size.height) / 2)
                        drawText(layout, color = Color(0xFFB0102E), topLeft = at, drawStyle = Stroke(width = 6.dp.toPx()))
                        drawText(layout, color = Color(0xFFFFE23D), topLeft = at, shadow = Shadow(Color(0x55000000), Offset(0f, 3f), 4f))
                    },
            )
        }
    }
}
