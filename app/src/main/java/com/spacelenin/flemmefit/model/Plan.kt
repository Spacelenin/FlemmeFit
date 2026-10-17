package com.spacelenin.flemmefit.model

import kotlinx.serialization.Serializable

/**
 * Format d'import FlemmeFit, version 1. Voir FORMAT.md à la racine du dépôt.
 *
 * Ces classes servent à la fois pour l'import et pour le stockage interne :
 * toute évolution doit rester rétro-compatible (champs nouveaux = optionnels).
 */
const val PLAN_FORMAT_ID = "flemmefit"
const val PLAN_FORMAT_VERSION = 1

const val DAYS_PER_WEEK = 7
const val MAX_TOTAL_WEEKS = 520
const val MAX_REPEAT = 100
const val MAX_SETS = 100

@Serializable
data class Plan(
    val format: String = PLAN_FORMAT_ID,
    val version: Int,
    val name: String,
    val description: String? = null,
    val repeat: Int = 1,
    val weeks: List<PlanWeek>,
)

@Serializable
data class PlanWeek(
    val label: String? = null,
    val days: List<PlanDay> = emptyList(),
)

@Serializable
data class PlanDay(
    /** 1 à 7 : position dans la semaine, comptée depuis la date de début (pas forcément un lundi). */
    val day: Int,
    val title: String? = null,
    val notes: String? = null,
    val exercises: List<Exercise>,
)

@Serializable
data class Exercise(
    val name: String,
    val description: String? = null,
    val sets: Int? = null,
    val reps: String? = null,
    val duration: String? = null,
    val load: String? = null,
    val rest: String? = null,
)
