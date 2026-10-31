package com.spacelenin.flemmefit.data

import android.util.AtomicFile
import android.util.Log
import com.spacelenin.flemmefit.model.AppState
import com.spacelenin.flemmefit.model.CustomExercise
import com.spacelenin.flemmefit.model.ExerciseLog
import com.spacelenin.flemmefit.model.Plan
import com.spacelenin.flemmefit.model.PlanParser
import com.spacelenin.flemmefit.model.ProgramSchedule
import com.spacelenin.flemmefit.model.StoredProgram
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** Source de vérité unique : l'état complet tient dans un petit fichier JSON écrit de façon atomique. */
class ProgramRepository(dir: File) {

    private val file = AtomicFile(File(dir, "state.json"))
    // Filet de sécurité : une erreur imprévue en arrière-plan est journalisée au lieu de faire planter l'appli.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> Log.e(TAG, "Erreur en arrière-plan", e) })
    private val writeLock = Mutex()

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private fun load(): AppState = try {
        if (!file.baseFile.exists()) AppState()
        else PlanParser.json.decodeFromString(AppState.serializer(), file.readFully().decodeToString())
    } catch (e: Exception) {
        Log.e(TAG, "Lecture de l'état impossible, sauvegarde mise de côté", e)
        file.baseFile.copyTo(File(file.baseFile.parentFile, "state.corrupt.json"), overwrite = true)
        AppState()
    }

    private fun mutate(transform: (AppState) -> AppState) {
        _state.update(transform)
        scope.launch {
            writeLock.withLock {
                // Un état plus récent a pu arriver entre-temps : on écrit toujours le dernier.
                val latest = _state.value
                val out = file.startWrite()
                try {
                    out.write(PlanParser.json.encodeToString(AppState.serializer(), latest).encodeToByteArray())
                    file.finishWrite(out)
                } catch (e: Exception) {
                    file.failWrite(out)
                    Log.e(TAG, "Écriture de l'état impossible", e)
                }
            }
        }
    }

    /** Remplace tout l'état par celui d'une sauvegarde, écrit sur le disque avant de rendre la main. */
    suspend fun restore(state: AppState) = writeLock.withLock {
        _state.value = state
        val out = file.startWrite()
        try {
            out.write(PlanParser.json.encodeToString(AppState.serializer(), state).encodeToByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    private fun updateProgram(id: String, transform: (StoredProgram) -> StoredProgram) = mutate { state ->
        state.copy(programs = state.programs.map { if (it.id == id) transform(it) else it })
    }

    fun addProgram(plan: Plan, start: LocalDate, tests: Map<String, Int> = emptyMap()): String {
        val id = UUID.randomUUID().toString()
        mutate { state ->
            val usedColors = state.programs.map { it.colorIndex }.toSet()
            val color = (0 until PROGRAM_COLOR_COUNT).firstOrNull { it !in usedColors }
                ?: (state.programs.size % PROGRAM_COLOR_COUNT)
            val program = StoredProgram(
                id = id,
                plan = plan,
                startDate = start.toString(),
                colorIndex = color,
                importedAt = System.currentTimeMillis(),
            ).withTests(tests)
            state.copy(programs = state.programs + program)
        }
        return id
    }

    fun deleteProgram(id: String) = mutate { state -> state.copy(programs = state.programs.filterNot { it.id == id }) }

    fun toggleExercise(id: String, dayIndex: Int, exerciseIndex: Int) =
        updateProgram(id) { it.toggle(dayIndex, exerciseIndex) }

    fun setSessionDone(id: String, dayIndex: Int, exerciseCount: Int, done: Boolean) =
        updateProgram(id) { it.setSessionDone(dayIndex, exerciseCount, done) }

    fun saveLog(id: String, dayIndex: Int, exerciseIndex: Int, log: ExerciseLog, complete: Boolean) =
        updateProgram(id) { it.saveLog(dayIndex, exerciseIndex, log, complete) }

    fun addExtra(id: String, date: LocalDate, text: String) = updateProgram(id) { it.withExtra(date, text) }

    fun removeExtra(id: String, date: LocalDate, index: Int) = updateProgram(id) { it.withoutExtra(date, index) }

    fun setFeeling(id: String, dayIndex: Int, feeling: Int) = updateProgram(id) { it.withFeeling(dayIndex, feeling) }

    fun setExerciseFeeling(id: String, dayIndex: Int, exerciseIndex: Int, feeling: Int?) =
        updateProgram(id) { it.withExerciseFeeling(dayIndex, exerciseIndex, feeling) }

    fun setWeights(id: String, dayIndex: Int, exerciseIndex: Int, on: Boolean) =
        updateProgram(id) { it.withWeights(dayIndex, exerciseIndex, on) }

    fun skipVariant(id: String, dayIndex: Int) = updateProgram(id) { it.skipVariant(dayIndex) }

    fun addCustom(id: String, exercise: CustomExercise) = updateProgram(id) { it.addCustom(exercise) }

    fun updateCustom(id: String, exerciseId: String, transform: (CustomExercise) -> CustomExercise) =
        updateProgram(id) { it.updateCustom(exerciseId, transform) }

    fun burst(id: String, dayIndex: Int) = updateProgram(id) { it.burst(dayIndex) }

    fun undoBurst(id: String, dayIndex: Int) = updateProgram(id) { it.undoBurst(dayIndex) }

    fun chill(id: String, dayIndex: Int) = updateProgram(id) { it.chill(dayIndex) }

    fun undoChill(id: String, dayIndex: Int) = updateProgram(id) { it.undoChill(dayIndex) }

    fun postpone(id: String, dayIndex: Int) = updateProgram(id) { it.postpone(dayIndex) }

    fun undoPostpone(id: String, dayIndex: Int) = updateProgram(id) { it.undoPostpone(dayIndex) }

    fun changeStartDate(id: String, date: LocalDate) = updateProgram(id) { it.withStartDate(date) }

    /** Nouvelle version de la routine, suivi conservé. */
    fun replacePlan(id: String, plan: Plan) = updateProgram(id) { it.withPlan(plan) }

    /** Valide les exos notés en entier que l'ancienne règle laissait incomplets (voir [ProgramSchedule.revalidated]). */
    fun revalidate(id: String, today: LocalDate) {
        val program = state.value.programs.find { it.id == id } ?: return
        val added = ProgramSchedule.revalidated(program, today).done - program.done
        if (added.isNotEmpty()) updateProgram(id) { it.copy(done = it.done + added) }
    }

    fun setColor(id: String, colorIndex: Int) = updateProgram(id) { it.copy(colorIndex = colorIndex) }

    fun changeTestOption(id: String, testId: String, option: String, result: Int?) =
        updateProgram(id) { it.changeTestOption(testId, option, LocalDate.now(), result) }

    fun setTests(id: String, results: Map<String, Int>) = updateProgram(id) { it.withTests(results) }

    companion object {
        private const val TAG = "ProgramRepository"
        const val PROGRAM_COLOR_COUNT = 6
    }
}
