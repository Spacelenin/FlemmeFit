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
    /** Objectif de la série à fond : dernier max + 1 (Boss, test), ou le prévu (« au moins »). */
    val target: Int? = null,
    val level: Int = 0,
    val isBoss: Boolean = false,
    /** Test : une seule série à fond, son résultat devient la référence du test. */
    val isTest: Boolean = false,
    /** Palier en cours (0-based) sur [levelCount], si l'exercice a des paliers. */
    val palier: Int? = null,
    val levelCount: Int = 0,
    private val secondsMax: Boolean? = null,
    /** Semaine de décharge : ce qui aurait été demandé sans elle (« 4 × 3 »). */
    val beforeDeload: String? = null,
) {
    val key get() = exercise.key
    val repsLabel: String? get() = reps?.toString() ?: exercise.reps
    val durationLabel: String? get() = seconds?.let(Measures::formatSeconds) ?: exercise.duration
    val loadLabel: String? get() = load?.let(Measures::formatLoad) ?: exercise.load

    /** Le max se mesure en répétitions, sauf pour un exercice uniquement en durée. */
    val maxIsSeconds: Boolean get() = secondsMax ?: (reps == null && exercise.reps == null && (seconds != null || exercise.duration != null))

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
    fun isMetBy(log: ExerciseLog): Boolean {
        val perSet = log.perSet
        val planned = if (maxIsSeconds) seconds else reps
        if (perSet != null && planned != null) return perSet.count { it >= planned } >= (sets ?: 1)
        return (sets == null || (log.sets ?: sets) >= sets) &&
            (reps == null || (log.reps ?: reps) >= reps) &&
            (seconds == null || (log.seconds ?: seconds) >= seconds)
    }

    /** Ce qui est prévu pour une série : répétitions, ou secondes pour un exercice en durée. */
    val perSetPlanned: Int? get() = if (maxIsSeconds) seconds else reps

    companion object {
        /** Un test : une série à fond, objectif = dernier résultat + 1. */
        fun test(exercise: Exercise, test: PlanTest?, current: Int?) = Prescription(
            exercise, sets = null, reps = null, seconds = null, load = null,
            allOut = true, target = current?.plus(1), isTest = true, secondsMax = test?.isSeconds ?: false,
        )

        /**
         * Applique [level] crans de progression au départ défini par l'exercice (déjà ramené à son palier).
         * [boss] : dernière série à fond, objectif dernier max + 1. Sinon, si l'exercice est en [Exercise.amrap],
         * dernière série au max, objectif le prévu.
         */
        fun of(
            exercise: Exercise,
            level: Int,
            deload: Boolean,
            boss: Boolean,
            lastMax: Int?,
            palier: Int? = null,
        ): Prescription {
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

            val beforeDeload = if (deload) {
                listOfNotNull(sets?.let { "$it ×" }, reps?.toString() ?: seconds?.let(Measures::formatSeconds)).joinToString(" ").ifEmpty { null }
            } else {
                null
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

            val planned = reps ?: seconds?.takeIf { reps == null && exercise.reps == null }
            val target = when {
                boss -> lastMax?.plus(1) ?: planned
                exercise.amrap -> planned
                else -> null
            }
            return Prescription(
                exercise, sets, reps, seconds, load,
                allOut = boss || exercise.amrap, target = target, level = level, isBoss = boss,
                palier = palier, levelCount = exercise.levels.size, beforeDeload = beforeDeload,
            )
        }
    }
}
