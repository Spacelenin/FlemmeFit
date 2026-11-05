package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CelebrationTest {
    private val pushups = Exercise("Pompes", id = "pompes", sets = 3, reps = "10", amrap = true)
    private val allOut = Prescription.of(pushups, 0, false, false, null)
    private val plain = Prescription.of(pushups.copy(amrap = false), 0, false, false, null)

    @Test
    fun `an all-out set celebrates an exploit, else a beaten record`() {
        assertEquals(Celebration.EXPLOIT, Celebration.of(allOut, ExerciseLog.ofSets(listOf(10, 10, 25), allOut = true), previousBest = 12))
        assertEquals(Celebration.RECORD, Celebration.of(allOut, ExerciseLog.ofSets(listOf(10, 10, 14), allOut = true), previousBest = 12))
        assertNull(Celebration.of(allOut, ExerciseLog.ofSets(listOf(10, 10, 12), allOut = true), previousBest = 12))
        // Première fois : pas de record à battre.
        assertNull(Celebration.of(allOut, ExerciseLog.ofSets(listOf(10, 10, 14), allOut = true), previousBest = null))
        // Pas de série à fond : rien à fêter.
        assertNull(Celebration.of(plain, ExerciseLog.ofSets(listOf(10, 10, 25), allOut = false), previousBest = 12))
    }

    @Test
    fun `the schedule knows the best set before each session`() {
        val start = java.time.LocalDate.of(2026, 11, 2)
        val plan = Plan(
            version = 1, name = "R", repeat = REPEAT_FOREVER, deloadEvery = 0, boss = false,
            weeks = listOf(PlanWeek(days = listOf(PlanDay(1, exercises = listOf(pushups))))),
        )
        val p = StoredProgram("p", plan, start.toString())
            .saveLog(0, 0, ExerciseLog.ofSets(listOf(10, 10, 13), allOut = true), complete = true)
            .saveLog(7, 0, ExerciseLog.ofSets(listOf(10, 10, 15), allOut = true), complete = true)
        val s = ProgramSchedule(p, start.plusDays(14))
        assertNull(s.previousBest(s.sessionAt(0)!!, 0))
        assertEquals(13, s.previousBest(s.sessionAt(7)!!, 0))
        assertEquals(15, s.previousBest(s.sessionAt(14)!!, 0))
    }
}
