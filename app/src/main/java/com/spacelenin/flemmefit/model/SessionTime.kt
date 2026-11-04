package com.spacelenin.flemmefit.model

/** Durée estimée d'une séance, en secondes, par poste. */
data class SessionEstimate(
    /** Échauffement et étirements (facultatifs), repos compris. */
    val warmup: Int,
    /** Temps d'effort des exercices. */
    val work: Int,
    /** Repos entre les séries. */
    val rest: Int,
    /** Passage d'un exercice au suivant. */
    val transitions: Int,
) {
    val total: Int get() = warmup + work + rest + transitions

    /** Arrondi à la minute supérieure. */
    val minutes: Int get() = minutesOf(total)

    companion object {
        fun minutesOf(seconds: Int): Int = (seconds + 59) / 60
    }
}

/**
 * Estimation de la durée d'une séance telle qu'elle est prévue ce jour-là (variante, remplacement, Burst, Chill,
 * Boss compris) : durée des exos en temps, reps à quelques secondes chacune, repos prévus (bonus de repos compris),
 * petit repos entre les deux côtés, un moment pour passer d'un exo à l'autre.
 */
object SessionTime {
    const val SECONDS_PER_REP = 3
    /** Exos lents (« tempo ») : chaque rep prend plus de temps. */
    const val SECONDS_PER_SLOW_REP = 6
    const val TRANSITION_SECONDS = 30

    fun estimate(session: Session, restBonus: Int = 0): SessionEstimate {
        var warmup = 0
        var work = 0
        var rest = 0
        val exercises = session.order.map { session.prescriptions[it] }
        exercises.forEach { pr ->
            val sided = pr.sided
            val sets = (sided?.order?.size ?: pr.sets ?: 1).coerceAtLeast(1)
            val restBetween = pr.restSeconds(restBonus) ?: 0
            var effort = 0
            var pauses = 0
            for (set in 0 until sets) {
                effort += setSeconds(pr, sided?.plannedAt(set))
                if (set < sets - 1) pauses += if (sided?.switchesAfter(set) == true) minOf(sided.switchRest, restBetween) else restBetween
            }
            if (pr.exercise.optional) {
                warmup += effort + pauses
            } else {
                work += effort
                rest += pauses
            }
        }
        return SessionEstimate(warmup, work, rest, (exercises.size - 1).coerceAtLeast(0) * TRANSITION_SECONDS)
    }

    /** Une série : sa durée, ou ses reps au rythme d'un exo normal ou lent. [planned] : la valeur de ce côté-là. */
    private fun setSeconds(pr: Prescription, planned: Int?): Int {
        val timed = pr.maxIsSeconds || pr.seconds != null || (pr.reps == null && pr.exercise.duration != null)
        if (timed) return planned ?: pr.seconds ?: Measures.seconds(pr.exercise.duration) ?: 0
        val reps = planned ?: pr.reps ?: firstNumber(pr.exercise.reps) ?: 0
        return reps * if (isSlow(pr.exercise)) SECONDS_PER_SLOW_REP else SECONDS_PER_REP
    }

    private fun isSlow(ex: Exercise): Boolean {
        val name = ex.name.lowercase()
        return "tempo" in name || "lent" in name
    }

    /** « 8-10 » → 8, « max » → null. */
    private fun firstNumber(text: String?): Int? = text?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }
}
