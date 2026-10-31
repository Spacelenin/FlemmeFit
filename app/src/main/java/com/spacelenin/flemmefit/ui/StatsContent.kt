package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.model.ExerciseStats
import com.spacelenin.flemmefit.model.HistoryEntry
import com.spacelenin.flemmefit.model.Measures
import com.spacelenin.flemmefit.model.Stats
import com.spacelenin.flemmefit.model.WeekVolume
import com.spacelenin.flemmefit.ui.theme.programColor


/** Totaux, graphique et historique. */
fun LazyListScope.statsOverviewItems(stats: Stats, color: Color, showProgram: Boolean) {
    if (stats.isEmpty) {
        item(key = "stats-empty") {
            Text(
                "Pas encore de stats : termine des exercices dans le calendrier.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }
    item(key = "stats-totals") { Totals(stats, color) }
    item(key = "stats-chart") { WeeklyChart(stats.weeks, color) }
    if (stats.exploits.isNotEmpty()) {
        item(key = "stats-exploits-title") { SectionTitle("Exploits 🏆") }
        itemsIndexed(stats.exploits.take(10), key = { i, _ -> "x-$i" }) { _, x ->
            Text(
                "${x.date.shortLabel()} · ${x.label()}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
    item(key = "stats-history-title") { SectionTitle("Historique") }
    itemsIndexed(stats.history, key = { i, _ -> "h-$i" }) { _, h -> HistoryRow(h, showProgram) }
}

fun LazyListScope.statsExerciseItems(stats: Stats, color: Color) {
    // Échauffement et étirements : comptés dans les totaux, pas détaillés.
    val shown = stats.exercises.filter { !it.optional }
    if (shown.isEmpty()) return
    item(key = "stats-ex-title") { SectionTitle("Par exercice") }
    items(shown, key = { "e-${it.key}" }) { ExerciseStatsCard(it, color) }
}

/** Tuiles de chiffres, deux par ligne. */
@Composable
private fun Totals(stats: Stats, color: Color) {
    val tiles = listOf(
        Triple("${stats.sessionsDone}", "séances faites", color),
        Triple("${stats.currentStreak} 🔥", "d'affilée · record ${stats.bestStreak}", null),
        Triple("${stats.totalReps}", "reps · ${stats.totalSets} séries", null),
        Triple(formatTotalSeconds(stats.totalSeconds), "en durée", null),
        Triple("${stats.bossDefeated}/${stats.bossMet}", "Boss vaincus", BossColor),
        Triple("${stats.exploits.size}", if (stats.exploits.size > 1) "exploits" else "exploit", Color(0xFFB6F36A)),
    )
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (value, label, tint) ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = tint ?: MaterialTheme.colorScheme.onSurface)
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Séries faites par semaine : une seule série de barres, touche une barre pour sa valeur. */
@Composable
private fun WeeklyChart(weeks: List<WeekVolume>, color: Color) {
    var selected by rememberSaveable { mutableIntStateOf(weeks.lastIndex) }
    val top = (weeks.maxOfOrNull { it.sets } ?: 0).coerceAtLeast(1)
    val description = weeks.joinToString { "semaine du ${it.monday.format(DayMonth)} : ${it.sets} séries" }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Séries par semaine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            weeks.getOrNull(selected)?.let { w ->
                Text(
                    "Semaine du ${w.monday.format(DayMonth)} : ${w.sets} séries" +
                        (if (w.boss) " · Boss 💀" else "") + (if (w.deload) " · récup 🌿" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = description },
            ) {
                weeks.forEachIndexed { i, w ->
                    Box(
                        contentAlignment = Alignment.BottomCenter,
                        modifier = Modifier.weight(1f).fillMaxHeight().clickable { selected = i }.padding(horizontal = 3.dp),
                    ) {
                        val fraction = w.sets.toFloat() / top
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction.coerceAtLeast(0.02f))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    when {
                                        i == selected -> color
                                        w.deload -> color.copy(alpha = 0.3f)
                                        else -> color.copy(alpha = 0.6f)
                                    },
                                ),
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth()) {
                weeks.forEach { w ->
                    Text(
                        w.monday.format(DayMonth) + when {
                            w.boss -> "\n💀"
                            w.deload -> "\n🌿"
                            else -> ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseStatsCard(e: ExerciseStats, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Text(exerciseInitials(e.name), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = color)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(e.name, style = MaterialTheme.typography.titleMedium)
            val line = if (e.firstSummary != null && e.lastSummary != null && e.firstSummary != e.lastSummary) {
                "${e.firstSummary} → ${e.lastSummary}"
            } else {
                listOfNotNull(
                    e.totalReps.takeIf { it > 0 }?.let { "$it reps" },
                    "${e.totalSets} séries",
                    e.totalSeconds.takeIf { it > 0 }?.let(::formatTotalSeconds),
                ).joinToString(" · ")
            }
            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${e.times} fois" + (e.lastDate?.let { " · dernière le ${it.shortLabel()}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val record = listOfNotNull(e.bestReps?.let { "record $it" }, e.bestSeconds?.let(Measures::formatSeconds)).firstOrNull()
        if (record != null) Text(record, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HistoryRow(h: HistoryEntry, showProgram: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (h.isBoss) BossColor else programColor(h.colorIndex)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text((if (h.isBoss) "💀 " else "") + h.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                h.date.shortLabel() + if (showProgram) " · ${h.programName}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("${h.doneExercises}/${h.totalExercises}", style = MaterialTheme.typography.labelLarge)
    }
}
