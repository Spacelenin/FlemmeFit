package com.spacelenin.flemmefit.model

import java.time.DayOfWeek
import java.time.LocalDate

/** Tout ce que la fiche d'un exo de la routine montre (onglet Exos). */
data class ExerciseInfo(
    val key: String,
    val name: String,
    val description: String?,
    val muscles: List<String>,
    val assists: List<String>,
    /** Palier (0-based) sur [levelCount], le test qui le décide, son dernier résultat, et ce qu'il faut pour le suivant. */
    val palier: Int?,
    val levelCount: Int,
    val testName: String?,
    val testResult: Int?,
    val nextLevelAt: Int?,
    val testSeconds: Boolean,
    /** Prochaine dose prévue (« 3 × 8 »), et la règle de progression. */
    val dose: String?,
    val progression: String?,
    val adaptation: ExerciseAdaptation?,
    val weekdays: List<DayOfWeek>,
    val nextDate: LocalDate?,
    val stats: ExerciseStats?,
    /** Meilleure série de chaque séance faite, les dernières. */
    val history: List<TestPoint>,
    /** Séances faites en version excentrique (barres pâles). */
    val eccentricDates: Set<java.time.LocalDate> = emptySet(),
    val seconds: Boolean,
    val balance: SideBalance?,
    val alternatives: List<String>,
    val variants: List<VariantProgress>,
) {
    fun format(v: Int) = if (seconds) Measures.formatSeconds(v) else "$v"
}

/** « +1 rep toutes les 2 séances, jusqu'à 25 », « +5s chaque semaine ». */
fun Progression.describe(): String? {
    val step = listOfNotNull(reps?.let { "+$it rep" + if (it > 1) "s" else "" }, duration?.let { "+$it" }, load?.let { "+$it" })
        .joinToString(" et ").ifEmpty { return null }
    val rhythm = when {
        basis == Progression.BASIS_WEEK -> if (every <= 1) "chaque semaine" else "toutes les $every semaines"
        every <= 1 -> "à chaque séance réussie"
        else -> "toutes les $every séances réussies"
    }
    val cap = listOfNotNull(maxReps?.let { "$it reps" }, maxDuration, maxLoad).joinToString(", ").ifEmpty { null }
    val sets = if (addSets > 0) " ; au plafond, +$addSets série" + (maxSets?.let { " (jusqu'à $it)" } ?: "") else ""
    return "$step $rhythm" + (cap?.let { ", jusqu'à $it" } ?: "") + sets
}
