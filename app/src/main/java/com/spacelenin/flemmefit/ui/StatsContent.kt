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
import java.time.format.DateTimeFormatter

private val DayMonth = DateTimeFormatter.ofPattern("d/MM")

/** Totaux, graphique et historique. */
fun LazyListScope.statsOverviewItems(stats: Stats, color: Color, showProgram: Boolean) {
    if (stats.isEmpty) {
        item(key = "stats-empty") {
            Text(
                "Pas encore de stats : coche des exercices dans le calendrier.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }
    item(key = "stats-totals") { Totals(stats) }
    item(key = "stats-chart") { WeeklyChart(stats.weeks, color) }
    item(key = "stats-history-title") { SectionTitle("Historique") }
    itemsIndexed(stats.history, key = { i, _ -> "h-$i" }) { _, h -> HistoryRow(h, showProgram) }
}

fun LazyListScope.statsExerciseItems(stats: Stats) {
    if (stats.exercises.isEmpty()) return
    item(key = "stats-ex-title") { SectionTitle("Par exercice") }
    items(stats.exercises, key = { "e-${it.key}" }) { ExerciseStatsCard(it) }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Totals(stats: Stats) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Tile("${stats.sessionsDone}", "séances faites")
            Tile("${stats.totalReps}", "répétitions")
            Tile("${stats.totalSets}", "séries")
            Tile(formatTotalSeconds(stats.totalSeconds), "d'effort chronométré")
            Tile("${stats.currentStreak} 🔥", "série actuelle")
            Tile("${stats.bestStreak}", "meilleure série")
            Tile("${stats.bossDefeated}/${stats.bossMet} 💀", "Boss vaincus")
        }
    }
}

@Composable
private fun Tile(value: String, label: String) {
    Column(Modifier.widthIn(min = 88.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Séries faites par semaine : une seule série de barres, touche une barre pour sa valeur. */
@Composable
private fun WeeklyChart(weeks: List<WeekVolume>, color: Color) {
    var selected by rememberSaveable { mutableIntStateOf(weeks.lastIndex) }
    val top = (weeks.maxOfOrNull { it.sets } ?: 0).coerceAtLeast(1)
    val description = weeks.joinToString { "semaine du ${it.monday.format(DayMonth)} : ${it.sets} séries" }

    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Séries par semaine", style = MaterialTheme.typography.titleMedium)
            weeks.getOrNull(selected)?.let { w ->
                Text(
                    "Semaine du ${w.monday.format(DayMonth)} : ${w.sets} séries" +
                        (if (w.boss) " · Boss 💀" else "") + (if (w.deload) " · décharge 🌿" else ""),
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
private fun ExerciseStatsCard(e: ExerciseStats) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${e.times} fois", style = MaterialTheme.typography.labelLarge)
            }
            val totals = listOfNotNull(
                e.totalReps.takeIf { it > 0 }?.let { "$it reps" },
                "${e.totalSets} séries",
                e.totalSeconds.takeIf { it > 0 }?.let(::formatTotalSeconds),
            ).joinToString(" · ")
            Text(totals, style = MaterialTheme.typography.bodyMedium)
            val records = listOfNotNull(
                e.bestReps?.let { "$it reps" },
                e.bestSeconds?.let(Measures::formatSeconds),
            ).joinToString(" · ")
            if (records.isNotEmpty()) Text("Record : $records", style = MaterialTheme.typography.bodyMedium)
            if (e.firstSummary != null && e.lastSummary != null && e.firstSummary != e.lastSummary) {
                Text("Évolution : ${e.firstSummary} → ${e.lastSummary}", style = MaterialTheme.typography.bodyMedium)
            }
            e.lastDate?.let {
                Text(
                    "Dernière fois : ${it.shortLabel()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
