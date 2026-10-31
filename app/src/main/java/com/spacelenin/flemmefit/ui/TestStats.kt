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
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.spacelenin.flemmefit.model.Side
import com.spacelenin.flemmefit.model.SideBalance
import com.spacelenin.flemmefit.model.Sides
import com.spacelenin.flemmefit.model.TestPoint
import com.spacelenin.flemmefit.model.TestProgress
import com.spacelenin.flemmefit.model.VariantProgress
import com.spacelenin.flemmefit.model.Variants


/** Les tests du programme : courbe des résultats et prochain niveau. */
fun LazyListScope.statsTestItems(tests: List<TestProgress>, color: Color) {
    val shown = tests.filter { it.points.isNotEmpty() || it.goals.isNotEmpty() }
    if (shown.isEmpty()) return
    item(key = "tests-title") {
        SectionTitle("Tests 📏")
    }
    // Deux programmes peuvent avoir un test du même nom : la position rend la clé unique.
    itemsIndexed(shown, key = { i, t -> "test-$i-${t.test.id}" }) { _, t -> TestCard(t, color) }
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
                    Text(t.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
            if (t.points.size > 1) TestCurve(t.points, t::format, color, switches = t.switches)
            t.switchedAt?.let {
                Text(
                    "↔ ${t.name} depuis le ${it.date.format(DayMonth)} : départ et record repartent de là.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

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

/** Les variantes déjà faites : leurs max et le taux perso qui en sort. */
fun LazyListScope.statsVariantItems(variants: List<VariantProgress>, color: Color) {
    val shown = variants.filter { it.points.isNotEmpty() }
    if (shown.isEmpty()) return
    item(key = "variants-title") {
        SectionTitle("Variantes 🎲")
    }
    itemsIndexed(shown, key = { i, v -> "variant-$i-${v.variant.id}" }) { _, v -> VariantCard(v, color) }
}

@Composable
private fun VariantCard(v: VariantProgress, color: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text(v.variant.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(v.familyName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(v.format(v.points.last().value), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = color)
            }
            if (v.points.size > 1) TestCurve(v.points, v::format, color)
            v.ratio?.let { r ->
                Text(
                    "Ton taux : ${v.ratioLabel(r)}" + (v.startRatio?.takeIf { v.ratio != it }?.let { " (départ ${Variants.decimal(it)})" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Exercices faits une série par côté : l'écart entre gauche et droite, séance après séance. */
fun LazyListScope.statsBalanceItems(balances: List<SideBalance>, color: Color) {
    if (balances.isEmpty()) return
    item(key = "balance-title") {
        SectionTitle("Gauche / droite ↔")
    }
    itemsIndexed(balances, key = { i, b -> "balance-$i-${b.name}" }) { _, b -> BalanceCard(b, color) }
}

@Composable
private fun BalanceCard(b: SideBalance, color: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text(b.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(b.status { it.format(DayMonth) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${b.gapPercent} %", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = color)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Side.entries.forEach { side ->
                    Text(
                        "${sideMark(side)} ${Sides.label(b.limb, side)} : ${b.format(if (side == Side.LEFT) b.left else b.right)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = sideColor(side),
                    )
                }
            }
            if (b.points.size > 1) TestCurve(b.points, { "$it %" }, color)
        }
    }
}

/** Courbe simple : un point par test, régulièrement espacés, valeur au-dessus. */
@Composable
private fun TestCurve(all: List<TestPoint>, format: (Int) -> String, color: Color, switches: List<Int> = emptyList()) {
    // Les 8 derniers points : au-delà, les étiquettes ne tiennent plus.
    val points = all.takeLast(8)
    val shift = all.size - points.size
    val marks = switches.map { it - shift }.filter { it in 1..points.lastIndex }
    val values = points.map { it.value }
    val top = values.max()
    val bottom = values.min()
    val span = (top - bottom).coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.outlineVariant
    val description = points.joinToString { "${it.date.format(DayMonth)} : ${format(it.value)}" }

    Column {
        Row(Modifier.fillMaxWidth()) {
            points.forEach { p ->
                Text(
                    format(p.value),
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
            // Changement d'exercice (crunch → sit-up) : un trait vertical entre les deux points.
            marks.forEach { m ->
                val x = step * m
                drawLine(track, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
            }
            val path = Path().apply {
                // La courbe repart à chaque changement d'exercice : les deux ne se comparent pas.
                pts.forEachIndexed { i, o -> if (i == 0 || i in marks) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
            }
            drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            pts.forEach { drawCircle(color, radius = 5.dp.toPx(), center = it) }
        }
        Row(Modifier.fillMaxWidth()) {
            points.forEach { p ->
                Text(
                    p.date.format(DayMonth),
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
