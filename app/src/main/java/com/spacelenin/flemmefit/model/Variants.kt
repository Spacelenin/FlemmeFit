package com.spacelenin.flemmefit.model

import kotlinx.serialization.Serializable
import java.time.LocalDate
import kotlin.math.floor
import kotlin.math.roundToInt

/** Séries d'un bloc variante, la dernière au max. */
const val VARIANT_SETS = 3
/** Un bloc variante se fait à cette part du max estimé. */
const val VARIANT_SHARE = 0.6
/** Part des séries de l'exercice de base qui laisse place au bloc variante (au moins une). */
const val VARIANT_BASE_SHARE = 0.4
/** La version facile compte pour moitié moins dure : le max estimé double. */
const val VARIANT_EASY_FACTOR = 0.5
/** Passages d'un exercice perso « Autre » par semaine. */
const val OTHER_PER_WEEK = 2
/** Taille d'un cycle de variantes quand la routine n'a pas de semaine allégée. */
const val VARIANT_CYCLE_WEEKS = 4

/** Période où un exercice perso est actif : à partir du lendemain de [from], jusqu'à [until] inclus. */
@Serializable
data class ActivePeriod(val from: String, val until: String? = null) {
    fun covers(date: LocalDate) = date.isAfter(LocalDate.parse(from)) && (until == null || !date.isAfter(LocalDate.parse(until)))
}

/**
 * Exercice ajouté par l'utilisateur. Dans une famille de la routine, c'est une variante comme les autres ;
 * dans « Autre », il s'ajoute à des séances tirées au sort avec sa propre progression.
 */
@Serializable
data class CustomExercise(
    val id: String,
    val name: String,
    /** Id d'une famille de la routine, ou [OTHER]. */
    val family: String,
    val seconds: Boolean = false,
    /** Une rep (ou une seconde) vaut [ratio] unités du test de la famille. Null : « je sais pas », mesuré au premier passage. */
    val ratio: Double? = null,
    /** « Autre » : reps ou secondes par série au départ. */
    val start: Int? = null,
    val description: String? = null,
    /** Pauses et reprises : un changement ne touche jamais les jours passés. */
    val periods: List<ActivePeriod> = emptyList(),
    /** Supprimé : caché, mais son historique reste. */
    val deleted: Boolean = false,
) {
    val isOther get() = family == OTHER
    val isActive get() = !deleted && periods.lastOrNull()?.until == null

    fun activeOn(date: LocalDate) = periods.any { it.covers(date) }

    /** Mis en pause (ou supprimé) à partir de demain : la séance du jour n'est pas touchée. */
    fun stopped(today: LocalDate) = if (!isActive) this
    else copy(periods = periods.dropLast(1) + periods.last().copy(until = today.toString()))

    fun resumed(today: LocalDate) = if (isActive || deleted) this else copy(periods = periods + ActivePeriod(today.toString()))

    fun asVariant() = Variant(
        id = id, name = name, family = family, description = description, ratio = ratio,
        unit = if (seconds) PlanTest.UNIT_SECONDS else PlanTest.UNIT_REPS,
    )

    /** « Autre » : 3 séries, +1 rep ou +5 s toutes les 2 séances, jusqu'au triple du départ. */
    fun asExercise(): Exercise {
        val base = (start ?: if (seconds) 30 else 10).coerceAtLeast(1)
        return Exercise(
            name = name,
            id = id,
            description = description,
            sets = VARIANT_SETS,
            reps = if (seconds) null else "$base",
            duration = if (seconds) Measures.formatSeconds(base) else null,
            rest = "60s",
            amrap = true,
            counter = name,
            progression = if (seconds) {
                Progression(every = 2, duration = "5s", maxDuration = Measures.formatSeconds(base * 3))
            } else {
                Progression(every = 2, reps = 1, maxReps = base * 3)
            },
        )
    }

    companion object {
        const val OTHER = "autre"
        const val MAX_NAME = 40

        fun create(id: String, name: String, family: String, seconds: Boolean, ratio: Double?, start: Int?, description: String?, today: LocalDate) =
            CustomExercise(
                id = id,
                name = name.trim().take(MAX_NAME),
                family = family,
                seconds = seconds,
                ratio = ratio?.takeIf { it > 0 },
                start = start?.takeIf { it > 0 },
                description = description?.trim()?.ifEmpty { null },
                periods = listOf(ActivePeriod(today.toString())),
            )
    }
}

/** Ce qu'une prescription de bloc variante sait d'elle-même. */
data class VariantInfo(
    val id: String,
    val name: String,
    val familyName: String,
    /** Exercice de base dont la série max est remplacée. */
    val baseName: String,
    /** Taux utilisé pour doser (perso s'il est mesuré), null si inconnu. */
    val ratio: Double?,
    /** Taux de départ (routine, ou dit à la création). */
    val startRatio: Double?,
    /** Vraie version (sinon version facile). */
    val full: Boolean,
    val isSeconds: Boolean,
    val testSeconds: Boolean,
    val counter: String?,
    val custom: Boolean,
    /** Prend toute la place de l'exercice de base (famille en [PlanFamily.replace]). */
    val whole: Boolean = false,
) {
    /** Ce qui a été fait ([amount] reps ou secondes de variante), en unités du test de la famille. */
    fun equivalent(amount: Int): Int? = ratio?.let { (amount * it).roundToInt() }
}

