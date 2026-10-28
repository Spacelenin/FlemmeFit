package com.spacelenin.flemmefit.model

/** Déroulé d'un exercice en mode séance : les séries une par une, le repos entre elles, puis la saisie qui en sort. */
class GuidedExercise(val prescription: Prescription, restBonus: Int = 0) {
    /** Un test se fait en une seule série, à fond. */
    val sets: Int = if (prescription.isTest) 1 else (prescription.sets ?: 1).coerceAtLeast(1)

    /** Reps ou secondes prévues par série, ou null si rien n'est chiffré. */
    val planned: Int? = if (prescription.isTest) null else prescription.perSetPlanned

    val inSeconds: Boolean = prescription.maxIsSeconds

    val restSeconds: Int? = TimerPlans.rest(prescription, restBonus)?.seconds

    /** Une série par côté : le côté de chaque série. */
    val sided: SidedPlan? = prescription.sided.takeIf { !prescription.isTest }

    /** Côté de la série [set], null si l'exercice se fait des deux côtés à la fois. */
    fun sideOf(set: Int): Side? = sided?.sideAt(set)

    /** Ce qui est prévu pour la série [set] : chaque côté a le sien. */
    fun plannedAt(set: Int): Int? = if (sided != null && planned != null) sided.plannedAt(set) else planned

    /** À fond : la dernière série, ou la dernière de chaque côté. */
    fun isAllOut(set: Int) = prescription.allOut && if (sided != null) sided.isLastOf(set) else set == sets - 1

    /** Repos après la série [set] : court entre les deux côtés d'une paire, normal sinon. */
    fun restAfter(set: Int): Int? = if (sided?.switchesAfter(set) == true) minOf(Sides.SWITCH_REST, restSeconds ?: Sides.SWITCH_REST) else restSeconds

    /** La série demande un chiffre (reps faites, temps tenu, max). Sinon un simple « Série faite ». */
    fun needsValue(set: Int) = planned != null || isAllOut(set)

    /** Valeur proposée pour une série : le prévu, ou l'objectif pour une série à fond. */
    fun suggested(set: Int): Int = (if (isAllOut(set)) prescription.target ?: plannedAt(set) else plannedAt(set)) ?: 0

    /** Série au temps à rebours (la série à fond, elle, est chronométrée jusqu'à « Stop »). */
    fun countdown(set: Int): Int? = if (inSeconds && !isAllOut(set)) plannedAt(set) else null

    /**
     * Série à rebours qui continue après 0 (« + 0:12 ») jusqu'à « Stop » : le temps en plus compte pour le réglage
     * de l'exercice. Seulement les exercices qui progressent, pas l'échauffement ni les étirements.
     */
    fun overtime(set: Int): Boolean =
        countdown(set) != null && prescription.exercise.progression != null && !prescription.exercise.optional

    /** Temps tenu au total pour [extra] secondes en plus : jamais plus du double du prévu (Stop oublié). */
    fun withOvertime(set: Int, extra: Int): Int {
        val base = countdown(set) ?: 0
        return base + extra.coerceIn(0, base)
    }

    /** Série au temps chronométrée : à fond, ou test au temps. */
    fun countUp(set: Int): Boolean = inSeconds && isAllOut(set)

    /**
     * Saisie à enregistrer pour les séries faites ([values], une par série, 0 = pas faite) et si l'exercice est validé.
     * Rien de chiffré : l'exercice est simplement fait.
     */
    fun result(values: List<Int>): Pair<ExerciseLog, Boolean> {
        val padded = List(sets) { values.getOrElse(it) { 0 } }
        return when {
            prescription.isTest -> {
                val v = padded.last()
                ExerciseLog(max = v.takeIf { it > 0 }) to (v > 0)
            }
            planned == null -> ExerciseLog(max = padded.last().takeIf { prescription.allOut && it > 0 }) to true
            else -> ExerciseLog.ofSets(padded, prescription.allOut, sided?.order).let { it to prescription.isMetBy(it) }
        }
    }
}
