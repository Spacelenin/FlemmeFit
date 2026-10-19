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
        if (!plan.isForever && plan.repeat !in 1..MAX_REPEAT) {
            add("\"repeat\" doit être entre 1 et $MAX_REPEAT, ou \"forever\" (reçu ${plan.repeat}).")
        }
        if (plan.weeks.isEmpty()) add("\"weeks\" doit contenir au moins une semaine.")
        if (plan.weeks.size > MAX_TOTAL_WEEKS ||
            (!plan.isForever && plan.weeks.size.toLong() * plan.repeat > MAX_TOTAL_WEEKS)
        ) {
            add("Programme trop long : semaines × repeat doit rester ≤ $MAX_TOTAL_WEEKS.")
        }
        plan.deloadEvery?.let {
            if (it != 0 && it !in 2..52) add("\"deloadEvery\" doit valoir 0 (jamais) ou être entre 2 et 52 (reçu $it).")
        }

        val testIds = mutableSetOf<String>()
        plan.tests.forEachIndexed { t, test ->
            val at = "tests[$t]"
            if (test.id.isBlank()) add("$at.id ne doit pas être vide.")
            else if (!testIds.add(test.id)) add("$at.id : le test \"${test.id}\" apparaît deux fois.")
            if (test.name.isBlank()) add("$at.name ne doit pas être vide.")
            if (test.unit != PlanTest.UNIT_REPS && test.unit != PlanTest.UNIT_SECONDS) {
                add("$at.unit doit valoir \"reps\" ou \"seconds\" (reçu \"${test.unit}\").")
            }
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
                    if (ex.id != null && ex.id.isBlank()) add("$at.exercises[$e].id ne doit pas être vide.")
                    addAll(validateLevels(ex, testIds, "$at.exercises[$e]"))
                    ex.progression?.let { p ->
                        // Avec des paliers, chaque palier doit pouvoir progresser.
                        val variants = if (ex.levels.isEmpty()) listOf(ex) else ex.levels.indices.map(ex::atLevel)
                        variants.flatMap { validateProgression(it, p, "$at.exercises[$e].progression") }.distinct().forEach(::add)
                    }
                }
            }
        }
        if (plan.weeks.isNotEmpty() && plan.weeks.all { it.days.isEmpty() }) {
            add("Le programme ne contient aucune séance.")
        }
    }

    private fun validateLevels(ex: Exercise, testIds: Set<String>, at: String): List<String> = buildList {
        if (ex.test != null && ex.test !in testIds) add("$at.test : aucun test \"${ex.test}\" dans \"tests\".")
        if (ex.levels.isNotEmpty() && ex.test == null) add("$at.levels : il faut aussi \"test\" pour choisir le palier.")
        if (ex.retest && ex.test == null) add("$at.retest : il faut aussi \"test\" (le test refait).")
        if (ex.retest && ex.levels.isNotEmpty()) add("$at : un exercice \"retest\" n'a pas de paliers.")
        ex.levels.forEachIndexed { l, level ->
            if (level.from < 0) add("$at.levels[$l].from doit être positif (reçu ${level.from}).")
            if (l > 0 && level.from <= ex.levels[l - 1].from) add("$at.levels : les \"from\" doivent être croissants.")
            if (level.sets != null && level.sets !in 1..MAX_SETS) add("$at.levels[$l].sets doit être entre 1 et $MAX_SETS.")
        }
    }

    private fun validateProgression(ex: Exercise, p: Progression, at: String): List<String> = buildList {
        if (p.basis != Progression.BASIS_DONE && p.basis != Progression.BASIS_WEEK) {
            add("$at.basis doit valoir \"done\" ou \"week\" (reçu \"${p.basis}\").")
        }
        if (p.every !in 1..52) add("$at.every doit être entre 1 et 52 (reçu ${p.every}).")
        if (p.reps == null && p.duration == null && p.load == null) {
            add("$at doit indiquer au moins un pas : reps, duration ou load.")
        }
        val baseReps = Measures.reps(ex.reps)
        if (p.reps != null) {
            if (p.reps !in 1..50) add("$at.reps doit être entre 1 et 50 (reçu ${p.reps}).")
            if (baseReps == null) add("$at.reps : l'exercice doit avoir \"reps\" chiffré (reçu \"${ex.reps}\").")
        }
        if (p.maxReps != null && baseReps != null && p.maxReps < baseReps) {
            add("$at.maxReps (${p.maxReps}) est inférieur aux reps de départ ($baseReps).")
        }
        val baseSeconds = Measures.seconds(ex.duration)
        if (p.duration != null) {
            if (Measures.seconds(p.duration).let { it == null || it <= 0 }) add("$at.duration illisible : \"${p.duration}\" (ex. \"5s\").")
            if (baseSeconds == null) add("$at.duration : l'exercice doit avoir \"duration\" chiffrée (reçu \"${ex.duration}\").")
        }
        if (p.maxDuration != null && Measures.seconds(p.maxDuration) == null) add("$at.maxDuration illisible : \"${p.maxDuration}\".")
        if (p.load != null) {
            if (Measures.load(p.load) == null) add("$at.load illisible : \"${p.load}\" (ex. \"1kg\").")
            if (Measures.load(ex.load) == null) add("$at.load : l'exercice doit avoir \"load\" chiffrée (reçu \"${ex.load}\").")
        }
        if (p.maxLoad != null && Measures.load(p.maxLoad) == null) add("$at.maxLoad illisible : \"${p.maxLoad}\".")
        if (p.addSets !in 0..5) add("$at.addSets doit être entre 0 et 5 (reçu ${p.addSets}).")
        if (p.maxSets != null && p.maxSets !in 1..MAX_SETS) add("$at.maxSets doit être entre 1 et $MAX_SETS.")
    }

    /** Nettoie les textes et trie les jours : la suite du code peut compter sur cet ordre. */
    private fun normalize(plan: Plan): Plan = plan.copy(
        format = PLAN_FORMAT_ID,
        name = plan.name.trim(),
        description = plan.description.clean(),
        tests = plan.tests.map { it.copy(id = it.id.trim(), name = it.name.trim(), description = it.description.clean()) },
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
                                id = ex.id.clean(),
                                description = ex.description.clean(),
                                reps = ex.reps.clean(),
                                duration = ex.duration.clean(),
                                load = ex.load.clean(),
                                rest = ex.rest.clean(),
                                test = ex.test?.trim(),
                                levels = ex.levels.map { l ->
                                    l.copy(reps = l.reps.clean(), duration = l.duration.clean(), load = l.load.clean(), rest = l.rest.clean(), note = l.note.clean())
                                },
                            )
                        },
                    )
                },
            )
        },
    )

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
