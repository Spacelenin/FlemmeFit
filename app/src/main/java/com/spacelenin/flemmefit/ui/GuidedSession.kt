package com.spacelenin.flemmefit.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spacelenin.flemmefit.model.ExerciseLog
import com.spacelenin.flemmefit.model.GuidedExercise
import com.spacelenin.flemmefit.model.Measures
import com.spacelenin.flemmefit.model.ProgramSchedule
import com.spacelenin.flemmefit.model.Session
import com.spacelenin.flemmefit.model.TimerPhase
import com.spacelenin.flemmefit.model.TimerRun
import kotlinx.coroutines.delay

private enum class Step { SET, RUN, REST, END }

/**
 * Mode séance : les exercices pas encore faits, série par série, avec le repos décompté entre deux.
 * Chaque série est enregistrée dès qu'elle est faite : l'appli peut se fermer, on reprend où on en était.
 */
@Composable
fun GuidedSession(
    schedule: ProgramSchedule,
    session: Session,
    color: Color,
    onSave: (exerciseIndex: Int, log: ExerciseLog, complete: Boolean) -> Unit,
    onWeights: (exerciseIndex: Int, on: Boolean) -> Unit,
    onClose: () -> Unit,
) {
    HoldSession()
    // Les exercices à faire, figés à l'ouverture : en valider un ne décale pas la suite.
    val queue = remember(session.index) {
        (0 until session.exerciseCount).filter { !schedule.program.isDone(session.index, it) }
    }
    /** Séries déjà faites d'un exercice commencé (mode séance quitté ou appli fermée en route). */
    fun doneSets(exerciseIndex: Int?): List<Int> {
        val i = exerciseIndex ?: return emptyList()
        val sets = session.prescriptions.getOrNull(i)?.let(::GuidedExercise)?.sets ?: return emptyList()
        val done = schedule.program.logOf(session.index, i)?.perSet?.takeIf { it.size == sets }?.takeWhile { it > 0 }.orEmpty()
        return if (done.size < sets) done else emptyList()
    }
    var pos by remember { mutableIntStateOf(0) }
    var values by remember { mutableStateOf(doneSets(queue.firstOrNull())) }
    var set by remember { mutableIntStateOf(values.size) }
    var step by remember { mutableStateOf(if (queue.isEmpty()) Step.END else Step.SET) }
    var run by remember { mutableStateOf<TimerRun?>(null) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    val exerciseIndex = queue.getOrNull(pos)
    val prescription = exerciseIndex?.let { session.prescriptions.getOrNull(it) }
    val restBonus = LocalRestBonus.current
    val guided = remember(prescription, restBonus) { prescription?.let { GuidedExercise(it, restBonus) } }
    var value by remember(pos, set) { mutableIntStateOf(guided?.suggested(set) ?: 0) }

    val context = LocalContext.current
    val feedback = remember { TimerFeedback(context) }
    // Repos ou série au temps : la notification prend le relais si le téléphone est verrouillé.
    TimerNotification(run?.let { it.copy(title = prescription?.exercise?.name ?: it.title) })
    DisposableEffect(Unit) { onDispose(feedback::release) }

    fun startRest(seconds: Int?) {
        if (seconds == null) {
            step = Step.SET
            return
        }
        run = TimerRun("Repos", listOf(TimerPhase("Repos", seconds, rest = true)), phaseStart = SystemClock.elapsedRealtime())
        step = Step.REST
    }

    /** Ce qui est fait de l'exercice en cours part dans le suivi (partiel si ça s'arrête en route). */
    fun savePartial() {
        val g = guided ?: return
        val i = exerciseIndex ?: return
        if (values.any { it > 0 }) {
            val (log, complete) = g.result(values)
            onSave(i, log, complete)
        }
    }

    fun nextExercise(rest: Int?) {
        run = null
        if (pos < queue.lastIndex) {
            pos++
            values = doneSets(queue[pos])
            set = values.size
            startRest(rest)
        } else {
            values = emptyList()
            set = 0
            step = Step.END
        }
    }

    fun finishSet(v: Int) {
        val g = guided ?: return
        values = values + v
        run = null
        if (set < g.sets - 1) {
            // Série enregistrée tout de suite (l'exercice reste incomplet tant que tout n'est pas fait).
            savePartial()
            set++
            startRest(g.restSeconds)
        } else {
            val (log, complete) = g.result(values)
            exerciseIndex?.let { onSave(it, log, complete) }
            nextExercise(g.restSeconds)
        }
    }

    fun close() {
        if (step != Step.END) savePartial()
        onClose()
    }

    // Le chrono d'une série ou du repos.
    LaunchedEffect(step, run?.phaseStart) {
        var lastBeep = -1
        while (step == Step.RUN || step == Step.REST) {
            now = SystemClock.elapsedRealtime()
            val r = run ?: break
            val next = r.advance(now)
            if (next.finished) {
                if (step == Step.REST) {
                    feedback.phaseEnded(toRest = false)
                    run = null
                    step = Step.SET
                } else {
                    feedback.phaseEnded(toRest = true)
                    finishSet(next.phase.seconds?.plus(next.bonusSeconds) ?: 0)
                }
                break
            }
            val left = r.leftSeconds(now)
            if (left != null && left in 1..3 && left != lastBeep) feedback.tick()
            lastBeep = left ?: -1
            delay(100)
        }
    }

    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        val view = LocalView.current
        DisposableEffect(view) {
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = false }
        }
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 560.dp).fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (step == Step.END) session.title() else "Exercice ${pos + 1} sur ${queue.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = ::close) { Icon(Icons.Default.Close, "Quitter le mode séance") }
                    }
                    val total = queue.size.coerceAtLeast(1)
                    val sets = guided?.sets ?: 1
                    LinearProgressIndicator(
                        progress = { if (step == Step.END) 1f else (pos + set.toFloat() / sets) / total },
                        color = color,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(24.dp))

                    if (step == Step.END || guided == null || prescription == null) {
                        EndContent(schedule, session, onClose)
                    } else if (step == Step.REST) {
                        val r = run
                        val left = r?.leftSeconds(now) ?: 0
                        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("Repos", style = MaterialTheme.typography.titleLarge, color = DeloadColor, fontWeight = FontWeight.Bold)
                            Text(clock(left), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.ExtraBold)
                            LinearProgressIndicator(progress = { r?.progress(now) ?: 1f }, color = DeloadColor, modifier = Modifier.fillMaxWidth(0.6f).padding(vertical = 12.dp))
                            Text(
                                "Ensuite : " + if (set == 0) prescription.exercise.name else "série ${set + 1} sur $sets",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(20.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                FilledTonalButton(onClick = { run = run?.addSeconds(15) }) { Text("+15 s") }
                                Button(onClick = { run = null; step = Step.SET }) { Text("Passer le repos") }
                            }
                        }
                    } else {
                        SetContent(
                            guided = guided,
                            set = set,
                            value = value,
                            onValue = { value = it },
                            running = if (step == Step.RUN) run else null,
                            now = now,
                            color = color,
                            onStart = { seconds ->
                                run = TimerRun(prescription.exercise.name, listOf(TimerPhase("Série", seconds)), phaseStart = SystemClock.elapsedRealtime())
                                step = Step.RUN
                            },
                            onStop = {
                                val r = run
                                finishSet(if (r == null) 0 else (r.elapsedMs(SystemClock.elapsedRealtime()) / 1000).toInt())
                            },
                            onSetDone = { finishSet(if (guided.needsValue(set)) value else 0) },
                            onWeights = { on -> exerciseIndex?.let { onWeights(it, on) } },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                savePartial()
                                nextExercise(rest = null)
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) { Text("Passer l'exercice") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetContent(
    guided: GuidedExercise,
    set: Int,
    value: Int,
    onValue: (Int) -> Unit,
    running: TimerRun?,
    now: Long,
    color: Color,
    onStart: (seconds: Int?) -> Unit,
    onStop: () -> Unit,
    onSetDone: () -> Unit,
    onWeights: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val p = guided.prescription
    val fmt = { v: Int -> if (guided.inSeconds) Measures.formatSeconds(v) else "$v" }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(p.exercise.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Text(
            listOfNotNull(
                if (guided.sets > 1) "Série ${set + 1} sur ${guided.sets}" else null,
                p.loadLabel,
                if (p.isTest) "test" else null,
            ).joinToString(" · "),
            style = MaterialTheme.typography.titleMedium,
            color = color,
        )
        // Le choix se fait avant de commencer : en cours de chrono, les chiffres ne bougent plus.
        p.weighted?.let { on ->
            Spacer(Modifier.height(8.dp))
            WeightsChip(on, enabled = running == null, onChange = onWeights)
        }
        if (guided.isAllOut(set)) {
            Text(
                (if (p.isTest) "📏 À fond" else "Série à fond") + (p.target?.let { " · objectif ${fmt(it)}" } ?: ""),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = BossColor,
            )
        }
        Spacer(Modifier.height(28.dp))

        val countdown = guided.countdown(set)
        when {
            running != null -> {
                val left = running.leftSeconds(now)
                Text(clock(left ?: (running.elapsedMs(now) / 1000).toInt()), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.ExtraBold)
                if (left != null) {
                    LinearProgressIndicator(progress = { running.progress(now) }, color = color, modifier = Modifier.fillMaxWidth(0.6f).padding(vertical = 12.dp))
                } else {
                    Text("Tiens le plus longtemps possible", style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(20.dp))
                Button(onClick = onStop, colors = ButtonDefaults.buttonColors(containerColor = if (left == null) BossColor else MaterialTheme.colorScheme.primary)) {
                    Text(if (left == null) "Stop" else "J'arrête là")
                }
            }
            countdown != null -> {
                Text(clock(countdown), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(20.dp))
                Button(onClick = { onStart(countdown) }, modifier = Modifier.height(56.dp)) { Text("▶ Démarrer") }
            }
            guided.countUp(set) -> {
                Text("0:00", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(20.dp))
                Button(onClick = { onStart(null) }, modifier = Modifier.height(56.dp)) { Text("▶ Démarrer le chrono") }
            }
            guided.needsValue(set) -> {
                val stepSize = if (guided.inSeconds) 5 else 1
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { onValue((value - stepSize).coerceAtLeast(0)) }, modifier = Modifier.size(64.dp)) {
                        Text("−", style = MaterialTheme.typography.headlineMedium)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(150.dp)) {
                        Text(fmt(value), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.ExtraBold)
                        Text(if (guided.inSeconds) "tenu" else "reps", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalIconButton(onClick = { onValue(value + stepSize) }, modifier = Modifier.size(64.dp)) {
                        Text("+", style = MaterialTheme.typography.headlineMedium)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Button(onClick = onSetDone, modifier = Modifier.height(56.dp)) { Text("✓ Série faite") }
            }
            else -> {
                p.durationLabel?.let { Text(it, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold) }
                p.repsLabel?.let { Text("$it reps", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold) }
                Spacer(Modifier.height(20.dp))
                Button(onClick = onSetDone, modifier = Modifier.height(56.dp)) { Text(if (guided.sets > 1) "✓ Série faite" else "✓ C'est fait") }
            }
        }

        p.exercise.description?.let {
            Spacer(Modifier.height(28.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EndContent(schedule: ProgramSchedule, session: Session, onClose: () -> Unit) {
    val done = schedule.doneCount(session)
    val all = done == session.exerciseCount
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(40.dp))
        Text(if (all) "💪" else "⏸", style = MaterialTheme.typography.displayLarge)
        Text(
            if (all) "Séance terminée" else "Séance en pause",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            if (all) "Tout est fait. Bien joué." else "$done exercice${if (done > 1) "s" else ""} sur ${session.exerciseCount} de fait. Le reste t'attend.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onClose) { Text("Fermer") }
    }
}
