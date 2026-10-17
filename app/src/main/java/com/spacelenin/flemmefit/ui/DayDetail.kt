package com.spacelenin.flemmefit.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.model.Exercise
import com.spacelenin.flemmefit.model.ProgramSchedule
import com.spacelenin.flemmefit.model.Session
import java.time.LocalDate

class DayActions(
    val onToggle: (session: Session, exerciseIndex: Int) -> Unit,
    val onSetAll: (session: Session, done: Boolean) -> Unit,
    val onPostpone: (session: Session) -> Unit,
    val onUndoPostpone: (session: Session) -> Unit,
)

/** Détail du jour sélectionné, sous forme d'éléments de liste pour s'insérer dans n'importe quelle LazyColumn. */
fun LazyListScope.dayDetailItems(
    schedule: ProgramSchedule,
    date: LocalDate,
    color: Color,
    actions: DayActions,
) {
    val session = schedule.sessionOn(date)
    item(key = "day-header") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(date.longLabel(), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            when {
                session != null -> SessionHeader(schedule, session, color, actions)
                schedule.isInProgram(date) -> Text("Repos 😴", style = MaterialTheme.typography.bodyLarge)
                date.isBefore(schedule.startDate) -> Text(
                    "Le programme commence le ${schedule.startDate.shortLabel()}.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Text(
                    "Programme terminé le ${schedule.endDate.shortLabel()}.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (session == null) return
    session.day.exercises.forEachIndexed { i, exercise ->
        item(key = "ex-${session.index}-$i") {
            ExerciseRow(
                exercise = exercise,
                done = schedule.program.isDone(session.index, i),
                color = color,
                onToggle = { actions.onToggle(session, i) },
                expandKey = "${session.index}-$i",
            )
        }
    }
    item(key = "day-footer") { Spacer(Modifier.height(24.dp)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionHeader(schedule: ProgramSchedule, session: Session, color: Color, actions: DayActions) {
    val done = schedule.doneCount(session)
    val total = session.exerciseCount
    val postponed = schedule.program.postponeCount(session.index)

    Text(session.title(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text(
        session.position(schedule.program.plan.repeat),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    session.day.notes?.let {
        Spacer(Modifier.height(4.dp))
        Text(it, style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done.toFloat() / total },
            color = color,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(12.dp))
        Text("$done / $total", style = MaterialTheme.typography.labelLarge)
    }
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val allDone = done == total
        AssistChip(
            onClick = { actions.onSetAll(session, !allDone) },
            label = { Text(if (allDone) "Tout décocher" else "Tout cocher") },
            leadingIcon = { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) },
        )
        AssistChip(
            onClick = { actions.onPostpone(session) },
            label = { Text("Repousser d'un jour") },
            leadingIcon = { Icon(Icons.Default.DateRange, null, Modifier.size(18.dp)) },
        )
        if (postponed > 0) {
            AssistChip(
                onClick = { actions.onUndoPostpone(session) },
                label = { Text(if (postponed > 1) "Annuler un report ($postponed)" else "Annuler le report") },
                leadingIcon = { Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun ExerciseRow(exercise: Exercise, done: Boolean, color: Color, onToggle: () -> Unit, expandKey: String) {
    var expanded by rememberSaveable(expandKey) { mutableStateOf(false) }
    val summary = exercise.summary()
    val hasMore = exercise.description != null

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (done) color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .animateContentSize(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { if (hasMore) expanded = !expanded else onToggle() }
                .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Checkbox(
                checked = done,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = color),
            )
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                )
                if (summary.isNotEmpty()) {
                    Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (expanded && exercise.description != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(exercise.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (hasMore) {
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Masquer la description" else "Voir la description",
                )
            }
        }
    }
}
