package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.model.TestProgress
import java.time.format.DateTimeFormatter

private val TestDay = DateTimeFormatter.ofPattern("d/MM")

/** Les tests du programme : courbe des résultats et prochain niveau. */
fun LazyListScope.statsTestItems(tests: List<TestProgress>, color: Color) {
    val shown = tests.filter { it.points.isNotEmpty() || it.goals.isNotEmpty() }
    if (shown.isEmpty()) return
    item(key = "tests-title") {
        Text(
            "Tests 📏",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
        )
    }
    items(shown, key = { "test-${it.test.id}" }) { TestCard(it, color) }
}

@Composable
private fun TestCard(t: TestProgress, color: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text(t.test.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val first = t.first
                    val best = t.best
                    if (first != null && best != null && t.points.size > 1) {
                        Text(
                            "Départ ${t.format(first)} · record ${t.format(best)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    t.current?.let(t::format) ?: "–",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = color,
                )
            }
            if (t.points.size > 1) TestCurve(t, color)

            t.toNextLevel?.let { missing ->
                Text(
                    "Encore ${t.format(missing)} au prochain test pour monter d'un niveau.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            t.goals.forEach { g ->
                Text(
                    "${g.exercise} : niveau ${g.level + 1}/${g.levelCount}" + when {
                        g.nextFrom == null -> " · niveau max 🏁"
                        else -> " · au test ${t.format(g.nextFrom)} → ${g.nextLabel ?: "niveau ${g.level + 2}"}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                t.nextDate?.let { "Prochain test le ${it.longLabel()}" } ?: if (t.points.isEmpty()) "Pas encore de résultat." else "",
                style = MaterialTheme.typography.labelMedium,
                color = color,
            )
        }
    }
}

/** Courbe simple : un point par test, régulièrement espacés, valeur au-dessus. */
@Composable
private fun TestCurve(t: TestProgress, color: Color) {
    // Les 8 derniers tests : au-delà, les étiquettes ne tiennent plus.
    val points = t.points.takeLast(8)
    val values = points.map { it.value }
    val top = values.max()
    val bottom = values.min()
    val span = (top - bottom).coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.outlineVariant
    val description = points.joinToString { "${it.date.format(TestDay)} : ${t.format(it.value)}" }

    Column {
        Row(Modifier.fillMaxWidth()) {
            points.forEach { p ->
                Text(
                    t.format(p.value),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (p.value == top) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Canvas(Modifier.fillMaxWidth().height(72.dp).padding(vertical = 6.dp).semantics { contentDescription = description }) {
            val step = size.width / points.size
            val pts = points.mapIndexed { i, p ->
                Offset(step * (i + 0.5f), size.height * (1f - (p.value - bottom).toFloat() / span))
            }
            drawLine(track, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            val path = Path().apply {
                pts.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
            }
            drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            pts.forEach { drawCircle(color, radius = 5.dp.toPx(), center = it) }
        }
        Row(Modifier.fillMaxWidth()) {
            points.forEach { p ->
                Text(
                    p.date.format(TestDay),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}
