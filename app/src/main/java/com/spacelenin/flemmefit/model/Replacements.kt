package com.spacelenin.flemmefit.model

import kotlinx.serialization.Serializable

/**
 * Exercice qu'on peut faire à la place d'un exercice de la routine (matériel, envie) : dosé comme [follows]
 * (même niveau, séries comprises), avec [factor] fois ses reps (ou ses secondes). Russian twist : comme les abdos,
 * 2 twists pour 1 rep d'abdos.
 */
@Serializable
data class Alternative(
    val id: String,
    val name: String,
    val description: String? = null,
    /** Id de l'exercice de la routine dont il suit le dosage. */
    val follows: String,
    val factor: Double = 1.0,
)

/** Un remplacement gardé pour les séances à partir de [date] (ISO) ; [option] null : retour à l'exercice prévu. */
@Serializable
data class ReplacementChange(val exercise: String, val option: String?, val date: String)

/** Ce qu'on peut choisir pour remplacer un exercice : une alternative de la routine, ou un exo de la même famille. */
data class ReplacementOption(
    val id: String,
    val name: String,
    val description: String?,
    /** Clé de l'exercice dont il suit le dosage. */
    val follows: String,
    val factor: Double,
    /** Alternative écrite dans la routine (sinon : exercice de la même famille, avec sa propre progression). */
    val alternative: Boolean,
) {
    /** « Dosé comme les abdos, reps × 2 », « Son propre niveau ». */
    fun dosage(followsName: String?): String = when {
        !alternative -> "Avec son propre niveau"
        factor == 1.0 -> "Dosé comme ${followsName ?: follows}"
        else -> "Dosé comme ${followsName ?: follows}, reps × ${Variants.decimal(factor)}"
    }

    companion object {
        const val ALT = "alt:"
        const val EXERCISE = "ex:"
    }
}
