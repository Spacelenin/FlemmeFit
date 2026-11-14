package com.spacelenin.flemmefit.montre

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text

/* Le thème Wii Fit en version ronde : boutons bombés, bandeau rayé, anneau de progression sur le bord. */

val Nunito = FontFamily(
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
    Font(R.font.nunito_black, FontWeight.Black),
)

/** Couleur d'un contexte : [c] la teinte, [light] le haut du dégradé, [dark] la marche et le liseré. */
data class Ctx(val c: Color, val light: Color, val dark: Color)

val Green = Ctx(Color(0xFF5AAF2E), Color(0xFF9BDC6C), Color(0xFF3D8A1E))
val Red = Ctx(Color(0xFFD93A3A), Color(0xFFF08A8A), Color(0xFFA82424))
val Sky = Ctx(Color(0xFF2B9FD6), Color(0xFF7FD0F2), Color(0xFF1C74A0))
val Orange = Ctx(Color(0xFFE07A12), Color(0xFFF7B766), Color(0xFFAD5C08))
val Teal = Ctx(Color(0xFF2F9D8A), Color(0xFF7FD3C2), Color(0xFF1F7466))
val Grey = Ctx(Color(0xFF6E7C86), Color(0xFFA9B6BF), Color(0xFF4B565E))
val Purple = Ctx(Color(0xFF8A5CC7), Color(0xFFC0A2EA), Color(0xFF5E3A93))

fun ctxFor(mode: String): Ctx = when (mode) {
    WatchToday.MODE_BOSS -> Red
    WatchToday.MODE_BURST -> Orange
    WatchToday.MODE_DELOAD -> Teal
    WatchToday.MODE_CHILL -> Sky
    WatchToday.MODE_FORM -> Purple
    else -> Green
}

/** Fond blanc Wii Fit, ou nuit (fond noir) sous 15 % de batterie. */
data class Palette(
    val night: Boolean,
    val bg: Color,
    val ink: Color,
    val soft: Color,
    val track: Color,
    val plain: List<Color>,
    val plainRing: Color,
    val plainStep: Color,
    val plainText: Color,
    val chip: Color,
)

val Day = Palette(
    night = false, bg = Color.White, ink = Color(0xFF33393D), soft = Color(0xFF5D6B74), track = Color(0x388CA0AF),
    plain = listOf(Color.White, Color(0xFFEEF2F5), Color(0xFFDFE7EC)), plainRing = Color(0xFFB9C6CF), plainStep = Color(0xFF9DB8C8),
    plainText = Color(0xFF33393D), chip = Color(0xFFF3F6F8),
)
val Night = Palette(
    night = true, bg = Color.Black, ink = Color(0xFFF2F5F7), soft = Color(0xFF9AA7B0), track = Color(0x1FFFFFFF),
    plain = listOf(Color(0xFF4A535A), Color(0xFF3A4248), Color(0xFF2C3338)), plainRing = Color(0xFF11161A), plainStep = Color(0xFF11161A),
    plainText = Color(0xFFF2F5F7), chip = Color(0xFF1E2428),
)

val LocalPalette = compositionLocalOf { Day }
val LocalCtx = compositionLocalOf { Green }

const val NIGHT_BELOW = 15

@Composable
fun Themed(night: Boolean, ctx: Ctx, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides if (night) Night else Day, LocalCtx provides ctx, content = content)
}

private fun Color.deeper() = lerp(this, Color.Black, 0.18f)

/** Fines rayures horizontales, la matière des bandeaux et des boutons. Calculées une fois par taille, pas à chaque image. */
fun Modifier.stripes(color: Color = Color.White, alpha: Float = 0.16f, spacing: Dp = 3.dp): Modifier = drawWithCache {
    val step = spacing.toPx()
    val lines = Path()
    var y = step / 2
    while (y < size.height) {
        lines.moveTo(0f, y)
        lines.lineTo(size.width, y)
        y += step
    }
    val stroke = Stroke(1f)
    val tint = color.copy(alpha = alpha)
    onDrawBehind { drawPath(lines, tint, style = stroke) }
}

/** Le bouton bombé : marche dessous, liseré fin, bord blanc, dégradé, rayures, reflet. */
fun Modifier.glossy(fill: List<Color>, ring: Color, step: Color, shape: Shape, edge: Color = Color.White): Modifier = this
    .padding(bottom = 3.dp)
    .drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        translate(top = 3.dp.toPx()) { drawOutline(outline, step) }
    }
    .border(1.dp, ring, shape)
    .padding(1.dp)
    .border(2.dp, edge, shape)
    .clip(shape)
    .background(Brush.verticalGradient(0f to fill[0], 0.48f to fill[1], 1f to fill[2]))
    .stripes()
    .drawBehind {
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0f)), endY = size.height * 0.45f),
            topLeft = Offset(size.width * 0.1f, 2.dp.toPx()),
            size = Size(size.width * 0.8f, size.height * 0.38f),
            cornerRadius = CornerRadius(size.height * 0.4f),
        )
    }

fun text(size: TextUnit, weight: FontWeight = FontWeight.ExtraBold) = TextStyle(fontFamily = Nunito, fontSize = size, fontWeight = weight, textAlign = TextAlign.Center)

