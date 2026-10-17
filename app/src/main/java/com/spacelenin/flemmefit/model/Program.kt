package com.spacelenin.flemmefit.model

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** État persistant de l'app. [schemaVersion] permettra de migrer le stockage si besoin. */
@Serializable
data class AppState(
    val schemaVersion: Int = 1,
    val programs: List<StoredProgram> = emptyList(),
)

/**
 * Ce qui a réellement été fait sur un exercice, quand ça diffère du prévu, et/ou le max
 * noté sur une série « à fond ». Valeurs par série.
 */
@Serializable
data class ExerciseLog(
    val sets: Int? = null,
    val reps: Int? = null,
    val seconds: Int? = null,
    /** Max de la série à fond : répétitions, ou secondes pour un exercice en durée. */
    val max: Int? = null,
) {
    val isEmpty get() = sets == null && reps == null && seconds == null && max == null
}

/**
 * Un programme importé et son suivi.
 *
 * Les jours du programme sont numérotés à partir de 0, répétitions comprises (« index de jour »).
 * Chaque entrée de [postpones] insère un jour de décalage juste avant ce jour,
 * ce qui repousse ce jour et tout le reste du programme.
 */
@Serializable
data class StoredProgram(
    val id: String,
    val plan: Plan,
    /** Date ISO (yyyy-MM-dd) du jour 1 de la semaine 1. */
    val startDate: String,
    val colorIndex: Int = 0,
    val importedAt: Long = 0,
    val postpones: List<Int> = emptyList(),
    /** Exercices validés, clés produites par [doneKey]. */
    val done: Set<String> = emptySet(),
    /** Saisies (réel fait, max), mêmes clés que [done]. */
    val logs: Map<String, ExerciseLog> = emptyMap(),
) {
    val start: LocalDate get() = LocalDate.parse(startDate)

    fun isDone(dayIndex: Int, exerciseIndex: Int) = doneKey(dayIndex, exerciseIndex) in done

    fun logOf(dayIndex: Int, exerciseIndex: Int): ExerciseLog? = logs[doneKey(dayIndex, exerciseIndex)]

    /** Cocher / décocher. Décocher efface aussi la saisie. */
    fun toggle(dayIndex: Int, exerciseIndex: Int): StoredProgram {
        val key = doneKey(dayIndex, exerciseIndex)
        return if (key in done) copy(done = done - key, logs = logs - key) else copy(done = done + key)
    }

    fun setSessionDone(dayIndex: Int, exerciseCount: Int, value: Boolean): StoredProgram {
        val keys = (0 until exerciseCount).map { doneKey(dayIndex, it) }
        return if (value) copy(done = done + keys) else copy(done = done - keys.toSet(), logs = logs - keys.toSet())
    }

    /** Enregistre une saisie. [complete] = le prévu est atteint, l'exercice est validé ; sinon il est partiel. */
    fun saveLog(dayIndex: Int, exerciseIndex: Int, log: ExerciseLog, complete: Boolean): StoredProgram {
        val key = doneKey(dayIndex, exerciseIndex)
        val newLogs = if (log.isEmpty) logs - key else logs + (key to log)
        return copy(done = if (complete) done + key else done - key, logs = newLogs)
    }

    fun clearLog(dayIndex: Int, exerciseIndex: Int) = copy(logs = logs - doneKey(dayIndex, exerciseIndex))

    fun postpone(dayIndex: Int) = copy(postpones = (postpones + dayIndex).sorted())

    fun undoPostpone(dayIndex: Int): StoredProgram {
        val i = postpones.indexOf(dayIndex)
        return if (i < 0) this else copy(postpones = postpones.toMutableList().apply { removeAt(i) })
    }

    fun postponeCount(dayIndex: Int) = postpones.count { it == dayIndex }

    fun withStartDate(date: LocalDate) = copy(startDate = date.toString())

    /** Plus grand index de jour ayant une trace (case cochée ou saisie). */
    fun lastTrackedIndex(): Int = (done.asSequence() + logs.keys.asSequence())
        .mapNotNull { it.substringBefore(':').toIntOrNull() }
        .maxOrNull() ?: -1

    companion object {
        fun doneKey(dayIndex: Int, exerciseIndex: Int) = "$dayIndex:$exerciseIndex"
    }
}
