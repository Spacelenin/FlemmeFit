package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatsTest {

    private val start = LocalDate.of(2026, 10, 19) // un lundi

    private val plan = Plan(
        version = 1, name = "S", repeat = REPEAT_FOREVER, boss = false,
        weeks = listOf(
            PlanWeek(
                days = listOf(
                    PlanDay(1, exercises = listOf(Exercise("Pompes", sets = 3, reps = "10"), Exercise("Gainage", sets = 2, duration = "30s"))),
                    PlanDay(3, exercises = listOf(Exercise("pompes", sets = 2, reps = "12"), Exercise("Beat Saber"))),
                ),
            ),
        ),
    )

    @Test
    fun `totals per exercise use planned values, logs and maxes`() {
        val program = StoredProgram("id", plan, start.toString())
            .toggle(0, 0) // 3 × 10
            .toggle(0, 1) // 2 × 30s
            .saveLog(2, 0, ExerciseLog(reps = 9), complete = false) // partiel : 2 × 9
            .toggle(2, 1)
            .saveLog(7, 0, ExerciseLog(max = 15), complete = true) // max noté, pas une série à fond
        val stats = Stats.of(ProgramSchedule(program, start.plusDays(8)))

        val pushups = stats.exercises.first { it.key == "pompes" }
        assertEquals(3, pushups.times)
        assertEquals(30 + 18 + 30, pushups.totalReps)
        assertEquals(8, pushups.totalSets)
        assertEquals(15, pushups.bestReps)
        val plank = stats.exercises.first { it.key == "gainage" }
        assertEquals(60, plank.totalSeconds)
        assertEquals(1, stats.exercises.first { it.key == "beat saber" }.times)

        assertEquals(1, stats.sessionsDone)
        assertEquals(3, stats.history.size)
        assertEquals(Stats.CHART_WEEKS, stats.weeks.size)
        assertEquals(3 + 2 + 2 + 1, stats.weeks[Stats.CHART_WEEKS - 2].sets)
    }

    @Test
    fun `merge adds programs and fuses same exercise`() {
        val today = start.plusDays(3)
        val a = Stats.of(ProgramSchedule(StoredProgram("a", plan, start.toString()).toggle(0, 0), today))
        val b = Stats.of(ProgramSchedule(StoredProgram("b", plan, start.toString()).toggle(2, 0), today))
        val all = Stats.merge(listOf(a, b), today)
        assertEquals(30 + 24, all.exercises.first { it.key == "pompes" }.totalReps)
        assertEquals(2, all.history.size)
    }
}
