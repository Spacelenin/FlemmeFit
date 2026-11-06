package com.spacelenin.flemmefit.model

import kotlin.math.roundToInt

/**
 * Dose du jour d'un exercice, réduite par la mise en route (exo qui arrive) et la fatigue (muscles déjà travaillés
 * plus tôt dans la séance).
 */
data class DoseInfo(
    /** Fatigue : part gardée (1 = rien de retiré). */
    val fatigue: Double = 1.0,
    /** Mise en route : part gardée. */
    val ramp: Double = 1.0,
    /** Muscles déjà travaillés, et par quels exos. */
    val tired: List<String> = emptyList(),
    val after: List<String> = emptyList(),
) {
    /** Part de la dose gardée, jamais sous [Dose.FLOOR]. */
    val factor: Double get() = (fatigue * ramp).coerceAtLeast(Dose.FLOOR)
    val reduced: Boolean get() = factor < 0.995
    val percent: Int get() = (factor * 100).roundToInt()
}

object Dose {
    /** Un muscle principal déjà travaillé retire cette part, un muscle qui aide la moitié environ. */
    const val PRIMARY = 0.15
    const val SECONDARY = 0.07
    /** La fatigue seule ne descend pas plus bas. */
    const val FATIGUE_FLOOR = 0.65
    /** Fatigue et mise en route ensemble. */
    const val FLOOR = 0.5
    /** Séances faites d'un exo qui arrive : 60 % aux deux premières, 80 % aux deux suivantes. */
    const val RAMP_SESSIONS = 4
    /** Un exo qui apparaît moins de tant de jours après la première séance du programme fait partie du départ. */
    const val RAMP_AFTER_DAYS = 7L

    fun ramp(doneBefore: Int): Double = when {
        doneBefore < 2 -> 0.6
        doneBefore < RAMP_SESSIONS -> 0.8
        else -> 1.0
    }

    /** Ce que la dose sait d'un exercice de la séance. */
    data class Item(
        val name: String,
        val muscles: List<String>,
        val assists: List<String>,
        /** Compte dans la fatigue des suivants (pas l'échauffement ni les étirements). */
        val counts: Boolean,
        /** Sa dose peut être réduite. */
        val reducible: Boolean,
        val ramp: Double = 1.0,
    )

    private fun norm(m: String) = m.trim().lowercase()

    /** Les doses des exercices [items], dans l'ordre où ils se font. */
    fun compute(items: List<Item>): List<DoseInfo> {
        val worked = LinkedHashMap<String, MutableList<String>>()
        return items.map { item ->
            val primary = item.muscles.map(::norm).distinct()
            val secondary = item.assists.map(::norm).distinct().filter { it !in primary }
            val tiredPrimary = primary.filter { it in worked }
            val tiredSecondary = secondary.filter { it in worked }
            val dose = if (!item.reducible) DoseInfo() else DoseInfo(
                fatigue = (1 - PRIMARY * tiredPrimary.size - SECONDARY * tiredSecondary.size).coerceAtLeast(FATIGUE_FLOOR),
                ramp = item.ramp,
                tired = tiredPrimary + tiredSecondary,
                after = (tiredPrimary + tiredSecondary).flatMap { worked.getValue(it) }.distinct(),
            )
            if (item.counts) (primary + secondary).forEach { worked.getOrPut(it) { ArrayList() }.let { l -> if (item.name !in l) l += item.name } }
            dose
        }
    }

    /** Une valeur de série réduite : au moins 1 ; en secondes, aux 5 s à partir d'une minute. */
    fun scale(value: Int, factor: Double, seconds: Boolean): Int {
        val v = value * factor
        return if (seconds && value >= 60) (Math.round(v / 5) * 5).toInt().coerceAtLeast(5) else v.roundToInt().coerceAtLeast(1)
    }

    /**
     * Ordre de la séance : les exos de la routine (les facultatifs, échauffement et étirements, gardent leur place, les
     * autres suivent [keys] quand il y en a), puis la variante juste après son exercice de base et l'exo perso avant
     * le dernier.
     */
    fun arrange(day: List<Exercise>, keys: List<String>?, variantIndex: Int?, variantBase: Int?, otherIndex: Int?, n: Int = day.size): MutableList<Int> {
        val order = (0 until n).toMutableList()
        if (!keys.isNullOrEmpty()) {
            val movable = order.filter { !day[it].optional }
            val rank = keys.withIndex().associate { (i, k) -> k to i }
            val sorted = movable.sortedBy { rank[day[it].key] ?: (keys.size + movable.indexOf(it)) }
            var k = 0
            for (pos in order.indices) if (!day[order[pos]].optional) order[pos] = sorted[k++]
        }
        variantIndex?.takeIf { it != variantBase }?.let { order.add(order.indexOf(variantBase!!) + 1, it) }
        otherIndex?.let { if (n >= 2) order.add(order.indexOf(n - 1), it) else order.add(it) }
        return order
    }
}

/** La saisie ramenée à une autre dose (× [factor]) : ce que le réglage compare à la dose pleine. */
fun ExerciseLog.scaled(factor: Double): ExerciseLog {
    if (factor in 0.995..1.005) return this
    fun s(v: Int?) = v?.let { if (it == 0) 0 else (it * factor).roundToInt().coerceAtLeast(1) }
    return copy(reps = s(reps), seconds = s(seconds), max = s(max), perSet = perSet?.map { if (it == 0) 0 else (it * factor).roundToInt().coerceAtLeast(1) })
}

/** La prescription à [dose] : chaque série réduite d'autant, le reste inchangé. La dose pleine sert aux exploits. */
fun Prescription.withDose(dose: DoseInfo): Prescription {
    if (!dose.reduced) return this
    val f = dose.factor
    val inSeconds = maxIsSeconds
    fun s(v: Int?, seconds: Boolean) = v?.let { Dose.scale(it, f, seconds) }
    return copy(
        reps = s(reps, false),
        seconds = s(seconds, true),
        target = s(target, inSeconds),
        sided = sided?.copy(left = s(sided.left, inSeconds), right = s(sided.right, inSeconds)),
        fullPlanned = perSetPlanned,
        dose = dose,
    )
}
