package com.spacelenin.flemmefit.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
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
