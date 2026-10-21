package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlanUpdateTest {

    private fun plan(vararg names: String, tests: List<PlanTest> = emptyList()) = Plan(
        version = 1, name = "Maison", repeat = REPEAT_FOREVER, tests = tests,
        weeks = listOf(PlanWeek(days = listOf(PlanDay(1, exercises = names.map { Exercise(it) })))),
    )

    @Test
    fun `progress follows exercises when the routine changes`() {
        val old = StoredProgram(
            "p", plan("Pompes", "Shadow", "Gainage", tests = listOf(PlanTest("pompes", "Pompes"), PlanTest("vieux", "Vieux"))),
            "2026-10-19", tests = mapOf("pompes" to 8, "vieux" to 3), postpones = listOf(7),
        )
            .toggle(0, 0) // Pompes, semaine 1
            .saveLog(0, 2, ExerciseLog(seconds = 20), complete = false) // Gainage, partiel
            .toggle(7, 1) // Shadow, semaine 2

        // Nouvel ordre, Shadow retiré, un exercice ajouté.
        val newPlan = plan("Gainage", "Pompes", "Beat Saber", tests = listOf(PlanTest("pompes", "Pompes")))
        assertEquals(1, old.planUpdateLoss(newPlan))

        val updated = old.withPlan(newPlan)
        assertEquals(newPlan, updated.plan)
        assertEquals(setOf("0:1"), updated.done)
        assertEquals(mapOf("0:0" to ExerciseLog(seconds = 20)), updated.logs)
        assertEquals(mapOf("pompes" to 8), updated.tests)
        assertEquals(listOf(7), updated.postpones)
        assertEquals("2026-10-19", updated.startDate)
    }
}
