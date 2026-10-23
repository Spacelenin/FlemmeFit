package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TestChoiceTest {

    private val start = LocalDate.of(2026, 10, 19)

    private val abs = PlanTest(
        "abdos", "Crunchs ou sit-ups",
        options = listOf(TestOption("crunch", "Crunchs", "crunch"), TestOption("situp", "Sit-ups", "sit-up")),
    )
    private val crunches = Exercise(
        "Abdos ({abdos})", id = "abdos", test = "abdos", sets = 3,
        levels = listOf(Level(0, reps = "5"), Level(20, reps = "10"), Level(40, reps = "20")),
    )
    private val plan = Plan(
        version = 1, name = "Abdos", repeat = REPEAT_FOREVER, boss = false, deloadEvery = 0, tests = listOf(abs),
        weeks = listOf(PlanWeek(days = (1..7).map { PlanDay(it, exercises = listOf(crunches)) })),
    )

    @Test
    fun `names follow the choice, or show both until one is made`() {
        var program = StoredProgram("p", plan, start.toString(), tests = mapOf("abdos" to 45))
        assertEquals("Abdos (crunch ou sit-up)", ProgramSchedule(program, start).sessionAt(0)!!.prescriptions[0].exercise.name)
        assertEquals("Crunchs ou sit-ups", program.testName(abs, start))

        // Le premier choix vaut depuis le début.
        program = program.changeTestOption("abdos", "crunch", start.plusDays(5))
        assertEquals("crunch", program.optionOn("abdos", start))
        assertEquals("Abdos (crunch)", ProgramSchedule(program, start).sessionAt(0)!!.prescriptions[0].exercise.name)
        assertEquals("Crunchs", program.testName(abs, start))
    }

    @Test
    fun `switching with a new max recalibrates from that day, not before`() {
        val switchDay = start.plusDays(10)
        val program = StoredProgram("p", plan, start.toString(), tests = mapOf("abdos" to 45))
            .changeTestOption("abdos", "crunch", start)
            .changeTestOption("abdos", "situp", switchDay, result = 25)
        val s = ProgramSchedule(program, start.plusDays(30))
        assertEquals(20, s.sessionAt(9)!!.prescriptions[0].reps)
        assertEquals("Abdos (crunch)", s.sessionAt(9)!!.prescriptions[0].exercise.name)
        assertEquals(10, s.sessionAt(10)!!.prescriptions[0].reps)
        assertEquals("Abdos (sit-up)", s.sessionAt(10)!!.prescriptions[0].exercise.name)

        val progress = s.testProgress().single()
        assertEquals(listOf(45, 25), progress.points.map { it.value })
        assertEquals(listOf(1), progress.switches)
        // Départ et record ne comparent que les sit-ups.
        assertEquals(25, progress.first)
        assertEquals(25, progress.best)
        assertEquals(switchDay, progress.switchedAt!!.date)
        assertEquals("Sit-ups", progress.name)
    }

    @Test
    fun `switching without a max keeps the current level`() {
        val program = StoredProgram("p", plan, start.toString(), tests = mapOf("abdos" to 45))
            .changeTestOption("abdos", "crunch", start)
            .changeTestOption("abdos", "situp", start.plusDays(10))
        val s = ProgramSchedule(program, start.plusDays(30))
        assertEquals(20, s.sessionAt(15)!!.prescriptions[0].reps)
        assertNull(s.testProgress().single().switchedAt)
        // Choisir de nouveau la même chose ne change rien.
        assertEquals(program, program.changeTestOption("abdos", "situp", start.plusDays(20)))
    }
}
