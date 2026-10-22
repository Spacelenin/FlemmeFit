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
    /** Détail série par série (répétitions, ou secondes pour un exercice en durée) ; 0 = série pas faite. */
    val perSet: List<Int>? = null,
) {
    val isEmpty get() = sets == null && reps == null && seconds == null && max == null && perSet == null

    companion object {
        /** Saisie série par série : les séries faites, et la dernière comme max si elle était à fond. */
        fun ofSets(values: List<Int>, allOut: Boolean) = ExerciseLog(
            sets = values.count { it > 0 },
            max = values.lastOrNull()?.takeIf { allOut && it > 0 },
            perSet = values,
        )
    }
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
    /** Résultats des tests de départ ([Plan.tests]), par id de test. */
    val tests: Map<String, Int> = emptyMap(),
    /** Jours (index) passés en Burst : séance renforcée, une par semaine au plus. */
    val bursts: List<Int> = emptyList(),
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

    fun burst(dayIndex: Int) = if (dayIndex in bursts) this else copy(bursts = (bursts + dayIndex).sorted())

    fun undoBurst(dayIndex: Int) = copy(bursts = bursts - dayIndex)

    fun postpone(dayIndex: Int) = copy(postpones = (postpones + dayIndex).sorted())

    fun undoPostpone(dayIndex: Int): StoredProgram {
        val i = postpones.indexOf(dayIndex)
        return if (i < 0) this else copy(postpones = postpones.toMutableList().apply { removeAt(i) })
    }

    fun postponeCount(dayIndex: Int) = postpones.count { it == dayIndex }

    fun withStartDate(date: LocalDate) = copy(startDate = date.toString())

    /**
     * Remplace la routine en gardant le suivi : chaque case cochée ou saisie suit son exercice
     * (même clé, même jour du calendrier), même si l'ordre des exercices a changé.
     * Ce qui ne correspond plus à rien dans la nouvelle routine est perdu (voir [planUpdateLoss]).
     */
    fun withPlan(newPlan: Plan): StoredProgram {
        val moved = remap(newPlan)
        return copy(
            plan = newPlan,
            done = done.mapNotNull { moved[it] }.toSet(),
            logs = logs.mapNotNull { (k, v) -> moved[k]?.let { it to v } }.toMap(),
            tests = tests.filterKeys { id -> newPlan.tests.any { it.id == id } },
        )
    }

    /** Nombre de cases cochées ou saisies qui ne trouveraient plus leur exercice dans [newPlan]. */
    fun planUpdateLoss(newPlan: Plan): Int {
        val moved = remap(newPlan)
        return (done + logs.keys).count { it !in moved }
    }

    /** Ancienne clé de suivi → nouvelle clé, pour les exercices retrouvés dans [newPlan]. */
    private fun remap(newPlan: Plan): Map<String, String> {
        val result = HashMap<String, String>()
        (done + logs.keys).mapNotNull { key ->
            val day = key.substringBefore(':').toIntOrNull() ?: return@mapNotNull null
            val ex = key.substringAfter(':').toIntOrNull() ?: return@mapNotNull null
            Triple(key, day, ex)
        }.groupBy { it.second }.forEach { (dayIndex, entries) ->
            val oldExercises = plan.dayAt(dayIndex)?.exercises ?: return@forEach
            val newExercises = newPlan.dayAt(dayIndex)?.exercises ?: return@forEach
            // Un même exercice peut apparaître deux fois dans une séance : on les apparie dans l'ordre.
            val used = HashSet<Int>()
            for (e in oldExercises.indices) {
                val key = oldExercises[e].key
                val target = newExercises.indices.firstOrNull { it !in used && newExercises[it].key == key } ?: continue
                used += target
                entries.firstOrNull { it.third == e }?.let { result[it.first] = doneKey(dayIndex, target) }
            }
        }
        return result
    }

    fun withTests(results: Map<String, Int>) = copy(tests = results.filterKeys { id -> plan.tests.any { it.id == id } })

    /** Tests de départ pas encore renseignés. */
    val missingTests: List<PlanTest> get() = plan.tests.filter { it.id !in tests }

    /** Plus grand index de jour ayant une trace (case cochée ou saisie). */
    fun lastTrackedIndex(): Int = (done.asSequence() + logs.keys.asSequence())
        .mapNotNull { it.substringBefore(':').toIntOrNull() }
        .maxOrNull() ?: -1

    companion object {
        fun doneKey(dayIndex: Int, exerciseIndex: Int) = "$dayIndex:$exerciseIndex"
    }
}
