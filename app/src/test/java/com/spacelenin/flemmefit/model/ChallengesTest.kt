package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChallengesTest {

    private val monday = LocalDate.of(2026, 10, 26)
    private val min = 60_000L

    @Test
    fun `one or two a week, the same for everyone, only on session days`() {
        val counts = (0L until 20L).map { Challenges.weeklyCount(monday.plusWeeks(it)) }
        assertTrue(counts.all { it in 1..2 })
        assertTrue(counts.toSet().size == 2)
        val days = listOf(0L, 1L, 2L, 4L, 5L).map(monday::plusDays)
        val a = Challenges.slots(monday, days, seed = 1)
        val b = Challenges.slots(monday, days, seed = 2)
        assertEquals(Challenges.weeklyCount(monday), a.size)
        assertEquals(a.size, b.size)
        assertTrue(a.all { it.date in days && it.minuteOfDay in 600 until 1260 })
        assertEquals(a.size, a.map { it.date }.toSet().size)
        assertEquals(a, Challenges.slots(monday, days, seed = 1))
        // Pas plus de défis que de jours de séance, et rien sans séance.
        assertTrue(Challenges.slots(monday, listOf(monday.plusDays(3)), seed = 1).size == 1)
        assertTrue(Challenges.slots(monday, emptyList(), seed = 1).isEmpty())
    }

    @Test
    fun `the dose is 60 percent of the session volume`() {
        val pompes = Prescription(Exercise("Pompes classiques", test = "pompes"), sets = 5, reps = 13, seconds = null, load = null)
        val planche = Prescription(Exercise("Gainage (planche)", test = "gainage"), sets = 3, reps = null, seconds = 45, load = null)
        val warmup = Prescription(Exercise("Échauffement", optional = true), sets = 1, reps = null, seconds = 300, load = null)
        assertEquals(ChallengeDose("Pompes classiques", 39, false), Challenges.pick(listOf(warmup, pompes), seed = 3))
        assertEquals(ChallengeDose("Gainage (planche)", 80, true), Challenges.pick(listOf(planche), seed = 3))
        assertNull(Challenges.pick(listOf(warmup), seed = 3))
        // Un côté à la fois : la dose est par côté, et le dit.
        val hipDips = pompes.copy(
            exercise = Exercise("Hip dips", test = "abdos", perSide = "côté"), sets = 6, reps = 10,
            sided = SidedPlan("côté", Sides.order(3, 3), 10, 10),
        )
        val dose = Challenges.pick(listOf(hipDips), seed = 3)!!
        assertEquals(18, dose.amount)
        assertEquals("18 hip dips par côté", Challenge("x", "2026-10-31", dose.exercise, dose.amount, dose.seconds, 0, perSide = dose.perSide).label)
    }

    @Test
    fun `30 minutes to accept, an hour to do it, points only when won`() {
        val c = Challenge("s", "2026-10-27", "Pompes", 39, false, offeredAt = 0)
        assertEquals(Challenge.Status.OFFERED, c.status(29 * min))
        assertEquals(Challenge.Status.EXPIRED, c.status(31 * min))
        assertEquals(c, c.accept(31 * min))
        assertEquals(c, c.finish(true, 10 * min)) // pas accepté : rien à gagner

        val accepted = c.accept(10 * min)
        assertEquals(Challenge.Status.RUNNING, accepted.status(69 * min))
        assertEquals(Challenge.Status.ASK_RESULT, accepted.status(71 * min))
        assertEquals(Challenge.Status.LOST, accepted.status(101 * min))
        val won = accepted.finish(true, 40 * min)
        assertEquals(Challenge.Status.WON, won.status(40 * min))
        assertFalse(won.open(40 * min))
        assertEquals(Challenge.Status.REFUSED, c.refuse(5 * min).status(6 * min))
        assertEquals(1, Challenges.wonCount(listOf(won, c.refuse(5 * min))))

        val card = FriendCard(id = "x", pseudo = "X", date = "2026-10-27", updatedAt = 0, sessionsDone = 3, defis = 1)
        assertEquals(3 + Challenges.POINTS, card.score)
    }
}
