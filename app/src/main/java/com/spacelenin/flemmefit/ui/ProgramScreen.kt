package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.data.ProgramRepository
import com.spacelenin.flemmefit.model.Progress
import com.spacelenin.flemmefit.model.ProgramSchedule
import com.spacelenin.flemmefit.model.Stats
import com.spacelenin.flemmefit.model.StoredProgram
import com.spacelenin.flemmefit.model.testProgress
import com.spacelenin.flemmefit.ui.theme.programColor

/** Un programme en détail : où il en est, ses tests et ses stats. Le quotidien (calendrier, séances) est sur l'accueil. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramScreen(
    program: StoredProgram,
    repository: ProgramRepository,
    onBack: () -> Unit,
    /** Réimporter la routine pour la mettre à jour. */
    onUpdateRoutine: () -> Unit = {},
) {
    val today = rememberToday()
    val schedule = remember(program, today) { ProgramSchedule(program, today) }
    val color = programColor(program.colorIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(program.plan.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                },
                actions = {
                    ProgramMenuButton(program = program, repository = repository, onUpdateRoutine = onUpdateRoutine, onDeleted = onBack)
                },
            )
        },
    ) { padding ->
        StatsPanes(
            remember(schedule) { Stats.of(schedule) },
            color,
            showProgram = false,
            modifier = Modifier.padding(padding),
            tests = remember(schedule) { schedule.testProgress() },
            header = { item(key = "summary") { ProgressSummary(schedule.progress(), schedule, color, Modifier.padding(16.dp)) } },
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
