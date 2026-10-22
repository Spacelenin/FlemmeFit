package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CountersTest {

    private val start = LocalDate.of(2026, 10, 19)

    private val plan = Plan(
        version = 1, name = "Compteurs", repeat = REPEAT_FOREVER, boss = false, deloadEvery = 0,
        weeks = listOf(
            PlanWeek(
                days = listOf(
                    PlanDay(
                        1,
                        exercises = listOf(
                            Exercise("Pompes lentes", sets = 3, reps = "10", counter = "Pompes"),
                            Exercise("Pompes", sets = 2, reps = "15", counter = "Pompes"),
                            Exercise("Shadow", sets = 2, duration = "3min", counter = "Boxe", weights = WeightsOption(default = true)),
                            Exercise("Étirements", duration = "5min"),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `exercises sharing a counter add up, what is not counted stays out`() {
        val program = StoredProgram("p", plan, start.toString())
            .setSessionDone(0, 4, true)
            .saveLog(7, 0, ExerciseLog.ofSets(listOf(10, 12, 8), allOut = false), complete = false)
        val counters = Stats.of(ProgramSchedule(program, start.plusDays(7))).counters
        val pushups = counters.first { it.name == "Pompes" }
        assertEquals(30 + 30 + 30, pushups.reps)
        val boxing = counters.first { it.name == "Boxe" }
        assertEquals(360, boxing.seconds)
        assertEquals(360, boxing.weightedSeconds)
        assertEquals(2, counters.size)
        // Les compteurs en reps passent avant ceux en temps.
        assertEquals(listOf("Pompes", "Boxe"), counters.map { it.name })
    }

    @Test
    fun `counters unlock at a thousand reps or ten hours`() {
        assertFalse(Counter.unlocked(listOf(Counter("Pompes", reps = 999), Counter("Boxe", seconds = 35_999))))
        assertTrue(Counter.unlocked(listOf(Counter("Pompes", reps = 1000))))
        assertEquals("Boxe", Counter.milestone(listOf(Counter("Pompes", reps = 10), Counter("Boxe", seconds = 36_000)))?.name)
    }

    @Test
    fun `equivalences give a feel for the totals`() {
        assertEquals("23 fois le défi des 100 pompes", Counter("Pompes", reps = 2345).equivalence())
        assertEquals("120 m d'ascension, 40 étages (36 % de la tour Eiffel)", Counter("Tractions", reps = 300).equivalence())
        assertEquals("26 rounds de 3 min, soit 2 combats de 12 rounds", Counter("Boxe", seconds = 26 * 180 + 30).equivalence())
        assertEquals("40 chansons découpées, ou 1 film de 2 h", Counter("Beat Saber", seconds = 2 * 3600).equivalence())
        assertNull(Counter("Pompes", reps = 50).equivalence())
        assertNull(Counter("Autre", reps = 5000).equivalence())
    }

    @Test
    fun `counters of several programs merge by name`() {
        val merged = Counter.merge(listOf(listOf(Counter("Pompes", reps = 10)), listOf(Counter("Pompes", reps = 5), Counter("Boxe", seconds = 60))))
        assertEquals(listOf(Counter("Pompes", reps = 15), Counter("Boxe", seconds = 60)), merged)
    }
}
