package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BadgesTest {

    @Test
    fun `tiers grow exponentially`() {
        assertEquals(-1, Badges.tierOf(9))
        assertEquals(0, Badges.tierOf(10))
        assertEquals(2, Badges.tierOf(499))
        assertEquals(3, Badges.tierOf(500))
        assertEquals(5, Badges.tierOf(123_456))
    }

    @Test
    fun `badges cover sessions, bosses and counters, time in hours`() {
        val stats = Stats(
            sessionsDone = 12, totalSets = 0, totalReps = 0, totalSeconds = 0, bestStreak = 0, currentStreak = 0,
            bossDefeated = 3, bossMet = 3, exercises = emptyList(), weeks = emptyList(), history = emptyList(),
            counters = listOf(Counter("Pompes", reps = 1234), Counter("Boxe", seconds = 11 * 3600)),
        )
        val badges = Badges.of(stats).associateBy { it.family }
        assertEquals(10, badges.getValue("Séances").reached)
        assertEquals(50, badges.getValue("Séances").next)
        assertNull(badges.getValue("Boss").reached)
        assertEquals(1000, badges.getValue("Pompes").reached)
        assertEquals("🏆", badges.getValue("Pompes").medal)
        assertEquals(11, badges.getValue("Boxe").value)
        assertEquals("h de boxe", badges.getValue("Boxe").unit)
    }

    @Test
    fun `extras and feelings are kept apart from the counts`() {
        val day = LocalDate.of(2026, 10, 22)
        val p = StoredProgram("p", Plan(version = 1, name = "x", weeks = listOf(PlanWeek(days = listOf(PlanDay(1, exercises = listOf(Exercise("A"))))))), "2026-10-19")
            .withExtra(day, "  Marche 40 min ").withExtra(day, "Vélo").withExtra(day, "   ")
            .withFeeling(3, 2)
        assertEquals(listOf("Marche 40 min", "Vélo"), p.extrasOn(day))
        assertEquals(listOf("Vélo"), p.withoutExtra(day, 0).extrasOn(day))
        assertEquals(emptyMap<String, List<String>>(), p.withoutExtra(day, 0).withoutExtra(day, 0).extras)
        assertEquals(2, p.feelingOf(3))
        assertNull(p.feelingOf(4))
    }
}
