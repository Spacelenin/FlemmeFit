package com.spacelenin.flemmefit.model

import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Séance en cours sur un exo fait une série par côté : une seule série notée ne doit rien casser. */
class CrashTest {
    @Test
    fun `one side logged mid-session does not crash the schedule`() {
        val plan = (PlanParser.parse(File("../examples/routine-maison.json").readText()) as ParseResult.Success).plan
        val start = LocalDate.of(2026, 10, 26)
        val saturday = 5
        var p = StoredProgram("p", plan, start.toString(), tests = mapOf("pompes" to 20, "abdos" to 25, "gainage" to 60, "tractions" to 8, "squats" to 25))
        val today = start.plusDays(saturday.toLong())
        val session = ProgramSchedule(p, today).sessionAt(saturday)!!
        val e = session.prescriptions.indexOfFirst { it.key == "battements-lateraux" }
        val g = GuidedExercise(session.prescriptions[e])
        // Première série (côté gauche) faite, le reste pas encore.
        val (log, complete) = g.result(listOf(5))
        p = p.saveLog(saturday, e, log, complete)
        val s = ProgramSchedule(p, today)
        assertNotNull(s.sessionAt(saturday + 7))
        Stats.of(s)
        s.adaptations()
        s.balances()
        ProgramSchedule.adaptationChanges(p, saturday, today)
        // Puis toute la séance, et un côté seulement les jours suivants.
        val (full, done) = g.result(listOf(5, 5, 5, 5, 5, 5))
        p = p.saveLog(saturday, e, full, done)
        ProgramSchedule(p, today.plusDays(30)).let { Stats.of(it); it.adaptations(); it.balances(); it.sessionAt(40) }
    }

    @Test
    fun `partial logs of any length on every exercise never crash`() {
        val plan = (PlanParser.parse(File("../examples/routine-maison.json").readText()) as ParseResult.Success).plan
        val start = LocalDate.of(2026, 9, 7)
        val strong = mapOf("pompes" to 60, "abdos" to 60, "gainage" to 180, "tractions" to 12, "squats" to 40)
        for (tests in listOf(strong, mapOf("pompes" to 3))) {
            var p = StoredProgram("p", plan, start.toString(), tests = tests)
            for (i in 0 until 7 * 10) {
                // Un jour sur sept en Chill, un autre en Burst : variantes et côtés compris.
                if (i % 7 == 2) p = p.chill(i)
                if (i % 7 == 4) p = p.burst(i)
                val today = start.plusDays(i.toLong())
                val session = ProgramSchedule(p, today).sessionAt(i) ?: continue
                session.prescriptions.forEachIndexed { e, pr ->
                    val g = GuidedExercise(pr)
                    // Un exo sur trois s'arrête en route, à une longueur qui change d'un jour à l'autre.
                    val stop = if ((i + e) % 3 == 0) (i + e) % g.sets else g.sets
                    val values = (0 until stop).map { g.suggested(it).coerceAtLeast(1) + (i + e) % 2 }
                    if (values.isEmpty()) return@forEachIndexed
                    val (log, done) = g.result(values)
                    p = p.saveLog(i, e, log, done)
                    if ((i + e) % 4 == 0) p = p.withExerciseFeeling(i, e, (i + e) % 3)
                    val s = ProgramSchedule(p, today)
                    s.sessionAt(i + 7)
                    Stats.of(s)
                    s.adaptations()
                    s.balances()
                    ProgramSchedule.adaptationChanges(p, i, today)
                }
            }
        }
    }

    @Test
    fun `more than planned on one side mid-session`() {
        val plan = (PlanParser.parse(File("../examples/routine-maison.json").readText()) as ParseResult.Success).plan
        for (startOffset in 0L..6L) for (stop in 1..6) for (v in listOf(8, 1, 20)) {
            val start = LocalDate.of(2026, 10, 26).minusDays(startOffset * 7)
            val saturday = (startOffset * 7 + 5).toInt()
            var p = StoredProgram("p", plan, start.toString(), tests = mapOf("pompes" to 12, "abdos" to 15, "gainage" to 40, "tractions" to 5, "squats" to 20))
            val today = start.plusDays(saturday.toLong())
            val session = ProgramSchedule(p, today).sessionAt(saturday) ?: continue
            val e = session.prescriptions.indexOfFirst { it.exercise.perSide != null }
            if (e < 0) continue
            val g = GuidedExercise(session.prescriptions[e])
            val (log, complete) = g.result(List(stop) { if (it == 0) v else g.suggested(it) })
            p = p.saveLog(saturday, e, log, complete)
            val s = ProgramSchedule(p, today)
            s.allSessionsOrUpTo(today.plusDays(30)); Stats.of(s); s.adaptations(); s.balances()
            ProgramSchedule.adaptationChanges(p, saturday, today)
            GuidedExercise(ProgramSchedule(p, today).sessionAt(saturday)!!.prescriptions[e]).let { gg -> (0 until gg.sets).forEach { gg.suggested(it); gg.restAfter(it); gg.countdown(it) } }
        }
    }
}
