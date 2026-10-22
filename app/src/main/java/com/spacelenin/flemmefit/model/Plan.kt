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
    /** Révision de la routine elle-même (la version, elle, est celle du format). Sert à mettre à jour la routine intégrée. */
    val revision: Int = 0,
    val name: String,
    val description: String? = null,
    @Serializable(with = RepeatSerializer::class)
    val repeat: Int = 1,
    /** Une semaine sur N est allégée de moitié. 0 = jamais. Absent = [DEFAULT_DELOAD_EVERY] s'il y a des progressions. */
    val deloadEvery: Int? = null,
    /** Jours Boss aléatoires (toutes les 3 à 4 semaines). */
    val boss: Boolean = true,
    /** Tests de départ : leur résultat choisit le palier des exercices qui s'y rattachent. */
    val tests: List<PlanTest> = emptyList(),
    val weeks: List<PlanWeek>,
) {
    val isForever get() = repeat == REPEAT_FOREVER

    val effectiveDeloadEvery: Int
        get() = deloadEvery
            ?: if (weeks.any { w -> w.days.any { d -> d.exercises.any { it.progression != null } } }) DEFAULT_DELOAD_EVERY else 0

    fun test(id: String?): PlanTest? = id?.let { t -> tests.firstOrNull { it.id == t } }

    /** Séance prévue à l'index de jour [dayIndex] (répétitions comprises, sans tenir compte des reports). */
    fun dayAt(dayIndex: Int): PlanDay? {
        if (dayIndex < 0 || weeks.isEmpty()) return null
        val week = weeks[(dayIndex / DAYS_PER_WEEK) % weeks.size]
        return week.days.firstOrNull { it.day == dayIndex % DAYS_PER_WEEK + 1 }
    }
}

/** Un test « à fond » (max de répétitions, ou durée tenue) fait au départ puis refait en cours de route. */
@Serializable
data class PlanTest(
    val id: String,
    val name: String,
    val description: String? = null,
    /** "reps" ou "seconds". */
    val unit: String = UNIT_REPS,
) {
    val isSeconds get() = unit == UNIT_SECONDS

    companion object {
        const val UNIT_REPS = "reps"
        const val UNIT_SECONDS = "seconds"
    }
}

/**
 * Palier d'un exercice, choisi d'après le dernier résultat du test : le palier retenu est le dernier
 * dont [from] est inférieur ou égal au résultat. Les champs présents remplacent ceux de l'exercice.
 */
@Serializable
data class Level(
    val from: Int = 0,
    val sets: Int? = null,
    val reps: String? = null,
    val duration: String? = null,
    val load: String? = null,
    val rest: String? = null,
    /** Ajouté à la description (variante plus facile, consigne…). */
    val note: String? = null,
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
    /** Relie le même exercice sur plusieurs jours (progression et stats). Par défaut : le nom. */
    val id: String? = null,
    val description: String? = null,
    val sets: Int? = null,
    val reps: String? = null,
    val duration: String? = null,
    val load: String? = null,
    val rest: String? = null,
    val progression: Progression? = null,
    /** Test de départ auquel l'exercice se rattache (id dans [Plan.tests]). */
    val test: String? = null,
    /** Paliers choisis d'après le résultat de [test], du plus facile au plus dur. */
    val levels: List<Level> = emptyList(),
    /** Dernière série : le maximum possible (« au moins » le prévu), à noter. */
    val amrap: Boolean = false,
    /** Cet exercice est le test lui-même : une série à fond dont le résultat devient la nouvelle référence. */
    val retest: Boolean = false,
    /** Se fait au choix avec ou sans petits poids, à dire le jour même. */
    val weights: WeightsOption? = null,
) {
    /** Clé de regroupement : stable entre jours et entre programmes. */
    val key: String get() = (id ?: name).trim().lowercase()

    /** Index du palier pour un résultat de test (0 si pas de résultat). */
    fun levelIndexFor(result: Int?): Int = levels.indexOfLast { it.from <= (result ?: 0) }.coerceAtLeast(0)

    /** L'exercice tel qu'il se présente au palier [index]. */
    fun atLevel(index: Int): Exercise {
        val l = levels.getOrNull(index) ?: return this
        return copy(
            sets = l.sets ?: sets,
            reps = l.reps ?: reps,
            duration = l.duration ?: duration,
            load = l.load ?: load,
            rest = l.rest ?: rest,
            description = listOfNotNull(description, l.note).joinToString("\n\n").ifEmpty { null },
        )
    }
}

/**
 * Exercice faisable avec ou sans poids. Avec poids, chaque cran de progression ne vaut que [factor] du pas :
 * même plafond, atteint plus tard.
 */
@Serializable
data class WeightsOption(
    /** Choix proposé tant que l'utilisateur n'a rien dit. */
    val default: Boolean = false,
    val factor: Double = DEFAULT_FACTOR,
) {
    companion object {
        const val DEFAULT_FACTOR = 0.85
    }
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
