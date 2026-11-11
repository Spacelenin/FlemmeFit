package com.spacelenin.flemmefit.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.spacelenin.flemmefit.model.Celebration
import com.spacelenin.flemmefit.model.ExerciseLog
import com.spacelenin.flemmefit.ui.theme.LocalFitTheme
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
    onEccentric: (exerciseIndex: Int, on: Boolean) -> Unit = { _, _ -> },
    onTired: (exerciseIndex: Int, on: Boolean) -> Unit = { _, _ -> },
    onFeeling: (exerciseIndex: Int, feeling: Int?) -> Unit,
    onClose: () -> Unit,
) {
    HoldSession()
    // Les exercices à faire, figés à l'ouverture : en valider un ne décale pas la suite.
    val queue = remember(session.index) {
        session.order.filter { !schedule.program.isDone(session.index, it) && !finishedAttempt(schedule, session, it) }
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
    // Temps en plus : l'appli a-t-elle été quittée pendant ? Alors « Stop » demande ce qui a vraiment été tenu.
    var awayDuringOvertime by remember { mutableStateOf(false) }
    var confirmExtra by remember { mutableStateOf<Int?>(null) }
    // Temps en plus très long : confirmé avant d'être gardé (Stop oublié ?).
    var longExtra by remember { mutableStateOf(false) }
    // Exercice qui vient d'être fini : son ressenti est proposé pendant le repos qui suit (ou à la fin).
    var feelingFor by remember { mutableStateOf<Int?>(null) }
    // Bannière de fête (thème fit) : exploit, record, Boss vaincu.
    var banner by remember { mutableStateOf<String?>(null) }
    val fit = LocalFitTheme.current
    // Tout fini en entier, sans exo passé : de quoi annoncer un Boss vaincu.
    var allComplete by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (step == Step.RUN && run?.inOvertime == true) awayDuringOvertime = true }

    val exerciseIndex = queue.getOrNull(pos)
    val prescription = exerciseIndex?.let { session.prescriptions.getOrNull(it) }
    val restBonus = LocalRestBonus.current
    val guided = remember(prescription, restBonus) { prescription?.let { GuidedExercise(it, restBonus) } }
    var value by remember(pos, set) { mutableIntStateOf(guided?.suggested(set) ?: 0) }

    val context = LocalContext.current
    val feedback = remember { TimerFeedback(context) }
    // Repos ou série au temps : la notification prend le relais si le téléphone est verrouillé.
    TimerNotification(run?.let { it.copy(title = prescription?.exercise?.name ?: it.title) })
    AskNotifications()
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
            if (fit && session.isBoss && allComplete) banner = Celebration.BOSS
        }
    }

    fun finishSet(v: Int) {
        val g = guided ?: return
        values = values + v
        run = null
        if (set < g.sets - 1) {
            // Série enregistrée tout de suite (l'exercice reste incomplet tant que tout n'est pas fait).
            savePartial()
            val rest = g.restAfter(set)
            set++
            startRest(rest)
        } else {
            val (log, complete) = g.result(values)
            if (!complete) allComplete = false
            exerciseIndex?.let { i ->
                if (fit) banner = Celebration.of(g.prescription, log, runCatching { schedule.previousBest(session, i) }.getOrNull()) ?: banner
                onSave(i, log, complete)
            }
            feelingFor = exerciseIndex?.takeIf { g.prescription.exercise.progression != null && !g.prescription.exercise.optional }
            nextExercise(g.restSeconds)
        }
    }

    /** « Stop » : le temps tenu, temps en plus compris ; à confirmer si l'appli était fermée pendant le temps en plus. */
    fun stopSet() {
        val g = guided ?: return
        val r = run ?: return finishSet(0)
        // Pendant la mise en place : rien n'a commencé, retour avant « Démarrer ».
        if (r.inSetup) {
            run = null
            step = Step.SET
            return
        }
        val elapsed = (r.elapsedMs(SystemClock.elapsedRealtime()) / 1000).toInt()
        if (!r.inOvertime) return finishSet(elapsed)
        if (awayDuringOvertime && elapsed > AWAY_CHECK_SECONDS) {
            run = r.pause(SystemClock.elapsedRealtime())
            longExtra = false
            confirmExtra = elapsed
        } else if (g.isLongOvertime(set, elapsed)) {
            run = r.pause(SystemClock.elapsedRealtime())
            longExtra = true
            confirmExtra = elapsed
        } else {
            finishSet(g.withOvertime(set, elapsed))
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
            if (next.index != r.index) {
                // Fin du temps prévu : ça sonne, et le chrono continue en temps en plus jusqu'à « Stop ».
                feedback.phaseEnded(toRest = false)
                awayDuringOvertime = false
                run = next
                break
            }
            val left = r.leftSeconds(now)
            if (left != null && left in 1..3 && left != lastBeep) feedback.tick()
            lastBeep = left ?: -1
            delay(100)
        }
    }

    FullScreenDialog(onDismissRequest = ::close, dismissOnClickOutside = false) {
        val view = LocalView.current
        DisposableEffect(view) {
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = false }
        }
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
                EndContent(schedule, session, onClose) {
                    feelingFor?.let { i -> ExerciseFeeling(session, schedule, i, onFeeling) }
                }
            } else if (step == Step.REST) {
                val r = run
                val left = r?.leftSeconds(now) ?: 0
                Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("Repos", style = MaterialTheme.typography.titleLarge, color = DeloadColor, fontWeight = FontWeight.Bold)
                    Text(clock(left), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.ExtraBold)
                    FitProgress(progress = { r?.progress(now) ?: 1f }, color = DeloadColor, modifier = Modifier.fillMaxWidth(0.6f).padding(vertical = 12.dp))
                    Text(
                        "Ensuite : " + if (set == 0) prescription.exercise.name else "série ${set + 1} sur $sets",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    // Une série par côté : le côté qui vient, en couleur.
                    guided.sideOf(set)?.let { side ->
                        Text(
                            "${sideMark(side)} ${guided.sided!!.label(side)}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = sideColor(side),
                        )
                    }
                    feelingFor?.takeIf { set == 0 && it == queue.getOrNull(pos - 1) }?.let { i ->
                        Spacer(Modifier.height(20.dp))
                        ExerciseFeeling(session, schedule, i, onFeeling)
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(onClick = { run = run?.addSeconds(15) }) { Text("+15 s") }
                        PillButton(onClick = { run = null; step = Step.SET }) { Text("Passer le repos") }
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
                        // Quelques secondes pour se mettre en place avant que le temps compte.
                        val phases = listOfNotNull(
                            TimerPhase.setup(),
                            TimerPhase("Série", seconds),
                            TimerPhase(OVERTIME_LABEL, null).takeIf { guided.overtime(set) },
                        )
                        run = TimerRun(prescription.exercise.name, phases, phaseStart = SystemClock.elapsedRealtime())
                        step = Step.RUN
                    },
                    onStop = ::stopSet,
                    onSkipSetup = { run = run?.next(SystemClock.elapsedRealtime()) },
                    onSetDone = { finishSet(if (guided.needsValue(set)) value else 0) },
                    onWeights = { on -> exerciseIndex?.let { onWeights(it, on) } },
                    onEccentric = { on -> exerciseIndex?.let { onEccentric(it, on) } },
                    onTired = { on -> exerciseIndex?.let { onTired(it, on) } },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        savePartial()
                        if (!prescription.exercise.optional) allComplete = false
                        nextExercise(rest = null)
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text(if (prescription.exercise.optional) "Passer (facultatif)" else "Passer l'exercice") }
            }
        }
        banner?.let { RecordBanner(it, onDone = { banner = null }) }
    }
    confirmExtra?.let { extra ->
        val g = guided
        OvertimeDialog(g?.countdown(set) ?: 0, extra, long = longExtra) { chosen ->
            confirmExtra = null
            awayDuringOvertime = false
            finishSet(g?.withOvertime(set, chosen) ?: 0)
        }
    }
}

