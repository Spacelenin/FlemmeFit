package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PerSetTest {

    private val start = LocalDate.of(2026, 10, 19)
    private val plan = Plan(
        version = 1, name = "Séries",
        weeks = listOf(
            PlanWeek(
                days = listOf(
                    PlanDay(1, exercises = listOf(Exercise("Pompes", sets = 3, reps = "10", amrap = true), Exercise("Gainage", sets = 2, duration = "30s"))),
                ),
            ),
        ),
    )

    private fun prescriptions() = ProgramSchedule(StoredProgram("p", plan, start.toString()), start).sessionAt(0)!!.prescriptions

    @Test
    fun `a per-set log is complete only when every planned set reaches the target`() {
        val pushups = prescriptions()[0]
        assertTrue(pushups.isMetBy(ExerciseLog.ofSets(listOf(10, 10, 14), allOut = true)))
        assertFalse(pushups.isMetBy(ExerciseLog.ofSets(listOf(10, 9, 14), allOut = true)))
        assertFalse(pushups.isMetBy(ExerciseLog.ofSets(listOf(10, 10, 0), allOut = true)))
        val plank = prescriptions()[1]
        assertTrue(plank.isMetBy(ExerciseLog.ofSets(listOf(30, 35), allOut = false)))
        assertFalse(plank.isMetBy(ExerciseLog.ofSets(listOf(30, 20), allOut = false)))
    }

    @Test
    fun `the last set of an all-out exercise becomes the max`() {
        val log = ExerciseLog.ofSets(listOf(10, 9, 14), allOut = true)
        assertEquals(14, log.max)
        assertEquals(3, log.sets)
        assertEquals(null, ExerciseLog.ofSets(listOf(10, 9), allOut = false).max)
    }

    @Test
    fun `stats sum the sets actually done`() {
        val program = StoredProgram("p", plan, start.toString())
            .saveLog(0, 0, ExerciseLog.ofSets(listOf(10, 8, 0), allOut = true), complete = false)
            .saveLog(0, 1, ExerciseLog.ofSets(listOf(30, 25), allOut = false), complete = false)
        val stats = Stats.of(ProgramSchedule(program, start.plusDays(1)))
        val pushups = stats.exercises.first { it.key == "pompes" }
        assertEquals(18, pushups.totalReps)
        assertEquals(10, pushups.bestReps)
        assertEquals(55, stats.exercises.first { it.key == "gainage" }.totalSeconds)
    }
}