/** Bouton bombé : de la couleur du contexte, ou blanc ([plain]). */
@Composable
fun FitButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    plain: Boolean = false,
    big: Boolean = false,
    ctx: Ctx = LocalCtx.current,
) {
    val pal = LocalPalette.current
    val shape = RoundedCornerShape(50)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = if (big) 46.dp else 38.dp)
            .glossy(
                fill = if (plain) pal.plain else listOf(ctx.light, ctx.c, ctx.c.deeper()),
                ring = if (plain) pal.plainRing else ctx.dark,
                step = if (plain) pal.plainStep else ctx.dark,
                shape = shape,
                edge = if (pal.night) Color(0xFF1C1C1C) else Color.White,
            )
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = if (big) 22.dp else 16.dp, vertical = 6.dp),
    ) {
        Text(label, style = text(if (big) 16.sp else 14.sp, FontWeight.Black), color = if (plain) pal.plainText else Color.White, maxLines = 1)
    }
}

/** Bulle ronde (− / +). */
@Composable
fun FitRound(label: String, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(46.dp)
            .glossy(pal.plain, pal.plainRing, pal.plainStep, CircleShape, edge = if (pal.night) Color(0xFF1C1C1C) else Color.White)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
    ) { Text(label, style = text(20.sp, FontWeight.Black), color = pal.plainText) }
}

@Composable
fun Row2(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}

/** L'écran rond : fond (ligné le jour), anneau de progression sur le bord, contenu centré. */
@Composable
fun Dial(progress: Float, ctx: Ctx = LocalCtx.current, content: @Composable BoxScope.() -> Unit) {
    val pal = LocalPalette.current
    Box(
        Modifier
            .fillMaxSize()
            .background(pal.bg)
            .then(if (pal.night) Modifier else Modifier.stripes(Color(0xFF8C9AA6), alpha = 0.08f, spacing = 4.dp)),
    ) {
        Ring(progress, ctx)
        content()
    }
}

/** Anneau du bord : piste pâle, progression de la couleur du contexte, stries fines en biais. */
@Composable
fun Ring(progress: Float, ctx: Ctx) {
    val pal = LocalPalette.current
    val p = progress.coerceIn(0f, 1f)
    Box(
        Modifier.fillMaxSize().drawWithCache {
            // Tout ce qui ne dépend que de la taille est préparé une fois.
            val thick = 9.dp.toPx()
            val inset = 3.dp.toPx() + thick / 2
            val topLeft = Offset(inset, inset)
            val arc = Size(size.width - inset * 2, size.height - inset * 2)
            val stroke = Stroke(thick)
            val brush = Brush.verticalGradient(listOf(ctx.light, ctx.c, ctx.dark))
            onDrawBehind {
                drawArc(pal.track, 0f, 360f, false, topLeft, arc, style = stroke)
                // Sans les stries découpées en anneau : coûteuses à chaque image sur la montre.
                if (p > 0f) drawArc(brush, -90f, 360f * p, false, topLeft, arc, style = stroke)
            }
        },
    )
}

/** Bandeau rayé du haut, de la couleur du contexte. */
@Composable
fun BoxScope.Band(title: String, extra: String? = null, ctx: Ctx = LocalCtx.current) {
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(56.dp)
            .background(Brush.verticalGradient(listOf(ctx.light, ctx.c)))
            .stripes(alpha = 0.18f)
            .padding(bottom = 7.dp),
    ) {
        Text(
            title + (extra?.let { "  $it" } ?: ""),
            style = text(15.sp, FontWeight.Black), color = Color.White, maxLines = 1,
        )
    }
}

@Composable
fun Kicker(value: String, color: Color = LocalPalette.current.soft) {
    Text(value, style = text(12.sp), color = color, maxLines = 2)
}

@Composable
fun Title(value: String, size: TextUnit = 19.sp) {
    Text(value, style = text(size, FontWeight.Black).copy(lineHeight = size * 1.15f), color = LocalPalette.current.ink, maxLines = 3)
}

@Composable
fun Meta(value: String) {
    Text(value, style = text(12.sp, FontWeight.SemiBold), color = LocalPalette.current.soft, maxLines = 3)
}

/** Les étoiles du palier (★★★☆). */
@Composable
fun Stars(level: Int, count: Int) {
    val filled = level.coerceIn(0, count)
    Text(
        "★".repeat(filled) + "☆".repeat(count - filled),
        style = text(14.sp, FontWeight.Black), color = Color(0xFFF2B705),
    )
}

/** Pastille de contexte (« 💀 objectif 24 »). */
@Composable
fun Chip(value: String, ctx: Ctx = LocalCtx.current) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.verticalGradient(listOf(ctx.light, ctx.c)))
            .padding(horizontal = 10.dp, vertical = 2.dp),
    ) { Text(value, style = text(11.sp, FontWeight.Black), color = Color.White) }
}

/** Texte posé à la verticale, pour nommer les pages voisines sur les bords. */
fun Modifier.vertical(clockwise: Boolean): Modifier = this
    .layout { measurable, _ ->
        val p = measurable.measure(Constraints())
        layout(p.height, p.width) { p.place((p.height - p.width) / 2, (p.width - p.height) / 2) }
    }
    .rotate(if (clockwise) 90f else -90f)
