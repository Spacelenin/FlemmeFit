package com.spacelenin.flemmefit.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacelenin.flemmefit.model.Measures
import com.spacelenin.flemmefit.model.TimerPhase
import com.spacelenin.flemmefit.model.TimerRun
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Minuteur de séance (repos, chrono), partagé par tous les écrans. */
class WorkoutTimer {
    private val _run = MutableStateFlow<TimerRun?>(null)
    val run: StateFlow<TimerRun?> = _run

    /** Ce que propose le bouton de fin (« Marquer comme fait »), s'il y en a un. */
    var onFinished: (() -> Unit)? = null
        private set

    private fun now() = SystemClock.elapsedRealtime()

    fun start(title: String, phases: List<TimerPhase>, onFinished: (() -> Unit)? = null) {
        if (phases.isEmpty()) return
        this.onFinished = onFinished
        _run.value = TimerRun(title, phases, phaseStart = now())
    }

    fun update(transform: (TimerRun, Long) -> TimerRun) {
        val r = _run.value ?: return
        _run.value = transform(r, now())
    }

    fun stop() {
        onFinished = null
        _run.value = null
    }
}

val LocalWorkoutTimer = staticCompositionLocalOf { WorkoutTimer() }

/** Le minuteur en cours, posé en bas de l'écran. L'écran reste allumé tant qu'il tourne. */
@Composable
fun TimerOverlay(timer: WorkoutTimer, modifier: Modifier = Modifier) {
    val run by timer.run.collectAsStateWithLifecycle()
    val current = run ?: return
    val context = LocalContext.current
    val feedback = remember { TimerFeedback(context) }
    DisposableEffect(Unit) { onDispose(feedback::release) }

    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(current.finished) {
        var lastBeep = -1
        while (!current.finished) {
            now = SystemClock.elapsedRealtime()
            val r = timer.run.value ?: break
            val next = r.advance(now)
            if (next != r) {
                timer.update { _, _ -> next }
                if (next.finished) feedback.finished() else feedback.phaseEnded(next.phase.rest)
            } else {
                // 3, 2, 1 avant la fin d'une étape à rebours.
                val left = r.leftSeconds(now)
                if (!r.paused && left != null && left in 1..3 && left != lastBeep) feedback.tick()
                lastBeep = left ?: -1
            }
            delay(100)
        }
        now = SystemClock.elapsedRealtime()
    }
    // Fini : le bandeau reste quelques secondes, le temps de marquer l'exercice.
    LaunchedEffect(current.finished) {
        if (current.finished) {
            delay(if (timer.onFinished != null) 15_000 else 4_000)
            timer.stop()
        }
    }

    val phase = current.phase
    val left = current.leftSeconds(now)
    val accent = if (phase.rest) DeloadColor else MaterialTheme.colorScheme.primary
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier.fillMaxWidth().widthIn(max = 560.dp).padding(horizontal = 12.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (current.finished) "Terminé ✓" else phase.label + if (current.phases.size > 1) " · ${current.index + 1}/${current.phases.size}" else "",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                    )
                    Text(current.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                IconButton(onClick = timer::stop) { Icon(Icons.Default.Close, "Arrêter le minuteur") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                val shown = left ?: (current.elapsedMs(now) / 1000).toInt()
                Column(Modifier.weight(1f)) {
                    Text(clock(shown), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold)
                    when {
                        current.finished && left == null -> Text("Tenu ${Measures.formatSeconds(shown)}", style = MaterialTheme.typography.bodyMedium)
                        current.finished -> {}
                        left == null -> Text(
                            "Chrono : touche Stop quand tu lâches" + (phase.target?.let { " · objectif $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> current.phases.getOrNull(current.index + 1)?.let {
                            Text("Ensuite : ${it.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (left != null && !current.finished) {
                LinearProgressIndicator(
                    progress = { current.progress(now) },
                    color = accent,
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp, bottom = 8.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (current.finished) {
                    val done = timer.onFinished
                    if (done != null) {
                        Button(onClick = { timer.stop(); done() }) { Text("Marquer comme fait") }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = timer::stop) { Text("Fermer") }
                } else if (phase.seconds == null) {
                    Button(onClick = { timer.update { r, t -> r.next(t) } }) { Text("Stop") }
                } else {
                    FilledTonalButton(onClick = { timer.update { r, t -> if (r.paused) r.resume(t) else r.pause(t) } }) {
                        Text(if (current.paused) "Reprendre" else "Pause")
                    }
                    FilledTonalButton(onClick = { timer.update { r, _ -> r.addSeconds(15) } }) { Text("+15 s") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { timer.update { r, t -> r.next(t) } }) { Text(if (current.isLast) "Finir" else "Passer") }
                }
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}

internal fun clock(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)

/** Vibrations et bips du minuteur. Le son suit le volume média, comme la musique. */
internal class TimerFeedback(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    private val tones: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 90)
    } catch (_: RuntimeException) {
        null
    }

    fun tick() {
        tones?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        vibrate(longArrayOf(0, 60))
    }

    /** Fin d'une étape : on enchaîne sur un repos, ou on repart. */
    fun phaseEnded(toRest: Boolean) {
        tones?.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
        vibrate(if (toRest) longArrayOf(0, 250) else longArrayOf(0, 250, 120, 250))
    }

    fun finished() {
        tones?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600)
        vibrate(longArrayOf(0, 400, 150, 400, 150, 400))
    }

    private fun vibrate(pattern: LongArray) {
        try {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (_: RuntimeException) {
        }
    }

    fun release() {
        tones?.release()
    }
}
