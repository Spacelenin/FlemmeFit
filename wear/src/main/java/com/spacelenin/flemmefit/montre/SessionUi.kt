package com.spacelenin.flemmefit.montre

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay

/** L'heure, rafraîchie plusieurs fois par seconde : les minuteurs se recalculent toujours depuis l'heure. */
@Composable
private fun clock(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(250)
        }
    }
    return now
}

fun formatSeconds(total: Int): String {
    val t = total.coerceAtLeast(0)
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun plannedLabel(ex: WatchExercise): String =
    if (ex.timed) formatSeconds(ex.planned) else "${ex.planned}"

@Composable
private fun Big(value: String, unit: String? = null, size: Int = 56) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = text(size.sp, FontWeight.Black), color = LocalPalette.current.ink, maxLines = 1)
        if (unit != null) Text(unit, style = text(15.sp, FontWeight.ExtraBold), color = LocalPalette.current.ink, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
    }
}

@Composable
private fun Stack(top: Int = 0, content: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = top.dp),
    ) { content() }
}

/** La séance en cours : un écran par étape. */
@Composable
fun SessionScreen(run: Run, today: WatchToday, ambient: Boolean) {
    val context = LocalContext.current
    val now = clock()
    val ex = SessionFlow.current(run, today)
    val progress = SessionFlow.progress(run, today)
    val dayCtx = ctxFor(today.mode)
    var showHow by remember(run.pos) { mutableStateOf(false) }

    if (ambient) {
        AmbientScreen(run, today, ex, now)
        return
    }

    when {
        showHow && ex != null -> Themed(LocalPalette.current.night, dayCtx) {
            Dial(progress) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 30.dp, end = 30.dp, top = 40.dp, bottom = 40.dp),
                ) {
                    Kicker(ex.name)
                    Text(ex.how ?: "Pas de consigne pour cet exo.", style = text(13.sp, FontWeight.SemiBold), color = LocalPalette.current.ink)
                    FitButton("Retour", { showHow = false }, plain = true)
                    FitButton("Passer l'exo", { showHow = false; SessionFlow.skipExercise(context, today) }, plain = true)
                    FitButton("Arrêter la séance", { showHow = false; SessionFlow.stopEarly() }, plain = true, ctx = Red)
                }
            }
        }

        run.phase == Phase.REST -> Themed(LocalPalette.current.night, Sky) {
            val left = ((run.restEndsAt - now + 999) / 1000).toInt()
            val restTotal = (ex?.restSec ?: 60).coerceAtLeast(1)
            Dial((left.toFloat() / restTotal).coerceIn(0f, 1f)) {
                Stack {
                    Kicker("Repos")
                    Big(formatSeconds(left))
                    if (ex != null) Meta("Ensuite : série ${run.set + 1}/${ex.sets} · ${plannedLabel(ex)}" + (if (ex.timed) "" else " reps"))
                    Row2 {
                        FitButton("+15 s", { SessionFlow.addRest() }, plain = true)
                        FitButton(if (left <= 0) "Go" else "Passer", { SessionFlow.endRest() })
                    }
                }
            }
        }

        run.phase == Phase.NEXT && ex != null -> Themed(LocalPalette.current.night, Sky) {
            Dial(progress) {
                Stack {
                    Kicker("Exo suivant · ${run.pos + 1}/${run.queue.size}")
                    Title(ex.name)
                    Big(if (ex.timed) formatSeconds(ex.planned) else "${ex.sets} × ${ex.planned}", size = 40)
                    Meta(listOfNotNull(ex.restSec.takeIf { it > 0 }?.let { "repos $it s" }, "dernière à fond".takeIf { ex.allOut }, ex.tag).joinToString(" · "))
                    FitButton("On y va", { SessionFlow.go() })
                }
            }
        }

        run.phase == Phase.INPUT && ex != null -> Themed(LocalPalette.current.night, if (today.mode == WatchToday.MODE_BOSS) Red else dayCtx) {
            InputScreen(run, today, ex, progress)
        }

        run.phase == Phase.EXERCISE && ex != null -> Themed(LocalPalette.current.night, if (ex.timed) Orange else dayCtx) {
            val elapsed = ((now - run.phaseStartedAt) / 1000).toInt()
            Dial(if (ex.timed) (elapsed.toFloat() / ex.planned.coerceAtLeast(1)).coerceIn(0f, 1f) else progress) {
                Stack {
                    Kicker(
                        "Exo ${run.pos + 1}/${run.queue.size} · série ${run.set + 1}/${ex.sets}" +
                            (SessionFlow.side(run, ex)?.let { " · $it" } ?: ""),
                    )
                    // Toucher le nom : la consigne.
                    Box(Modifier.clickable(role = Role.Button, onClickLabel = "Consigne") { showHow = true }) { Title(ex.name, 17.sp) }
                    if (ex.timed) {
                        Big(formatSeconds(elapsed), size = 44)
                        Meta("objectif ${formatSeconds(ex.planned)}")
                        FitButton("Terminé", { SessionFlow.setDone(context, today) })
                    } else {
                        Big("${ex.planned}", "reps")
                        when {
                            SessionFlow.needsInput(run, ex) -> Chip("à fond" + (ex.target?.let { " · objectif $it" } ?: ""), if (today.mode == WatchToday.MODE_BOSS) Red else LocalCtx.current)
                            ex.level != null && ex.levelCount != null -> Stars(ex.level, ex.levelCount)
                            ex.tag != null -> Meta(ex.tag)
                        }
                        FitButton("✓ Série faite", { SessionFlow.setDone(context, today) })
                    }
                }
            }
        }

        run.phase == Phase.FEELING -> Themed(LocalPalette.current.night, dayCtx) {
            Dial(1f) {
                Stack {
                    Kicker("Ressenti ?")
                    val feelings = listOf("😴", "👌", "😰", "🥵")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        feelings.chunked(2).forEachIndexed { row, pair ->
                            Row2 {
                                pair.forEachIndexed { col, emoji ->
                                    FitButton(emoji, { SessionFlow.feeling(context, row * 2 + col) }, plain = true)
                                }
                            }
                        }
                    }
                    Meta("Toucher pour valider")
                }
            }
        }

        else -> Themed(LocalPalette.current.night, dayCtx) { RecapScreen(run, today) }
    }
}