/** Où en est une variante : ses max notés et le taux qui en sort. */
data class VariantProgress(
    val variant: Variant,
    val familyName: String,
    val points: List<TestPoint>,
    val ratio: Double?,
    val startRatio: Double?,
    val custom: Boolean,
    /** Test de la famille, l'étalon du taux. */
    val testName: String?,
    val testSeconds: Boolean,
) {
    fun format(value: Int): String = if (variant.isSeconds) Measures.formatSeconds(value) else "$value"

    /** « 1 rep ≈ 3,3 pompes », « 30 s ≈ 15 abdos ». */
    fun ratioLabel(r: Double): String {
        val unit = testName?.lowercase() ?: familyName.lowercase()
        val per = if (variant.isSeconds && !testSeconds) 30 else 1
        val value = r * per
        val amount = if (testSeconds) "${Variants.decimal(value)} s de $unit" else "${Variants.decimal(value)} $unit"
        return (if (variant.isSeconds) "$per s" else "1 rep") + " ≈ " + amount
    }
}

object Variants {
    /** « 3,3 », « 15 » : une décimale au plus, à la française. */
    fun decimal(value: Double): String {
        val rounded = (value * 10).roundToInt() / 10.0
        return if (rounded == rounded.toLong().toDouble()) "${rounded.toLong()}" else "$rounded".replace('.', ',')
    }

    /** Séries de base qui restent un jour variante : environ 40 % en moins, au moins une de moins, au moins une gardée. */
    fun baseSets(sets: Int): Int = (sets - (sets * VARIANT_BASE_SHARE).roundToInt().coerceAtLeast(1)).coerceAtLeast(1)

    /** Jours variante par cycle : 1 en version facile, 2, puis 3 à partir d'une fois et demie le seuil. */
    fun quota(v: Variant, test: Int?): Int {
        val from = v.from ?: return 2
        val t = test ?: 0
        return when {
            t < from -> 1
            t < from * 1.5 -> 2
            else -> 3
        }
    }

    /** Part des séances de la famille données aux variantes, en [PlanFamily.replace] : une sur deux, puis 5 sur 6. */
    fun replaceShare(family: PlanFamily, variants: List<Variant>, test: Int?): Double {
        val t = test ?: 0
        if (family.minTest != null && t < family.minTest) return 0.0
        val full = variants.mapNotNull { it.from }.minOrNull()
        return if (full == null || t >= full) 5.0 / 6 else 0.5
    }

    /**
     * Passages de variantes sur le cycle [cycle] pour [slots] séances : un cycle sur deux arrondi au-dessus, l'autre
     * au-dessous (5/6 de 3 séances : 3 puis 2, soit l'exercice de base une fois tous les 2 cycles).
     */
    fun replaceCount(share: Double, slots: Int, cycle: Int): Int {
        if (share <= 0.0) return 0
        return floor(slots * share + if (cycle % 2 == 1) 0.5 else 0.0).toInt().coerceIn(0, slots)
    }

    /** Taux après un max noté : la moyenne avec le précédent, pour qu'une mauvaise séance ne fasse pas tout basculer. */
    fun learn(previous: Double?, test: Int, max: Int): Double {
        val measured = test.toDouble() / max
        return previous?.let { (it + measured) / 2 } ?: measured
    }

    /**
     * Le bloc variante : 3 séries à [VARIANT_SHARE] du max estimé, la dernière au max ; une seule série au max sans repère.
     * Variante d'un côté à la fois ([Variant.perSide]) : autant de chaque côté, en alternance ; le côté [weak] (en retard)
     * commence et a une série de plus.
     */
    fun block(
        v: Variant, family: PlanFamily, base: Exercise, test: Int?, ratio: Double?, startRatio: Double?, custom: Boolean, testSeconds: Boolean,
        weak: Side? = null,
    ): Prescription {
        val full = v.from == null || (test ?: 0) >= v.from
        val r = ratio?.let { if (full) it else it * VARIANT_EASY_FACTOR }
        val estimate = if (r != null && r > 0 && test != null && test > 0) test / r else null
        val per = estimate?.let {
            if (v.isSeconds) (((it * VARIANT_SHARE) / 5).roundToInt() * 5).coerceAtLeast(5)
            else (it * VARIANT_SHARE).roundToInt().coerceAtLeast(1)
        }
        val exercise = Exercise(
            name = if (full) v.name else "${v.name} (version facile)",
            id = v.id,
            description = listOfNotNull(
                v.description,
                v.easy?.takeIf { !full }?.let { "Version facile : $it" },
                if (per == null) "Pas encore de repère : une seule série, ton max. Il servira à doser la suite." else null,
            ).joinToString("\n\n").ifEmpty { null },
            sets = if (per == null) 1 else VARIANT_SETS,
            reps = per?.takeIf { !v.isSeconds }?.toString(),
            duration = per?.takeIf { v.isSeconds }?.let(Measures::formatSeconds),
            rest = base.rest,
            amrap = true,
            sides = v.sides.takeIf { v.perSide == null },
            perSide = v.perSide,
        )
        val sided = v.perSide?.let { limb ->
            val n = exercise.sets ?: 1
            val extra = if (per != null) weak else null
            SidedPlan(
                limb, Sides.order(n + if (extra == Side.LEFT) 1 else 0, n + if (extra == Side.RIGHT) 1 else 0, extra ?: Side.LEFT),
                per, per, if (extra != null) BalanceMode.CATCH_UP else BalanceMode.EVEN, extra,
            )
        }
        return Prescription(
            exercise,
            sets = sided?.order?.size ?: exercise.sets,
            reps = per?.takeIf { !v.isSeconds },
            seconds = per?.takeIf { v.isSeconds },
            load = null,
            allOut = true,
            target = per,
            secondsMax = v.isSeconds,
            variant = VariantInfo(
                id = v.id, name = v.name, familyName = family.name, baseName = base.name, ratio = ratio, startRatio = startRatio,
                full = full, isSeconds = v.isSeconds, testSeconds = testSeconds, counter = family.counter, custom = custom,
                whole = family.replace,
            ),
            sided = sided,
        )
    }
}
