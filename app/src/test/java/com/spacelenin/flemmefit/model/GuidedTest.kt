package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedTest {

    private fun guided(exercise: Exercise, boss: Boolean = false, lastMax: Int? = null) =
        GuidedExercise(Prescription.of(exercise, level = 0, deload = false, boss = boss, lastMax = lastMax))

    @Test
    fun `rep sets are logged one by one and validated when all reach the plan`() {
        val g = guided(Exercise("Pompes", sets = 3, reps = "10", rest = "90s"))
        assertEquals(3, g.sets)
        assertEquals(10, g.suggested(0))
        assertEquals(90, g.restSeconds)
        assertTrue(g.needsValue(1))
        assertNull(g.countdown(0))
        val (log, complete) = g.result(listOf(10, 10, 10))
        assertTrue(complete)
        assertEquals(listOf(10, 10, 10), log.perSet)
        assertFalse(g.result(listOf(10, 8, 10)).second)
    }

    @Test
    fun `stopping early leaves the exercise incomplete`() {
        val (log, complete) = guided(Exercise("Pompes", sets = 3, reps = "10")).result(listOf(10))
        assertFalse(complete)
        assertEquals(listOf(10, 0, 0), log.perSet)
    }

    @Test
    fun `timed sets count down, the all-out last one counts up and becomes the max`() {
        val g = guided(Exercise("Gainage", sets = 2, duration = "30s", amrap = true))
        assertEquals(30, g.countdown(0))
        assertNull(g.countdown(1))
        assertTrue(g.countUp(1))
        val (log, complete) = g.result(listOf(30, 52))
        assertTrue(complete)
        assertEquals(52, log.max)
    }

    @Test
    fun `boss last set suggests beating the last max`() {
        val g = guided(Exercise("Pompes", sets = 3, reps = "10"), boss = true, lastMax = 14)
        assertEquals(10, g.suggested(0))
        assertEquals(15, g.suggested(2))
    }

    @Test
    fun `a test is one all-out set whose result is the max`() {
        val test = PlanTest("pompes", "Pompes", unit = "reps")
        val g = GuidedExercise(Prescription.test(Exercise("Pompes", retest = true, test = "pompes"), test, current = 12))
        assertEquals(1, g.sets)
        assertEquals(13, g.suggested(0))
        val (log, complete) = g.result(listOf(18))
        assertTrue(complete)
        assertEquals(18, log.max)
        assertFalse(g.result(listOf(0)).second)
    }

    @Test
    fun `nothing numbered is simply done`() {
        val g = guided(Exercise("Étirements", description = "Tout le corps"))
        assertEquals(1, g.sets)
        assertFalse(g.needsValue(0))
        val (log, complete) = g.result(listOf(0))
        assertTrue(complete)
        assertTrue(log.isEmpty)
    }
}
