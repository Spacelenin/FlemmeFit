package com.spacelenin.flemmefit.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * Réglages du sur-mesure, écrits dans la routine (à la racine, ou sur un exercice / une variante). Tout est
 * facultatif : ce qui manque garde la valeur par défaut ([AdaptParams], [SidesParams], [VariantParams]).
 */
@Serializable
data class Tuning(
    val adapt: AdaptTuning? = null,
    val sides: SidesTuning? = null,
    /** Seulement à la racine de la routine. */
    val variants: VariantTuning? = null,
)

@Serializable
data class AdaptTuning(
    val calibration: Int? = null,
    val gainStart: Double? = null,
    val gainEnd: Double? = null,
    val gainDown: Double? = null,
    val threshold: Double? = null,
    val exerciseFeeling: Double? = null,
    val sessionFeeling: Double? = null,
    val testCeiling: Double? = null,
    val testTarget: Double? = null,
)

@Serializable
data class SidesTuning(
    val evenBelow: Double? = null,
    val weakOnlyFrom: Double? = null,
    val catchUpDays: Int? = null,
    val switchSessions: Int? = null,
    val switchRest: Int? = null,
)

@Serializable
data class VariantTuning(
    val sets: Int? = null,
    val share: Double? = null,
    val easyFactor: Double? = null,
    val cycleWeeks: Int? = null,
    val otherPerWeek: Int? = null,
)

/** Le réglage sur mesure d'un exercice, valeurs résolues. */
data class AdaptParams(
    /** Séances de calibrage : les premières corrigent vite, ensuite ça se lisse. */
    val calibration: Int = 6,
    /** Part de l'écart corrigée par la première séance… */
    val gainStart: Double = 0.75,
    /** … et une fois calé. */
    val gainEnd: Double = 0.3,
    /** Au minimum, à la baisse : quand ça ne passe pas, on redescend franchement. */
    val gainDown: Double = 0.6,
    /** Écart (en crans) avant de bouger une fois calé ; un demi-cran pendant le calibrage. */
    val threshold: Double = 0.9,
    /** Ressenti d'un exercice (« dur » / « facile »), en crans. */
    val exerciseFeeling: Double = 0.7,
    /** Ressenti de la séance, pour les exercices sans ressenti propre : compte moins. */
    val sessionFeeling: Double = 0.35,
    /** Le test refait : une série prévue au-dessus de cette part du max est trop ambitieuse… */
    val testCeiling: Double = 0.8,
    /** … et redescend jusqu'à cette part. */
    val testTarget: Double = 0.65,
) {
    /** Part de l'écart corrigée par une séance : forte au début, plus douce une fois calé, toujours franche à la baisse. */
    fun gain(samples: Int, signal: Double): Double {
        val g = maxOf(gainEnd, gainStart - (gainStart - gainEnd) * samples / calibration.coerceAtLeast(1))
        return if (signal < 0) maxOf(g, gainDown) else g
    }

    fun with(t: AdaptTuning?): AdaptParams = if (t == null) this else AdaptParams(
        calibration = t.calibration ?: calibration,
        gainStart = t.gainStart ?: gainStart,
        gainEnd = t.gainEnd ?: gainEnd,
        gainDown = t.gainDown ?: gainDown,
        threshold = t.threshold ?: threshold,
        exerciseFeeling = t.exerciseFeeling ?: exerciseFeeling,
        sessionFeeling = t.sessionFeeling ?: sessionFeeling,
        testCeiling = t.testCeiling ?: testCeiling,
        testTarget = t.testTarget ?: testTarget,
    ).bounded()

    /** Dans les bornes, quoi qu'aient donné la routine et le profil. */
    fun bounded(): AdaptParams {
        val ceiling = testCeiling.coerceIn(0.3, 0.95)
        val end = gainEnd.coerceIn(0.05, 1.0)
        return AdaptParams(
            calibration = calibration.coerceIn(2, 30),
            gainStart = gainStart.coerceIn(end, 1.0),
            gainEnd = end,
            gainDown = gainDown.coerceIn(0.1, 1.0),
            threshold = threshold.coerceIn(0.5, 3.0),
            exerciseFeeling = exerciseFeeling.coerceIn(0.0, 3.0),
            sessionFeeling = sessionFeeling.coerceIn(0.0, 3.0),
            testCeiling = ceiling,
            testTarget = testTarget.coerceIn(0.2, ceiling - 0.05),
        )
    }

    fun toTuning() = AdaptTuning(calibration, gainStart, gainEnd, gainDown, threshold, exerciseFeeling, sessionFeeling, testCeiling, testTarget)
}

