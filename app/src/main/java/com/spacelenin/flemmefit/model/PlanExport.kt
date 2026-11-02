package com.spacelenin.flemmefit.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** La routine d'un programme en JSON, à modifier puis réimporter (« Mettre à jour la routine »). */
object PlanExport {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        encodeDefaults = false
        explicitNulls = false
    }

    /** La routine telle quelle : seulement ce qui y est écrit. */
    fun routine(plan: Plan): String = encode(plan)

    /**
     * La routine avec tous les réglages du sur-mesure écrits en entier (valeurs de la routine, sinon celles par
     * défaut), et une aide pour chacun : de quoi tout ajuster à la main.
     */
    fun template(plan: Plan): String {
        val tuning = Tuning(
            adapt = AdaptParams().with(plan.tuning?.adapt).toTuning(),
            sides = SidesParams().with(plan.tuning?.sides).toTuning(),
            variants = VariantParams().with(plan.tuning?.variants).toTuning(),
        )
        val root = json.encodeToJsonElement(Plan.serializer(), plan.copy(tuning = tuning)).jsonObject
        return json.encodeToString(JsonObject.serializer(), JsonObject(withHead(root) + ("aide" to HELP)))
    }

    private fun encode(plan: Plan): String =
        json.encodeToString(JsonObject.serializer(), JsonObject(withHead(json.encodeToJsonElement(Plan.serializer(), plan).jsonObject)))

    /** « format » et « version » en tête, même quand ce sont les valeurs par défaut. */
    private fun withHead(root: JsonObject): Map<String, kotlinx.serialization.json.JsonElement> =
        linkedMapOf<String, kotlinx.serialization.json.JsonElement>(
            "format" to JsonPrimitive(PLAN_FORMAT_ID),
            "version" to JsonPrimitive(PLAN_FORMAT_VERSION),
        ) + root.filterKeys { it != "format" && it != "version" }

    /** Ignorée à l'import : juste là pour s'y retrouver. */
    private val HELP = buildJsonObject {
        put("tuning", "Réglages du sur-mesure. Un bloc \"tuning\" se met aussi sur un exercice ou une variante (adapt et sides seulement) : il prime alors pour lui. Le rythme choisi dans l'appli (Doux, Normal, Vif) agit par-dessus.")
        put("adapt.calibration", "Séances pour te connaître (2 à 30).")
        put("adapt.gainStart", "Part de l'écart rattrapée à la première séance (0,05 à 1).")
        put("adapt.gainEnd", "Part de l'écart rattrapée une fois calé (0,05 à 1, sous gainStart).")
        put("adapt.gainDown", "Au minimum, à la baisse : redescendre vite quand ça ne passe pas (0,1 à 1).")
        put("adapt.threshold", "Écart en crans avant de bouger une fois calé (0,5 à 3).")
        put("adapt.exerciseFeeling", "Poids du ressenti d'un exo, en crans (0 à 3).")
        put("adapt.sessionFeeling", "Poids du ressenti de la séance, en crans (0 à 3).")
        put("adapt.testCeiling", "Au test refait : au-dessus de cette part du max, c'est trop (0,3 à 0,95).")
        put("adapt.testTarget", "… et ça redescend jusqu'à cette part (sous testCeiling).")
        put("sides.evenBelow", "Écart gauche / droite sous lequel les deux côtés font pareil (0,02 à 0,5).")
        put("sides.weakOnlyFrom", "Écart à partir duquel seul le côté faible progresse (au-dessus de evenBelow, jusqu'à 0,8).")
        put("sides.catchUpDays", "Jours visés pour rattraper l'écart (7 à 365).")
        put("sides.switchSessions", "Séances de suite avant de changer de cas (1 à 10).")
        put("sides.switchRest", "Repos entre les deux côtés, en secondes (0 à 300).")
        put("variants.sets", "Séries d'un bloc variante (1 à 10).")
        put("variants.share", "Part du max estimé pour un bloc variante (0,2 à 1).")
        put("variants.easyFactor", "La version facile, par rapport à la vraie (0,1 à 1).")
        put("variants.cycleWeeks", "Cycle des variantes sans semaine allégée, en semaines (1 à 12).")
        put("variants.otherPerWeek", "Passages par semaine d'un exo perso « Autre » (0 à 7).")
    }
}
