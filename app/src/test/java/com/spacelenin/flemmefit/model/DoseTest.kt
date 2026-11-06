package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DoseTest {

    private fun item(name: String, muscles: List<String>, assists: List<String> = emptyList(), ramp: Double = 1.0, optional: Boolean = false, steady: Boolean = false) =
        Dose.Item(name, muscles, assists, counts = !optional, reducible = !optional && !steady, ramp = ramp)

    @Test
    fun `fatigue counts each muscle already worked, with a floor`() {
        val d = Dose.compute(
            listOf(
                item("Pompes", listOf("pectoraux", "triceps"), listOf("épaules")),
                item("Dips", listOf("triceps"), listOf("épaules", "pectoraux")),
                item("Squats", listOf("quadriceps")),
                item("Pompes diamant", listOf("triceps", "pectoraux"), listOf("épaules")),
            ),
        )
        assertFalse(d[0].reduced)
        // Triceps (principal) −15 %, épaules et pectoraux (aide) −7 % chacun.
        assertEquals(0.71, d[1].factor, 1e-9)
        assertEquals(listOf("Pompes"), d[1].after)
        assertFalse(d[2].reduced)
        // Deux principaux et une aide déjà travaillés : −37 %.
        assertEquals(0.65, d[3].factor, 1e-9)
        assertEquals(listOf("Pompes", "Dips"), d[3].after)
    }

    @Test
    fun `warm-up does not tire, steady exercises are never reduced, ramp multiplies`() {
        val d = Dose.compute(
            listOf(
                item("Échauffement", listOf("triceps"), optional = true),
                item("Beat Saber", emptyList(), listOf("épaules"), steady = true),
                item("Dips", listOf("triceps"), listOf("épaules"), ramp = 0.6),
            ),
        )
        assertFalse(d[1].reduced)
        assertEquals(0.93 * 0.6, d[2].factor, 1e-9)
        assertEquals(listOf("Beat Saber"), d[2].after)
        assertEquals(Dose.FLOOR, DoseInfo(fatigue = 0.65, ramp = 0.6).factor, 1e-9)
    }

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
    fun `an exercise that arrives later starts softly, then a reorder changes the fatigue`() {
        // Trois semaines de pompes, puis la routine gagne les dips.
        var p = StoredProgram("p", plan(false), start.toString())
        for (w in 0 until 3) p = p.toggle(w * 7, 0)
        p = p.copy(plan = plan(true))
        fun dipsAt(week: Int, program: StoredProgram = p) = ProgramSchedule(program, start.plusDays(week * 7L)).sessionAt(week * 7)!!.prescriptions[1]
        // 10 reps × 0,6 (mise en route) × 0,71 (triceps, épaules et pectoraux après les pompes) = 0,43 → plancher 0,5.
        val first = dipsAt(3)
        assertEquals(0.6, first.dose!!.ramp, 1e-9)
        assertEquals(5, first.reps)
        assertEquals(10, first.exploitPlanned)
        p = p.toggle(21, 0).toggle(21, 1).toggle(28, 0).toggle(28, 1)
        assertEquals(0.8, dipsAt(5).dose!!.ramp, 1e-9)
        p = p.toggle(35, 0).toggle(35, 1).toggle(42, 0).toggle(42, 1)
        assertEquals(1.0, dipsAt(7).dose!!.ramp, 1e-9)
        assertEquals(7, dipsAt(7).reps)

        // Dips d'abord, à partir de cette semaine : ce sont les pompes qui sont réduites.
        val reordered = p.withOrder(49, 1, listOf("dips", "pompes"), start.plusDays(49), keep = true)
        val s = ProgramSchedule(reordered, start.plusDays(56)).sessionAt(56)!!
        assertEquals(listOf(1, 0), s.order)
        assertNull(s.prescriptions[1].dose)
        assertTrue(s.prescriptions[0].dose!!.reduced)
        // Aperçu d'un autre ordre, sans rien garder.
        assertTrue(s.dosesFor(listOf(0, 1)).getValue(1).reduced)
    }

    @Test
    fun `a reduced day that is done as planned teaches nothing wrong`() {
        val log = ExerciseLog.ofSets(listOf(7, 7, 7), allOut = false)
        assertEquals(listOf(10, 10, 10), log.scaled(1 / 0.7).perSet)
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
