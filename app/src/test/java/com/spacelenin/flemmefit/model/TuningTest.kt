package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TuningTest {

    private val start = LocalDate.of(2026, 10, 19)
    private val pushups = Exercise("Pompes", sets = 3, reps = "8", progression = Progression(reps = 1, every = 2))
    private val abs = Exercise("Abdos", sets = 3, reps = "10", progression = Progression(reps = 1, every = 2))

    private fun plan(tuning: Tuning? = null, pushups: Exercise = this.pushups) = Plan(
        version = 1, name = "P", repeat = REPEAT_FOREVER, deloadEvery = 0, boss = false, tuning = tuning,
        weeks = listOf(PlanWeek(days = listOf(PlanDay(1, exercises = listOf(pushups, abs)), PlanDay(4, exercises = listOf(pushups, abs))))),
    )

    private fun day(n: Int) = n / 2 * 7 + if (n % 2 == 0) 0 else 3
    private fun reps(program: StoredProgram, n: Int, e: Int = 0) =
        ProgramSchedule(program, start.plusDays(100)).sessionAt(day(n))!!.prescriptions[e].reps!!
    private fun StoredProgram.sets(n: Int, e: Int, vararg values: Int) = saveLog(day(n), e, ExerciseLog.ofSets(values.toList(), false), complete = true)

    /** Toujours 14 par série aux pompes : ce que chaque séance prévoit. */
    private fun climb(program: StoredProgram, sessions: Int = 6): List<Int> {
        var p = program
        return (0 until sessions).map { n -> reps(p, n).also { r -> p = p.sets(n, 0, *IntArray(3) { maxOf(r, 14) }) } }
    }

    @Test
    fun `defaults are the historical values`() {
        val a = AdaptParams()
        assertEquals(0.75, a.gain(0, 1.0), 1e-9)
        assertEquals(0.75 - 0.075 * 3, a.gain(3, 1.0), 1e-9)
        assertEquals(0.3, a.gain(10, 1.0), 1e-9)
        assertEquals(0.6, a.gain(10, -1.0), 1e-9)
        assertEquals(a, AdaptParams().with(AdaptTuning()))
        assertEquals(AdaptProfile.NORMAL.apply(a), a)
        assertEquals(42, SidesParams().catchUpDays)
    }

    @Test
    fun `profiles as announced`() {
        val gentle = AdaptProfile.GENTLE.apply(AdaptParams())
        assertEquals(9, gentle.calibration)
        assertEquals(1.2, gentle.threshold, 1e-9)
        assertEquals(0.5, gentle.exerciseFeeling, 1e-9)
        assertEquals(0.75, gentle.testCeiling, 1e-9)
        assertEquals(0.6, gentle.gainDown, 1e-9) // la baisse reste franche
        val brisk = AdaptProfile.BRISK.apply(AdaptParams())
        assertEquals(4, brisk.calibration)
        assertEquals(0.9, brisk.gainStart, 1e-9)
        assertEquals(0.85, brisk.testCeiling, 1e-9)
        assertEquals(0.6, brisk.gainDown, 1e-9)
        assertEquals(60, AdaptProfile.GENTLE.apply(SidesParams()).catchUpDays)
        assertEquals(28, AdaptProfile.BRISK.apply(SidesParams()).catchUpDays)
        // Le profil agit par-dessus la routine, sans dépasser les bornes.
        assertEquals(0.75, AdaptProfile.BRISK.apply(AdaptParams(testCeiling = 0.7, testTarget = 0.55)).testCeiling, 1e-9)
        assertEquals(0.95, AdaptProfile.BRISK.apply(AdaptParams(testCeiling = 0.95, testTarget = 0.8)).testCeiling, 1e-9)
    }

    @Test
    fun `brisk follows faster, gentle slower`() {
        val p = StoredProgram("p", plan(), start.toString())
        val normal = climb(p)
        val gentle = climb(p.withProfile(AdaptProfile.GENTLE, start))
        val brisk = climb(p.withProfile(AdaptProfile.BRISK, start))
        assertTrue("$gentle $normal $brisk", brisk[1] >= normal[1] && normal[1] >= gentle[1])
        assertTrue("$gentle $normal $brisk", gentle[2] < normal[2])
    }

    @Test
    fun `routine and exercise tuning override the defaults`() {
        // Ressenti sans poids dans la routine : « facile » ne change plus rien.
        val quiet = plan(Tuning(adapt = AdaptTuning(exerciseFeeling = 0.0)))
        var p = StoredProgram("p", quiet, start.toString()).toggle(day(0), 0).withExerciseFeeling(day(0), 0, Adaptation.FEELING_EASY)
        assertEquals(8, reps(p, 1))
        // Par exercice, ça prime sur la routine.
        val loud = plan(Tuning(adapt = AdaptTuning(exerciseFeeling = 0.0)), pushups.copy(tuning = Tuning(adapt = AdaptTuning(exerciseFeeling = 1.0))))
        p = StoredProgram("p", loud, start.toString()).toggle(day(0), 0).withExerciseFeeling(day(0), 0, Adaptation.FEELING_EASY)
        assertEquals(9, reps(p, 1))
    }

    @Test
    fun `a profile change only affects sessions from its date`() {
        val p = StoredProgram("p", plan(), start.toString())
        val before = climb(p, 4)
        // Passé en Vif le jour de la 4e séance : les trois premières restent comme elles étaient.
        val changed = climb(p.withProfile(AdaptProfile.BRISK, start.plusDays(day(3).toLong())), 4)
        assertEquals(before.take(4), changed.take(4))
        assertEquals(AdaptProfile.NORMAL, p.withProfile(AdaptProfile.BRISK, start.plusDays(7)).profileOn(start.plusDays(6)))
        assertEquals(AdaptProfile.BRISK, p.withProfile(AdaptProfile.BRISK, start.plusDays(7)).profileOn(start.plusDays(7)))
        // Changé deux fois le même jour : le dernier choix remplace l'autre.
        val twice = p.withProfile(AdaptProfile.BRISK, start).withProfile(AdaptProfile.GENTLE, start)
        assertEquals(1, twice.profiles.size)
        assertEquals(AdaptProfile.GENTLE, twice.profileOn(start))
    }

    @Test
    fun `suggests a brisker profile when everything is easy, a gentler one when it is hard`() {
        var easy = StoredProgram("p", plan(), start.toString())
        var hard = easy
        val today = start.plusDays(day(7).toLong())
        repeat(8) { n ->
            val s = ProgramSchedule(easy, today).sessionAt(day(n))!!
            easy = easy.sets(n, 0, *IntArray(3) { s.prescriptions[0].reps!! + 3 }).sets(n, 1, *IntArray(3) { s.prescriptions[1].reps!! + 3 })
            hard = hard.sets(n, 0, 2, 2, 2).sets(n, 1, 2, 2, 2)
        }
        assertEquals(AdaptProfile.BRISK, ProgramSchedule(easy, today).profileSuggestion())
        assertEquals(AdaptProfile.GENTLE, ProgramSchedule(hard, today).profileSuggestion())
        // Écartée, ou rythme changé récemment : rien pendant 3 semaines.
        assertNull(ProgramSchedule(easy.copy(profileHintDismissed = today.toString()), today).profileSuggestion())
        assertNull(ProgramSchedule(easy.withProfile(AdaptProfile.BRISK, today), today).profileSuggestion())
        // Trop peu de séances : rien.
        assertNull(ProgramSchedule(StoredProgram("p", plan(), start.toString()).sets(0, 0, 20, 20, 20), start.plusDays(1)).profileSuggestion())
    }

    @Test
    fun `tuning in the JSON is read and checked`() {
        val json = """
            {"format":"flemmefit","version":1,"name":"T",
             "tuning":{"adapt":{"calibration":8},"sides":{"catchUpDays":30},"variants":{"cycleWeeks":3}},
             "weeks":[{"days":[{"day":1,"exercises":[{"name":"Pompes","reps":"8","tuning":{"adapt":{"testCeiling":0.7}}}]}]}]}
        """.trimIndent()
        val plan = (PlanParser.parse(json) as ParseResult.Success).plan
        assertEquals(8, plan.tuning?.adapt?.calibration)
        assertEquals(0.7, plan.weeks[0].days[0].exercises[0].tuning?.adapt?.testCeiling)
        val schedule = ProgramSchedule(StoredProgram("p", plan, start.toString()), start)
        assertEquals(8, schedule.routineParams("pompes").calibration)
        assertEquals(0.7, schedule.routineParams("pompes").testCeiling, 1e-9)
        assertEquals(30, schedule.sidesParams("pompes", start).catchUpDays)

        val bad = json.replace("\"calibration\":8", "\"calibration\":100").replace("\"testCeiling\":0.7", "\"testCeiling\":0.7,\"testTarget\":0.8")
        val errors = (PlanParser.parse(bad) as ParseResult.Failure).errors
        assertTrue(errors.toString(), errors.any { "tuning.adapt.calibration" in it })
        assertTrue(errors.toString(), errors.any { "testTarget doit être sous testCeiling" in it })
    }

    @Test
    fun `exported routine and template read back`() {
        val routine = java.io.File("../examples/routine-maison.json").readText()
        val plan = (PlanParser.parse(routine) as ParseResult.Success).plan
        val again = (PlanParser.parse(PlanExport.routine(plan)) as ParseResult.Success).plan
        assertEquals(plan, again)
        val template = PlanExport.template(plan)
        assertTrue("\"aide\"" in template && "\"testCeiling\": 0.8" in template)
        val edited = (PlanParser.parse(template) as ParseResult.Success).plan
        assertEquals(plan.weeks, edited.weeks)
        assertEquals(AdaptParams(), AdaptParams().with(edited.tuning?.adapt))
        assertEquals(SidesParams(), SidesParams().with(edited.tuning?.sides))
        assertEquals(VariantParams(), VariantParams().with(edited.tuning?.variants))
    }
}
