package com.spacelenin.flemmefit.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.model.ExerciseLog
import com.spacelenin.flemmefit.model.ExerciseState
import com.spacelenin.flemmefit.model.Measures
import com.spacelenin.flemmefit.model.Prescription
import com.spacelenin.flemmefit.model.ProgramSchedule
import com.spacelenin.flemmefit.model.Session
import java.time.LocalDate

/** Rouge « Boss », indépendant du thème pour rester reconnaissable. */
val BossColor = Color(0xFFC62828)

class DayActions(
    val onToggle: (session: Session, exerciseIndex: Int) -> Unit,
    val onSetAll: (session: Session, done: Boolean) -> Unit,
    val onPostpone: (session: Session) -> Unit,
    val onUndoPostpone: (session: Session) -> Unit,
    /** Saisir ce qui a réellement été fait (appui long). */
    val onEditLog: (session: Session, exerciseIndex: Int) -> Unit,
    /** Cocher un exercice de Boss : demande le max de la série à fond. */
    val onBossCheck: (session: Session, exerciseIndex: Int) -> Unit,
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
                    "Programme terminé le ${schedule.endDate?.shortLabel()}.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (session == null) return
    session.prescriptions.forEachIndexed { i, prescription ->
        item(key = "ex-${session.index}-$i") {
            ExerciseRow(
                prescription = prescription,
                state = schedule.exerciseState(session, i),
                log = schedule.program.logOf(session.index, i),
                color = if (session.isBoss) BossColor else color,
                onToggle = {
                    val done = schedule.program.isDone(session.index, i)
                    if (prescription.allOut && !done) actions.onBossCheck(session, i) else actions.onToggle(session, i)
                },
                onEdit = { actions.onEditLog(session, i) },
                expandKey = "${session.index}-$i",
            )
        }
    }
    item(key = "day-footer") {
        Text(
            "Appui long sur un exercice : noter ce que tu as vraiment fait, ou ton max.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionHeader(schedule: ProgramSchedule, session: Session, color: Color, actions: DayActions) {
    val done = schedule.doneCount(session)
    val total = session.exerciseCount
    val ratio = if (total == 0) 0f else done.toFloat() / total
    val postponed = schedule.program.postponeCount(session.index)
    val accent = if (session.isBoss) BossColor else color

    if (session.isBoss) {
        Banner("💀 Jour Boss", "Chiffres de la semaine prochaine, et dernière série à fond. Bats ton dernier max !", BossColor)
        Spacer(Modifier.height(8.dp))
    } else if (session.isDeload) {
        Banner("🌿 Semaine de décharge", "Volume divisé par deux pour récupérer. La progression reprend la semaine suivante.", color)
        Spacer(Modifier.height(8.dp))
    }
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
        LinearProgressIndicator(progress = { ratio }, color = accent, modifier = Modifier.weight(1f))
        Spacer(Modifier.size(12.dp))
        Text("${(ratio * 100).toInt()} %", style = MaterialTheme.typography.labelLarge)
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
private fun Banner(title: String, text: String, color: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.14f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun ExerciseLog.doneLabel(p: Prescription): String {
    val sets = sets ?: p.sets
    val per = reps?.toString() ?: seconds?.let(Measures::formatSeconds)
    return when {
        sets != null && per != null -> "$sets × $per"
        per != null -> per
        sets != null -> "$sets séries"
        else -> ""
    }
}

private fun Prescription.maxLabel(value: Int) = if (maxIsSeconds) Measures.formatSeconds(value) else "$value reps"

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExerciseRow(
    prescription: Prescription,
    state: ExerciseState,
    log: ExerciseLog?,
    color: Color,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    expandKey: String,
) {
    var expanded by rememberSaveable(expandKey) { mutableStateOf(false) }
    val exercise = prescription.exercise
    val summary = prescription.summary()
    val hasMore = exercise.description != null
    val done = state == ExerciseState.DONE

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
                .combinedClickable(
                    onClick = { if (hasMore) expanded = !expanded else onToggle() },
                    onLongClick = onEdit,
                    onLongClickLabel = "Noter ce qui a été fait",
                )
                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        ) {
            TriStateCheckbox(
                state = when (state) {
                    ExerciseState.DONE -> ToggleableState.On
                    ExerciseState.PARTIAL -> ToggleableState.Indeterminate
                    ExerciseState.TODO -> ToggleableState.Off
                },
                onClick = onToggle,
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
                prescription.palier?.let {
                    Text(
                        "Palier ${it + 1}/${prescription.levelCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                    )
                }
                if (prescription.allOut) {
                    val target = prescription.target?.let(prescription::maxLabel)
                    Text(
                        when {
                            prescription.isTest -> "📏 Test : une seule série, à fond" + (target?.let { " · record à battre : $it" } ?: "")
                            prescription.isBoss -> "Dernière série à fond" + (target?.let { " · objectif $it" } ?: "")
                            else -> "Dernière série : le max possible" + (target?.let { " (au moins $it)" } ?: "")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = BossColor,
                    )
                }
                if (state == ExerciseState.PARTIAL && log != null) {
                    Text(
                        "Fait : ${log.doneLabel(prescription)} (partiel)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                log?.max?.let {
                    Text("Max noté : ${prescription.maxLabel(it)}", style = MaterialTheme.typography.bodyMedium)
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
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Noter ce qui a été fait", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
