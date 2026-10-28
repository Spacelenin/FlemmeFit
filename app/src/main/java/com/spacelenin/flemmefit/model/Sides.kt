package com.spacelenin.flemmefit.model

import kotlin.math.floor

/** Un côté du corps. Gauche en rouge, droite en bleu, comme les sabres de Beat Saber. */
enum class Side(val code: Char) {
    LEFT('L'),
    RIGHT('R'),
    ;

    val other: Side get() = if (this == LEFT) RIGHT else LEFT

    companion object {
        fun of(code: Char): Side? = entries.firstOrNull { it.code == code }
    }
}

/** Où en est l'équilibre gauche / droite d'un exercice. */
enum class BalanceMode {
    /** Écart sous [Sides.EVEN_BELOW] : les deux côtés font pareil. */
    EVEN,
    /** Écart modéré : les deux côtés montent, le faible un peu plus (une série et des crans en plus). */
    CATCH_UP,
    /** Gros écart (à partir de [Sides.WEAK_ONLY_FROM]) : le côté fort ne monte plus, seul le faible progresse. */
    WEAK_ONLY,
}

/**
 * Ce qui est demandé de chaque côté d'un exercice fait une série par côté : les séries dans l'ordre ([order]),
 * et pour chaque côté les reps (ou secondes) par série.
 */
data class SidedPlan(
    /** « bras », « jambe » ou « côté ». */
    val limb: String,
    val order: List<Side>,
    val left: Int?,
    val right: Int?,
    val mode: BalanceMode = BalanceMode.EVEN,
    /** Côté qui rattrape (le premier de chaque paire), null si équilibré. */
    val weak: Side? = null,
) {
    fun sideAt(set: Int): Side? = order.getOrNull(set)

    fun valueOf(side: Side): Int? = if (side == Side.LEFT) left else right

    fun plannedAt(set: Int): Int? = sideAt(set)?.let(::valueOf)

    fun setsOf(side: Side): Int = order.count { it == side }

    /** Dernière série de son côté (celle qui va « à fond » quand l'exercice en a une). */
    fun isLastOf(set: Int): Boolean = sideAt(set)?.let { order.lastIndexOf(it) == set } == true

    /** Après cette série, l'autre côté enchaîne dans la même paire : repos court. */
    fun switchesAfter(set: Int): Boolean = set % 2 == 0 && set + 1 < order.size && order[set + 1] != order[set]

    /** « Bras gauche », « Jambe droite ». */
    fun label(side: Side): String = Sides.label(limb, side)
}

/** Le suivi de l'équilibre d'un exercice, séance après séance. */
data class BalanceState(
    val mode: BalanceMode = BalanceMode.EVEN,
    val weak: Side? = null,
    /** Cas vers lequel l'écart pousse, et depuis combien de séances de suite. */
    val pending: BalanceMode? = null,
    val pendingCount: Int = 0,
    /** Crans donnés en plus au côté faible pour rattraper, et ce qu'il gagne à chaque séance. */
    val boost: Double = 0.0,
    val rate: Double = 0.0,
    /** Gros écart : niveau où le côté fort s'arrête. */
    val frozen: Int? = null,
) {
    val boostLevels: Int get() = floor(boost).toInt()
}

object Sides {
    /** Repos entre les deux côtés d'une paire : l'autre côté s'est reposé pendant ce temps. */
    const val SWITCH_REST = 20
    const val EVEN_BELOW = 0.10
    const val WEAK_ONLY_FROM = 0.20
    /** Séances de suite dans un autre cas avant d'en changer : une mauvaise séance ne fait pas tout basculer. */
    const val SWITCH_SESSIONS = 3
    /** Temps visé pour rattraper l'écart. */
    const val CATCH_UP_DAYS = 42

    fun label(limb: String, side: Side): String {
        val feminine = limb.trim().lowercase() == "jambe"
        val word = limb.trim().replaceFirstChar { it.uppercase() }
        return "$word " + when (side) {
            Side.LEFT -> "gauche"
            Side.RIGHT -> if (feminine) "droite" else "droit"
        }
    }

    /** « par bras », « par jambe », « par côté ». */
    fun per(limb: String) = "par ${limb.trim().lowercase()}"

    /**
     * Séries en alternance, [first] en tête de chaque paire ; les séries en plus d'un côté viennent à la fin.
     */
    fun order(leftSets: Int, rightSets: Int, first: Side = Side.LEFT): List<Side> {
        val counts = mutableMapOf(Side.LEFT to leftSets.coerceAtLeast(0), Side.RIGHT to rightSets.coerceAtLeast(0))
        val result = ArrayList<Side>()
        while (counts.values.any { it > 0 }) {
            for (s in listOf(first, first.other)) {
                if (counts.getValue(s) > 0) {
                    result += s
                    counts[s] = counts.getValue(s) - 1
                }
            }
        }
        return result
    }

