package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SessionModesTest {

    private val start = LocalDate.of(2026, 10, 19) // lundi

    private fun pompes() = Exercise("Pompes", sets = 4, reps = "10", progression = Progression(reps = 1))
    private val warmup = Exercise("Échauffement", duration = "5min", optional = true)
    private val stretch = Exercise("Étirements", duration = "3min", optional = true)

    private fun plan(boss: Boolean = false) = Plan(
        version = 1, name = "Modes", repeat = REPEAT_FOREVER, boss = boss, deloadEvery = 0,
        weeks = listOf(
            PlanWeek(
                days = (1..7).map { d ->
                    PlanDay(
                        d,
                        exercises = listOf(
                            warmup, pompes(), Exercise("Squats", sets = 3, reps = "15", progression = Progression(reps = 1)),
                            Exercise("Gainage", sets = 2, duration = "30s"), Exercise("Tractions", sets = 3, reps = "5"), stretch,
                        ),
                    )
                },
            ),
        ),
    )

    /** Coche tous les exercices obligatoires (ni échauffement ni étirements) des séances [days]. */
    private fun StoredProgram.doRequired(days: IntRange, only: Int = 4): StoredProgram =
        days.fold(this) { p, d -> (1..only).fold(p) { q, e -> q.toggle(d, e) } }

    @Test
    fun `skipping warm-up and stretching still completes the session`() {
        val program = StoredProgram("p", plan(), start.toString()).doRequired(0..2)
        val s = ProgramSchedule(program, start.plusDays(3))
        val session = s.sessionAt(0)!!
        assertEquals(4, session.requiredCount)
        assertTrue(s.isComplete(session))
        assertEquals(SessionStatus.DONE, s.status(session))
        assertEquals(3, s.progress().streak)
        assertEquals(3, Stats.of(s).sessionsDone)
    }

    @Test
    fun `chill halves the sets, once a week, and still counts for the streak`() {
        val fresh = StoredProgram("p", plan(), start.toString())
        val today = ProgramSchedule(fresh, start)
        assertTrue(today.canChill(today.sessionAt(0)!!))

        val chilled = fresh.chill(0)
        val s = ProgramSchedule(chilled, start)
        val session = s.sessionAt(0)!!
        assertTrue(session.isChill)
        assertEquals(2, session.prescriptions[1].sets)
        assertFalse(s.canBurst(session))
        assertTrue(s.canUndoChill(session))

        val later = ProgramSchedule(chilled, start.plusDays(2))
        assertFalse(later.canChill(later.sessionAt(2)!!))
        val nextWeek = ProgramSchedule(chilled, start.plusDays(7))
        assertTrue(nextWeek.canChill(nextWeek.sessionAt(7)!!))

        val done = ProgramSchedule(chilled.doRequired(0..0), start.plusDays(1))
        assertEquals(1, done.progress().streak)
    }

    @Test
    fun `boss is harder than a burst, and three quarters saves the day without points`() {
        val far = ProgramSchedule(StoredProgram("boss", plan(boss = true), start.toString()), start.plusDays(200))
        val boss = far.sessionsThrough(100).first { it.isBoss }
        val b = boss.index
        // Rien de validé avant : seuls les crans du Boss s'ajoutent, plus qu'un Burst.
        assertEquals(10 + BOSS_STEPS, boss.prescriptions[1].reps)
        assertTrue(BOSS_STEPS > BURST_STEPS)
        assertFalse(far.canChill(boss))

        val before = StoredProgram("boss", plan(boss = true), start.toString()).doRequired(0 until b)
        val day = boss.date.plusDays(1)

        val saved = ProgramSchedule(before.doRequired(b..b, only = 3), day)
        val savedBoss = saved.sessionAt(b)!!
        assertTrue(saved.isSaved(savedBoss))
        assertEquals(SessionStatus.SAVED, saved.status(savedBoss))
        assertEquals(b, saved.progress().streak)
        val savedStats = Stats.of(saved)
        assertEquals(0, savedStats.bossDefeated)
        assertEquals(b, savedStats.sessionsDone)

        val half = ProgramSchedule(before.doRequired(b..b, only = 2), day)
        assertEquals(SessionStatus.PARTIAL, half.status(half.sessionAt(b)!!))
        assertEquals(0, half.progress().streak)

        val won = ProgramSchedule(before.doRequired(b..b), day)
        assertEquals(b + 1, won.progress().streak)
        assertEquals(1, Stats.of(won).bossDefeated)
    }

    @Test
    fun `completed bursts are worth points`() {
        val program = StoredProgram("p", plan(), start.toString()).burst(0).doRequired(0..0)
        val card = Social.card("id", "Moi", listOf(program), start.plusDays(1), 0L, 1)
        assertEquals(1, card.bursts)
        assertEquals(1 + FriendCard.BURST_POINTS, card.score)
    }

    @Test
    fun `friends' new bosses are announced once, never on first sight`() {
        val bob = FriendCard(id = "b", pseudo = "Bob", date = "2026-10-27", updatedAt = 1, bossDefeated = 2)
        val al = FriendCard(id = "a", pseudo = "Al", date = "2026-10-27", updatedAt = 2, bossDefeated = 1)
        assertEquals(emptyList<String>(), Reminders.newBosses(listOf(bob, al), emptyMap()))
        val seen = Reminders.seenBosses(listOf(bob, al), emptyMap())
        assertEquals(emptyList<String>(), Reminders.newBosses(listOf(bob, al), seen))
        val news = Reminders.newBosses(listOf(bob.copy(bossDefeated = 3), al), seen)
        assertEquals(listOf("Bob"), news)
        assertEquals("💀 Boss vaincu", Reminders.bossMessage(news)!!.title)
    }
}
