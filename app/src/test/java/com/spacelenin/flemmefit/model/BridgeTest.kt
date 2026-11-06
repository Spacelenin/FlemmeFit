package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BridgeTest {

    private val start = LocalDate.of(2026, 11, 2) // lundi
    private val superman = Exercise("Superman", id = "superman", sets = 3, duration = "15s", rest = "45s", amrap = true)
    private val bridge = Variant(
        id = "pont", name = "Pont", family = "dos", unit = PlanTest.UNIT_SECONDS,
        start = 7, step = 2, cap = 11, easyUntil = 9, perCycle = 4,
        requires = mapOf("superman" to 30), check = "Je touche le mur", warning = "Échauffé",
        easy = "mains surélevées.",
    )

    /** Mercredi : le superman. */
    private val plan = Plan(
        version = 1, name = "R", repeat = REPEAT_FOREVER, deloadEvery = 0, boss = false,
        families = listOf(PlanFamily("dos", "Dos", exercises = listOf("superman"))),
        variants = listOf(bridge),
        weeks = listOf(PlanWeek(days = listOf(PlanDay(3, exercises = listOf(superman))))),
    )

    private fun day(week: Int) = week * 7 + 2
    private fun date(week: Int) = start.plusDays(day(week).toLong())

    @Test
    fun `locked until the superman reaches 30 s, then waits for the check`() {
        var p = StoredProgram("p", plan, start.toString())
        val s0 = ProgramSchedule(p, date(0))
        assertNull(s0.sessionAt(day(0))!!.variantIndex)
        assertTrue(s0.pendingChecks().isEmpty())
        assertEquals(listOf(VariantRequirement("superman", "Superman", 30, 0, true)), s0.requirements(bridge))

        p = p.saveLog(day(0), 0, ExerciseLog.ofSets(listOf(15, 15, 32), allOut = true), complete = true)
        val s1 = ProgramSchedule(p, date(1))
        assertEquals(listOf("pont"), s1.pendingChecks().map { it.id })
        assertNull(s1.sessionAt(day(1))!!.variantIndex) // pas confirmé : pas de pont

        // Confirmé le jour même : proposé à partir du lendemain, jamais avant.
        p = p.withVariantCheck("pont", date(1))
        val s2 = ProgramSchedule(p, date(2))
        assertTrue(s2.pendingChecks().isEmpty())
        assertNull(s2.sessionAt(day(1))!!.variantIndex)
        assertTrue(s2.sessionAt(day(2))!!.variantIndex != null)
    }

    @Test
    fun `fixed progression, easy version first, max at the cap`() {
        var p = StoredProgram("p", plan, start.toString())
            .saveLog(day(0), 0, ExerciseLog.ofSets(listOf(15, 15, 32), allOut = true), complete = true)
            .withVariantCheck("pont", date(0))
        fun block(week: Int): Prescription {
            val s = ProgramSchedule(p, date(week)).sessionAt(day(week))!!
            return s.prescriptions[s.variantIndex!!]
        }
        fun done(week: Int) {
            val s = ProgramSchedule(p, date(week)).sessionAt(day(week))!!
            p = p.toggle(day(week), s.variantIndex!!)
        }
        // Version facile : 7 s, puis 9 s ; validée à 9 s, vraie version depuis 7 s.
        assertEquals("Pont (version facile)", block(1).exercise.name)
        assertEquals(7, block(1).seconds)
        assertEquals(3, block(1).sets)
        assertFalse(block(1).allOut)
        done(1)
        assertEquals(9, block(2).seconds)
        done(2)
        assertEquals("Pont", block(3).exercise.name)
        assertEquals(7, block(3).seconds)
        // Pas validé : on reste au même niveau.
        assertEquals(7, block(4).seconds)
        done(4)
        done(5)
        // Au plafond (11 s) : la dernière série va au max.
        assertEquals(11, block(6).seconds)
        assertTrue(block(6).allOut)
        done(6)
        assertEquals(11, block(7).seconds)
    }

    @Test
    fun `requirements can name tests, variants or exercises`() {
        val routine = (PlanParser.parse(java.io.File("../examples/routine-maison.json").readText()) as ParseResult.Success).plan
        val pont = routine.variants.first { it.id == "pont" }
        assertEquals(mapOf("pompes-pike" to 5, "superman" to 30, "hip-thrust" to 15), pont.requires)
        assertTrue(pont.isFixed && pont.check != null && pont.warning != null)
        assertEquals("dos", pont.family)

        val bad = """
            {"format":"flemmefit","version":1,"name":"T","families":[{"id":"f","name":"F","exercises":["a"]}],
             "variants":[{"id":"v","name":"V","family":"f","requires":{"nulle-part":3}}],
             "weeks":[{"days":[{"day":1,"exercises":[{"name":"A","id":"a"}]}]}]}
        """.trimIndent()
        val errors = (PlanParser.parse(bad) as ParseResult.Failure).errors
        assertTrue(errors.toString(), errors.any { "nulle-part" in it })
    }

    @Test
    fun `routine has dips on the pull day and hip thrust with abs, each with an alternative`() {
        val routine = (PlanParser.parse(java.io.File("../examples/routine-maison.json").readText()) as ParseResult.Success).plan
        routine.weeks.forEach { w ->
            // Les dips le mercredi, avec le rowing et le superman : plus après les pompes et Beat Saber.
            assertTrue("dips" !in w.days.first { it.day == 5 }.exercises.map { it.key })
            val wednesday = w.days.first { it.day == 3 }.exercises.map { it.key }
            assertTrue(wednesday.indexOf("dips") > wednesday.indexOf("superman"))
            val tuesday = w.days.first { it.day == 2 }
            assertTrue(tuesday.exercises.any { it.key == "hip-thrust" })
            assertTrue("bird dog" in tuesday.exercises.first().description!!)
        }
        val all = routine.weeks.flatMap { w -> w.days.flatMap { it.exercises } }
        assertEquals("dips", all.first { it.key == "dips" }.alternatives.single().follows)
        assertEquals("hip-thrust", all.first { it.key == "hip-thrust" }.alternatives.single().follows)
    }

    @Test
    fun `a timed set starts with a setup countdown that counts nothing`() {
        val run = TimerRun("Gainage", listOf(TimerPhase.setup(), TimerPhase("Série", 30), TimerPhase("Temps en plus", null)), phaseStart = 0)
        assertTrue(run.inSetup)
        assertEquals(1, run.workIndex)
        assertEquals(4, run.leftSeconds(0))
        val working = run.advance(4_000)
        assertFalse(working.inSetup)
        assertFalse(working.inOvertime)
        assertEquals(30, working.leftSeconds(4_000))
        assertTrue(working.advance(34_000).inOvertime)
    }
}
