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
    fun restAfter(set: Int): Int? {
        val s = sided ?: return restSeconds
        return if (s.switchesAfter(set)) minOf(s.switchRest, restSeconds ?: s.switchRest) else restSeconds
    }

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

    /**
     * Au-delà de ce temps en plus (le double du prévu, au moins une minute), c'est vraiment long : on demande
     * confirmation (Stop oublié ?), sans jamais rien couper.
     */
    fun longOvertime(set: Int): Int = maxOf(2 * (countdown(set) ?: 0), MIN_OVERTIME_CAP)

    fun isLongOvertime(set: Int, extra: Int): Boolean = extra > longOvertime(set)

    /** Temps tenu au total pour [extra] secondes en plus : tout est gardé. */
    fun withOvertime(set: Int, extra: Int): Int = (countdown(set) ?: 0) + extra.coerceAtLeast(0)

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

/** Temps en plus au-delà duquel on demande confirmation, au minimum, en secondes. */
const val MIN_OVERTIME_CAP = 60

/** Ce qui se fête à la fin d'un exo à fond : un exploit, sinon un record battu. */
object Celebration {
    const val EXPLOIT = "Exploit !"
    const val RECORD = "Nouveau record !"
    const val BOSS = "Boss vaincu !"

    /** [previousBest] : la meilleure série notée de cet exo avant cette séance (null : jamais fait). */
    fun of(pr: Prescription, log: ExerciseLog, previousBest: Int?): String? {
        if (!pr.allOut || pr.isTest || pr.neutral) return null
        val max = log.max ?: return null
        val planned = pr.exploitPlanned
        if (planned != null && Exploit.isExploit(max, planned, pr.maxIsSeconds)) return EXPLOIT
        return if (previousBest != null && previousBest > 0 && max > previousBest) RECORD else null
    }
}
