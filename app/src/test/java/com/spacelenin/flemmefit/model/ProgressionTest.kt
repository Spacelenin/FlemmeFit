package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressionTest {

    private val start = LocalDate.of(2026, 10, 19)

    private val pushups = Exercise(
        name = "Pompes",
        sets = 3,
        reps = "8",
        progression = Progression(reps = 1, maxReps = 10, addSets = 1, maxSets = 4),
    )

    private fun at(level: Int, deload: Boolean = false, ex: Exercise = pushups) =
        Prescription.of(ex, level, deload, allOut = false, lastMax = null)

    @Test
    fun `double progression adds reps then a set`() {
        assertEquals("3 × 8", at(0).summary())
        assertEquals("3 × 10", at(2).summary())
        assertEquals("4 × 8", at(3).summary())
        assertEquals("4 × 10", at(5).summary())
        assertEquals("4 × 10", at(9).summary()) // plafond séries + reps atteint
    }

    @Test
    fun `duration and load progress`() {
        val plank = Exercise("Gainage", sets = 2, duration = "30s", progression = Progression(duration = "5s", maxDuration = "40s", addSets = 1))
        assertEquals("2 × 30s", at(0, ex = plank).summary())
        assertEquals("2 × 40s", at(2, ex = plank).summary())
        assertEquals("3 × 30s", at(3, ex = plank).summary())
        val row = Exercise("Rowing", sets = 3, reps = "12", load = "8 kg", progression = Progression(load = "1kg", maxLoad = "9.5kg"))
        assertEquals("3 × 12 · 10 kg".replace("10", "9,5"), at(5, ex = row).summary())
    }

    @Test
    fun `deload halves sets or reps`() {
        assertEquals("2 × 8", at(0, deload = true).summary())
        val single = Exercise("Gainage", sets = 1, duration = "45s")
        assertEquals("1 × 23s", at(0, deload = true, ex = single).summary())
    }

    @Test
    fun `free text is kept as is`() {
        val ex = Exercise("Pompes", reps = "max", duration = "1min30")
        val p = at(4, ex = ex)
        assertEquals("max reps · 1min30", p.summary())
        assertNull(p.reps)
        assertEquals(90, p.seconds)
    }

    @Test
    fun `met by log`() {
        val p = at(0)
        assertTrue(p.isMetBy(ExerciseLog(max = 12)))
        assertFalse(p.isMetBy(ExerciseLog(reps = 7)))
        assertFalse(p.isMetBy(ExerciseLog(sets = 2)))
    }

    private fun plan(boss: Boolean = false, deloadEvery: Int? = null, basis: String = Progression.BASIS_DONE) = Plan(
        version = 1, name = "P", repeat = REPEAT_FOREVER, boss = boss, deloadEvery = deloadEvery,
        weeks = listOf(
            PlanWeek(
                days = listOf(
                    PlanDay(1, exercises = listOf(pushups.copy(progression = pushups.progression!!.copy(basis = basis)))),
                    PlanDay(4, exercises = listOf(pushups.copy(progression = pushups.progression!!.copy(basis = basis)))),
                ),
            ),
        ),
    )

    @Test
    fun `done basis progresses only on validated sessions shared by key`() {
        var program = StoredProgram("id", plan(), start.toString())
        program = program.toggle(0, 0).toggle(3, 0) // semaine 1 : les deux séances faites
        val s = ProgramSchedule(program, start.plusDays(20))
        assertEquals(8, s.sessionAt(0)!!.prescriptions[0].reps)
        assertEquals(9, s.sessionAt(3)!!.prescriptions[0].reps)
        assertEquals(10, s.sessionAt(7)!!.prescriptions[0].reps)
        assertEquals(10, s.sessionAt(10)!!.prescriptions[0].reps) // semaine 2 ratée : pas de progression
    }

    @Test
    fun `week basis progresses with the calendar and skips deload weeks`() {
        val s = ProgramSchedule(StoredProgram("id", plan(deloadEvery = 4, basis = Progression.BASIS_WEEK), start.toString()), start)
        assertEquals(listOf(8, 9, 10), (0..2).map { s.sessionAt(it * 7)!!.prescriptions[0].reps })
        val deload = s.sessionAt(21)!!
        assertTrue(deload.isDeload)
        assertEquals(2, deload.prescriptions[0].sets) // 3 séries → 2 en décharge
        assertEquals("4 × 8", s.sessionAt(28)!!.prescriptions[0].summary()) // reprend après la décharge
    }

    @Test
    fun `default deload only with progression`() {
        assertEquals(4, plan().effectiveDeloadEvery)
        assertEquals(0, plan().copy(weeks = listOf(PlanWeek(days = listOf(PlanDay(1, exercises = listOf(Exercise("A"))))))).effectiveDeloadEvery)
        assertEquals(0, plan(deloadEvery = 0).effectiveDeloadEvery)
    }

    @Test
    fun `big max speeds progression up and low max slows it`() {
        val base = StoredProgram("id", plan(), start.toString())
        val strong = base.saveLog(0, 0, ExerciseLog(max = 13), complete = true) // prévu 8, +5 ≥ 3 pas
        assertEquals(10, ProgramSchedule(strong, start.plusDays(10)).sessionAt(3)!!.prescriptions[0].reps)
        val weak = base.saveLog(0, 0, ExerciseLog(max = 5), complete = true)
        assertEquals(8, ProgramSchedule(weak, start.plusDays(10)).sessionAt(3)!!.prescriptions[0].reps)
    }

    @Test
    fun `boss days come every 3 to 4 weeks, are reproducible and revealed the day before`() {
        val program = StoredProgram("boss-id", plan(boss = true), start.toString())
        val today = start.plusDays(7L * 60)
        val s = ProgramSchedule(program, today)
        val bosses = s.sessionsThrough(7 * 60 - 1).filter { it.isBoss }
        val weeks = bosses.map { it.programWeek }
        assertTrue(weeks.toString(), weeks.first() in 2..4)
        weeks.zipWithNext().forEach { (a, b) -> assertTrue(weeks.toString(), b - a in 3..5) }
        bosses.forEach { assertTrue(s.isDeloadWeek(it.programWeek).not()) }
        assertEquals(weeks, ProgramSchedule(program, today).sessionsThrough(7 * 60 - 1).filter { it.isBoss }.map { it.programWeek })

        val boss = bosses.first()
        assertTrue(boss.prescriptions[0].allOut)
        assertEquals(boss.prescriptions[0].reps, boss.prescriptions[0].target) // sans max noté : objectif = reps prévues

        val before = ProgramSchedule(program, boss.date.minusDays(2)).sessionAt(boss.index)!!
        assertFalse(before.isBoss)
        assertTrue(ProgramSchedule(program, boss.date.minusDays(1)).sessionAt(boss.index)!!.isBoss)
    }

    @Test
    fun `boss target is last max plus one`() {
        val program = StoredProgram("boss-id", plan(boss = true), start.toString())
            .saveLog(0, 0, ExerciseLog(max = 11), complete = true)
        val s = ProgramSchedule(program, start.plusDays(7L * 10))
        val boss = s.sessionsThrough(7 * 10).first { it.isBoss }
        assertEquals(12, boss.prescriptions[0].target)
    }

    @Test
    fun `measures parsing`() {
        assertEquals(45, Measures.seconds("45s"))
        assertEquals(45, Measures.seconds("45"))
        assertEquals(120, Measures.seconds("2min"))
        assertEquals(90, Measures.seconds("1min30"))
        assertEquals(90, Measures.seconds("1m30s"))
        assertEquals(90, Measures.seconds("1:30"))
        assertNull(Measures.seconds("max"))
        assertEquals("1min30", Measures.formatSeconds(90))
        assertEquals(Measures.Load(2.5, "kg"), Measures.load("2,5 kg"))
        assertNull(Measures.load("élastique rouge"))
        assertEquals(12, Measures.reps("12"))
        assertNull(Measures.reps("8-10"))
    }
}
