package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ScheduleTest {

    private val start = LocalDate.of(2026, 10, 19)

    private fun day(n: Int, vararg names: String) = PlanDay(day = n, exercises = names.map { Exercise(it) })

    private val plan = Plan(
        version = 1,
        name = "Test",
        repeat = 2,
        weeks = listOf(
            PlanWeek(days = listOf(day(1, "a", "b"), day(3, "c"))),
            PlanWeek(days = listOf(day(2, "d"))),
        ),
    )

    private fun program(postpones: List<Int> = emptyList(), done: Set<String> = emptySet()) =
        StoredProgram("id", plan, start.toString(), postpones = postpones, done = done)

    @Test
    fun `sessions are laid out across weeks and cycles`() {
        val s = ProgramSchedule(program())
        assertEquals(28, s.totalDays)
        assertEquals(listOf(0, 2, 8, 14, 16, 22), s.sessions.map { it.index })
        assertEquals(listOf(1, 1, 1, 2, 2, 2), s.sessions.map { it.cycle })
        assertEquals(listOf(1, 1, 2, 1, 1, 2), s.sessions.map { it.week })
        assertEquals(start.plusDays(8), s.sessions[2].date)
        assertEquals(start.plusDays(27), s.endDate)
    }

    @Test
    fun `postponing shifts the day and everything after`() {
        val s = ProgramSchedule(program().postpone(2))
        assertEquals(start, s.sessions[0].date)
        assertEquals(start.plusDays(3), s.sessions[1].date)
        assertEquals(start.plusDays(9), s.sessions[2].date)
        assertEquals(start.plusDays(28), s.endDate)
        assertNull(s.sessionOn(start.plusDays(2)))
        assertEquals(2, s.sessionOn(start.plusDays(3))?.index)
    }

    @Test
    fun `postponing twice and undoing`() {
        val p = program().postpone(2).postpone(2).postpone(8)
        assertEquals(2, p.postponeCount(2))
        assertEquals(start.plusDays(11), ProgramSchedule(p).sessions[2].date)
        val undone = p.undoPostpone(2)
        assertEquals(listOf(2, 8), undone.postpones)
        assertEquals(start.plusDays(3), ProgramSchedule(undone).sessions[1].date)
        assertEquals(undone, undone.undoPostpone(5))
    }

    @Test
    fun `progress counts exercises sessions and streak`() {
        val today = start.plusDays(8)
        var p = program()
        p = p.setSessionDone(0, 2, true).toggle(2, 0)
        var progress = ProgramSchedule(p).progress(today)
        assertEquals(3, progress.doneExercises)
        assertEquals(8, progress.totalExercises)
        assertEquals(2, progress.doneSessions)
        assertEquals(2, progress.streak) // séance du jour (index 8) pas encore faite : la série tient
        assertEquals(8, progress.nextSession?.index)

        p = p.toggle(2, 0)
        progress = ProgramSchedule(p).progress(today)
        assertEquals(0, progress.streak)
        assertEquals(1, progress.missedSessions)
    }

    @Test
    fun `status reflects completion and date`() {
        val today = start.plusDays(2)
        val p = program().toggle(0, 0)
        val s = ProgramSchedule(p)
        assertEquals(SessionStatus.PARTIAL, s.status(s.sessions[0], today))
        assertEquals(SessionStatus.PENDING, s.status(s.sessions[1], today))
        assertEquals(SessionStatus.MISSED, s.status(s.sessions[1], today.plusDays(1)))
        val full = ProgramSchedule(p.toggle(0, 1))
        assertEquals(SessionStatus.DONE, full.status(full.sessions[0], today))
    }

    @Test
    fun `state round trips through json`() {
        val state = AppState(programs = listOf(program(listOf(3), setOf("0:1"))))
        val json = PlanParser.json.encodeToString(AppState.serializer(), state)
        assertEquals(state, PlanParser.json.decodeFromString(AppState.serializer(), json))
    }
}
