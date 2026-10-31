package com.spacelenin.flemmefit.model

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sign

/**
 * Réglage sur mesure d'un exercice : des crans en plus (ou en moins) de sa courbe de base, appris séance après
 * séance d'après ce qui est noté et ressenti. Chaque exercice a le sien.
 */
data class AdaptState(
    /** Réglage appris, en crans (décimal). */
    val value: Double = 0.0,
    /** Crans appliqués aux séances : ne suit [value] que quand l'écart devient net. */
    val applied: Int = 0,
    /** Séances qui ont servi à régler. */
    val samples: Int = 0,
) {
    val stable: Boolean get() = samples >= Adaptation.CALIBRATION
}

object Adaptation {
    /** Séances de calibrage : les premières corrigent vite, ensuite ça se lisse. */
    const val CALIBRATION = 6
    /** Une séance ne pèse jamais plus que ça, en crans : une faute de frappe ne fait pas tout exploser. */
    const val MAX_SIGNAL = 5.0
    /** Ressenti d'un exercice (« dur » / « facile »), en crans. */
    const val EXERCISE_FEELING = 0.7
    /** Ressenti de la séance, pour les exercices sans ressenti propre : compte moins. */
    const val SESSION_FEELING = 0.35
    /** Le test refait : une série prévue au-dessus de cette part du max est trop ambitieuse… */
    const val TEST_CEILING = 0.8
    /** … et redescend jusqu'à cette part. */
    const val TEST_TARGET = 0.65

    /** Part de l'écart corrigée par une séance : forte au début, plus douce une fois calé, toujours franche à la baisse. */
    fun gain(samples: Int, signal: Double): Double {
        val g = maxOf(0.3, 0.75 - 0.075 * samples)
        return if (signal < 0) maxOf(g, 0.6) else g
    }

    /**
     * Ajoute une séance au réglage, entre [minimum] (le départ du palier) et [maximum] (le plafond de l'exercice).
     * Les crans appliqués ne bougent que si l'écart est net (un demi-cran en calibrage, presque un cran ensuite).
     */
    fun update(state: AdaptState, signal: Double, minimum: Int = Int.MIN_VALUE, maximum: Int = Int.MAX_VALUE): AdaptState {
        val value = (state.value + gain(state.samples, signal) * signal).coerceIn(minimum.toDouble(), maxOf(minimum, maximum).toDouble())
        val samples = state.samples + 1
        val threshold = if (samples <= CALIBRATION) 0.5 else 0.9
        val diff = value - state.applied
        val applied = if (abs(diff) >= threshold) state.applied + (sign(diff) * floor(abs(diff) + 0.5)).toInt() else state.applied
        return AdaptState(value, applied.coerceIn(minimum, maxOf(minimum, maximum)), samples)
    }

    /**
     * Ce qu'une séance dit d'un exercice, en crans : + au-dessus du prévu, − en dessous, 0 pile (le rythme est bon).
     * Null : rien à en tirer (pas fait, rien noté).
     * - séries normales : la plus faible compte (17-3-3 ne vaut pas plus que 3-3-3, 6-6-6 si) ;
     * - série à fond : son excès compte pour un quart (un max est toujours plus haut qu'une série normale) ;
     * - ressenti de l'exercice, sinon celui de la séance, plus faible.
     * [final] : la séance est finie (passée ou validée) ; une séance en cours ne dit encore rien de ce qui manque.
     * [step] : un cran, en reps ou en secondes par série.
     */
    fun signal(
        prescription: Prescription,
        log: ExerciseLog?,
        done: Boolean,
        final: Boolean,
        step: Double?,
        exerciseFeeling: Int?,
        sessionFeeling: Int?,
        /** Exercice fait une série par côté : ce que la séance dit de ce côté-là. */
        side: Side? = null,
        /** Crans ajoutés ce jour-là (Boss, Burst) : ce qui est fait se compare au prévu sans eux. */
        extra: Int = 0,
    ): Double? {
        val numeric = numeric(prescription, log, done, final, step, side)?.plus(extra)
        val touched = done || numeric != null || exerciseFeeling != null
        if (!touched) return null
        val feeling = when (exerciseFeeling) {
            FEELING_HARD -> -EXERCISE_FEELING
            FEELING_EASY -> EXERCISE_FEELING
            FEELING_OK -> 0.0
            else -> when (sessionFeeling) {
                FEELING_HARD -> -SESSION_FEELING
                FEELING_EASY -> SESSION_FEELING
                else -> 0.0
            }
        }
        return ((numeric ?: 0.0) + feeling).coerceIn(-MAX_SIGNAL, MAX_SIGNAL)
    }

    private fun numeric(p: Prescription, log: ExerciseLog?, done: Boolean, final: Boolean, step: Double?, side: Side?): Double? {
        val sided = p.sided.takeIf { side != null }
        val planned = if (sided != null) sided.valueOf(side!!) else p.perSetPlanned
        if (planned == null || step == null || step <= 0 || log == null || log.isEmpty) return if (done) 0.0 else null
        val sets = (if (sided != null) sided.setsOf(side!!) else p.sets ?: 1).coerceAtLeast(1)
        val values = (if (side != null) log.valuesOf(side) else log.perSet)?.takeIf { it.isNotEmpty() } ?: run {
            val per = if (p.maxIsSeconds) log.seconds else log.reps
            if (per != null) List(log.sets ?: sets) { per } else emptyList()
        }
        val allOut = p.allOut && values.size == sets
        val normal = if (allOut) values.dropLast(1) else values
        // Variante : son max recale déjà le dosage (le taux), il ne compte pas une deuxième fois ici.
        val max = if (p.variant != null) null
        else (if (allOut) values.last().takeIf { it > 0 } else null) ?: log.max.takeIf { side == null || log.sides == null }
        val normalCount = if (p.allOut) sets - 1 else sets
        val doneNormal = normal.filter { it > 0 }
        // Séries manquantes d'une séance finie : comptées à 0 (c'était trop).
        val worst = when {
            doneNormal.isEmpty() -> null
            final && !done && doneNormal.size < normalCount -> 0
            else -> doneNormal.min()
        }
        val sNormal = worst?.let { (it - planned) / step }
        val sMax = max?.let { (it - planned) / step }
        return when {
            sNormal != null && sMax != null -> sNormal + when {
                sMax > sNormal -> 0.25 * (sMax - sNormal)
                sMax < 0 -> 0.5 * sMax
                else -> 0.0
            }
            sNormal != null -> sNormal
            sMax != null -> 0.5 * sMax
            done -> 0.0
            else -> null
        }
    }

