package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class SidesTest {

    private val start = LocalDate.of(2026, 10, 19)

    private val hipDips = Exercise("Hip dips", id = "hip", sets = 3, reps = "6", rest = "45s", perSide = "côté", progression = Progression(reps = 1, every = 2))

    /** Hip dips les lundis et jeudis, sans décharge ni Boss. */
    private val plan = Plan(
        version = 1, name = "S", repeat = REPEAT_FOREVER, deloadEvery = 0, boss = false,
        weeks = listOf(PlanWeek(days = listOf(PlanDay(1, exercises = listOf(hipDips)), PlanDay(4, exercises = listOf(hipDips))))),
    )

    private fun day(n: Int) = n / 2 * 7 + if (n % 2 == 0) 0 else 3

    private fun at(program: StoredProgram, n: Int) = ProgramSchedule(program, start.plusDays(400)).sessionAt(day(n))!!.prescriptions[0]

    /** Ce qui est fait : chaque côté va jusqu'à sa capacité, à chaque série. */
    private fun StoredProgram.does(n: Int, left: Int, right: Int): StoredProgram {
        val pr = at(this, n)
        val sided = pr.sided!!
        val values = sided.order.map { side -> if (side == Side.LEFT) left else right }
        return saveLog(day(n), 0, ExerciseLog.ofSets(values, pr.allOut, sided.order), complete = pr.isMetBy(ExerciseLog.ofSets(values, pr.allOut, sided.order)))
    }

    @Test
    fun `one set per side, alternating, labels and summary`() {
        val pr = at(StoredProgram("p", plan, start.toString()), 0)
        val sided = pr.sided!!
        assertEquals(listOf(Side.LEFT, Side.RIGHT, Side.LEFT, Side.RIGHT, Side.LEFT, Side.RIGHT), sided.order)
        assertEquals(6, pr.sets)
        assertEquals("3 × 6 par côté · repos 45s", pr.summary())
        assertEquals("Côté gauche", sided.label(Side.LEFT))
        assertEquals("Jambe droite", Sides.label("jambe", Side.RIGHT))
        assertEquals("Bras droit", Sides.label("bras", Side.RIGHT))
        val g = GuidedExercise(pr, 0)
        assertEquals(Side.RIGHT, g.sideOf(1))
        assertEquals(Sides.SWITCH_REST, g.restAfter(0)) // gauche → droite : court
        assertEquals(45, g.restAfter(1)) // fin de paire : repos normal
        assertEquals(listOf(Side.LEFT, Side.RIGHT, Side.RIGHT), Sides.order(1, 2, Side.LEFT))
        assertEquals(listOf(Side.RIGHT, Side.LEFT, Side.RIGHT, Side.LEFT, Side.RIGHT), Sides.order(2, 3, Side.RIGHT))
    }

    @Test
    fun `log keeps each side and averages the max`() {
        val log = ExerciseLog.ofSets(listOf(6, 4, 7, 5), allOut = true, order = Sides.order(2, 2))
        assertEquals("LRLR", log.sides)
        assertEquals(listOf(6, 7), log.valuesOf(Side.LEFT))
        assertEquals(listOf(4, 5), log.valuesOf(Side.RIGHT))
        assertEquals(6, log.max) // (7 + 5) / 2
        assertEquals(listOf(1, 2), ExerciseLog(perSet = listOf(1, 2)).valuesOf(Side.LEFT)) // ancienne saisie : tout
    }

    @Test
    fun `small gap stays even, a moderate one makes the weak side catch up`() {
        var p = StoredProgram("p", plan, start.toString())
        repeat(6) { n -> p = p.does(n, 10, 10) }
        assertEquals(BalanceMode.EVEN, at(p, 6).sided!!.mode)
        // Côté droit à 85 % du gauche, plusieurs séances : rattrapage du côté droit.
        var q = StoredProgram("p", plan, start.toString())
        repeat(8) { n -> q = q.does(n, 13, 11) }
        val pr = at(q, 8)
        val sided = pr.sided!!
        assertTrue(sided.toString(), sided.mode != BalanceMode.EVEN)
        assertEquals(Side.RIGHT, sided.weak)
        assertEquals(Side.RIGHT, sided.order.first()) // le faible commence
        assertEquals(sided.setsOf(Side.LEFT) + 1, sided.setsOf(Side.RIGHT)) // et a une série de plus
        val balance = ProgramSchedule(q, start.plusDays(400)).balances().single()
        assertTrue(balance.gapPercent > 0)
    }

    @Test
    fun `big gap freezes the strong side`() {
        var p = StoredProgram("p", plan, start.toString())
        repeat(8) { n -> p = p.does(n, 15, 10) }
        val sided = at(p, 8).sided!!
        assertEquals(BalanceMode.WEAK_ONLY, sided.mode)
        val strongNow = sided.left!!
        repeat(4) { k -> p = p.does(8 + k, 15, 10) }
        assertTrue(at(p, 12).sided!!.left!! <= strongNow) // le fort ne monte plus
    }

    @Test
    fun `balance switches only after three sessions in a row`() {
        var s = BalanceState()
        s = Sides.next(s, 0.15, Side.LEFT, 2, 2.0, 10, true)
        s = Sides.next(s, 0.15, Side.LEFT, 2, 2.0, 10, true)
        assertEquals(BalanceMode.EVEN, s.mode)
        s = Sides.next(s, 0.05, Side.LEFT, 2, 2.0, 10, true) // une séance revenue : on recompte
        s = Sides.next(s, 0.15, Side.LEFT, 2, 2.0, 10, true)
        s = Sides.next(s, 0.15, Side.LEFT, 2, 2.0, 10, true)
        assertEquals(BalanceMode.EVEN, s.mode)
        s = Sides.next(s, 0.15, Side.LEFT, 2, 2.0, 10, true)
        assertEquals(BalanceMode.CATCH_UP, s.mode)
        assertEquals(Side.LEFT, s.weak)
        assertEquals(2.0 / 12, s.rate, 1e-9) // 2 crans en 6 semaines à 2 séances par semaine
        assertNull(s.frozen)
        assertFalse(Sides.next(s, 0.15, Side.LEFT, 2, 2.0, 10, true).boost == 0.0)
    }

    @Test
    fun `routine has the new exercises and the perche needs three tests`() {
        val plan = (PlanParser.parse(File("../examples/routine-maison.json").readText()) as ParseResult.Success).plan
        val saturdays = plan.weeks.map { w -> w.days.first { it.day == 6 }.exercises.map { it.key } }
        assertTrue(saturdays[0].contains("battements-lateraux"))
        assertTrue(saturdays[1].contains("coups-pied-lateraux"))
        val perche = plan.variants.first { it.id == "perche" }
        assertEquals("tractions", perche.family)
        assertEquals(mapOf("tractions" to 10, "gainage" to 60, "pompes" to 20), perche.requires)
        assertEquals("bras", plan.variants.first { it.id == "pompes-une-main" }.perSide)
    }

    @Test
    fun `end of session announces the side falling behind`() {
        var p = StoredProgram("p", plan, start.toString())
        var announced: List<AdaptChange> = emptyList()
        var n = 0
        while (announced.none { it.kind == AdaptChange.Kind.BEHIND } && n < 10) {
            p = p.does(n, 13, 11)
            announced = ProgramSchedule.adaptationChanges(p, day(n), start.plusDays(400))
            n++
        }
        val behind = announced.first { it.kind == AdaptChange.Kind.BEHIND }
        assertEquals("↔ Hip dips : côté droit en retard. Il commence et fait une série de plus", behind.message)
        assertEquals(3, n) // dès que l'écart a tenu 3 séances
    }

    @Test
    fun `a one-side variant catches up at the pace of its own passages`() {
        val routine = (PlanParser.parse(File("../examples/routine-maison.json").readText()) as ParseResult.Success).plan
        val tests = mapOf("pompes" to 60, "abdos" to 60, "gainage" to 180, "tractions" to 12, "squats" to 40)
        var p = StoredProgram("p", routine, start.toString(), tests = tests)
        val today = start.plusDays(7L * 60)
        val log = StringBuilder()
        var worst = 0
        var pass = 0
        repeat(14) {
            val s = ProgramSchedule(p, today)
            val day = (0 until 7 * 60).mapNotNull(s::sessionAt).firstOrNull { d ->
                d.variantIndex != null && d.prescriptions[d.variantIndex!!].variant!!.id == "pompes-une-main" && p.logOf(d.index, d.variantIndex!!) == null
            } ?: return@repeat
            val pr = day.prescriptions[day.variantIndex!!]
            val sided = pr.sided!!
            log.append("${sided.left}/${sided.right} ${sided.mode} ; ")
            worst = maxOf(worst, (sided.right ?: 0) - (sided.left ?: 0))
            // Trois passages où le bras droit ne fait que 60 %, puis chaque bras tient ce qui lui est demandé.
            val values = sided.order.mapIndexed { i, side ->
                val planned = sided.plannedAt(i)!!
                if (pass < 3 && side == Side.RIGHT) (planned * 0.6).toInt().coerceAtLeast(1) else planned
            }
            p = p.saveLog(day.index, day.variantIndex!!, ExerciseLog.ofSets(values, false, sided.order), complete = true)
            pass++
        }
        assertTrue(log.toString(), pass >= 8)
        // Le rattrapage se fait par petits crans : le bras faible ne passe jamais nettement devant l'autre.
        assertTrue(log.toString(), worst <= 1)
    }
}
