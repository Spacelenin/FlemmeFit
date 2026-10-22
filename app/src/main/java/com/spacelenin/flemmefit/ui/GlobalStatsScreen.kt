package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.model.Progress
import com.spacelenin.flemmefit.model.ProgramSchedule
import com.spacelenin.flemmefit.model.Stats
import com.spacelenin.flemmefit.model.StoredProgram
import com.spacelenin.flemmefit.model.testProgress
import com.spacelenin.flemmefit.ui.theme.programColor

/** Les stats : bilan de chaque programme, courbes des tests, puis les chiffres tous programmes confondus. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalStatsScreen(programs: List<StoredProgram>) {
    val today = rememberToday()
    val schedules = remember(programs, today) { programs.map { ProgramSchedule(it, today) } }
    val stats = remember(schedules) { Stats.merge(schedules.map(Stats::of), today) }
    val tests = remember(schedules) { schedules.flatMap { it.testProgress() } }
    val color = MaterialTheme.colorScheme.primary
    val several = programs.size > 1

    Scaffold(topBar = { TopAppBar(title = { Text("Stats") }) }) { padding ->
        StatsPanes(
            stats,
            color,
            showProgram = several,
            modifier = Modifier.padding(padding),
            tests = tests,
            header = {
                schedules.forEachIndexed { i, schedule ->
                    item(key = "summary-$i") {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                            if (several) {
                                Text(schedule.program.plan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                            }
                            ProgressSummary(schedule.progress(), schedule, programColor(schedule.program.colorIndex))
                        }
                    }
                }
            },
        )
    }
}

@Composable
fun ProgressSummary(progress: Progress, schedule: ProgramSchedule, color: Color, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${progress.doneSessions}/${progress.totalSessions}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = color,
                )
                Text(
                    if (schedule.isForever) " séances à ce jour" else " séances",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 4.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${schedule.startDate.shortLabel()} → ${schedule.endDate?.shortLabel() ?: "∞"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { progress.ratio }, color = color, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Stat("${progress.doneExercises}/${progress.totalExercises}", "exos")
                Stat("${progress.streak} 🔥", "d'affilée")
                Stat("${progress.missedSessions}", "manquées")
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
