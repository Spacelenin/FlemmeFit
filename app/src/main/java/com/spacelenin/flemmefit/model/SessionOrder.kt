package com.spacelenin.flemmefit.model

import kotlin.math.roundToInt

/** Ordre des exos d'une séance, et petites réductions de dose (« Crevé »). */
object SessionOrder {
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

/**
 * « Crevé » ([Exercise.tired]) : chaque série réduite de [percent] %. La dose pleine reste connue pour les exploits.
 */
fun Prescription.cutBy(percent: Int): Prescription {
    val f = 1 - percent.coerceIn(0, 90) / 100.0
    val inSeconds = maxIsSeconds
    fun s(v: Int?, seconds: Boolean) = v?.let { SessionOrder.scale(it, f, seconds) }
    return copy(
        reps = s(reps, false),
        seconds = s(seconds, true),
        target = s(target, inSeconds),
        sided = sided?.copy(left = s(sided.left, inSeconds), right = s(sided.right, inSeconds)),
        fullPlanned = perSetPlanned,
        tired = true,
    )
}