    /** Écart entre les deux côtés (0,15 = le faible est 15 % sous le fort), null si rien à comparer. */
    fun gap(left: Int?, right: Int?): Double? {
        if (left == null || right == null) return null
        val strong = maxOf(left, right)
        if (strong <= 0) return null
        return (strong - minOf(left, right)).toDouble() / strong
    }

    fun band(gap: Double): BalanceMode = when {
        gap < EVEN_BELOW -> BalanceMode.EVEN
        gap < WEAK_ONLY_FROM -> BalanceMode.CATCH_UP
        else -> BalanceMode.WEAK_ONLY
    }

    /**
     * Une séance de plus dans le suivi : [gap] l'écart du moment, [weakNow] le côté le plus faible,
     * [gapLevels] l'écart en crans, [sessionsPerWeek] la fréquence de l'exercice. [strongLevel] : niveau du côté fort.
     * Le cas ne change qu'après [SWITCH_SESSIONS] séances de suite qui y poussent. Le rattrapage n'avance que quand
     * le côté faible a tenu ce qui lui était demandé ([weakMet]).
     */
    fun next(
        state: BalanceState,
        gap: Double,
        weakNow: Side,
        gapLevels: Int,
        sessionsPerWeek: Double,
        strongLevel: Int,
        weakMet: Boolean,
    ): BalanceState {
        // Le côté qui rattrape a dépassé l'autre : on n'est plus dans le même écart.
        val band = if (state.weak != null && weakNow != state.weak) BalanceMode.EVEN else band(gap)
        var s = state
        if (band == s.mode) {
            s = s.copy(pending = null, pendingCount = 0)
        } else {
            val count = if (s.pending == band) s.pendingCount + 1 else 1
            s = if (count >= SWITCH_SESSIONS) switchTo(s, band, weakNow, gapLevels, sessionsPerWeek, strongLevel) else s.copy(pending = band, pendingCount = count)
        }
        if (weakMet && s.mode != BalanceMode.EVEN && s.weak == state.weak && state.mode != BalanceMode.EVEN) s = s.copy(boost = s.boost + s.rate)
        return s
    }

    private fun switchTo(s: BalanceState, band: BalanceMode, weak: Side, gapLevels: Int, sessionsPerWeek: Double, strongLevel: Int): BalanceState {
        val sessions = (sessionsPerWeek * CATCH_UP_DAYS / 7).coerceAtLeast(1.0)
        return when (band) {
            BalanceMode.EVEN -> BalanceState()
            else -> {
                val starting = s.mode == BalanceMode.EVEN
                s.copy(
                    mode = band,
                    weak = if (starting) weak else s.weak,
                    pending = null,
                    pendingCount = 0,
                    boost = if (starting) 0.0 else s.boost,
                    rate = if (starting) gapLevels.coerceAtLeast(1) / sessions else s.rate,
                    frozen = if (band == BalanceMode.WEAK_ONLY) s.frozen ?: strongLevel else null,
                )
            }
        }
    }
}

/** L'équilibre d'un exercice fait une série par côté, de quoi le montrer. */
data class SideBalance(
    val name: String,
    val limb: String,
    /** Reps (ou secondes) par série de chaque côté, au niveau du moment. */
    val left: Int?,
    val right: Int?,
    val gap: Double,
    val mode: BalanceMode,
    val weak: Side?,
    /** L'écart en %, séance après séance. */
    val points: List<TestPoint>,
    /** Fin estimée du rattrapage. */
    val eta: java.time.LocalDate?,
    /** [left] et [right] en secondes (exercice en durée). */
    val seconds: Boolean = false,
) {
    fun format(value: Int?): String = value?.let { if (seconds) Measures.formatSeconds(it) else "$it" } ?: "–"

    val gapPercent: Int get() = Math.round(gap * 100).toInt()

    /** « Écart 15 % · bras gauche en rattrapage, vers le 12/12 », « Équilibré (4 %) ». */
    fun status(dateLabel: (java.time.LocalDate) -> String): String = when (mode) {
        BalanceMode.EVEN -> "Équilibré ($gapPercent % d'écart)"
        else -> "Écart $gapPercent % · " + (weak?.let { Sides.label(limb, it).lowercase() } ?: "côté faible") +
            (if (mode == BalanceMode.WEAK_ONLY) " : seul à monter" else " en rattrapage") +
            (eta?.let { ", vers le ${dateLabel(it)}" } ?: "")
    }
}
