package com.spacelenin.flemmefit.model

/**
 * Une étape du minuteur : effort ou repos. À rebours sur [seconds], ou chronométrée si null
 * (série à fond : ça tourne jusqu'à « Stop »).
 */
data class TimerPhase(
    val label: String,
    val seconds: Int?,
    val rest: Boolean = false,
    /** Objectif affiché pendant un chrono à fond (« 1min05 »). */
    val target: String? = null,
)

/**
 * Minuteur en cours. Les temps sont des millisecondes d'une horloge monotone (elapsedRealtime) :
 * l'appli en arrière-plan rattrape les étapes écoulées à son retour.
 */
data class TimerRun(
    val title: String,
    val phases: List<TimerPhase>,
    val phaseStart: Long,
    val index: Int = 0,
    val pausedAt: Long? = null,
    /** Secondes ajoutées à l'étape en cours (« +15 s »). */
    val bonusSeconds: Int = 0,
    val finished: Boolean = false,
) {
    val phase: TimerPhase get() = phases[index]
    val isLast: Boolean get() = index == phases.lastIndex
    val paused: Boolean get() = pausedAt != null && !finished

    fun elapsedMs(now: Long): Long = ((pausedAt ?: now) - phaseStart).coerceAtLeast(0)

    /** Secondes restantes de l'étape (arrondies au-dessus), ou null pour un chrono. */
    fun leftSeconds(now: Long): Int? = phase.seconds?.let { s ->
        val left = (s + bonusSeconds) * 1000L - elapsedMs(now)
        ((left + 999) / 1000).toInt().coerceAtLeast(0)
    }

    /** Avancement de l'étape entre 0 et 1 (0 pour un chrono). */
    fun progress(now: Long): Float {
        val total = (phase.seconds ?: return 0f) + bonusSeconds
        return if (total <= 0) 1f else (elapsedMs(now) / (total * 1000f)).coerceIn(0f, 1f)
    }

    fun pause(now: Long) = if (pausedAt == null && !finished) copy(pausedAt = now) else this

    fun resume(now: Long) = pausedAt?.takeIf { !finished }?.let { copy(phaseStart = phaseStart + (now - it), pausedAt = null) } ?: this

    fun addSeconds(seconds: Int) = if (phase.seconds != null && !finished) copy(bonusSeconds = bonusSeconds + seconds) else this

    /** Passer à l'étape suivante (ou finir) tout de suite. */
    fun next(now: Long) = when {
        finished -> this
        isLast -> copy(finished = true, pausedAt = pausedAt ?: now)
        else -> copy(index = index + 1, phaseStart = now, pausedAt = null, bonusSeconds = 0)
    }

    /** Fait passer les étapes à rebours écoulées ; la suivante démarre pile à la fin de la précédente. */
    fun advance(now: Long): TimerRun {
        var r = this
        while (!r.finished && r.pausedAt == null) {
            val s = r.phase.seconds ?: break
            val end = r.phaseStart + (s + r.bonusSeconds) * 1000L
            if (now < end) break
            r = if (r.isLast) r.copy(finished = true, pausedAt = end) else r.copy(index = r.index + 1, phaseStart = end, bonusSeconds = 0)
        }
        return r
    }
}

/** Ce que le minuteur sait lancer pour un exercice. */
object TimerPlans {
    /** Repos entre deux séries, si l'exercice en indique un lisible. */
    fun rest(p: Prescription): TimerPhase? =
        Measures.seconds(p.exercise.rest)?.takeIf { it > 0 }?.let { TimerPhase("Repos", it, rest = true) }

    /**
     * Chrono d'un exercice en durée : séries et repos enchaînés, dernière série chronométrée si elle est à fond.
     * Null si l'exercice ne se fait pas au temps.
     */
    fun exercise(p: Prescription): List<TimerPhase>? {
        val target = p.target?.takeIf { p.maxIsSeconds }?.let(Measures::formatSeconds)
        if (p.isTest) return if (p.maxIsSeconds) listOf(TimerPhase("À fond", null, target = target)) else null
        val work = p.seconds?.takeIf { it > 0 } ?: return null
        val sets = (p.sets ?: 1).coerceAtLeast(1)
        val rest = rest(p)
        return buildList {
            for (i in 1..sets) {
                val last = i == sets
                val label = if (sets > 1) "Série $i/$sets" else "Effort"
                if (last && p.allOut && p.maxIsSeconds) add(TimerPhase("$label · à fond", null, target = target))
                else add(TimerPhase(label, work))
                if (!last && rest != null) add(rest)
            }
        }
    }
}
