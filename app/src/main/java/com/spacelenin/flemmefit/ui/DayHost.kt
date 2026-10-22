package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.data.ProgramRepository
import com.spacelenin.flemmefit.model.ExerciseLog
import com.spacelenin.flemmefit.model.ProgramSchedule
import com.spacelenin.flemmefit.model.StoredProgram
import java.time.LocalDate

/**
 * Tout ce qui se fait sur les séances d'un programme (cocher, saisir, détail, mode séance, Burst, décalage),
 * avec les fenêtres que ça ouvre. Renvoie les actions à brancher sur [dayDetailItems].
 */
@Composable
fun rememberDayActions(
    program: StoredProgram,
    schedule: ProgramSchedule,
    repository: ProgramRepository,
    color: Color,
    /** Montre un jour (après un décalage, la séance a bougé). */
    onSelect: (LocalDate) -> Unit,
    /** Ouvre le mode séance sur la séance du jour dès que possible. */
    startGuided: Boolean = false,
    onGuidedStarted: () -> Unit = {},
): DayActions {
    val today = schedule.today
    // Exercice en cours de saisie : index de jour, index d'exercice, et s'il s'agit du max de Boss.
    var logDay by rememberSaveable(program.id) { mutableStateOf<Int?>(null) }
    var logExercise by rememberSaveable(program.id) { mutableStateOf(0) }
    var logBoss by rememberSaveable(program.id) { mutableStateOf(false) }
    // Exercice dont le détail est ouvert : index de jour et d'exercice.
    var detailDay by rememberSaveable(program.id) { mutableStateOf<Int?>(null) }
    var detailExercise by rememberSaveable(program.id) { mutableStateOf(0) }
    var burstAsk by rememberSaveable(program.id) { mutableStateOf<Int?>(null) }
    var postponeIndex by rememberSaveable(program.id) { mutableStateOf<Int?>(null) }
    var guidedDay by rememberSaveable(program.id) { mutableStateOf<Int?>(null) }
    var extraFor by rememberSaveable(program.id) { mutableStateOf<Long?>(null) }
    // Ressenti : demandé quand la séance du jour vient d'être terminée (après le mode séance s'il est ouvert).
    var feelingFor by rememberSaveable(program.id) { mutableStateOf<Int?>(null) }
    val todaySession = schedule.sessionOn(today)
    val todayComplete = todaySession != null && schedule.doneCount(todaySession) == todaySession.exerciseCount
    var wasComplete by remember(program.id, todaySession?.index) { mutableStateOf(todayComplete) }
    LaunchedEffect(todayComplete) {
        if (todayComplete && !wasComplete && todaySession != null && program.feelingOf(todaySession.index) == null) {
            feelingFor = todaySession.index
        }
        wasComplete = todayComplete
    }
    val timer = LocalWorkoutTimer.current

    LaunchedEffect(startGuided) {
        if (startGuided) {
            schedule.sessionOn(today)?.takeIf { !schedule.isLocked(it) }?.let { guidedDay = it.index }
            onGuidedStarted()
        }
    }

    val actions = DayActions(
        onToggle = { s, i -> repository.toggleExercise(program.id, s.index, i) },
        onSetAll = { s, done -> repository.setSessionDone(program.id, s.index, s.exerciseCount, done) },
        onPostpone = { s -> postponeIndex = s.index },
        onUndoPostpone = { s ->
            repository.undoPostpone(program.id, s.index)
            onSelect(ProgramSchedule(program.undoPostpone(s.index), today).dateOf(s.index))
        },
        onEditLog = { s, i -> logDay = s.index; logExercise = i; logBoss = false },
        onBossCheck = { s, i -> logDay = s.index; logExercise = i; logBoss = true },
        onDetail = { s, i -> detailDay = s.index; detailExercise = i },
        onBurst = { s, on -> if (on) burstAsk = s.index else repository.undoBurst(program.id, s.index) },
        onGuided = { s -> guidedDay = s.index },
        onWeights = { s, i, on -> repository.setWeights(program.id, s.index, i, on) },
        onAddExtra = { date -> extraFor = date.toEpochDay() },
        onRemoveExtra = { date, i -> repository.removeExtra(program.id, date, i) },
    )

    logDay?.let { dayIndex ->
        val session = schedule.sessionAt(dayIndex)
        val prescription = session?.prescriptions?.getOrNull(logExercise) ?: return@let
        val close = { logDay = null }
        val clear = {
            // Effacer le détail ne retire pas l'exercice fait.
            repository.saveLog(program.id, dayIndex, logExercise, ExerciseLog(), program.isDone(dayIndex, logExercise))
            close()
        }
        if (logBoss) {
            BossMaxDialog(
                prescription = prescription,
                onSave = { max ->
                    if (max == null) repository.toggleExercise(program.id, dayIndex, logExercise)
                    else repository.saveLog(program.id, dayIndex, logExercise, ExerciseLog(max = max), complete = true)
                    close()
                },
                onDismiss = close,
            )
        } else if (prescription.sets != null && prescription.perSetPlanned != null) {
            SetsLogSheet(
                prescription = prescription,
                existing = program.logOf(dayIndex, logExercise),
                onSave = { log, complete ->
                    repository.saveLog(program.id, dayIndex, logExercise, log, complete)
                    close()
                },
                onClear = clear,
                onDismiss = close,
            )
        } else {
            LogDialog(
                prescription = prescription,
                existing = program.logOf(dayIndex, logExercise),
                onSave = { log, complete ->
                    repository.saveLog(program.id, dayIndex, logExercise, log, complete)
                    close()
                },
                onClear = clear,
                onDismiss = close,
            )
        }
    }

    detailDay?.let { dayIndex ->
        val session = schedule.sessionAt(dayIndex)
        val prescription = session?.prescriptions?.getOrNull(detailExercise) ?: return@let
        val done = program.isDone(dayIndex, detailExercise)
        ExerciseDetailSheet(
            locked = schedule.isLocked(session),
            prescription = prescription,
            plan = program.plan,
            testResults = remember(schedule) { schedule.currentTestResults() },
            color = if (session.isBoss) BossColor else color,
            done = done,
            onLog = {
                detailDay = null
                actions.onEditLog(session, detailExercise)
            },
            onDone = {
                detailDay = null
                if (prescription.allOut && !done) actions.onBossCheck(session, detailExercise)
                else actions.onToggle(session, detailExercise)
            },
            onDismiss = { detailDay = null },
            onWeights = { repository.setWeights(program.id, dayIndex, detailExercise, it) },
        )
    }

    guidedDay?.let { dayIndex ->
        val session = schedule.sessionAt(dayIndex) ?: return@let
        // Le minuteur flottant laisse la place à celui du mode séance.
        LaunchedEffect(Unit) { timer.stop() }
        GuidedSession(
            schedule = schedule,
            session = session,
            color = if (session.isBoss) BossColor else color,
            onSave = { i, log, complete -> repository.saveLog(program.id, dayIndex, i, log, complete) },
            onWeights = { i, on -> repository.setWeights(program.id, dayIndex, i, on) },
            onClose = { guidedDay = null },
        )
    }

    extraFor?.let { epoch ->
        ExtraDialog(
            onSave = { text ->
                repository.addExtra(program.id, LocalDate.ofEpochDay(epoch), text)
                extraFor = null
            },
            onDismiss = { extraFor = null },
        )
    }

    feelingFor?.takeIf { guidedDay == null }?.let { index ->
        FeelingDialog(
            onPick = { f ->
                repository.setFeeling(program.id, index, f)
                feelingFor = null
            },
            onSkip = { feelingFor = null },
        )
    }

    burstAsk?.let { index ->
        BurstDialog(
            onConfirm = {
                repository.burst(program.id, index)
                burstAsk = null
            },
            onDismiss = { burstAsk = null },
        )
    }

    postponeIndex?.let { index ->
        val from = schedule.dateOf(index)
        AlertDialog(
            onDismissRequest = { postponeIndex = null },
            title = { Text("Décaler la séance ?") },
            text = {
                Text(
                    "La séance du ${from.shortLabel()} passe au ${from.plusDays(1).shortLabel()}. " +
                        "Tout le reste du programme est décalé d'un jour.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repository.postpone(program.id, index)
                    onSelect(from.plusDays(1))
                    postponeIndex = null
                }) { Text("Décaler") }
            },
            dismissButton = { TextButton(onClick = { postponeIndex = null }) { Text("Annuler") } },
        )
    }

    return actions
}

/** « J'ai fait autre chose » : une activité en plus, notée telle quelle. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtraDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("J'ai fait autre chose") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Ça s'affiche sur le calendrier, sans toucher à ta série, à la progression ni aux stats.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Marche", "Vélo", "Course", "Natation", "Étirements", "Foot", "Escalade").forEach { idea ->
                        SuggestionChip(onClick = { text = if (text.isBlank()) idea else "$text, $idea" }, label = { Text(idea) })
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(StoredProgram.MAX_EXTRA_LENGTH) },
                    placeholder = { Text("Marche 40 min…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onSave(text) }) { Text("Ajouter") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Ressenti juste après la séance, à passer si on veut. */
@Composable
private fun FeelingDialog(onPick: (Int) -> Unit, onSkip: () -> Unit) {
    val labels = listOf("Dur", "Bien", "En forme")
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("Séance finie ! Comment c'était ?") },
        text = {
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                StoredProgram.FEELINGS.forEachIndexed { i, emoji ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onPick(i) }.padding(12.dp),
                    ) {
                        Text(emoji, style = MaterialTheme.typography.displaySmall)
                        Text(labels[i], style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onSkip) { Text("Passer") } },
    )
}
