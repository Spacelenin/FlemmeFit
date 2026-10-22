package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TestProgressTest {

    private val start = LocalDate.of(2026, 10, 19)

    private val pushups = Exercise(
        "Pompes", test = "pompes",
        levels = listOf(Level(0, sets = 5, reps = "2"), Level(6, sets = 5, reps = "5"), Level(11, sets = 5, reps = "8")),
        progression = Progression(reps = 1),
    )

    private val plan = Plan(
        version = 1, name = "Test", repeat = REPEAT_FOREVER, boss = false, deloadEvery = 0,
        tests = listOf(PlanTest("pompes", "Max de pompes")),
        weeks = listOf(
            PlanWeek(days = listOf(PlanDay(1, exercises = listOf(pushups)))),
            PlanWeek(days = listOf(PlanDay(1, exercises = listOf(Exercise("Test pompes", test = "pompes", retest = true))))),
        ),
    )

    @Test
    fun `the curve starts from the first test and follows each retest`() {
        val program = StoredProgram("p", plan, start.toString(), tests = mapOf("pompes" to 7))
            .saveLog(7, 0, ExerciseLog(max = 10), complete = true)
        val progress = ProgramSchedule(program, start.plusDays(10)).testProgress().single()
        assertEquals(listOf(TestPoint(start, 7), TestPoint(start.plusDays(7), 10)), progress.points)
        assertEquals(10, progress.current)
        assertEquals(7, progress.first)
        // Prochain test : semaine 4 du cycle de deux semaines.
        assertEquals(start.plusDays(21), progress.nextDate)
        val goal = progress.goals.single()
        assertEquals(1, goal.level)
        assertEquals(11, goal.nextFrom)
        assertEquals("5 × 8", goal.nextLabel)
        assertEquals(1, progress.toNextLevel)
    }

    @Test
    fun `nothing left to reach at the top level`() {
        val program = StoredProgram("p", plan, start.toString(), tests = mapOf("pompes" to 15))
        val progress = ProgramSchedule(program, start).testProgress().single()
        assertNull(progress.goals.single().nextFrom)
        assertNull(progress.toNextLevel)
        assertEquals(start.plusDays(7), progress.nextDate)
    }

    @Test
    fun `no tests, no curves`() {
        val program = StoredProgram("p", plan.copy(tests = emptyList()), start.toString())
        assertEquals(emptyList<TestProgress>(), ProgramSchedule(program, start).testProgress())
    }
}
