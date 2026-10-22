package com.spacelenin.flemmefit.model

import kotlin.math.roundToInt

/**
 * Compteur caché : tout ce qui a été fait sur une famille d'exercices (les exercices qui portent le même
 * `counter` dans la routine), en répétitions ou en temps.
 */
data class Counter(
    val name: String,
    val reps: Int = 0,
    val seconds: Int = 0,
    /** Part du temps faite avec poids. */
    val weightedSeconds: Int = 0,
) {
    val isTime: Boolean get() = reps == 0 && seconds > 0

    operator fun plus(other: Counter) =
        Counter(name, reps + other.reps, seconds + other.seconds, weightedSeconds + other.weightedSeconds)

    /** Un repère pour se rendre compte, ou null si c'est encore trop petit pour en dire quelque chose. */
    fun equivalence(): String? = when (name.trim().lowercase()) {
        "pompes" -> (reps / 100).takeIf { it >= 1 }?.let { "$it fois le défi des 100 pompes" }
        "abdos" -> (reps / 200).takeIf { it >= 1 }?.let { "$it fois le défi des 200 abdos" }
        // Une traction monte le corps d'environ 40 cm, un étage fait environ 3 m.
        "tractions" -> climb(reps * 0.4)
        // Un squat sur une jambe : environ 50 cm de descente et de remontée.
        "squats" -> climb(reps * 0.5)
        "boxe" -> (seconds / ROUND_SECONDS).takeIf { it >= 1 }?.let { rounds ->
            val fights = rounds / ROUNDS_PER_FIGHT
            "$rounds rounds de 3 min" + if (fights >= 1) ", soit $fights combat${plural(fights)} de 12 rounds" else ""
        }
        "gainage" -> (seconds / SONG_SECONDS).takeIf { it >= 1 }?.let { "$it chanson${plural(it)} entière${plural(it)} tenue${plural(it)} en planche" }
        "beat saber" -> (seconds / SONG_SECONDS).takeIf { it >= 1 }?.let { songs ->
            val films = seconds / FILM_SECONDS
            "$songs chansons découpées" + if (films >= 1) ", ou $films film${plural(films)} de 2 h" else ""
        }
        else -> null
    }

    private fun climb(meters: Double): String? {
        if (meters < FLOOR_METERS) return null
        val floors = (meters / FLOOR_METERS).toInt()
        val eiffel = meters / EIFFEL_METERS
        return "${meters.roundToInt()} m d'ascension, $floors étage${plural(floors)}" +
            if (eiffel >= 0.1) " (${(eiffel * 100).roundToInt()} % de la tour Eiffel)" else ""
    }

    companion object {
        /** Les compteurs se dévoilent quand l'un d'eux atteint ce cap… */
        const val UNLOCK_REPS = 1000
        /** … ou ce temps cumulé (10 h). */
        const val UNLOCK_SECONDS = 10 * 3600

        private const val ROUND_SECONDS = 180
        private const val ROUNDS_PER_FIGHT = 12
        private const val SONG_SECONDS = 180
        private const val FILM_SECONDS = 2 * 3600
        private const val FLOOR_METERS = 3.0
        private const val EIFFEL_METERS = 330.0

        private fun plural(n: Int) = if (n > 1) "s" else ""

        /** Assez fait pour dévoiler les compteurs ? */
        fun unlocked(counters: List<Counter>) = counters.any { it.reps >= UNLOCK_REPS || it.seconds >= UNLOCK_SECONDS }

        /** Le compteur qui a fait passer le cap, pour le message de déblocage. */
        fun milestone(counters: List<Counter>): Counter? =
            counters.firstOrNull { it.reps >= UNLOCK_REPS } ?: counters.firstOrNull { it.seconds >= UNLOCK_SECONDS }

        fun merge(all: List<List<Counter>>): List<Counter> =
            all.flatten().groupBy { it.name }.map { (_, list) -> list.reduce(Counter::plus) }.sortedBy { it.isTime }
    }
}
