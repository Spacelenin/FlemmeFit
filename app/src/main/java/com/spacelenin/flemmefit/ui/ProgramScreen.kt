package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.data.ProgramRepository
import com.spacelenin.flemmefit.model.ExerciseLog
import com.spacelenin.flemmefit.model.ProgramSchedule
import com.spacelenin.flemmefit.model.Stats
import com.spacelenin.flemmefit.model.Progress
import com.spacelenin.flemmefit.model.StoredProgram
import com.spacelenin.flemmefit.model.testProgress
import com.spacelenin.flemmefit.ui.theme.programColor
import java.time.LocalDate
import java.time.YearMonth


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramScreen(
    program: StoredProgram,
    repository: ProgramRepository,
    onBack: () -> Unit,
    /** Ouvre directement la saisie des tests de départ (juste après l'import). */
    askTests: Boolean = false,
    onTestsAsked: () -> Unit = {},
    /** Jour à afficher en ouvrant (choisi sur le calendrier de l'accueil). */
    openOn: LocalDate? = null,
    /** 0 : calendrier, 1 : stats. */
    initialTab: Int = 0,
    /** Réimporter la routine pour la mettre à jour. */
    onUpdateRoutine: () -> Unit = {},
    /** Ouvre directement le mode séance sur la séance du jour (« C'est parti » de l'accueil). */
    startGuided: Boolean = false,
    onGuidedStarted: () -> Unit = {},
) {
    val today = rememberToday()
    val schedule = remember(program, today) { ProgramSchedule(program, today) }
    val color = programColor(program.colorIndex)

    val initial = remember(program.id) { openOn ?: initialDate(schedule, LocalDate.now()) }
    // Sauvegardés en types simples pour survivre au pliage / dépliage et à la rotation.
    var selectedEpoch by rememberSaveable(program.id) { mutableStateOf(initial.toEpochDay()) }
    var monthIndex by rememberSaveable(program.id) { mutableStateOf(YearMonth.from(initial).let { it.year * 12 + it.monthValue - 1 }) }
    val selected = LocalDate.ofEpochDay(selectedEpoch)
    val month = YearMonth.of(monthIndex / 12, monthIndex % 12 + 1)

    fun select(date: LocalDate) {
        selectedEpoch = date.toEpochDay()
        monthIndex = date.year * 12 + date.monthValue - 1
    }

    var editTests by remember { mutableStateOf(askTests) }
    var postponeIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    // Exercice en cours de saisie : index de jour, index d'exercice, et s'il s'agit du max de Boss.
    var logDay by rememberSaveable { mutableStateOf<Int?>(null) }
    var logExercise by rememberSaveable { mutableStateOf(0) }
    var logBoss by rememberSaveable { mutableStateOf(false) }
    // Exercice dont le détail est ouvert : index de jour et d'exercice.
    var detailDay by rememberSaveable { mutableStateOf<Int?>(null) }
    var detailExercise by rememberSaveable { mutableStateOf(0) }
    var burstAsk by rememberSaveable { mutableStateOf<Int?>(null) }
    var guidedDay by rememberSaveable { mutableStateOf<Int?>(null) }
    val timer = LocalWorkoutTimer.current
    LaunchedEffect(startGuided) {
        if (startGuided) {
            schedule.sessionOn(today)?.takeIf { !schedule.isLocked(it) }?.let { guidedDay = it.index }
            onGuidedStarted()
        }
    }
    var tab by rememberSaveable { mutableStateOf(initialTab) }

    val actions = DayActions(
        onToggle = { s, i -> repository.toggleExercise(program.id, s.index, i) },
        onSetAll = { s, done -> repository.setSessionDone(program.id, s.index, s.exerciseCount, done) },
        onPostpone = { s -> postponeIndex = s.index },
        onUndoPostpone = { s ->
            repository.undoPostpone(program.id, s.index)
            select(ProgramSchedule(program.undoPostpone(s.index), today).dateOf(s.index))
        },
        onEditLog = { s, i -> logDay = s.index; logExercise = i; logBoss = false },
        onBossCheck = { s, i -> logDay = s.index; logExercise = i; logBoss = true },
        onDetail = { s, i -> detailDay = s.index; detailExercise = i },
        onBurst = { s, on -> if (on) burstAsk = s.index else repository.undoBurst(program.id, s.index) },
        onGuided = { s -> guidedDay = s.index },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(program.plan.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                },
                actions = {
                    IconButton(onClick = { select(today) }) { Icon(Icons.Default.DateRange, "Aujourd'hui") }
                    ProgramMenuButton(
                        program = program,
                        repository = repository,
                        onUpdateRoutine = onUpdateRoutine,
                        onDeleted = onBack,
                        onStartChanged = ::select,
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Calendrier") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Stats") })
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= TwoPaneMinWidth
                if (tab == 1) {
                    StatsPanes(remember(schedule) { Stats.of(schedule) }, color, showProgram = false, tests = remember(schedule) { schedule.testProgress() })
                } else {
                    val calendarItems: LazyListScope.() -> Unit = {
                        if (program.missingTests.isNotEmpty()) {
                            item(key = "tests") {
                                Card(
                                    onClick = { editTests = true },
                                    colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.14f)),
                                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text("📏 Tests de niveau à faire", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(
                                            program.missingTests.joinToString { it.name } +
                                                " : en attendant, tu es au niveau le plus facile. Touche pour saisir tes résultats.",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                            }
                        }
                        item(key = "summary") {
                            ProgressSummary(schedule.progress(), schedule, color, Modifier.padding(16.dp))
                        }
                        item(key = "calendar") {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                MonthCalendar(
                                    schedule = schedule,
                                    color = color,
                                    month = month,
                                    onMonthChange = { monthIndex = it.year * 12 + it.monthValue - 1 },
                                    selected = selected,
                                    today = today,
                                    onSelect = ::select,
                                )
                            }
                        }
                        item(key = "legend") { Legend(color) }
                    }

                    if (wide) {
                        Row(Modifier.fillMaxSize()) {
                            LazyColumn(Modifier.weight(1f).fillMaxHeight(), content = calendarItems)
                            VerticalDivider()
                            LazyColumn(Modifier.weight(1f).fillMaxHeight()) {
                                dayDetailItems(schedule, selected, color, actions)
                            }
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            calendarItems(this)
                            dayDetailItems(schedule, selected, color, actions)
                        }
                    }
                }
            }
        }
    }

    logDay?.let { dayIndex ->
        val session = schedule.sessionAt(dayIndex)
        val prescription = session?.prescriptions?.getOrNull(logExercise) ?: return@let
        val close = { logDay = null }
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
                // Comme la saisie classique : effacer le détail ne retire pas l'exercice fait.
                onClear = {
                    repository.saveLog(program.id, dayIndex, logExercise, ExerciseLog(), program.isDone(dayIndex, logExercise))
                    close()
                },
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
                onClear = {
                    repository.saveLog(program.id, dayIndex, logExercise, ExerciseLog(), program.isDone(dayIndex, logExercise))
                    close()
                },
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
                    select(from.plusDays(1))
                    postponeIndex = null
                }) { Text("Décaler") }
            },
            dismissButton = { TextButton(onClick = { postponeIndex = null }) { Text("Annuler") } },
        )
    }

    if (editTests) {
        TestsDialog(
            program = program,
            current = remember(schedule) { schedule.currentTestResults() },
            onSave = { results ->
                repository.setTests(program.id, results)
                editTests = false
                onTestsAsked()
            },
            onDismiss = {
                editTests = false
                onTestsAsked()
            },
        )
    }

}

/** Aujourd'hui si on est dans le programme, sinon son début ou sa fin. */
private fun initialDate(schedule: ProgramSchedule, today: LocalDate): LocalDate = when {
    today.isBefore(schedule.startDate) -> schedule.startDate
    else -> schedule.endDate?.takeIf { today.isAfter(it) } ?: today
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(color: Color) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(8.dp),
    ) {
        LegendItem(color, "faite")
        LegendItem(color.copy(alpha = 0.35f), "commencée")
        LegendItem(MaterialTheme.colorScheme.errorContainer, "manquée")
        LegendItem(Color.Transparent, "prévue", border = color)
        Text("💀 Boss", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun LegendItem(fill: Color, label: String, border: Color? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .padding(end = 4.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(fill)
                .then(if (border != null) Modifier.border(1.5.dp, border, CircleShape) else Modifier),
        )
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
