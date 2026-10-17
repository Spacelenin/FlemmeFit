package com.spacelenin.flemmefit.model

import kotlin.math.ceil

/** Ce qui est demandé pour un exercice lors d'une séance précise (progression, décharge et Boss appliqués). */
data class Prescription(
    val exercise: Exercise,
    val sets: Int?,
    /** Répétitions chiffrées par série, ou null si texte libre. */
    val reps: Int?,
    /** Durée chiffrée par série, en secondes. */
    val seconds: Int?,
    val load: Measures.Load?,
    /** Dernière série « à fond » (jour Boss). */
    val allOut: Boolean = false,
    /** Objectif de la série à fond : dernier max + 1. */
    val target: Int? = null,
    val level: Int = 0,
) {
    val key get() = exercise.key
    val repsLabel: String? get() = reps?.toString() ?: exercise.reps
    val durationLabel: String? get() = seconds?.let(Measures::formatSeconds) ?: exercise.duration
    val loadLabel: String? get() = load?.let(Measures::formatLoad) ?: exercise.load

    /** Le max se mesure en répétitions, sauf pour un exercice uniquement en durée. */
    val maxIsSeconds: Boolean get() = reps == null && exercise.reps == null && (seconds != null || exercise.duration != null)

    /** « 3 × 12 · 8 kg · repos 60s » */
    fun summary(): String = listOfNotNull(
        when {
            sets != null && repsLabel != null -> "$sets × $repsLabel"
            sets != null && durationLabel != null -> "$sets × $durationLabel"
            sets != null -> "$sets séries"
            repsLabel != null -> "$repsLabel reps"
            else -> null
        },
        durationLabel?.takeIf { sets == null || repsLabel != null },
        loadLabel,
        exercise.rest?.let { "repos $it" },
    ).joinToString(" · ")

    /** Une saisie atteint-elle le prévu ? Ce qui n'est pas chiffré ne bloque pas. */
    fun isMetBy(log: ExerciseLog): Boolean =
        (sets == null || (log.sets ?: sets) >= sets) &&
            (reps == null || (log.reps ?: reps) >= reps) &&
            (seconds == null || (log.seconds ?: seconds) >= seconds)

    companion object {
        /** Applique [level] crans de progression au départ défini par l'exercice. */
        fun of(exercise: Exercise, level: Int, deload: Boolean, allOut: Boolean, lastMax: Int?): Prescription {
            val baseReps = Measures.reps(exercise.reps)
            val baseSeconds = Measures.seconds(exercise.duration)
            var sets = exercise.sets
            var reps = baseReps
            var seconds = baseSeconds
            var load = Measures.load(exercise.load)

            exercise.progression?.let { p ->
                val repStep = p.reps
                val secStep = Measures.seconds(p.duration)
                val maxSeconds = Measures.seconds(p.maxDuration)
                val loadStep = Measures.load(p.load)
                val maxLoad = Measures.load(p.maxLoad)

                fun tryAddSets(): Boolean {
                    if (p.addSets <= 0) return false
                    val next = (sets ?: 1) + p.addSets
                    if (p.maxSets != null && next > p.maxSets) return false
                    sets = next
                    return true
                }

                repeat(level.coerceAtLeast(0)) {
                    val r = reps
                    if (repStep != null && r != null) {
                        reps = when {
                            p.maxReps == null || r + repStep <= p.maxReps -> r + repStep
                            tryAddSets() -> baseReps
                            else -> p.maxReps
                        }
                    }
                    val s = seconds
                    if (secStep != null && s != null) {
                        seconds = when {
                            maxSeconds == null || s + secStep <= maxSeconds -> s + secStep
                            // Les séries ne s'ajoutent qu'une fois par cran : les reps sont prioritaires.
                            repStep == null && tryAddSets() -> baseSeconds
                            else -> maxSeconds
                        }
                    }
                    val l = load
                    if (loadStep != null && l != null) {
                        val next = l.value + loadStep.value
                        load = l.copy(value = if (maxLoad != null) minOf(next, maxLoad.value) else next)
                    }
                }
            }

            if (deload) {
                val s = sets
                if (s != null && s >= 2) {
                    sets = ceil(s / 2.0).toInt()
                } else {
                    reps = reps?.let { ceil(it / 2.0).toInt() }
                    seconds = seconds?.let { ceil(it / 2.0).toInt() }
                }
            }

            val target = if (!allOut) null else lastMax?.plus(1) ?: reps ?: seconds?.takeIf { reps == null && exercise.reps == null }
            return Prescription(exercise, sets, reps, seconds, load, allOut, target, level)
        }
    }
}