    /** Crans à appliquer sur [exercise] pour retrouver au moins [current] par série (0 si rien à garder). */
    fun match(exercise: Exercise, current: Int?): Int {
        if (current == null) return 0
        var level = 0
        while (level < MAX_ANCHOR && perSet(exercise, level) < current) level++
        return level
    }

    /** [level] ramené sous [TEST_TARGET] du max [test] s'il dépasse [TEST_CEILING], jamais sous [floor]. */
    fun guard(exercise: Exercise, level: Int, test: Int, floor: Int = 0): Int {
        var l = level
        if (perSet(exercise, l) > TEST_CEILING * test) while (l > floor && perSet(exercise, l) > TEST_TARGET * test) l--
        return l
    }

    /** Premier cran à partir duquel l'exercice ne bouge plus (durée ou reps au plafond), null s'il monte toujours. */
    fun ceiling(exercise: Exercise, weighted: Boolean = false): Int? {
        if (exercise.progression?.let { it.maxReps == null && it.maxDuration == null && it.maxLoad == null } != false) return null
        var level = 0
        while (level < MAX_ANCHOR) {
            val here = Prescription.of(exercise, level, false, false, null, weighted = weighted)
            if ((1..3).all { Prescription.of(exercise, level + it, false, false, null, weighted = weighted).let { n -> n.sets == here.sets && n.perSetPlanned == here.perSetPlanned && n.load == here.load } }) return level
            level++
        }
        return null
    }

    private fun perSet(exercise: Exercise, level: Int) = Prescription.of(exercise, level, false, false, null).perSetPlanned ?: 0

    private const val MAX_ANCHOR = 200

    const val FEELING_HARD = 0
    const val FEELING_OK = 1
    const val FEELING_EASY = 2
}

/** Une prescription de base (palier, crans de la courbe) sur laquelle le réglage s'ajoute. */
data class AdaptBase(val exercise: Exercise, val steps: Int, val applied: Int, val weighted: Boolean)

/** Le réglage d'un exercice, de quoi le montrer : par série, avec et sans réglage. */
data class ExerciseAdaptation(
    val name: String,
    val adapted: Int?,
    val base: Int?,
    val seconds: Boolean,
    val adaptedSets: Int?,
    val baseSets: Int?,
    val crans: Int,
    val samples: Int,
    val stable: Boolean,
) {
    private fun format(v: Int) = if (seconds) Measures.formatSeconds(v) else "$v"

    /** « +3 reps par série », « −10s par série », « Comme prévu ». */
    val headline: String
        get() {
            val diff = (adapted ?: 0) - (base ?: 0)
            val setsDiff = (adaptedSets ?: 0) - (baseSets ?: 0)
            return when {
                diff == 0 && setsDiff == 0 -> "Comme prévu"
                diff == 0 -> (if (setsDiff > 0) "+" else "−") + "${kotlin.math.abs(setsDiff)} série" + if (kotlin.math.abs(setsDiff) > 1) "s" else ""
                else -> (if (diff > 0) "+" else "−") + (if (seconds) Measures.formatSeconds(kotlin.math.abs(diff)) else "${kotlin.math.abs(diff)} reps") + " par série"
            }
        }

    /** « 3 × 15 au lieu de 3 × 12 · calé », « 3 × 12 · en calibrage (2/6) ». */
    val detail: String
        get() {
            fun label(sets: Int?, v: Int?) = listOfNotNull(sets?.let { "$it ×" }, v?.let(::format)).joinToString(" ")
            val now = label(adaptedSets, adapted)
            val plain = label(baseSets, base)
            return (if (now == plain) now else "$now au lieu de $plain") + " · " +
                if (stable) "calé" else "en calibrage ($samples/${Adaptation.CALIBRATION})"
        }
}

/** Ce qu'une séance a changé au réglage d'un exercice, à annoncer en fin de séance. */
data class AdaptChange(
    val name: String,
    val kind: Kind,
    /** Ce qui sera demandé, et ce qui l'aurait été sans cette séance (« 3 × 14 », « 3 × 12 »). */
    val now: String? = null,
    val was: String? = null,
    /** Côté en retard (« côté droit »), pour [Kind.BEHIND]. */
    val side: String? = null,
    /** Gros écart : le côté fort attend. */
    val strongWaits: Boolean = false,
) {
    enum class Kind { UP, DOWN, BEHIND, EVEN }

    val message: String
        get() = when (kind) {
            Kind.UP -> "💪 $name : $now la prochaine fois (au lieu de $was)"
            Kind.DOWN -> "🌿 $name : $now la prochaine fois (au lieu de $was), on lève un peu le pied"
            Kind.BEHIND -> "↔ $name : $side en retard. Il commence et fait une série de plus" +
                if (strongWaits) ", l'autre côté attend" else ""
            Kind.EVEN -> "✅ $name : côtés rééquilibrés, même nombre des deux côtés"
        }
}
