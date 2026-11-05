package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.lerp
import com.spacelenin.flemmefit.ui.theme.FitPalette
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.ui.theme.LocalFitTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Fond d'un écran entier : le fond du thème, ou la salle claire au sol carrelé du thème fit. */
@Composable
fun Modifier.screenBackground(): Modifier {
    if (!LocalFitTheme.current) return background(MaterialTheme.colorScheme.background)
    val room = LocalFitRoom.current
    return drawBehind { drawFitRoom(room) }
}

/**
 * La salle : murs blancs teintés de la couleur [tint] vers le bas, une plinthe colorée à l'horizon, un sol carrelé
 * en perspective, teinté lui aussi, qui s'estompe vers l'horizon.
 */
private fun DrawScope.drawFitRoom(tint: Color) {
    // Le sol n'occupe que le bas de l'écran : la plinthe ne passe pas au milieu du contenu.
    val horizon = size.height * 0.8f
    val floor = size.height - horizon
    drawRect(
        Brush.verticalGradient(listOf(Color.White, lerp(Color.White, tint, 0.08f), lerp(Color.White, tint, 0.2f)), endY = horizon),
        size = androidx.compose.ui.geometry.Size(size.width, horizon),
    )
    drawRect(
        Brush.verticalGradient(listOf(lerp(Color.White, tint, 0.06f), lerp(Color(0xFFF2F4F6), tint, 0.03f)), startY = horizon, endY = size.height),
        topLeft = Offset(0f, horizon),
    )
    // Reflet blanc, comme un sol brillant.
    drawOval(
        Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0f)),
            center = Offset(size.width / 2, horizon + floor * 0.35f),
            radius = size.width * 0.45f,
        ),
        topLeft = Offset(size.width * 0.05f, horizon),
        size = androidx.compose.ui.geometry.Size(size.width * 0.9f, floor * 0.7f),
    )
    // Plinthe : une bande de la couleur de la salle, avec un liseré clair.
    val plinth = 4.dp.toPx()
    drawRect(lerp(tint, Color.White, 0.55f), topLeft = Offset(0f, horizon - plinth), size = androidx.compose.ui.geometry.Size(size.width, plinth))
    drawLine(lerp(tint, Color.White, 0.8f), Offset(0f, horizon - plinth), Offset(size.width, horizon - plinth), strokeWidth = 2.dp.toPx())
    val line = lerp(Color(0xFFCBD3DA), tint, 0.12f)
    // Lignes horizontales : de plus en plus serrées vers l'horizon.
    var t = 1f
    while (t > 0.04f) {
        val y = horizon + floor * t * t
        drawLine(line.copy(alpha = 0.15f + 0.6f * t), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.2f + 1.2f * t)
        t -= 0.09f
    }
    // Lignes fuyantes vers un point au-dessus de l'horizon.
    val vanish = Offset(size.width / 2, horizon - floor * 0.6f)
    val count = 14
    for (i in -count..count) {
        val xBottom = size.width / 2 + i * size.width / 7f
        val k = (horizon - vanish.y) / (size.height - vanish.y)
        val xTop = vanish.x + (xBottom - vanish.x) * k
        drawLine(
            Brush.verticalGradient(listOf(line.copy(alpha = 0f), line.copy(alpha = 0.7f)), startY = horizon, endY = size.height),
            Offset(xTop, horizon), Offset(xBottom, size.height), strokeWidth = 1.4f,
        )
    }
    // Brume claire sur l'horizon.
    drawRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0f)), startY = horizon, endY = horizon + floor * 0.35f),
        topLeft = Offset(0f, horizon),
    )
}

/** Tampon d'une séance faite : une pastille festonnée, un peu penchée, avec une coche. */
@Composable
fun FitStamp(color: Color, modifier: Modifier = Modifier, size: Dp = 26.dp, faded: Boolean = false) {
    Canvas(modifier.size(size).rotate(-14f)) {
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val outer = this.size.minDimension / 2
        val bumps = 12
        val path = Path()
        for (i in 0..bumps * 8) {
            val a = 2 * PI * i / (bumps * 8)
            val r = outer * (0.9f + 0.1f * cos(a * bumps).toFloat())
            val p = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        path.close()
        val alpha = if (faded) 0.55f else 1f
        drawPath(path, color.copy(alpha = alpha))
        drawCircle(Color.White.copy(alpha = 0.9f * alpha), radius = outer * 0.66f, center = c, style = Stroke(width = outer * 0.09f))
        val check = Path().apply {
            moveTo(c.x - outer * 0.36f, c.y + outer * 0.02f)
            lineTo(c.x - outer * 0.08f, c.y + outer * 0.3f)
            lineTo(c.x + outer * 0.4f, c.y - outer * 0.3f)
        }
        drawPath(check, Color.White.copy(alpha = alpha), style = Stroke(width = outer * 0.17f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * Avis en bulle : sur le thème fit, une bulle blanche au bord coloré avec sa petite pointe ; sinon le [plain]
 * habituel (carte teintée).
 */
@Composable
fun NoticeBubble(color: Color, modifier: Modifier = Modifier, plain: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    if (!LocalFitTheme.current) {
        plain()
        return
    }
    Box(modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .border(2.5.dp, color, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            content = content,
        )
        // La pointe de la bulle, en bas à gauche.
        Canvas(Modifier.align(Alignment.BottomStart).offset(x = 28.dp, y = 8.dp).size(18.dp, 11.dp)) {
            val tip = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width / 2, size.height)
                lineTo(size.width, 0f)
            }
            drawPath(tip, Color.White)
            drawPath(tip, color, style = Stroke(width = 2.5.dp.toPx(), join = StrokeJoin.Round))
            // Recouvre le bord de la bulle à la jonction.
            drawLine(Color.White, Offset(2.5.dp.toPx(), 0f), Offset(size.width - 2.5.dp.toPx(), 0f), strokeWidth = 3.dp.toPx())
        }
    }
}

/**
 * Bouton principal. Thème fit : une pastille blanche au contour épais coloré (la couleur demandée, sinon le vert),
 * texte gris foncé. Sinon le bouton plein habituel.
 */
@Composable
fun PillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (!LocalFitTheme.current) {
        Button(onClick = onClick, modifier = modifier, enabled = enabled, colors = colors ?: ButtonDefaults.buttonColors(), content = content)
        return
    }
    val scheme = MaterialTheme.colorScheme
    val accent = colors?.containerColor?.takeIf { it != scheme.onSurface && it != Color.Unspecified } ?: scheme.outline
    OutlinedButton(
        onClick = onClick,
        // Petite brillance : blanc en haut, gris très clair en bas.
        modifier = modifier
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFE9EEF2), Color(0xFFD8E0E6))), CircleShape)
            .drawBehind {
                // Reflet nacré sur la moitié haute.
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), endY = size.height * 0.5f),
                    topLeft = Offset(size.height * 0.3f, 2.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size((size.width - size.height * 0.6f).coerceAtLeast(0f), size.height * 0.42f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                )
            },
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = FitPalette.Text),
        border = BorderStroke(3.dp, if (enabled) accent else scheme.outlineVariant),
        content = content,
    )
}
