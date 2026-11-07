package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DoseTest {

    @Test
    fun `order follows the chosen keys, warm-up and stretches stay in place`() {
        val day = listOf(
            Exercise("Échauffement", optional = true), Exercise("A"), Exercise("B"), Exercise("C"), Exercise("Étirements", optional = true),
        )
        assertEquals(listOf(0, 3, 1, 2, 4), Dose.arrange(day, listOf("c", "a", "b"), null, null, null))
        // Clé inconnue ou manquante : le reste garde son ordre.
        assertEquals(listOf(0, 2, 1, 3, 4), Dose.arrange(day, listOf("b"), null, null, null))
        // La variante (index 5) reste juste après son exercice de base.
        assertEquals(listOf(0, 2, 5, 1, 3, 4), Dose.arrange(day, listOf("b", "a", "c"), 5, 2, null))
    }

    private val start = LocalDate.of(2026, 11, 2) // lundi
    private val pushups = Exercise("Pompes", id = "pompes", sets = 3, reps = "10", muscles = listOf("pectoraux", "triceps"), assists = listOf("épaules"))
    private val dips = Exercise("Dips", id = "dips", sets = 3, reps = "10", muscles = listOf("triceps"), assists = listOf("épaules", "pectoraux"))

    private fun plan(withDips: Boolean) = Plan(
        version = 1, name = "R", repeat = REPEAT_FOREVER, deloadEvery = 0, boss = false,
        weeks = listOf(PlanWeek(days = listOf(PlanDay(1, exercises = listOfNotNull(pushups, dips.takeIf { withDips }))))),
    )

    @Test
    fun `no more ramp nor fatigue, whatever the order or when the exercise arrives`() {
        // Trois semaines de pompes, puis la routine gagne les dips : ils arrivent à leur dose pleine.
        var p = StoredProgram("p", plan(false), start.toString())
        for (w in 0 until 3) p = p.toggle(w * 7, 0)
        p = p.copy(plan = plan(true))
        val s = ProgramSchedule(p, start.plusDays(21)).sessionAt(21)!!
        assertEquals(listOf(10, 10), s.prescriptions.map { it.reps })
        // Dips d'abord : rien n'est réduit non plus, seul l'ordre change.
        val reordered = p.withOrder(21, 1, listOf("dips", "pompes"), start.plusDays(21), keep = true)
        val r = ProgramSchedule(reordered, start.plusDays(21)).sessionAt(21)!!
        assertEquals(listOf(1, 0), r.order)
        assertEquals(listOf(10, 10), r.prescriptions.map { it.reps })
    }

    @Test
    fun `tired cuts the dose of the day, counts as done and shows on the friend card`() {
        val saber = Exercise("Beat Saber", id = "beatsaber", duration = "40min", steady = true, tired = 20)
        val plan = Plan(
            version = 1, name = "R", repeat = REPEAT_FOREVER, deloadEvery = 0, boss = false,
            weeks = listOf(PlanWeek(days = listOf(PlanDay(1, exercises = listOf(saber))))),
        )
        val p = StoredProgram("p", plan, start.toString())
        val normal = ProgramSchedule(p, start).sessionAt(0)!!.prescriptions[0]
        assertEquals(2400, normal.seconds)
        assertEquals(20, normal.tiredOffer)
        val tired = p.withTired(0, 0, true).toggle(0, 0)
        val sched = ProgramSchedule(tired, start)
        val pr = sched.sessionAt(0)!!.prescriptions[0]
        assertTrue(pr.tired)
        assertEquals(1920, pr.seconds) // 32 min
        assertTrue(sched.isComplete(sched.sessionAt(0)!!))
        // Changement de routine : la marque suit son exo.
        assertTrue(tired.withPlan(plan).isTired(0, 0))
    }
}

class ExerciseInfoTest {
    @Test
    fun `the exercise sheet knows level, dose, days and history`() {
        val start = LocalDate.of(2026, 11, 2)
        val routine = (PlanParser.parse(java.io.File("../examples/routine-maison.json").readText()) as ParseResult.Success).plan
        var p = StoredProgram("p", routine, start.toString(), tests = mapOf("pompes" to 11, "tractions" to 3, "abdos" to 7, "gainage" to 45, "squats" to 20))
        p = p.saveLog(4, 1, ExerciseLog.ofSets(listOf(7, 7, 9), allOut = true), complete = true)
        val info = ProgramSchedule(p, start.plusDays(5)).exerciseInfo("pompes")!!
        assertEquals(listOf("pectoraux", "triceps"), info.muscles)
        assertEquals(2, info.palier) // 11 au test : palier 3
        assertEquals(21, info.nextLevelAt)
        assertEquals(listOf(java.time.DayOfWeek.FRIDAY), info.weekdays)
        assertEquals(listOf(9), info.history.map { it.value })
        assertTrue(info.progression!!.startsWith("+1 rep"))
        assertTrue(info.dose != null && info.nextDate != null)
        assertTrue(Surprise.due(false, 0, start))
        assertFalse(Surprise.due(true, 0, start))
        assertTrue(Surprise.due(true, 2026, LocalDate.of(2027, 4, 1)))
        assertFalse(Surprise.due(true, 2027, LocalDate.of(2027, 4, 1)))
    }
}
