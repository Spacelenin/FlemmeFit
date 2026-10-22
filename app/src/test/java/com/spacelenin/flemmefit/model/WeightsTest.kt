package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeightsTest {

    private val beatSaber = Exercise(
        "Beat Saber", duration = "40min", weights = WeightsOption(default = false),
        progression = Progression(duration = "10min", maxDuration = "90min"),
    )
    private val shadow = Exercise(
        "Shadow", sets = 3, duration = "3min", rest = "1min", weights = WeightsOption(default = true),
        progression = Progression(duration = "30s", maxDuration = "4min", addSets = 1, maxSets = 6),
    )

    private fun at(ex: Exercise, level: Int, weighted: Boolean) =
        Prescription.of(ex, level, deload = false, boss = false, lastMax = null, weighted = weighted)

    private fun minutes(p: Prescription) = p.seconds!! / 60

    @Test
    fun `beat saber climbs 85 percent as fast with weights and stops at the same ceiling`() {
        assertEquals(listOf(40, 50, 60, 70, 80, 90, 90, 90), (0..7).map { minutes(at(beatSaber, it, false)) })
        assertEquals(listOf(40, 48, 57, 65, 74, 82, 90, 90), (0..7).map { minutes(at(beatSaber, it, true)) })
    }

    @Test
    fun `shadow boxing reaches 4 min rounds before adding a set, later with weights`() {
        fun label(p: Prescription) = "${p.sets}×${Measures.formatSeconds(p.seconds!!)}"
        assertEquals(
            listOf("3×3min", "3×3min30", "3×4min", "4×3min", "4×3min30", "4×4min", "5×3min"),
            (0..6).map { label(at(shadow, it, false)) },
        )
        assertEquals(
            listOf("3×3min", "3×3min25", "3×3min50", "3×4min", "4×3min", "4×3min25", "4×3min50", "4×4min", "5×3min"),
            (0..8).map { label(at(shadow, it, true)) },
        )
        // Tout au bout, même plafond.
        assertEquals("6×4min", label(at(shadow, 30, false)))
        assertEquals("6×4min", label(at(shadow, 30, true)))
    }

    @Test
    fun `exercises without the option ignore it`() {
        val pushups = Exercise("Pompes", sets = 3, reps = "10", progression = Progression(reps = 1))
        val p = at(pushups, 3, true)
        assertEquals(13, p.reps)
        assertNull(p.weighted)
        assertTrue(at(shadow, 0, true).summary().contains("avec poids"))
        assertFalse(at(beatSaber, 0, false).summary().contains("poids"))
    }

    private val start = LocalDate.of(2026, 10, 19)
    private val plan = Plan(
        version = 1, name = "Poids", repeat = REPEAT_FOREVER, boss = false, deloadEvery = 0,
        weeks = listOf(PlanWeek(days = (1..5).map { PlanDay(it, exercises = listOf(shadow, beatSaber)) })),
    )

    @Test
    fun `the default comes from the routine, then from the last choice said`() {
        var program = StoredProgram("p", plan, start.toString())
        var s = ProgramSchedule(program, start)
        assertEquals(true, s.sessionAt(0)!!.prescriptions[0].weighted)
        assertEquals(false, s.sessionAt(0)!!.prescriptions[1].weighted)

        program = program.withWeights(1, 0, false).withWeights(1, 1, true)
        s = ProgramSchedule(program, start)
        assertEquals(true, s.sessionAt(0)!!.prescriptions[0].weighted)
        assertEquals(false, s.sessionAt(1)!!.prescriptions[0].weighted)
        assertEquals(true, s.sessionAt(1)!!.prescriptions[1].weighted)
        // Le jour d'après reprend le dernier choix.
        assertEquals(false, s.sessionAt(2)!!.prescriptions[0].weighted)
        assertEquals(true, s.sessionAt(2)!!.prescriptions[1].weighted)
    }

    @Test
    fun `switching weights keeps the same steps`() {
        val program = StoredProgram("p", plan, start.toString()).toggle(0, 1).toggle(1, 1).withWeights(2, 1, true)
        val s = ProgramSchedule(program, start)
        // Deux séances validées sans poids, puis la troisième avec : 2 crans à 85 %.
        assertEquals(57, s.sessionAt(2)!!.prescriptions[1].seconds!! / 60)
    }

    @Test
    fun `a routine update keeps the weights choices`() {
        val program = StoredProgram("p", plan, start.toString()).withWeights(1, 0, false)
        val updated = program.withPlan(plan.copy(revision = 2))
        assertEquals(false, updated.weightsChoice(1, 0))
    }
}
