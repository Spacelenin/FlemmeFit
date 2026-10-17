package com.spacelenin.flemmefit.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

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

/** Valeur interne de [Plan.repeat] pour `"repeat": "forever"`. */
const val REPEAT_FOREVER = -1

/** Semaine de décharge par défaut quand le programme contient des progressions. */
const val DEFAULT_DELOAD_EVERY = 4

@Serializable
data class Plan(
    val format: String = PLAN_FORMAT_ID,
    val version: Int,
    val name: String,
    val description: String? = null,
    @Serializable(with = RepeatSerializer::class)
    val repeat: Int = 1,
    /** Une semaine sur N est allégée de moitié. 0 = jamais. Absent = [DEFAULT_DELOAD_EVERY] s'il y a des progressions. */
    val deloadEvery: Int? = null,
    /** Jours Boss aléatoires (toutes les 3 à 4 semaines). */
    val boss: Boolean = true,
    val weeks: List<PlanWeek>,
) {
    val isForever get() = repeat == REPEAT_FOREVER

    val effectiveDeloadEvery: Int
        get() = deloadEvery
            ?: if (weeks.any { w -> w.days.any { d -> d.exercises.any { it.progression != null } } }) DEFAULT_DELOAD_EVERY else 0
}

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
    /** Relie le même exercice sur plusieurs jours (progression et stats). Par défaut : le nom. */
    val id: String? = null,
    val description: String? = null,
    val sets: Int? = null,
    val reps: String? = null,
    val duration: String? = null,
    val load: String? = null,
    val rest: String? = null,
    val progression: Progression? = null,
) {
    /** Clé de regroupement : stable entre jours et entre programmes. */
    val key: String get() = (id ?: name).trim().lowercase()
}

/**
 * Progression automatique d'un exercice. Chaque « cran » ajoute les pas indiqués ;
 * arrivé au plafond, on ajoute [addSets] séries et on repart de la valeur de départ.
 */
@Serializable
data class Progression(
    /** "done" : un cran par séance validée. "week" : un cran par semaine du calendrier. */
    val basis: String = BASIS_DONE,
    /** Nombre de séances validées (ou de semaines) pour un cran. */
    val every: Int = 1,
    val reps: Int? = null,
    val maxReps: Int? = null,
    val duration: String? = null,
    val maxDuration: String? = null,
    val load: String? = null,
    val maxLoad: String? = null,
    val addSets: Int = 0,
    val maxSets: Int? = null,
) {
    companion object {
        const val BASIS_DONE = "done"
        const val BASIS_WEEK = "week"
    }
}

/** `"repeat"` accepte un entier ou `"forever"`. */
object RepeatSerializer : KSerializer<Int> {
    override val descriptor = PrimitiveSerialDescriptor("FlemmeFitRepeat", PrimitiveKind.STRING)

    private val foreverWords = setOf("forever", "infini", "infinite", "illimité", "illimite")

    override fun deserialize(decoder: Decoder): Int {
        if (decoder !is JsonDecoder) return decoder.decodeInt()
        val primitive = decoder.decodeJsonElement().jsonPrimitive
        primitive.intOrNull?.let { return it }
        if (primitive.content.trim().lowercase() in foreverWords) return REPEAT_FOREVER
        throw SerializationException("\"repeat\" doit être un nombre ou \"forever\" (reçu \"${primitive.content}\")")
    }

    override fun serialize(encoder: Encoder, value: Int) {
        if (encoder is JsonEncoder) {
            encoder.encodeJsonElement(if (value == REPEAT_FOREVER) JsonPrimitive("forever") else JsonPrimitive(value))
        } else {
            encoder.encodeInt(value)
        }
    }
}
