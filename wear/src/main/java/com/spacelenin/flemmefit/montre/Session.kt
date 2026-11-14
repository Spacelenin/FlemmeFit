package com.spacelenin.flemmefit.montre

import android.content.Context

/** Les gestes de la séance guidée : chacun fait avancer la [Run] enregistrée dans [Store]. */
object SessionFlow {
    private const val EXTRA_REST_MS = 15_000L

    fun current(run: Run, today: WatchToday): WatchExercise? =
        run.queue.getOrNull(run.pos)?.let { today.exercises.getOrNull(it) }

    fun nextExercise(run: Run, today: WatchToday): WatchExercise? =
        run.queue.getOrNull(run.pos + 1)?.let { today.exercises.getOrNull(it) }

    fun isLastSet(run: Run, ex: WatchExercise) = run.set >= ex.sets - 1

    /** Dernière série à fond : on saisit ce qu'on a fait. */
    fun needsInput(run: Run, ex: WatchExercise) = ex.allOut && !ex.timed && isLastSet(run, ex)

    /** Côté de la série en cours pour un exo par côté (gauche, droite, en alternance). */
    fun side(run: Run, ex: WatchExercise): String? = if (ex.sided) (if (run.set % 2 == 0) "gauche" else "droite") else null

    /** Avancement de la séance, de 0 à 1, pour l'anneau du bord (séries faites / séries prévues). */
    fun progress(run: Run, today: WatchToday): Float {
        val all = run.queue.mapNotNull { today.exercises.getOrNull(it) }
        val total = all.sumOf { it.sets }.coerceAtLeast(1)
        val before = all.take(run.pos).sumOf { it.sets }
        val now = if (run.phase == Phase.FEELING || run.phase == Phase.RECAP) total else before + run.values.size
        return (now.toFloat() / total).coerceIn(0f, 1f)
    }

    fun start(context: Context, today: WatchToday, demo: Boolean) {
        val queue = today.exercises.indices.filter { !today.exercises[it].done }
        if (queue.isEmpty()) return
        val now = System.currentTimeMillis()
        Store.setRun(
            Run(
                date = today.date, session = today.session, demo = demo, startedAt = now,
                startBattery = Store.readBattery(), queue = queue, phaseStartedAt = now,
            ),
        )
        SessionService.start(context)
    }

    /** « Série faite » : la valeur prévue, ou la saisie si c'est la série à fond. */
    fun setDone(context: Context, today: WatchToday) {
        val run = Store.run.value ?: return
        val ex = current(run, today) ?: return
        if (needsInput(run, ex)) {
            Store.setRun(run.copy(phase = Phase.INPUT))
            return
        }
        val value = if (ex.timed) ((System.currentTimeMillis() - run.phaseStartedAt) / 1000).toInt() else ex.planned
        record(context, today, value)
    }

    /** Valeur saisie (série à fond) ou mesurée (chrono). */
    fun record(context: Context, today: WatchToday, value: Int) {
        val run = Store.run.value ?: return
        val ex = current(run, today) ?: return
        val values = run.values + value
        val now = System.currentTimeMillis()
        if (!isLastSet(run, ex)) {
            Store.setRun(
                if (ex.restSec > 0) run.copy(draft = null, values = values, set = run.set + 1, phase = Phase.REST, restEndsAt = now + ex.restSec * 1000L, warned = false, rang = false)
                else run.copy(draft = null, values = values, set = run.set + 1, phase = Phase.EXERCISE, phaseStartedAt = now),
            )
            return
        }
        finishExercise(context, today, run.copy(draft = null, values = values), skipped = false)
    }

    fun skipExercise(context: Context, today: WatchToday) {
        val run = Store.run.value ?: return
        finishExercise(context, today, run, skipped = true)
    }

    private fun finishExercise(context: Context, today: WatchToday, run: Run, skipped: Boolean) {
        val ex = current(run, today) ?: return
        if (!run.demo) {
            Store.send(
                WatchLog(
                    id = Store.newId(), date = run.date, session = run.session, exercise = ex.i,
                    values = if (skipped) emptyList() else run.values, seconds = ex.timed, skipped = skipped,
                    at = System.currentTimeMillis(),
                ),
            )
        }
        val counted = (if (skipped) run.copy(skipped = run.skipped + 1) else run.copy(done = run.done + 1)).copy(draft = null)
        Store.setRun(
            if (run.pos + 1 < run.queue.size) counted.copy(pos = run.pos + 1, set = 0, values = emptyList(), phase = Phase.NEXT)
            else counted.copy(phase = Phase.FEELING),
        )
        PhoneLink.flush(context)
    }

    /** Mémorise la valeur réglée de la série en cours (rien n'est perdu en ouvrant la consigne). */
    fun setDraft(value: Int) {
        Store.updateRun { it.copy(draft = value) }
    }

    fun go() {
        Store.updateRun { it.copy(phase = Phase.EXERCISE, phaseStartedAt = System.currentTimeMillis(), warned = false, rang = false) }
    }

    fun addRest() {
        Store.updateRun { it.copy(restEndsAt = it.restEndsAt + EXTRA_REST_MS, warned = false, rang = false) }
    }

    fun endRest() = go()

    /** Arrêter avant la fin : on passe au ressenti, ce qui est fait reste fait. */
    fun stopEarly() {
        Store.updateRun { it.copy(phase = Phase.FEELING) }
    }

    fun feeling(context: Context, value: Int?) {
        val run = Store.run.value ?: return
        val end = System.currentTimeMillis()
        val ended = run.copy(feeling = value, phase = Phase.RECAP, endedAt = end, endBattery = Store.readBattery())
        if (!run.demo) {
            Store.send(
                WatchLog(
                    id = Store.newId(), date = run.date, session = run.session, feeling = value,
                    minutes = ended.minutes, battery = ended.batteryUsed, at = end,
                ),
            )
        }
        Store.setRun(ended)
        SessionService.stop(context)
        PhoneLink.flush(context)
    }

    fun close(context: Context) {
        Store.setRun(null)
        SessionService.stop(context)
    }
}