/** Le rééquilibrage gauche / droite, valeurs résolues. */
data class SidesParams(
    /** Écart sous cette part : les deux côtés font pareil. */
    val evenBelow: Double = 0.10,
    /** Gros écart à partir de cette part : le côté fort ne monte plus, seul le faible progresse. */
    val weakOnlyFrom: Double = 0.20,
    /** Temps visé pour rattraper l'écart. */
    val catchUpDays: Int = 42,
    /** Séances de suite dans un autre cas avant d'en changer : une mauvaise séance ne fait pas tout basculer. */
    val switchSessions: Int = 3,
    /** Repos entre les deux côtés d'une paire, en secondes. */
    val switchRest: Int = 20,
) {
    fun with(t: SidesTuning?): SidesParams = if (t == null) this else SidesParams(
        evenBelow = t.evenBelow ?: evenBelow,
        weakOnlyFrom = t.weakOnlyFrom ?: weakOnlyFrom,
        catchUpDays = t.catchUpDays ?: catchUpDays,
        switchSessions = t.switchSessions ?: switchSessions,
        switchRest = t.switchRest ?: switchRest,
    ).bounded()

    fun bounded(): SidesParams {
        val even = evenBelow.coerceIn(0.02, 0.5)
        return SidesParams(
            evenBelow = even,
            weakOnlyFrom = weakOnlyFrom.coerceIn(even + 0.02, 0.8),
            catchUpDays = catchUpDays.coerceIn(7, 365),
            switchSessions = switchSessions.coerceIn(1, 10),
            switchRest = switchRest.coerceIn(0, 300),
        )
    }

    fun toTuning() = SidesTuning(evenBelow, weakOnlyFrom, catchUpDays, switchSessions, switchRest)
}

/** Les variantes, valeurs résolues. */
data class VariantParams(
    /** Séries d'un bloc variante, la dernière au max. */
    val sets: Int = 3,
    /** Un bloc variante se fait à cette part du max estimé. */
    val share: Double = 0.6,
    /** La version facile compte pour autant de la vraie : le max estimé grimpe d'autant. */
    val easyFactor: Double = 0.5,
    /** Taille d'un cycle de variantes quand la routine n'a pas de semaine allégée. */
    val cycleWeeks: Int = 4,
    /** Passages d'un exercice perso « Autre » par semaine. */
    val otherPerWeek: Int = 2,
) {
    fun with(t: VariantTuning?): VariantParams = if (t == null) this else VariantParams(
        sets = (t.sets ?: sets).coerceIn(1, 10),
        share = (t.share ?: share).coerceIn(0.2, 1.0),
        easyFactor = (t.easyFactor ?: easyFactor).coerceIn(0.1, 1.0),
        cycleWeeks = (t.cycleWeeks ?: cycleWeeks).coerceIn(1, 12),
        otherPerWeek = (t.otherPerWeek ?: otherPerWeek).coerceIn(0, 7),
    )

    fun toTuning() = VariantTuning(sets, share, easyFactor, cycleWeeks, otherPerWeek)
}

/** Rythme d'adaptation choisi pour un programme : agit par-dessus les réglages de la routine. */
enum class AdaptProfile(val id: String, val label: String, val emoji: String, val summary: String) {
    GENTLE(
        "doux", "Doux", "🐢",
        "Reprise, blessure, période chargée : monte lentement, seulement si tu dépasses nettement, et garde plus de marge sous ton max.",
    ),
    NORMAL("normal", "Normal", "🙂", "Le rythme de base."),
    BRISK(
        "vif", "Vif", "🚀",
        "Tu fais souvent plus que prévu : l'appli te connaît plus vite, monte dès que tu dépasses, et te laisse aller plus près de ton max.",
    ),
    ;

    /** Ce que le profil fait des réglages de la routine (la baisse, elle, reste toujours aussi franche). */
    fun apply(p: AdaptParams): AdaptParams = when (this) {
        NORMAL -> p
        GENTLE -> p.copy(
            calibration = (p.calibration * 1.5).roundToInt(),
            gainStart = p.gainStart * 0.73,
            gainEnd = p.gainEnd * 0.67,
            threshold = p.threshold * 4 / 3,
            exerciseFeeling = p.exerciseFeeling * 5 / 7,
            sessionFeeling = p.sessionFeeling * 5 / 7,
            testCeiling = p.testCeiling - 0.05,
            testTarget = p.testTarget - 0.05,
        ).bounded()
        BRISK -> p.copy(
            calibration = (p.calibration * 2.0 / 3).roundToInt(),
            gainStart = p.gainStart * 1.2,
            gainEnd = p.gainEnd * 4 / 3,
            threshold = p.threshold * 7 / 9,
            exerciseFeeling = p.exerciseFeeling * 9 / 7,
            sessionFeeling = p.sessionFeeling * 9 / 7,
            testCeiling = p.testCeiling + 0.05,
            testTarget = p.testTarget + 0.05,
        ).bounded()
    }

    fun apply(p: SidesParams): SidesParams = when (this) {
        NORMAL -> p
        GENTLE -> p.copy(catchUpDays = (p.catchUpDays * 10.0 / 7).roundToInt()).bounded()
        BRISK -> p.copy(catchUpDays = (p.catchUpDays * 2.0 / 3).roundToInt()).bounded()
    }

    companion object {
        fun of(id: String?): AdaptProfile = entries.firstOrNull { it.id == id } ?: NORMAL
    }
}

/** Changement de rythme d'adaptation : vaut pour les séances à partir de [date] (ISO). */
@Serializable
data class ProfileChange(val date: String, val profile: String)
