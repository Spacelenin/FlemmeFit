package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AdaptationTest {

    private val start = LocalDate.of(2026, 10, 19)

    private val pushups = Exercise("Pompes", sets = 3, reps = "8", progression = Progression(reps = 1, every = 2))
    private val abs = Exercise("Abdos", sets = 3, reps = "10", progression = Progression(reps = 1, every = 2))

    /** Pompes et abdos les lundis et jeudis, sans décharge. */
    private val plan = Plan(
        version = 1, name = "P", repeat = REPEAT_FOREVER, deloadEvery = 0, boss = false,
        weeks = listOf(PlanWeek(days = listOf(PlanDay(1, exercises = listOf(pushups, abs)), PlanDay(4, exercises = listOf(pushups, abs))))),
    )

    /** Index des séances, dans l'ordre : lundi, jeudi, lundi… */
    private fun day(n: Int) = n / 2 * 7 + if (n % 2 == 0) 0 else 3

    private fun reps(program: StoredProgram, n: Int, e: Int = 0) =
        ProgramSchedule(program, start.plusDays(100)).sessionAt(day(n))!!.prescriptions[e].reps!!

    private fun StoredProgram.sets(n: Int, e: Int, vararg values: Int) = saveLog(day(n), e, ExerciseLog.ofSets(values.toList(), false), complete = true)

    @Test
    fun `done as planned keeps the base curve`() {
        var p = StoredProgram("p", plan, start.toString())
        repeat(6) { p = p.toggle(day(it), 0) }
        assertEquals(listOf(8, 8, 9, 9, 10, 10, 11), (0..6).map { reps(p, it) })
    }

    @Test
    fun `regular extra sets raise fast, a single big set does not`() {
        val p = StoredProgram("p", plan, start.toString())
        // 12-12-12 pour 8 : +4 crans d'écart, les trois quarts repris tout de suite.
        assertEquals(11, reps(p.sets(0, 0, 12, 12, 12), 1))
        // 20-8-8 : la plus faible série compte, rien ne bouge.
        assertEquals(8, reps(p.sets(0, 0, 20, 8, 8), 1))
    }

    @Test
    fun `calibrates within a few sessions then stops moving`() {
        // Capable de 14 par série d'entrée : il fait toujours 14 quand le prévu est plus bas, le prévu sinon.
        var p = StoredProgram("p", plan, start.toString())
        val planned = ArrayList<Int>()
        repeat(14) { n ->
            val r = reps(p, n)
            planned += r
            p = p.sets(n, 0, *IntArray(3) { maxOf(r, 14) })
        }
        assertTrue(planned.toString(), planned[3] >= 13) // calé en quelques séances
        // Ensuite ça suit la courbe de base, sans yo-yo.
        planned.drop(4).zipWithNext().forEach { (a, b) -> assertTrue(planned.toString(), b - a in 0..1) }
    }

    @Test
    fun `below plan goes down and each exercise is independent`() {
        var p = StoredProgram("p", plan, start.toString())
        repeat(4) { n -> p = p.toggle(day(n), 0).toggle(day(n), 1) }
        // Séance 4 : pompes à 10 prévues, faites 6-6-6 ; abdos faciles.
        p = p.sets(4, 0, 6, 6, 6).withExerciseFeeling(day(4), 1, Adaptation.FEELING_EASY).toggle(day(4), 1)
        assertTrue(reps(p, 5) < 10)
        assertEquals(12, reps(p, 5, e = 1)) // abdos : 12 prévus de base, le « facile » seul ne fait pas sauter un cran
        // Deux fois facile : là ça monte.
        p = p.toggle(day(5), 0).withExerciseFeeling(day(5), 1, Adaptation.FEELING_EASY).toggle(day(5), 1)
        assertTrue(reps(p, 6, e = 1) > 13)
    }

    @Test
    fun `session feeling counts less than the exercise one`() {
        val p = StoredProgram("p", plan, start.toString()).toggle(day(0), 0)
        val s = Adaptation.signal(
            ProgramSchedule(p, start).sessionAt(0)!!.prescriptions[0], null, done = true, final = true, step = 1.0,
            exerciseFeeling = null, sessionFeeling = Adaptation.FEELING_EASY,
        )!!
        val e = Adaptation.signal(
            ProgramSchedule(p, start).sessionAt(0)!!.prescriptions[0], null, done = true, final = true, step = 1.0,
            exerciseFeeling = Adaptation.FEELING_EASY, sessionFeeling = Adaptation.FEELING_HARD,
        )!!
        assertTrue(s in 0.1..0.5)
        assertEquals(Adaptation.EXERCISE_FEELING, e, 1e-9)
        assertNull(Adaptation.signal(ProgramSchedule(p, start).sessionAt(0)!!.prescriptions[1], null, false, true, 1.0, null, null))
    }

    @Test
    fun `retest keeps the adaptation unless it went too far`() {
        val levelled = Exercise(
            "Pompes", test = "pompes", amrap = true, progression = Progression(reps = 1, every = 2),
            levels = listOf(Level(0, sets = 3, reps = "4"), Level(10, sets = 3, reps = "8")),
        )
        val p = Plan(
            version = 1, name = "T", repeat = REPEAT_FOREVER, deloadEvery = 0, boss = false, tests = listOf(PlanTest("pompes", "Pompes")),
            weeks = listOf(
                PlanWeek(days = listOf(PlanDay(1, exercises = listOf(levelled)))),
                PlanWeek(days = listOf(PlanDay(1, exercises = listOf(Exercise("Test", test = "pompes", retest = true))))),
            ),
        )
        fun afterTest(test: Int): Int {
            val program = StoredProgram("t", p, start.toString(), tests = mapOf("pompes" to 6))
                .saveLog(0, 0, ExerciseLog.ofSets(listOf(9, 9, 12), true), complete = true) // 4 prévus : bien au-dessus
                .saveLog(7, 0, ExerciseLog(max = test), complete = true)
            return ProgramSchedule(program, start.plusDays(30)).sessionAt(14)!!.prescriptions[0].reps!!
        }
        // Test à 20 : palier 10 (3×8 de départ), le volume appris (≈ 8-9) est gardé.
        assertTrue(afterTest(20) in 8..10)
        // Test à 8 : 8-9 par série dépasse 80 % du max, ramené vers 65 % (pas sous le départ du palier : 4).
        assertEquals(5, afterTest(8))
    }

    @Test
    fun `gain is strong at first then smooth, always firm downwards`() {
        assertEquals(0.75, Adaptation.gain(0, 1.0), 1e-9)
        assertEquals(0.3, Adaptation.gain(10, 1.0), 1e-9)
        assertEquals(0.6, Adaptation.gain(10, -1.0), 1e-9)
        val calm = AdaptState(value = 2.0, applied = 2, samples = 10)
        assertEquals(2, Adaptation.update(calm, 2.5).applied) // +0,75 : pas encore un cran net
        assertFalse(Adaptation.update(AdaptState(), 0.0).stable)
    }

    @Test
    fun `adaptation labels and overtime cap`() {
        val more = ExerciseAdaptation("Pompes", 15, 12, false, 3, 3, 3, 2, false)
        assertEquals("+3 reps par série", more.headline)
        assertEquals("3 × 15 au lieu de 3 × 12 · en calibrage (2/6)", more.detail)
        val plank = ExerciseAdaptation("Gainage", 35, 45, true, 3, 3, -2, 8, true)
        assertEquals("−10s par série", plank.headline)
        assertEquals("Comme prévu", ExerciseAdaptation("A", 5, 5, false, 3, 3, 0, 1, false).headline)

        val shadow = Exercise("Shadow", sets = 2, duration = "3min", progression = Progression(duration = "15s", every = 3, maxDuration = "4min"))
        val g = GuidedExercise(Prescription.of(shadow, 0, false, false, null))
        assertTrue(g.overtime(0))
        assertEquals(200, g.withOvertime(0, 20))
        assertEquals(360, g.withOvertime(0, 999)) // Stop oublié : jamais plus du double
        assertFalse(GuidedExercise(Prescription.of(shadow.copy(optional = true), 0, false, false, null)).overtime(0))
    }
}
