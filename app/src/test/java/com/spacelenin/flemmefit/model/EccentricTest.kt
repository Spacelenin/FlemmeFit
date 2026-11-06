package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EccentricTest {

    private val start = LocalDate.of(2026, 10, 19)

    private val pushups = Exercise(
        "Pompes", sets = 3, reps = "8", amrap = true, progression = Progression(reps = 1, every = 2),
        eccentric = "Descends le plus lentement possible.",
    )

    /** Pompes les lundis et jeudis, sans décharge. */
    private val plan = Plan(
        version = 1, name = "P", repeat = REPEAT_FOREVER, deloadEvery = 0, boss = false,
        weeks = listOf(PlanWeek(days = listOf(PlanDay(1, exercises = listOf(pushups)), PlanDay(4, exercises = listOf(pushups))))),
    )

    private fun day(n: Int) = n / 2 * 7 + if (n % 2 == 0) 0 else 3

    private fun session(p: StoredProgram, n: Int) = ProgramSchedule(p, start.plusDays(100)).sessionAt(day(n))!!

    private fun StoredProgram.sets(n: Int, vararg values: Int) = saveLog(day(n), 0, ExerciseLog.ofSets(values.toList(), false), complete = true)

    @Test
    fun `eccentric sessions count as done but leave the adaptation and the base curve alone`() {
        var normal = StoredProgram("p", plan, start.toString())
        var eccentric = normal
        repeat(4) { n ->
            normal = normal.toggle(day(n), 0)
            eccentric = eccentric.sets(n, 20, 20, 20).withEccentric(day(n), 0, true)
        }
        // La courbe de base avance avec les séances validées en normal…
        assertTrue(session(normal, 4).prescriptions[0].reps!! > 8)
        // … pas en excentrique, même avec de grosses séries notées.
        assertEquals(8, session(eccentric, 4).prescriptions[0].reps)
        val s = session(eccentric, 0)
        assertTrue(s.prescriptions[0].eccentric)
        assertTrue(ProgramSchedule(eccentric, start.plusDays(100)).isComplete(s))
    }

    @Test
    fun `no exploit nor record in eccentric`() {
        val p = StoredProgram("p", plan, start.toString()).sets(0, 30, 30, 30).withEccentric(day(0), 0, true)
        val pr = session(p, 0).prescriptions[0]
        assertNull(pr.exploitPlanned)
        assertNull(Celebration.of(pr.copy(allOut = true), ExerciseLog.ofSets(listOf(30, 30, 30), false), previousBest = 5))
        val stats = Stats.of(ProgramSchedule(p, start.plusDays(100))).exercises.first()
        assertNull(stats.bestReps)
        assertEquals(90, stats.totalReps)
        assertTrue(Stats.of(ProgramSchedule(p, start.plusDays(100))).exploits.isEmpty())
    }

    @Test
    fun `exercises without an eccentric version ignore the flag`() {
        val plain = plan.copy(weeks = listOf(PlanWeek(days = listOf(PlanDay(1, exercises = listOf(pushups.copy(eccentric = null)))))))
        val p = StoredProgram("p", plain, start.toString()).withEccentric(0, 0, true)
        assertFalse(ProgramSchedule(p, start.plusDays(100)).sessionAt(0)!!.prescriptions[0].eccentric)
    }

    @Test
    fun `the routine has eccentric versions on its rep exercises only`() {
        val file = listOf("../examples/routine-maison.json", "examples/routine-maison.json").map { java.io.File(it) }.first { it.exists() }
        val routine = (PlanParser.parse(file.readText()) as ParseResult.Success).plan
        val withIt = routine.weeks.flatMap { w -> w.days.flatMap { it.exercises } }.filter { it.eccentric != null }.map { it.key }.toSet()
        assertEquals(11, withIt.size)
        assertFalse("gainage" in withIt)
        assertFalse("beatsaber" in withIt)
        assertTrue("tractions-table" in withIt)
        // Les négatives ne sont plus un palier des tractions : c'est le bouton excentrique.
        val tractions = routine.weeks.flatMap { w -> w.days.flatMap { it.exercises } }.first { it.key == "tractions-table" }
        assertTrue(tractions.levels.none { it.note?.contains("négative") == true })
    }
}

class RoutineExercisesTest {
    @Test
    fun `the full list holds every adjustable exercise, done or not, with its days`() {
        val file = listOf("../examples/routine-maison.json", "examples/routine-maison.json").map { java.io.File(it) }.first { it.exists() }
        val routine = (PlanParser.parse(file.readText()) as ParseResult.Success).plan
        val start = LocalDate.of(2026, 11, 2) // un lundi
        val list = ProgramSchedule(StoredProgram("p", routine, start.toString()), start).routineExercises()
        val keys = list.map { it.key }
        assertTrue("squats-classiques" in keys)
        assertFalse("echauffement" in keys)
        assertFalse("beatsaber" in keys)
        assertEquals(listOf(java.time.DayOfWeek.SATURDAY), list.first { it.key == "squats-classiques" }.weekdays)
        assertEquals(keys.distinct(), keys)
    }
}
