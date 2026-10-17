package com.spacelenin.flemmefit.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

sealed interface ParseResult {
    data class Success(val plan: Plan) : ParseResult
    data class Failure(val errors: List<String>) : ParseResult
}

object PlanParser {

    /** Tolérant en lecture : clés inconnues ignorées, nombres acceptés là où un texte est attendu. */
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        encodeDefaults = true
        @OptIn(ExperimentalSerializationApi::class)
        explicitNulls = false
    }

    fun parse(text: String): ParseResult {
        val trimmed = text.trim().removePrefix("﻿")
        if (trimmed.isEmpty()) return ParseResult.Failure(listOf("Le fichier est vide."))

        val root = try {
            json.parseToJsonElement(trimmed) as? JsonObject
                ?: return ParseResult.Failure(listOf("Le fichier doit contenir un objet JSON { ... }."))
        } catch (e: SerializationException) {
            return ParseResult.Failure(listOf("JSON invalide : ${e.message.orEmpty()}"))
        }

        // Vérifié avant le décodage pour donner un message clair si le format évolue.
        val format = root["format"]?.jsonPrimitive?.contentOrNull
        if (format != null && format != PLAN_FORMAT_ID) {
            return ParseResult.Failure(listOf("\"format\" doit valoir \"$PLAN_FORMAT_ID\" (reçu \"$format\")."))
        }
        val version = root["version"]?.jsonPrimitive?.intOrNull
            ?: return ParseResult.Failure(listOf("Champ \"version\" manquant ou invalide (attendu : $PLAN_FORMAT_VERSION)."))
        if (version != PLAN_FORMAT_VERSION) {
            return ParseResult.Failure(
                listOf("Version $version non supportée : cette app lit la version $PLAN_FORMAT_VERSION."),
            )
        }

        val plan = try {
            json.decodeFromJsonElement(Plan.serializer(), root)
        } catch (e: MissingFieldException) {
            val path = Regex("at path: (\\S+)").find(e.message.orEmpty())?.groupValues?.get(1)
            val where = path?.let { " (dans $it)" }.orEmpty()
            return ParseResult.Failure(
                listOf("Champ(s) obligatoire(s) manquant(s) : ${e.missingFields.joinToString()}$where."),
            )
        } catch (e: SerializationException) {
            return ParseResult.Failure(listOf("Structure invalide : ${e.message.orEmpty()}"))
        } catch (e: IllegalArgumentException) {
            return ParseResult.Failure(listOf("Structure invalide : ${e.message.orEmpty()}"))
        }

        val errors = validate(plan)
        return if (errors.isEmpty()) ParseResult.Success(normalize(plan)) else ParseResult.Failure(errors)
    }

    fun validate(plan: Plan): List<String> = buildList {
        if (plan.name.isBlank()) add("\"name\" ne doit pas être vide.")
        if (plan.repeat !in 1..MAX_REPEAT) add("\"repeat\" doit être entre 1 et $MAX_REPEAT (reçu ${plan.repeat}).")
        if (plan.weeks.isEmpty()) add("\"weeks\" doit contenir au moins une semaine.")
        if (plan.weeks.size.toLong() * plan.repeat > MAX_TOTAL_WEEKS) {
            add("Programme trop long : semaines × repeat doit rester ≤ $MAX_TOTAL_WEEKS.")
        }

        plan.weeks.forEachIndexed { w, week ->
            val seen = mutableSetOf<Int>()
            week.days.forEachIndexed { d, day ->
                val at = "weeks[$w].days[$d]"
                if (day.day !in 1..DAYS_PER_WEEK) add("$at.day doit être entre 1 et 7 (reçu ${day.day}).")
                else if (!seen.add(day.day)) add("$at.day : le jour ${day.day} apparaît deux fois dans la semaine ${w + 1}.")
                if (day.exercises.isEmpty()) add("$at.exercises doit contenir au moins un exercice.")
                day.exercises.forEachIndexed { e, ex ->
                    if (ex.name.isBlank()) add("$at.exercises[$e].name ne doit pas être vide.")
                    if (ex.sets != null && ex.sets !in 1..MAX_SETS) {
                        add("$at.exercises[$e].sets doit être entre 1 et $MAX_SETS (reçu ${ex.sets}).")
                    }
                }
            }
        }
        if (plan.weeks.isNotEmpty() && plan.weeks.all { it.days.isEmpty() }) {
            add("Le programme ne contient aucune séance.")
        }
    }

    /** Nettoie les textes et trie les jours : la suite du code peut compter sur cet ordre. */
    private fun normalize(plan: Plan): Plan = plan.copy(
        format = PLAN_FORMAT_ID,
        name = plan.name.trim(),
        description = plan.description.clean(),
        weeks = plan.weeks.map { week ->
            week.copy(
                label = week.label.clean(),
                days = week.days.sortedBy { it.day }.map { day ->
                    day.copy(
                        title = day.title.clean(),
                        notes = day.notes.clean(),
                        exercises = day.exercises.map { ex ->
                            ex.copy(
                                name = ex.name.trim(),
                                description = ex.description.clean(),
                                reps = ex.reps.clean(),
                                duration = ex.duration.clean(),
                                load = ex.load.clean(),
                                rest = ex.rest.clean(),
                            )
                        },
                    )
                },
            )
        },
    )

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
