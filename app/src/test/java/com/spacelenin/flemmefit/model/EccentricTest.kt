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

class EccentricFollowUpTest {
    private val start = LocalDate.of(2026, 10, 19)
    private val pushups = Exercise("Pompes", sets = 3, reps = "8", progression = Progression(reps = 1, every = 2), eccentric = "Lentement.")
    private val abs = Exercise("Abdos", sets = 3, reps = "10", progression = Progression(reps = 1, every = 2), eccentric = "Lentement.")
    private fun plan(vararg ex: Exercise) = Plan(
        version = 1, name = "P", repeat = REPEAT_FOREVER, deloadEvery = 0, boss = false,
        weeks = listOf(PlanWeek(days = listOf(PlanDay(1, exercises = ex.toList())))),
    )

    @Test
    fun `eccentric marks and one-day replacements follow their exercise when the routine changes order`() {
        val before = StoredProgram("p", plan(pushups, abs), start.toString())
            .toggle(0, 1).withEccentric(0, 1, true)
            .copy(dayReplacements = mapOf(StoredProgram.doneKey(0, 1) to "x"))
        val after = before.withPlan(plan(abs, pushups))
        assertTrue(after.isEccentric(0, 0))
        assertFalse(after.isEccentric(0, 1))
        assertEquals(setOf(StoredProgram.doneKey(0, 0)), after.dayReplacements.keys)
    }

    @Test
    fun `an eccentric exercise left undone counts as missed, like any other`() {
        val p = plan(pushups)
        fun reps(program: StoredProgram) = ProgramSchedule(program, start.plusDays(60)).sessionAt(28)!!.prescriptions[0].reps
        var missed = StoredProgram("p", p, start.toString())
        var flagged = missed
        for (w in 0 until 4) {
            missed = missed.saveLog(w * 7, 0, ExerciseLog.ofSets(listOf(3, 0, 0), false), complete = false)
            flagged = flagged.saveLog(w * 7, 0, ExerciseLog.ofSets(listOf(3, 0, 0), false), complete = false)
        }
        // Rien noté du tout sur une séance cochée excentrique : le réglage la voit comme ratée.
        val idle = StoredProgram("p", p, start.toString())
        val idleFlagged = (0 until 4).fold(idle) { acc, w -> acc.withEccentric(w * 7, 0, true) }
        assertEquals(reps(idle), reps(idleFlagged))
        assertTrue(reps(missed)!! <= 8)
    }

    @Test
    fun `friend card extras are short and dropped first`() {
        val e = SharedExercise("Pompes", version = SharedExercise.VERSION_ECCENTRIC, level = 2, levelCount = 7, feeling = 0, dosePercent = 80, weighted = true)
        val json = kotlinx.serialization.json.Json.encodeToString(SharedExercise.serializer(), e)
        assertTrue(json, "\"v\":\"x\"" in json && "\"lv\":2" in json)
        assertEquals(SharedExercise("Pompes"), e.bare())
    }
}
