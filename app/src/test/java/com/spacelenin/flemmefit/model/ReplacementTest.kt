package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReplacementTest {

    private val start = LocalDate.of(2026, 11, 2) // lundi
    private val twist = Alternative("russian-twist", "Russian twist", follows = "abdos", factor = 2.0)
    private val abs = Exercise("Abdos", id = "abdos", sets = 4, reps = "6", progression = Progression(reps = 1, every = 1))
    private val obliques = Exercise("Hip dips", id = "obliques", sets = 3, reps = "5", perSide = "côté", progression = Progression(reps = 1, every = 1), alternatives = listOf(twist))
    private val pushups = Exercise("Pompes", id = "pompes", sets = 3, reps = "10", progression = Progression(reps = 1, every = 2))
    private val slow = Exercise("Pompes lentes", id = "pompes-lentes", sets = 3, reps = "5", progression = Progression(reps = 1, every = 2))

    /** Lundi : abdos et pompes ; mercredi : hip dips ; vendredi : pompes lentes. */
    private val plan = Plan(
        version = 1, name = "R", repeat = REPEAT_FOREVER, deloadEvery = 0, boss = false,
        families = listOf(PlanFamily("pompes", "Pompes", exercises = listOf("pompes", "pompes-lentes"))),
        weeks = listOf(
            PlanWeek(
                days = listOf(
                    PlanDay(1, exercises = listOf(abs, pushups)),
                    PlanDay(3, exercises = listOf(obliques)),
                    PlanDay(5, exercises = listOf(slow)),
                ),
            ),
        ),
    )

    private fun program() = StoredProgram("p", plan, start.toString())
    private fun schedule(p: StoredProgram) = ProgramSchedule(p, start.plusDays(60))
    private fun at(p: StoredProgram, day: Int, e: Int = 0) = schedule(p).sessionAt(day)!!.prescriptions[e]

    @Test
    fun `russian twist follows the abs, twice the reps`() {
        val p = program().withReplacement(2, 0, "obliques", ReplacementOption.ALT + "russian-twist", start.plusDays(2), keep = false)
        val pr = at(p, 2)
        assertEquals("Russian twist", pr.exercise.name)
        assertEquals(4, pr.sets)
        assertEquals(12, pr.reps)
        assertEquals("Hip dips", pr.replaces)
        assertNull(pr.sided)
        // Juste ce jour-là : la semaine suivante, les hip dips reviennent.
        assertEquals("Hip dips", at(p, 9).exercise.name)
    }

    @Test
    fun `the twist moves up with the abs and teaches nothing`() {
        var p = program().withReplacement(2, 0, "obliques", ReplacementOption.ALT + "russian-twist", start.plusDays(2), keep = true)
        // Deux séances d'abdos faites : +2 reps aux abdos, donc +4 au twist.
        p = p.toggle(0, 0).toggle(7, 0)
        assertEquals(8, at(p, 14).reps)
        assertEquals(16, at(p, 16).reps)
        // Gardé : toutes les séances suivantes ; ce qui y est noté ne règle pas les hip dips.
        p = p.saveLog(16, 0, ExerciseLog.ofSets(listOf(30, 30, 30, 30), false), complete = true)
        val s = schedule(p)
        assertTrue(s.adaptations().none { it.name == "Hip dips" && it.samples > 0 })
        assertTrue(s.balances().isEmpty())
    }

    @Test
    fun `back to the planned exercise from a date`() {
        val p = program()
            .withReplacement(2, 0, "obliques", ReplacementOption.ALT + "russian-twist", start.plusDays(2), keep = true)
            .withReplacement(16, 0, "obliques", null, start.plusDays(16), keep = true)
        assertEquals("Russian twist", at(p, 9).exercise.name)
        assertEquals("Hip dips", at(p, 16).exercise.name)
        assertEquals("Hip dips", at(p, 23).exercise.name)
        // Un seul jour remis tel que prévu au milieu d'un remplacement gardé.
        val once = program()
            .withReplacement(2, 0, "obliques", ReplacementOption.ALT + "russian-twist", start.plusDays(2), keep = true)
            .withReplacement(9, 0, "obliques", null, start.plusDays(9), keep = false)
        assertEquals("Hip dips", at(once, 9).exercise.name)
        assertEquals("Russian twist", at(once, 16).exercise.name)
    }

    @Test
    fun `same family exercises are offered, with their own level`() {
        val s = schedule(program())
        val monday = s.sessionAt(0)!!
        val options = s.replacementOptions(plan.weeks[0].days[0].exercises[1], monday.day)
        assertEquals(listOf("ex:pompes-lentes"), options.map { it.id })
        assertEquals(listOf(1), s.replaceable(monday)) // les abdos n'ont rien pour les remplacer
        val p = program().withReplacement(0, 1, "pompes", "ex:pompes-lentes", start, keep = false)
        val pr = at(p, 0, 1)
        assertEquals("Pompes lentes", pr.exercise.name)
        assertEquals(5, pr.reps)
        assertEquals("Pompes", pr.replaces)
        assertEquals("ex:pompes-lentes", schedule(p).currentReplacement(schedule(p).sessionAt(0)!!, 1))
    }

    @Test
    fun `changing the exercise clears what was noted on it that day`() {
        val p = program().toggle(2, 0).saveLog(2, 0, ExerciseLog.ofSets(listOf(5, 5), false), complete = true)
            .withReplacement(2, 0, "obliques", ReplacementOption.ALT + "russian-twist", start.plusDays(2), keep = false)
        assertTrue(!p.isDone(2, 0))
        assertNull(p.logOf(2, 0))
    }

    @Test
    fun `alternatives are read from the routine and checked`() {
        val routine = java.io.File("../examples/routine-maison.json").readText()
        val parsed = (PlanParser.parse(routine) as ParseResult.Success).plan
        val s = ProgramSchedule(StoredProgram("p", parsed, start.toString()), start)
        val obliquesDay = parsed.weeks[0].days.first { d -> d.exercises.any { it.key == "obliques" } }
        val ex = obliquesDay.exercises.first { it.key == "obliques" }
        assertTrue(s.replacementOptions(ex, obliquesDay).any { it.id == "alt:russian-twist" && it.follows == "abdos" && it.factor == 2.0 })

        val bad = """
            {"format":"flemmefit","version":1,"name":"T","weeks":[{"days":[{"day":1,"exercises":[
              {"name":"Hip dips","alternatives":[{"id":"t","name":"Twist","follows":"inconnu","factor":2}]}]}]}]}
        """.trimIndent()
        val errors = (PlanParser.parse(bad) as ParseResult.Failure).errors
        assertTrue(errors.toString(), errors.any { "follows" in it && "inconnu" in it })
    }
}
