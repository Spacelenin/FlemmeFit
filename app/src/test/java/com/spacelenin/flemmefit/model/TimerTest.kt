package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerTest {

    private fun plain(exercise: Exercise) = Prescription.of(exercise, level = 0, deload = false, boss = false, lastMax = null)

    @Test
    fun `a timed exercise chains its sets and rests`() {
        val phases = TimerPlans.exercise(plain(Exercise("Shadow", sets = 3, duration = "3min", rest = "1min")))!!
        assertEquals(listOf(180, 60, 180, 60, 180), phases.map { it.seconds })
        assertEquals(listOf(false, true, false, true, false), phases.map { it.rest })
        assertEquals("Série 2/3", phases[2].label)
    }

    @Test
    fun `the last all-out set of a timed exercise counts up`() {
        val phases = TimerPlans.exercise(plain(Exercise("Gainage", sets = 2, duration = "30s", amrap = true)))!!
        assertEquals(listOf(30, null), phases.map { it.seconds })
        assertEquals("30s", phases.last().target)
    }

    @Test
    fun `rep exercises have no chrono but keep their rest`() {
        val pushups = plain(Exercise("Pompes", sets = 4, reps = "10", rest = "90s"))
        assertNull(TimerPlans.exercise(pushups))
        assertEquals(90, TimerPlans.rest(pushups)!!.seconds)
        assertNull(TimerPlans.rest(plain(Exercise("Pompes", sets = 4, reps = "10", rest = "à l'aise"))))
    }

    @Test
    fun `phases advance on time, even after a long absence`() {
        val run = TimerRun("Shadow", listOf(TimerPhase("A", 10), TimerPhase("R", 5, rest = true), TimerPhase("B", 10)), phaseStart = 0)
        assertEquals(0, run.advance(9_999).index)
        assertEquals(1, run.advance(10_000).index)
        val later = run.advance(17_000)
        assertEquals(2, later.index)
        assertEquals(8, later.leftSeconds(17_000))
        val done = run.advance(60_000)
        assertTrue(done.finished)
    }

    @Test
    fun `pause freezes the countdown and extra seconds extend it`() {
        val run = TimerRun("Repos", listOf(TimerPhase("Repos", 60, rest = true)), phaseStart = 0)
        val paused = run.pause(10_000)
        assertEquals(50, paused.advance(40_000).leftSeconds(40_000))
        val resumed = paused.resume(40_000)
        assertEquals(50, resumed.leftSeconds(40_000))
        assertEquals(65, resumed.addSeconds(15).leftSeconds(40_000))
        assertFalse(resumed.addSeconds(15).advance(99_000).finished)
        assertTrue(resumed.advance(90_000).finished)
    }

    @Test
    fun `a count-up phase waits for stop`() {
        val run = TimerRun("Test", listOf(TimerPhase("À fond", null)), phaseStart = 0)
        assertFalse(run.advance(600_000).finished)
        assertNull(run.leftSeconds(5_000))
        val stopped = run.next(72_000)
        assertTrue(stopped.finished)
        assertEquals(72_000, stopped.elapsedMs(999_000))
    }
}
