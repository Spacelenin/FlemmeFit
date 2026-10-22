package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BurstTest {

    private val start = LocalDate.of(2026, 10, 19) // lundi
    private val plan = Plan(
        version = 1, name = "Burst", repeat = REPEAT_FOREVER, deloadEvery = 0,
        weeks = listOf(
            PlanWeek(
                days = listOf(
                    PlanDay(1, exercises = listOf(Exercise("Pompes", sets = 3, reps = "10", progression = Progression(reps = 1)), Exercise("Shadow", sets = 2, duration = "3min"))),
                    PlanDay(3, exercises = listOf(Exercise("Pompes", sets = 3, reps = "10", progression = Progression(reps = 1)))),
                ),
            ),
        ),
    )

    @Test
    fun `a burst adds progression steps, or a set without progression`() {
        val program = StoredProgram("p", plan, start.toString()).burst(0)
        val s = ProgramSchedule(program, start).sessionAt(0)!!
        assertTrue(s.isBurst)
        assertEquals(10 + BURST_STEPS, s.prescriptions[0].reps)
        assertEquals(3, s.prescriptions[1].sets)
    }

    @Test
    fun `one burst per week, only today and before anything is checked`() {
        val fresh = StoredProgram("p", plan, start.toString())
        val today = ProgramSchedule(fresh, start)
        assertTrue(today.canBurst(today.sessionAt(0)!!))
        assertFalse(today.canBurst(today.sessionAt(2)!!)) // pas aujourd'hui

        val used = fresh.burst(0)
        val laterSameWeek = ProgramSchedule(used, start.plusDays(2))
        assertFalse(laterSameWeek.canBurst(laterSameWeek.sessionAt(2)!!))
        val nextWeek = ProgramSchedule(used, start.plusDays(7))
        assertTrue(nextWeek.canBurst(nextWeek.sessionAt(7)!!))

        val started = ProgramSchedule(fresh.toggle(0, 1), start)
        assertFalse(started.canBurst(started.sessionAt(0)!!))
    }

    @Test
    fun `future sessions are locked`() {
        val schedule = ProgramSchedule(StoredProgram("p", plan, start.toString()), start)
        assertFalse(schedule.isLocked(schedule.sessionAt(0)!!))
        assertTrue(schedule.isLocked(schedule.sessionAt(2)!!))
    }
}
