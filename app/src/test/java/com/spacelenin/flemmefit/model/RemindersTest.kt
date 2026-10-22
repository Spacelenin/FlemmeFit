package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RemindersTest {

    private val start = LocalDate.of(2026, 10, 19)

    private val plan = Plan(
        version = 1, name = "Maison", repeat = REPEAT_FOREVER, boss = false,
        weeks = listOf(PlanWeek(days = listOf(PlanDay(1, title = "Pompes", exercises = listOf(Exercise("A"), Exercise("B")))))),
    )

    private fun program(p: Plan = plan) = StoredProgram("id", p, start.toString())

    @Test
    fun `daily reminder only on unfinished session days`() {
        assertEquals(1, Reminders.daily(listOf(program()), start).size)
        assertTrue(Reminders.daily(listOf(program()), start.plusDays(1)).isEmpty()) // repos
        assertTrue(Reminders.daily(listOf(program().setSessionDone(0, 2, true)), start).isEmpty())
        val text = Reminders.daily(listOf(program()), start).single().text
        assertFalse(text, text.contains("{"))
        assertEquals(text, Reminders.daily(listOf(program()), start).single().text) // stable
    }

    @Test
    fun `evening reminder counts remaining exercises and streak`() {
        val p = program().toggle(0, 0).setSessionDone(7, 2, true).setSessionDone(14, 1, true)
        val msg = Reminders.evening(listOf(p.setSessionDone(14, 2, true).toggle(14, 1)), start.plusDays(14)).single()
        assertTrue(msg.text, msg.text.contains("1"))
        assertTrue(msg.text, msg.text.contains("1 🔥 d'affilée"))
        assertTrue(Reminders.evening(listOf(p.setSessionDone(14, 2, true)), start.plusDays(14)).isEmpty())
    }

    @Test
    fun `boss is announced the day before and on the day`() {
        val program = program(plan.copy(boss = true))
        val schedule = ProgramSchedule(program, start.plusDays(7L * 20))
        val boss = schedule.sessionsThrough(7 * 20).first { it.isBoss }
        val eve = Reminders.daily(listOf(program), boss.date.minusDays(1)).single()
        assertTrue(eve.title, eve.title.contains("Boss demain"))
        assertTrue(Reminders.daily(listOf(program), boss.date).single().title.startsWith("💀"))
    }
}
