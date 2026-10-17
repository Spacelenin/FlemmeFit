package com.spacelenin.flemmefit.data

import android.util.AtomicFile
import android.util.Log
import com.spacelenin.flemmefit.model.AppState
import com.spacelenin.flemmefit.model.ExerciseLog
import com.spacelenin.flemmefit.model.Plan
import com.spacelenin.flemmefit.model.PlanParser
import com.spacelenin.flemmefit.model.StoredProgram
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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

    private fun updateProgram(id: String, transform: (StoredProgram) -> StoredProgram) = mutate { state ->
        state.copy(programs = state.programs.map { if (it.id == id) transform(it) else it })
    }

    fun addProgram(plan: Plan, start: LocalDate): String {
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
            )
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

    fun postpone(id: String, dayIndex: Int) = updateProgram(id) { it.postpone(dayIndex) }

    fun undoPostpone(id: String, dayIndex: Int) = updateProgram(id) { it.undoPostpone(dayIndex) }

    fun changeStartDate(id: String, date: LocalDate) = updateProgram(id) { it.withStartDate(date) }

    companion object {
        private const val TAG = "ProgramRepository"
        const val PROGRAM_COLOR_COUNT = 6
    }
}
