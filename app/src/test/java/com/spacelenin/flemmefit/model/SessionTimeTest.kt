package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SessionTimeTest {

    private val start = LocalDate.of(2026, 11, 2)

    @Test
    fun `estimate counts warm-up, effort, rest and transitions, rounded up`() {
        val plan = Plan(
            version = 1, name = "T", repeat = REPEAT_FOREVER, deloadEvery = 0, boss = false,
            weeks = listOf(
                PlanWeek(
                    days = listOf(
                        PlanDay(
                            1,
                            exercises = listOf(
                                Exercise("Échauffement", duration = "5min", optional = true),
                                Exercise("Pompes", sets = 3, reps = "10", rest = "60s"),
                                Exercise("Pompes lentes (tempo)", sets = 2, reps = "5", rest = "60s"),
                                Exercise("Gainage", sets = 3, duration = "30s", rest = "45s"),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val session = ProgramSchedule(StoredProgram("p", plan, start.toString()), start).sessionAt(0)!!
        val e = SessionTime.estimate(session)
        assertEquals(300, e.warmup)
        assertEquals(3 * 10 * 3 + 2 * 5 * 6 + 3 * 30, e.work) // 90 + 60 + 90
        assertEquals(2 * 60 + 60 + 2 * 45, e.rest)
        assertEquals(3 * 30, e.transitions)
        assertEquals(300 + 240 + 270 + 90, e.total) // 900 s
        assertEquals(15, e.minutes)
        // Repos en plus : +50 % sur les repos seulement (arrondi à 5 s : 45 s → 70 s).
        assertEquals(2 * 90 + 90 + 2 * 70, SessionTime.estimate(session, restBonus = 50).rest)
        assertEquals(1, SessionEstimate.minutesOf(1))
        assertEquals(15, SessionEstimate.minutesOf(900))
        assertEquals(16, SessionEstimate.minutesOf(901))
    }
}
