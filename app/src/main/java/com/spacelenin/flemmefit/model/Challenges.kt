package com.spacelenin.flemmefit.model

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt
import kotlin.random.Random

/** Défi surprise envoyé un jour de séance : un exercice, une quantité à faire dans l'heure. */
@Serializable
data class Challenge(
    /** Créneau d'origine (« 2026-10-26#0 ») : un créneau ne sert qu'une fois. */
    val id: String,
    val date: String,
    val exercise: String,
    val amount: Int,
    val seconds: Boolean,
    val offeredAt: Long,
    val acceptedAt: Long? = null,
    val refused: Boolean = false,
    /** Réponse donnée : réussi ou raté. */
    val won: Boolean? = null,
) {
    enum class Status { OFFERED, RUNNING, ASK_RESULT, WON, LOST, REFUSED, EXPIRED }

    fun status(now: Long): Status = when {
        won == true -> Status.WON
        won == false -> Status.LOST
        refused -> Status.REFUSED
        acceptedAt == null -> if (now < offeredAt + Challenges.ANSWER_MS) Status.OFFERED else Status.EXPIRED
        now < acceptedAt + Challenges.DURATION_MS -> Status.RUNNING
        now < acceptedAt + Challenges.DURATION_MS + Challenges.GRACE_MS -> Status.ASK_RESULT
        else -> Status.LOST
    }

    /** « 39 pompes classiques », « 2 min 30 de gainage (planche) ». */
    val label: String
        get() = if (seconds) "${Measures.formatSeconds(amount)} de ${exercise.lowercase()}" else "$amount ${exercise.lowercase()}"

    /** Encore en jeu : à proposer, en cours, ou en attente de la réponse. */
    fun open(now: Long) = status(now) in setOf(Status.OFFERED, Status.RUNNING, Status.ASK_RESULT)

    fun accept(now: Long) = if (status(now) == Status.OFFERED) copy(acceptedAt = now) else this

    fun refuse(now: Long) = if (status(now) == Status.OFFERED) copy(refused = true) else this

    /** Réussi : seulement un défi accepté, avant la fin du délai de réponse. */
    fun finish(won: Boolean, now: Long) =
        if (status(now) == Status.RUNNING || status(now) == Status.ASK_RESULT) copy(won = won) else this
}

/** Créneau d'un défi : un jour de séance, une heure. */
data class ChallengeSlot(val id: String, val date: LocalDate, val minuteOfDay: Int)

object Challenges {
    const val ANSWER_MS = 30 * 60 * 1000L
    const val DURATION_MS = 60 * 60 * 1000L
    /** Après l'heure, le temps de dire si c'est fait. */
    const val GRACE_MS = 30 * 60 * 1000L
    const val POINTS = 2
    /** Séances faites avant les premiers défis : ils commencent au 7e jour d'entraînement. */
    const val SESSIONS_BEFORE = 6
    const val SHARE = 0.6
    /** Défis lancés entre 10 h et 21 h. */
    private const val FIRST_MINUTE = 10 * 60
    private const val LAST_MINUTE = 21 * 60

    fun monday(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Défis de la semaine : 1 ou 2, tirés d'après la semaine seule, donc les mêmes pour tout le monde. */
    fun weeklyCount(monday: LocalDate): Int = 1 + Random(monday.toEpochDay() * 7919 + 17).nextInt(2)

    /**
     * Créneaux de la semaine de [monday] parmi les jours de séance [sessionDays] (au plus un défi par jour) :
     * le jour et l'heure sont propres à chacun ([seed]).
     */
    fun slots(monday: LocalDate, sessionDays: List<LocalDate>, seed: Long): List<ChallengeSlot> {
        val days = sessionDays.filter { monday(it) == monday }.distinct().sorted()
        val random = Random(seed * 31 + monday.toEpochDay())
        return days.shuffled(random).take(weeklyCount(monday)).sorted().mapIndexed { i, d ->
            ChallengeSlot("$monday#$i", d, FIRST_MINUTE + random.nextInt(LAST_MINUTE - FIRST_MINUTE))
        }
    }

    /**
     * Le défi : un exercice de la séance du jour rattaché à un test (pompes, abdos, gainage…), à faire à
     * [SHARE] de son volume en séance, en autant de fois qu'on veut. Null si rien ne s'y prête.
     */
    fun pick(prescriptions: List<Prescription>, seed: Long): Pair<String, Pair<Int, Boolean>>? {
        val candidates = prescriptions.filter {
            !it.exercise.optional && !it.isTest && it.variant == null && it.exercise.test != null &&
                it.sets != null && (it.reps != null || it.seconds != null)
        }
        if (candidates.isEmpty()) return null
        val p = candidates[Random(seed).nextInt(candidates.size)]
        val seconds = p.reps == null
        val per = p.reps ?: p.seconds!!
        val total = p.sets!! * per * SHARE
        val amount = if (seconds) ((total / 5).roundToInt() * 5).coerceAtLeast(20) else total.roundToInt().coerceAtLeast(5)
        return p.exercise.name to (amount to seconds)
    }

    fun wonCount(challenges: List<Challenge>) = challenges.count { it.won == true }
}
