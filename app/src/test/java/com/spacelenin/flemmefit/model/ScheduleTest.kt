package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScheduleTest {

    private val start = LocalDate.of(2026, 10, 19)

    private fun day(n: Int, vararg names: String) = PlanDay(day = n, exercises = names.map { Exercise(it) })

    private val plan = Plan(
        version = 1,
        name = "Test",
        repeat = 2,
        boss = false,
        weeks = listOf(
            PlanWeek(days = listOf(day(1, "a", "b"), day(3, "c"))),
            PlanWeek(days = listOf(day(2, "d"))),
        ),
    )

    private fun program(postpones: List<Int> = emptyList(), done: Set<String> = emptySet(), p: Plan = plan) =
        StoredProgram("id", p, start.toString(), postpones = postpones, done = done)

    private fun ProgramSchedule.all() = sessionsThrough(totalDays!! - 1)

    @Test
    fun `sessions are laid out across weeks and cycles`() {
        val s = ProgramSchedule(program(), start)
        assertEquals(28, s.totalDays)
        assertEquals(listOf(0, 2, 8, 14, 16, 22), s.all().map { it.index })
        assertEquals(listOf(1, 1, 1, 2, 2, 2), s.all().map { it.cycle })
        assertEquals(listOf(1, 1, 2, 1, 1, 2), s.all().map { it.week })
        assertEquals(start.plusDays(8), s.all()[2].date)
        assertEquals(start.plusDays(27), s.endDate)
    }

    @Test
    fun `postponing shifts the day and everything after`() {
        val s = ProgramSchedule(program().postpone(2), start)
        assertEquals(start, s.all()[0].date)
        assertEquals(start.plusDays(3), s.all()[1].date)
        assertEquals(start.plusDays(9), s.all()[2].date)
        assertEquals(start.plusDays(28), s.endDate)
        assertNull(s.sessionOn(start.plusDays(2)))
        assertNull(s.indexOn(start.plusDays(2)))
        assertEquals(2, s.sessionOn(start.plusDays(3))?.index)
        assertEquals(1, s.lastIndexOnOrBefore(start.plusDays(2)))
    }

    @Test
    fun `postponing twice and undoing`() {
        val p = program().postpone(2).postpone(2).postpone(8)
        assertEquals(2, p.postponeCount(2))
        assertEquals(start.plusDays(11), ProgramSchedule(p, start).all()[2].date)
        val undone = p.undoPostpone(2)
        assertEquals(listOf(2, 8), undone.postpones)
        assertEquals(start.plusDays(3), ProgramSchedule(undone, start).all()[1].date)
        assertEquals(undone, undone.undoPostpone(5))
    }

    @Test
    fun `progress counts exercises sessions and streak`() {
        val today = start.plusDays(8)
        var p = program()
        p = p.setSessionDone(0, 2, true).toggle(2, 0)
        var progress = ProgramSchedule(p, today).progress()
        assertEquals(3, progress.doneExercises)
        assertEquals(8, progress.totalExercises)
        assertEquals(2, progress.doneSessions)
        assertEquals(2, progress.streak) // séance du jour (index 8) pas encore faite : la série tient
        assertEquals(8, progress.nextSession?.index)

        p = p.toggle(2, 0)
        progress = ProgramSchedule(p, today).progress()
        assertEquals(0, progress.streak)
        assertEquals(1, progress.missedSessions)
    }

    @Test
    fun `status reflects completion partial logs and date`() {
        val today = start.plusDays(2)
        val p = program().toggle(0, 0)
        val s = ProgramSchedule(p, today)
        assertEquals(SessionStatus.PARTIAL, s.status(s.all()[0]))
        assertEquals(SessionStatus.PENDING, s.status(s.all()[1]))
        val tomorrow = ProgramSchedule(p, today.plusDays(1))
        assertEquals(SessionStatus.MISSED, tomorrow.status(tomorrow.all()[1]))
        val full = ProgramSchedule(p.toggle(0, 1), today)
        assertEquals(SessionStatus.DONE, full.status(full.all()[0]))

        val logged = ProgramSchedule(program().saveLog(2, 0, ExerciseLog(reps = 3), complete = false), today.plusDays(1))
        assertEquals(SessionStatus.PARTIAL, logged.status(logged.all()[1]))
        assertEquals(ExerciseState.PARTIAL, logged.exerciseState(logged.all()[1], 0))
    }

    @Test
    fun `forever programs never end`() {
        val forever = plan.copy(repeat = REPEAT_FOREVER)
        val today = start.plusDays(400)
        val s = ProgramSchedule(program(p = forever).postpone(30), today)
        assertNull(s.totalDays)
        assertNull(s.endDate)
        assertTrue(s.isInProgram(start.plusYears(5)))
        val far = s.sessionOn(start.plusDays(7 * 100 + 1)) // décalé d'un jour par le report
        assertEquals(1, far?.day?.day)
        assertEquals(51, far?.cycle)
        val progress = s.progress()
        // 3 séances par cycle de 2 semaines, jusqu'à aujourd'hui seulement.
        assertTrue(progress.totalSessions in 85..87)
        assertTrue(progress.nextSession!!.date >= today)
    }

    @Test
    fun `state round trips through json`() {
        val state = AppState(
            programs = listOf(
                program(listOf(3), setOf("0:1")).saveLog(2, 0, ExerciseLog(sets = 2, reps = 5, max = 9), complete = false),
                program(p = plan.copy(repeat = REPEAT_FOREVER)),
            ),
        )
        val json = PlanParser.json.encodeToString(AppState.serializer(), state)
        assertTrue(json.contains("\"forever\""))
        assertEquals(state, PlanParser.json.decodeFromString(AppState.serializer(), json))
    }

    @Test
    fun `old state without new fields still loads`() {
        val old = """{"schemaVersion":1,"programs":[{"id":"x","plan":{"format":"flemmefit","version":1,"name":"N","repeat":2,
            "weeks":[{"days":[{"day":1,"exercises":[{"name":"A"}]}]}]},"startDate":"2026-10-19","done":["0:0"]}]}"""
        val state = PlanParser.json.decodeFromString(AppState.serializer(), old)
        assertTrue(state.programs.single().logs.isEmpty())
        assertTrue(state.programs.single().plan.boss)
    }
}