@Composable
private fun OvertimeDialog(planned: Int, extra: Int, long: Boolean, onDone: (Int) -> Unit) {
    var value by remember { mutableIntStateOf(extra) }
    PanelDialog(
        onDismissRequest = {},
        title = { Text(if (long) "Temps vraiment long" else "Temps en plus") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (long) "${clock(planned + value)} au total pour ${Measures.formatSeconds(planned)} prévues. Tu veux vraiment valider ce temps ? Corrige-le si tu as oublié d'appuyer sur Stop."
                    else "L'appli était fermée pendant ce temps-là : tu as continué combien après les ${Measures.formatSeconds(planned)} ?",
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { value = (value - 5).coerceAtLeast(0) }) { Text("−") }
                    Text("+ ${clock(value)}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                    FilledTonalIconButton(onClick = { value += 5 }) { Text("+") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(value) }) { Text(if (long) "Valider" else "OK") } },
        dismissButton = { TextButton(onClick = { onDone(0) }) { Text("Rien en plus") } },
    )
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
    onSkipSetup: () -> Unit,
    onSetDone: () -> Unit,
    onWeights: (Boolean) -> Unit,
    onEccentric: (Boolean) -> Unit,
    onTired: (Boolean) -> Unit,
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
                if (p.exercise.optional) "facultatif" else null,
            ).joinToString(" · "),
            style = MaterialTheme.typography.titleMedium,
            color = color,
        )
        // Une série par côté : le côté de cette série, en gros et en couleur.
        guided.sideOf(set)?.let { side ->
            Spacer(Modifier.height(8.dp))
            Text(
                "${sideMark(side)} ${guided.sided!!.label(side)}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = sideColor(side),
            )
        }
        // Un côté à la fois : quels membres travaillent, à ne pas rater.
        p.exercise.sides?.let {
            Spacer(Modifier.height(8.dp))
            Text("↔ $it", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
        // Jour variante : rappelé au moment des séries concernées.
        val reminder = p.variant?.let {
            if (it.whole) "🎲 Variante, à la place de ${it.baseName.lowercase()}" else "🎲 Variante, à la place de ta série max de ${it.baseName.lowercase()}"
        }
            ?: p.replacedBy?.let { "🎲 Pas de série max ici : elle laisse place à ${it.lowercase()}" }
        reminder?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = BurstColor, textAlign = TextAlign.Center)
        }
        // Le choix se fait avant de commencer : en cours de chrono, les chiffres ne bougent plus.
        p.weighted?.let { on ->
            Spacer(Modifier.height(8.dp))
            WeightsChip(on, enabled = running == null, onChange = onWeights)
        }
        // Excentrique ou Crevé activés : en haut, bien visibles, avec leur consigne.
        if (p.eccentric) {
            Spacer(Modifier.height(8.dp))
            EccentricSection(p, enabled = running == null, onEccentric = onEccentric, center = true)
        }
        if (p.tired) {
            Spacer(Modifier.height(8.dp))
            TiredSection(p, enabled = running == null && set == 0, onTired = onTired, center = true)
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
            running != null && running.inSetup -> {
                Text(running.phase.label + "…", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
                Text("${running.leftSeconds(now) ?: 0}", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.ExtraBold)
                FitProgress(progress = { running.progress(now) }, color = color, modifier = Modifier.fillMaxWidth(0.6f).padding(vertical = 12.dp))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onStop) { Text("Annuler") }
                    PillButton(onClick = onSkipSetup) { Text("Go !") }
                }
            }
            running != null -> {
                val left = running.leftSeconds(now)
                val elapsed = (running.elapsedMs(now) / 1000).toInt()
                val overtime = running.inOvertime
                Text(
                    if (overtime) "+ ${clock(elapsed)}" else clock(left ?: elapsed),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (overtime) color else Color.Unspecified,
                )
                when {
                    overtime -> Text("Temps en plus : Stop quand tu t'arrêtes", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    left != null -> FitProgress(progress = { running.progress(now) }, color = color, modifier = Modifier.fillMaxWidth(0.6f).padding(vertical = 12.dp))
                    else -> Text("Tiens le plus longtemps possible", style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(20.dp))
                PillButton(onClick = onStop, colors = ButtonDefaults.buttonColors(containerColor = if (left == null) BossColor else MaterialTheme.colorScheme.primary)) {
                    Text(if (left == null) "Stop" else "J'arrête là")
                }
            }
            countdown != null -> {
                Text(clock(countdown), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(20.dp))
                StartButton(onClick = { onStart(countdown) }, modifier = Modifier.height(56.dp), badge = "▶") { Text("Démarrer") }
            }
            guided.countUp(set) -> {
                Text("0:00", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(20.dp))
                StartButton(onClick = { onStart(null) }, modifier = Modifier.height(56.dp), badge = "▶") { Text("Démarrer le chrono") }
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
                StartButton(onClick = onSetDone, modifier = Modifier.height(56.dp), badge = "✓") { Text("Série faite") }
            }
            else -> {
                p.durationLabel?.let { Text(it, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold) }
                p.repsLabel?.let { Text("$it reps", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold) }
                Spacer(Modifier.height(20.dp))
                StartButton(onClick = onSetDone, modifier = Modifier.height(56.dp), badge = "✓") { Text(if (guided.sets > 1) "Série faite" else "C'est fait") }
            }
        }

        p.exercise.description?.let {
            Spacer(Modifier.height(28.dp))
            InstructionBubble(it)
        }
        // Éteints : une petite ligne discrète, sous la consigne.
        if (p.eccentricOffer != null && !p.eccentric) {
            Spacer(Modifier.height(8.dp))
            EccentricSection(p, enabled = running == null, onEccentric = onEccentric, center = true)
        }
        if (p.tiredOffer != null && !p.tired) {
            Spacer(Modifier.height(8.dp))
            TiredSection(p, enabled = running == null && set == 0, onTired = onTired, center = true)
        }
    }
}

