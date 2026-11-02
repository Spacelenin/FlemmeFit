package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.model.WeekVolume
import com.spacelenin.flemmefit.ui.theme.FitPalette

/** Séries par semaine, thème fit : un panneau à bandeau, une courbe à gros points, la semaine choisie surlignée. */
@Composable
fun FitWeeklyChart(weeks: List<WeekVolume>, color: Color) {
    var selected by rememberSaveable { mutableIntStateOf(weeks.lastIndex) }
    val top = (weeks.maxOfOrNull { it.sets } ?: 0).coerceAtLeast(1)
    val description = weeks.joinToString { "semaine du ${it.monday.format(DayMonth)} : ${it.sets} séries" }
    val frame = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(frame)
            .background(Color.White)
            .border(3.dp, color, frame),
    ) {
        Text(
            "Séries par semaine",
            style = MaterialTheme.typography.titleLarge.copy(color = Color.White, shadow = Shadow(Color(0x55000000), Offset(0f, 2f), 3f)),
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().background(color).padding(vertical = 8.dp),
        )
        weeks.getOrNull(selected)?.let { w ->
            Text(
                "Semaine du ${w.monday.format(DayMonth)} : ${w.sets} séries" +
                    (if (w.boss) " · Boss 💀" else "") + (if (w.deload) " · récup 🌿" else ""),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(horizontal = 8.dp, vertical = 12.dp)
                .semantics { contentDescription = description }
                .pointerInput(weeks.size) {
                    detectTapGestures { p -> selected = (p.x / (size.width.toFloat() / weeks.size)).toInt().coerceIn(0, weeks.lastIndex) }
                },
        ) {
            if (weeks.isEmpty()) return@Canvas
            val step = size.width / weeks.size
            // Quadrillage léger, et la semaine choisie surlignée sur toute la hauteur.
            for (k in 0..4) {
                val y = size.height * k / 4
                drawLine(FitPalette.Line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            weeks.indices.forEach { i ->
                val x = step * i
                drawLine(FitPalette.Line.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            }
            drawRect(FitPalette.Highlight, topLeft = Offset(step * selected, -12.dp.toPx()), size = Size(step, size.height + 24.dp.toPx()))
            val pts = weeks.mapIndexed { i, w -> Offset(step * (i + 0.5f), size.height * (1f - w.sets.toFloat() / top)) }
            val path = Path().apply { pts.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
            drawPath(path, color, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            pts.forEachIndexed { i, o ->
                val r = if (i == selected) 8.dp.toPx() else 6.dp.toPx()
                val ring = when {
                    weeks[i].boss -> BossColorStatic
                    weeks[i].deload -> FitDeload
                    else -> color
                }
                drawCircle(if (i == selected) ring else Color.White, radius = r, center = o)
                drawCircle(ring, radius = r, center = o, style = Stroke(width = 3.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 10.dp)) {
            weeks.forEachIndexed { i, w ->
                Text(
                    w.monday.format(DayMonth),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (i == selected) FontWeight.Black else FontWeight.Normal,
                    color = if (i == selected) FitPalette.Text else FitPalette.TextSoft,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private val BossColorStatic = Color(0xFFE05252)
private val FitDeload = Color(0xFF2E9E57)