@Composable
private fun InputScreen(run: Run, today: WatchToday, ex: WatchExercise, progress: Float) {
    val context = LocalContext.current
    var value by remember(run.pos, run.set) { mutableIntStateOf(ex.target ?: ex.planned) }
    var scroll by remember { mutableFloatStateOf(0f) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val reached = ex.target == null || value >= ex.target
    Dial(progress) {
        Box(
            Modifier
                .fillMaxSize()
                // Lunette (tactile sur la Watch 5 Pro) : un cran = une répétition.
                .onRotaryScrollEvent { e ->
                    scroll += e.verticalScrollPixels
                    val steps = (scroll / 40f).toInt()
                    if (steps != 0) {
                        value = (value + steps).coerceIn(0, 999)
                        scroll -= steps * 40f
                    }
                    true
                }
                .focusRequester(focus)
                .focusable(),
        ) {
            Stack {
                ex.target?.let { Chip("💀 objectif $it") }
                Kicker("Dernière série · à fond")
                Row2 {
                    FitRound("−") { value = (value - 1).coerceAtLeast(0) }
                    Text("$value", style = text(52.sp, FontWeight.Black), color = if (reached) Green.c else LocalPalette.current.ink)
                    FitRound("+") { value = (value + 1).coerceAtMost(999) }
                }
                Meta("Tourne la lunette")
                FitButton("Valider", { SessionFlow.record(context, today, value) })
            }
        }
    }
}

@Composable
private fun RecapScreen(run: Run, today: WatchToday) {
    val context = LocalContext.current
    val pal = LocalPalette.current
    val pending = Store.pending.value.size
    Dial(1f) {
        Stack {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(44.dp).clip(CircleShape).background(LocalCtx.current.c).border(3.dp, Color.White, CircleShape),
            ) { Text("✓", style = text(22.sp, FontWeight.Black), color = Color.White) }
            Title(if (run.skipped == 0 && run.done == run.queue.size) "Séance finie !" else "Séance arrêtée", 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                RecapStat("exos", "${run.done}/${run.queue.size}")
                RecapStat("durée", "${run.minutes} min")
                run.batteryUsed?.let { RecapStat("batterie", "−$it %") }
            }
            Meta(
                when {
                    run.demo -> "Démo : rien n'est envoyé."
                    pending > 0 -> "📵 $pending envoi(s) en attente du téléphone (Bluetooth)."
                    else -> "✓ Envoyé au téléphone"
                },
            )
            FitButton("OK", { SessionFlow.close(context) })
        }
    }
}

@Composable
private fun RecapStat(label: String, value: String) {
    val pal = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = text(10.sp, FontWeight.SemiBold), color = pal.soft)
        Text(value, style = text(13.sp, FontWeight.Black), color = pal.ink)
    }
}

/** Poignet baissé : noir, traits fins, l'essentiel seulement (écran basse consommation). */
@Composable
private fun AmbientScreen(run: Run, today: WatchToday, ex: WatchExercise?, now: Long) {
    val grey = Color(0xFF8A8A8A)
    Box(Modifier.fillMaxSize().background(Color.Black).border(2.dp, Color(0xFF333333), CircleShape), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (run.phase) {
                Phase.REST -> {
                    Text("Repos", style = text(13.sp, FontWeight.SemiBold), color = grey)
                    Text(formatSeconds(((run.restEndsAt - now + 999) / 1000).toInt()), style = text(48.sp, FontWeight.SemiBold), color = Color.White)
                }
                Phase.EXERCISE -> if (ex != null && ex.timed) {
                    Text(ex.name, style = text(13.sp, FontWeight.SemiBold), color = grey, maxLines = 1)
                    Text(formatSeconds(((now - run.phaseStartedAt) / 1000).toInt()), style = text(44.sp, FontWeight.SemiBold), color = Color.White)
                } else {
                    Text(ex?.name ?: "", style = text(15.sp, FontWeight.SemiBold), color = Color.White, maxLines = 2)
                    if (ex != null) Text("série ${run.set + 1}/${ex.sets} · ${ex.planned} reps", style = text(13.sp, FontWeight.SemiBold), color = grey)
                }
                else -> Text(today.title ?: "FlemmeFit", style = text(15.sp, FontWeight.SemiBold), color = Color.White)
            }
        }
    }
}
