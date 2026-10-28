package com.spacelenin.flemmefit.model

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

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
    /** Exercice au choix avec ou sans poids : le choix du jour. Null si l'exercice ne le propose pas. */
    val weighted: Boolean? = null,
    /** Bloc variante (jour variante), ou exercice perso. */
    val variant: VariantInfo? = null,
    /** Exercice de base un jour variante : la variante qui prend la place de sa série max. */
    val replacedBy: String? = null,
    /** Une série par côté, en alternance : ce qui est demandé de chaque côté ([sets] compte toutes les séries). */
    val sided: SidedPlan? = null,
) {
    val key get() = exercise.key
    val repsLabel: String? get() = reps?.toString() ?: exercise.reps
    val durationLabel: String? get() = seconds?.let(Measures::formatSeconds) ?: exercise.duration
    val loadLabel: String? get() = load?.let(Measures::formatLoad) ?: exercise.load

    /** Le max se mesure en répétitions, sauf pour un exercice uniquement en durée. */
    val maxIsSeconds: Boolean get() = secondsMax ?: (reps == null && exercise.reps == null && (seconds != null || exercise.duration != null))

    /**
     * Repos entre deux séries en secondes, allongé de [bonusPercent] % (réglage « repos en plus »,
     * jamais plus du double), arrondi aux 5 s. Null si l'exercice n'en indique pas de lisible.
     */
    fun restSeconds(bonusPercent: Int = 0): Int? {
        val base = Measures.seconds(exercise.rest)?.takeIf { it > 0 } ?: return null
        return withRestBonus(base, bonusPercent)
    }

    /** Le repos tel qu'affiché : celui de la routine, ou allongé par le réglage. */
    fun restLabel(bonusPercent: Int = 0): String? =
        if (bonusPercent <= 0) exercise.rest else restSeconds(bonusPercent)?.let(Measures::formatSeconds) ?: exercise.rest

    /** « 3 × 12 · 8 kg · repos 60s » ; une série par côté : « 3 × 8 par bras », ou chaque côté s'ils diffèrent. */
    fun summary(restBonus: Int = 0): String = listOfNotNull(
        when {
            sided != null -> sidedLabel(sided)
            sets != null && repsLabel != null -> "$sets × $repsLabel"
            sets != null && durationLabel != null -> "$sets × $durationLabel"
            sets != null -> "$sets séries"
            repsLabel != null -> "$repsLabel reps"
            else -> null
        },
        durationLabel?.takeIf { sets == null || repsLabel != null },
        loadLabel,
        restLabel(restBonus)?.let { "repos $it" },
        if (weighted == true) "avec poids" else null,
    ).joinToString(" · ")

    private fun sidedLabel(s: SidedPlan): String {
        fun value(v: Int?) = v?.let { if (maxIsSeconds) Measures.formatSeconds(it) else "$it" } ?: "?"
        val l = "${s.setsOf(Side.LEFT)} × ${value(s.left)}"
        val r = "${s.setsOf(Side.RIGHT)} × ${value(s.right)}"
        return if (l == r) "$l ${Sides.per(s.limb)}" else "${s.label(Side.LEFT)} $l · ${s.label(Side.RIGHT)} $r"
    }

    /** Une saisie atteint-elle le prévu ? Ce qui n'est pas chiffré ne bloque pas. */
    fun isMetBy(log: ExerciseLog): Boolean {
        val perSet = log.perSet
        if (sided != null && perSet != null) return sided.order.indices.all { i -> (perSet.getOrNull(i) ?: 0) >= (sided.plannedAt(i) ?: 0) && (perSet.getOrNull(i) ?: 0) > 0 }
        val planned = if (maxIsSeconds) seconds else reps
        if (perSet != null && planned != null) return perSet.count { it >= planned } >= (sets ?: 1)
        return (sets == null || (log.sets ?: sets) >= sets) &&
            (reps == null || (log.reps ?: reps) >= reps) &&
            (seconds == null || (log.seconds ?: seconds) >= seconds)
    }

    /** Ce qui est prévu pour une série : répétitions, ou secondes pour un exercice en durée. */
    val perSetPlanned: Int? get() = if (maxIsSeconds) seconds else reps

    companion object {
        /** Repos en plus au maximum : le double du repos prévu. */
        const val MAX_REST_BONUS = 100

        /** [base] secondes de repos allongées de [bonusPercent] %, arrondies aux 5 s, sans dépasser le double. */
        fun withRestBonus(base: Int, bonusPercent: Int): Int {
            val extra = (base * bonusPercent.coerceIn(0, MAX_REST_BONUS) / 100.0 / 5).roundToInt() * 5
            return base + extra.coerceAtMost(base)
        }

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
            /** Burst : sans progression, une série de plus (les crans en plus sont déjà dans [level]). */
            burst: Boolean = false,
            /** Avec poids : chaque cran ne vaut qu'une part du pas (voir [WeightsOption]). */
            weighted: Boolean = false,
        ): Prescription {
            val baseReps = Measures.reps(exercise.reps)
            val baseSeconds = Measures.seconds(exercise.duration)
            var sets = exercise.sets
            var reps = baseReps
            var seconds = baseSeconds
            var load = Measures.load(exercise.load)
            val withWeights = weighted && exercise.weights != null
            val factor = if (withWeights) exercise.weights?.factor ?: WeightsOption.DEFAULT_FACTOR else 1.0

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

                // Avec poids, les pas sont fractionnaires : on cumule en décimal, on arrondit à la fin.
                var repsD = reps?.toDouble()
                var secondsD = seconds?.toDouble()
                repeat(level.coerceAtLeast(0)) {
                    val r = repsD
                    if (repStep != null && r != null) {
                        val next = r + repStep * factor
                        repsD = when {
                            p.maxReps == null || next <= p.maxReps -> next
                            // Avec poids, on touche d'abord le plafond avant d'ajouter une série.
                            withWeights && r < p.maxReps -> p.maxReps.toDouble()
                            tryAddSets() -> baseReps?.toDouble()
                            else -> p.maxReps.toDouble()
                        }
                    }
                    val s = secondsD
                    if (secStep != null && s != null) {
                        val next = s + secStep * factor
                        secondsD = when {
                            maxSeconds == null || next <= maxSeconds -> next
                            withWeights && s < maxSeconds -> maxSeconds.toDouble()
                            // Les séries ne s'ajoutent qu'une fois par cran : les reps sont prioritaires.
                            repStep == null && tryAddSets() -> baseSeconds?.toDouble()
                            else -> maxSeconds.toDouble()
                        }
                    }
                    val l = load
                    if (loadStep != null && l != null) {
                        val next = l.value + loadStep.value * factor
                        load = l.copy(value = if (maxLoad != null) minOf(next, maxLoad.value) else next)
                    }
                }
                reps = repsD?.let { if (withWeights) floor(it).toInt() else it.roundToInt() }
                seconds = secondsD?.let { if (withWeights) floorSeconds(it) else it.roundToInt() }
            }

            if (burst && exercise.progression == null) sets = sets?.plus(1)

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
                weighted = if (exercise.weights != null) weighted else null,
            )
        }

        /** Durée avec poids, arrondie vers le bas : à la minute au-delà de 10 min, aux 5 s en dessous. */
        private fun floorSeconds(value: Double): Int {
            val unit = if (value >= 600) 60 else 5
            return (floor(value / unit) * unit).toInt()
        }
    }
}