/** Ressenti d'un exercice fini, à donner si on veut : il règle cet exercice-là, pas les autres. */
@Composable
private fun ExerciseFeeling(session: Session, schedule: ProgramSchedule, exerciseIndex: Int, onFeeling: (Int, Int?) -> Unit) {
    val name = session.prescriptions.getOrNull(exerciseIndex)?.exercise?.name ?: return
    val current = schedule.program.exerciseFeelingOf(session.index, exerciseIndex)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$name, c'était comment ?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        ExerciseFeelingChips(current) { onFeeling(exerciseIndex, it) }
    }
}

private const val OVERTIME_LABEL = "Temps en plus"

/** Temps en plus au-delà duquel, l'appli ayant été quittée, on demande ce qui a vraiment été tenu. */
private const val AWAY_CHECK_SECONDS = 60

@Composable
private fun EndContent(schedule: ProgramSchedule, session: Session, onClose: () -> Unit, feeling: @Composable () -> Unit) {
    val done = schedule.requiredDone(session)
    val all = schedule.isComplete(session)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(40.dp))
        Text(if (all) "💪" else "⏸", style = MaterialTheme.typography.displayLarge)
        Text(
            if (all) "Séance terminée" else "Séance en pause",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            if (all) "Tout est fait. Bien joué." else "$done exercice${if (done > 1) "s" else ""} sur ${session.requiredCount} de fait. Le reste t'attend.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        feeling()
        Spacer(Modifier.height(20.dp))
        PillButton(onClick = onClose) { Text("Fermer") }
    }
}

/** Toutes les séries déjà notées sans que l'exo soit validé (Boss sous le prévu) : pas la peine de le refaire. */
private fun finishedAttempt(schedule: ProgramSchedule, session: Session, exerciseIndex: Int): Boolean {
    val sets = session.prescriptions.getOrNull(exerciseIndex)?.let(::GuidedExercise)?.sets ?: return false
    val values = schedule.program.logOf(session.index, exerciseIndex)?.perSet ?: return false
    return values.size == sets && values.all { it > 0 }
}
