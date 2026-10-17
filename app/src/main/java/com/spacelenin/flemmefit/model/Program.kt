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
 * Un programme importé et son suivi.
 *
 * Les jours du programme sont numérotés de 0 à [ProgramSchedule.totalDays] - 1, répétitions comprises
 * (« index de jour »). Chaque entrée de [postpones] insère un jour de décalage juste avant ce jour,
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
    /** Exercices cochés, clés produites par [doneKey]. */
    val done: Set<String> = emptySet(),
) {
    val start: LocalDate get() = LocalDate.parse(startDate)

    fun isDone(dayIndex: Int, exerciseIndex: Int) = doneKey(dayIndex, exerciseIndex) in done

    fun toggle(dayIndex: Int, exerciseIndex: Int): StoredProgram {
        val key = doneKey(dayIndex, exerciseIndex)
        return copy(done = if (key in done) done - key else done + key)
    }

    fun setSessionDone(dayIndex: Int, exerciseCount: Int, value: Boolean): StoredProgram {
        val keys = (0 until exerciseCount).map { doneKey(dayIndex, it) }
        return copy(done = if (value) done + keys else done - keys.toSet())
    }

    fun postpone(dayIndex: Int) = copy(postpones = (postpones + dayIndex).sorted())

    fun undoPostpone(dayIndex: Int): StoredProgram {
        val i = postpones.indexOf(dayIndex)
        return if (i < 0) this else copy(postpones = postpones.toMutableList().apply { removeAt(i) })
    }

    fun postponeCount(dayIndex: Int) = postpones.count { it == dayIndex }

    fun withStartDate(date: LocalDate) = copy(startDate = date.toString())

    companion object {
        fun doneKey(dayIndex: Int, exerciseIndex: Int) = "$dayIndex:$exerciseIndex"
    }
}
