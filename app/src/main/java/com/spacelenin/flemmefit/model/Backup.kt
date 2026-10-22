package com.spacelenin.flemmefit.model

import kotlinx.serialization.Serializable

/** Une préférence sauvegardée, avec son type pour la relire telle quelle. */
@Serializable
data class PrefValue(val type: String, val value: String = "", val values: List<String>? = null) {
    /** La valeur Android d'origine (Boolean, Int, Long, Float, String ou Set<String>), ou null si illisible. */
    fun toAny(): Any? = when (type) {
        TYPE_BOOL -> value.toBooleanStrictOrNull()
        TYPE_INT -> value.toIntOrNull()
        TYPE_LONG -> value.toLongOrNull()
        TYPE_FLOAT -> value.toFloatOrNull()
        TYPE_STRING -> value
        TYPE_SET -> values?.toSet()
        else -> null
    }

    companion object {
        const val TYPE_BOOL = "bool"
        const val TYPE_INT = "int"
        const val TYPE_LONG = "long"
        const val TYPE_FLOAT = "float"
        const val TYPE_STRING = "string"
        const val TYPE_SET = "set"

        fun of(value: Any?): PrefValue? = when (value) {
            is Boolean -> PrefValue(TYPE_BOOL, value.toString())
            is Int -> PrefValue(TYPE_INT, value.toString())
            is Long -> PrefValue(TYPE_LONG, value.toString())
            is Float -> PrefValue(TYPE_FLOAT, value.toString())
            is String -> PrefValue(TYPE_STRING, value)
            is Set<*> -> PrefValue(TYPE_SET, values = value.filterIsInstance<String>().sorted())
            else -> null
        }
    }
}

/** Tout ce qu'il faut pour retrouver l'appli à l'identique : programmes et suivi, réglages, pseudo. */
@Serializable
data class Backup(
    val format: String = FORMAT,
    val version: Int = 1,
    /** Date et heure locales de la sauvegarde (« 2026-10-22T18:04 »). */
    val createdAt: String,
    val appVersion: Int? = null,
    val state: AppState,
    /** Fichiers de préférences → clé → valeur. */
    val prefs: Map<String, Map<String, PrefValue>> = emptyMap(),
) {
    /** Exercices faits, tous programmes confondus. */
    val doneExercises: Int get() = state.programs.sumOf { it.done.size }

    fun encode(): String = PlanParser.json.encodeToString(serializer(), this)

    companion object {
        const val FORMAT = "flemmefit-backup"
        const val FILE_NAME = "flemmefit-sauvegarde.json"

        /** Préférences sauvegardées. Les mises à jour restent propres au téléphone. */
        val PREF_FILES = listOf("settings", "reminders", "social")

        /** Clés propres à ce téléphone : où il sauvegarde (l'accès au fichier ne se transporte pas). */
        val LOCAL_KEYS = setOf("backup_uri", "backup_last", "backup_error")

        fun prefsOf(files: Map<String, Map<String, *>>): Map<String, Map<String, PrefValue>> =
            files.mapValues { (_, entries) ->
                entries.filterKeys { it !in LOCAL_KEYS }.mapNotNull { (k, v) -> PrefValue.of(v)?.let { k to it } }.toMap()
            }

        /** Lit une sauvegarde, ou null si le texte n'en est pas une. */
        fun decode(text: String): Backup? = try {
            PlanParser.json.decodeFromString(serializer(), text).takeIf { it.format == FORMAT }
        } catch (_: Exception) {
            null
        }
    }
}
