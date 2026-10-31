package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class VariantsTest {

    private val start = LocalDate.of(2026, 10, 19)

    private val routine: Plan by lazy {
        val file = listOf("../examples/routine-maison.json", "examples/routine-maison.json").map { java.io.File(it) }.first { it.exists() }
        (PlanParser.parse(file.readText()) as ParseResult.Success).plan
    }

    private val strong = mapOf("pompes" to 60, "tractions" to 10, "abdos" to 60, "gainage" to 180, "squats" to 40)

    private fun schedule(program: StoredProgram, today: LocalDate = start.plusYears(1)) = ProgramSchedule(program, today)

    @Test
    fun `the routine describes its families and variants`() {
        assertEquals(9, routine.variants.size)
        val keys = routine.weeks.flatMap { w -> w.days.flatMap { d -> d.exercises.map { it.key } } }.toSet()
        routine.families.forEach { f -> f.exercises.forEach { assertTrue("${f.id} : $it", it in keys) } }
    }

    @Test
    fun `every variant comes back each cycle, never in the light week, at most one per session`() {
        val s = schedule(StoredProgram("p", routine, start.toString(), tests = strong))
        for (cycle in 0 until 3) {
            val sessions = (cycle * 28 until (cycle + 1) * 28).mapNotNull(s::sessionAt)
            val picked = sessions.mapNotNull { it.variantIndex?.let { i -> it.prescriptions[i].variant!!.id } }
            // Les jambes (variante à la place de tout l'exercice) ont leur propre rythme, testé à part.
            routine.variants.filter { it.family != "jambes" }.forEach { v -> assertTrue("cycle $cycle : ${v.id} absent", v.id in picked) }
            sessions.filter { it.isDeload }.forEach { assertNull(it.variantIndex) }
            sessions.forEach { assertTrue(it.prescriptions.count { p -> p.variant != null } <= 1) }
        }
    }

    @Test
    fun `a variant day shortens the base exercise and doses the block from the test`() {
        val s = schedule(StoredProgram("p", routine, start.toString(), tests = strong))
        val session = (0 until 28).mapNotNull(s::sessionAt).first { s2 ->
            s2.variantIndex?.let { s2.prescriptions[it].variant!!.id } == "pompes-diamant"
        }
        val base = session.prescriptions[session.variantBaseIndex!!]
        val normal = schedule(StoredProgram("p", routine, start.toString(), tests = strong, variantSkips = setOf(session.index)))
            .sessionAt(session.index)!!.prescriptions[session.variantBaseIndex!!]
        assertEquals(Variants.baseSets(normal.sets!!), base.sets)
        assertEquals("Pompes diamant", base.replacedBy)
        assertTrue(!base.allOut || base.isBoss)

        val block = session.prescriptions[session.variantIndex!!]
        // 60 pompes au test / 3,3 = 18 diamant au max, 60 % = 11.
        assertEquals(VARIANT_SETS, block.sets)
        assertEquals(11, block.reps)
        assertTrue(block.allOut && block.variant!!.full)
        // Le bloc suit son exercice de base.
        assertEquals(session.order.indexOf(session.variantBaseIndex!!) + 1, session.order.indexOf(session.variantIndex!!))
    }

    @Test
    fun `below the threshold it is the easy version`() {
        val s = schedule(StoredProgram("p", routine, start.toString(), tests = strong + ("pompes" to 10)))
        val blocks = (0 until 28).mapNotNull(s::sessionAt).mapNotNull { it.variantIndex?.let(it.prescriptions::get) }
        val onehand = blocks.first { it.variant!!.id == "pompes-une-main" }
        assertTrue(!onehand.variant!!.full && onehand.exercise.name.contains("facile"))
        assertTrue(onehand.exercise.description!!.contains("table"))
        // Une seule fois par cycle en version facile.
        assertEquals(1, blocks.count { it.variant!!.id == "pompes-une-main" })
        // 10 / (5 × 0,5) = 4 au max, 60 % = 2.
        assertEquals(2, onehand.reps)
    }

    @Test
    fun `the base keeps about 60 percent of its sets`() {
        assertEquals(listOf(1, 2, 2, 3, 4, 5), listOf(2, 3, 4, 5, 6, 8).map(Variants::baseSets))
    }

    @Test
    fun `a max on the block recalibrates the personal ratio`() {
        var program = StoredProgram("p", routine, start.toString(), tests = strong + ("pompes" to 40))
        val first = schedule(program).let { s -> (0 until 84).mapNotNull(s::sessionAt).first { it.variantIndex?.let { i -> it.prescriptions[i].variant!!.id } == "pompes-diamant" } }
        program = program.saveLog(first.index, first.variantIndex!!, ExerciseLog.ofSets(listOf(7, 7, 20), allOut = true), complete = true)
        val s = schedule(program)
        val progress = s.variantProgress().first { it.variant.id == "pompes-diamant" }
        // 40 / 20 = 2, moyenne avec 3,3.
        assertEquals(2.65, progress.ratio!!, 0.001)
        assertEquals(listOf(20), progress.points.map { it.value })
        val next = (first.index + 1 until first.index + 84).mapNotNull(s::sessionAt)
            .first { it.variantIndex?.let { i -> it.prescriptions[i].variant!!.id } == "pompes-diamant" }
        // 40 / 2,65 = 15 au max, 60 % = 9 ; et 20 au max pour 7 prévus règle la variante 2 crans plus haut.
        assertEquals(11, next.prescriptions[next.variantIndex!!].reps)
        assertTrue(s.adaptations().any { it.name == "Pompes diamant" && it.crans == 2 })
    }

    @Test
    fun `refusing a variant day only changes what comes after`() {
        val program = StoredProgram("p", routine, start.toString(), tests = strong)
        val before = schedule(program)
        val day = (0 until 28).mapNotNull(before::sessionAt).first { it.variantIndex != null }
        val skipped = schedule(program.skipVariant(day.index))
        (0 until day.index).forEach { i -> assertEquals(before.sessionAt(i)?.prescriptions, skipped.sessionAt(i)?.prescriptions) }
        assertNull(skipped.sessionAt(day.index)!!.variantIndex)
        val id = day.prescriptions[day.variantIndex!!].variant!!.id
        // Replacée plus loin dans le cycle.
        assertTrue((day.index + 1 until 28).mapNotNull(skipped::sessionAt).any { it.variantIndex?.let { i -> it.prescriptions[i].variant!!.id } == id })
    }

    @Test
    fun `a custom variant joins the draw from the next day without touching the past`() {
        val today = start.plusDays(10)
        val program = StoredProgram("p", routine, start.toString(), tests = strong)
        val custom = CustomExercise.create("perso-1", "Pompes archer", "pompes", false, 4.0, null, null, today)
        val with = ProgramSchedule(program.addCustom(custom), start.plusYears(1))
        val without = schedule(program)
        (0..10).forEach { i -> assertEquals(without.sessionAt(i)?.prescriptions, with.sessionAt(i)?.prescriptions) }
        val days = (11 until 28 * 3).mapNotNull(with::sessionAt).filter { it.variantIndex?.let { i -> it.prescriptions[i].variant!!.id } == "perso-1" }
        assertTrue(days.isNotEmpty())
        assertTrue(days.first().prescriptions[days.first().variantIndex!!].variant!!.custom)

        // En pause : plus rien après, le passé reste.
        val pausedOn = days.first().date
        val paused = ProgramSchedule(program.addCustom(custom).updateCustom("perso-1") { it.stopped(pausedOn) }, start.plusYears(1))
        assertEquals(with.sessionAt(days.first().index)!!.prescriptions, paused.sessionAt(days.first().index)!!.prescriptions)
        assertTrue((days.first().index + 1 until 28 * 4).mapNotNull(paused::sessionAt).none { it.variantIndex?.let { i -> it.prescriptions[i].variant!!.id } == "perso-1" })
    }

    @Test
    fun `an unknown ratio starts with a single max set`() {
        val custom = CustomExercise.create("perso-1", "Pompes archer", "pompes", false, null, null, null, start.minusDays(1))
        val program = StoredProgram("p", routine, start.toString(), tests = strong, custom = listOf(custom))
        val s = schedule(program)
        val day = (0 until 28).mapNotNull(s::sessionAt).first { it.variantIndex?.let { i -> it.prescriptions[i].variant!!.id } == "perso-1" }
        val block = day.prescriptions[day.variantIndex!!]
        assertEquals(1, block.sets)
        assertNull(block.reps)
        assertTrue(block.allOut)
        val logged = program.saveLog(day.index, day.variantIndex!!, ExerciseLog(max = 12), complete = true)
        assertEquals(5.0, schedule(logged).variantProgress().first { it.variant.id == "perso-1" }.ratio!!, 0.001)
    }

    @Test
    fun `an other exercise comes twice a week before the stretching, with its own progression`() {
        val custom = CustomExercise.create("perso-2", "Corde à sauter", CustomExercise.OTHER, true, null, 60, null, start.minusDays(1))
        val s = schedule(StoredProgram("p", routine, start.toString(), tests = strong, custom = listOf(custom)))
        for (week in 0 until 3) {
            val sessions = (week * 7 until week * 7 + 7).mapNotNull(s::sessionAt)
            assertEquals(OTHER_PER_WEEK, sessions.count { it.otherIndex != null })
        }
        assertTrue((21 until 28).mapNotNull(s::sessionAt).none { it.otherIndex != null })
        val day = (0 until 28).mapNotNull(s::sessionAt).first { it.otherIndex != null }
        val other = day.prescriptions[day.otherIndex!!]
        assertEquals("Corde à sauter", other.exercise.name)
        assertEquals(60, other.seconds)
        // Avant les étirements.
        assertEquals(day.otherIndex, day.order[day.order.size - 2])
    }

    @Test
    fun `future variant days stay hidden`() {
        val s = ProgramSchedule(StoredProgram("p", routine, start.toString(), tests = strong), start)
        assertTrue((1 until 28).mapNotNull(s::sessionAt).none { it.variantIndex != null })
    }

    @Test
    fun `variants count in their family counter as an equivalent`() {
        var program = StoredProgram("p", routine, start.toString(), tests = strong)
        val s0 = schedule(program)
        val day = (0 until 28).mapNotNull(s0::sessionAt).first { it.variantIndex?.let { i -> it.prescriptions[i].variant!!.id } == "pompes-pike" }
        program = program.saveLog(day.index, day.variantIndex!!, ExerciseLog.ofSets(listOf(4, 4, 6), allOut = true), complete = true)
        val counter = Stats.of(schedule(program)).counters.first { it.name == "Pompes" }
        // 14 pike × 10.
        assertEquals(140, counter.reps)
    }

    @Test
    fun `a routine update keeps what was logged on the variant and the other exercise`() {
        val custom = CustomExercise.create("perso-2", "Corde à sauter", CustomExercise.OTHER, true, null, 60, null, start.minusDays(1))
        var program = StoredProgram("p", routine, start.toString(), tests = strong, custom = listOf(custom))
        val day = (0 until 28).mapNotNull(schedule(program)::sessionAt).first { it.variantIndex != null }
        program = program.toggle(day.index, day.variantIndex!!)
        val updated = program.withPlan(routine.copy(revision = routine.revision + 1))
        assertTrue(updated.isDone(day.index, day.variantIndex!!))
        assertNotNull(schedule(updated).sessionAt(day.index)!!.variantIndex)
    }

    private fun legDays(tests: Map<String, Int>, cycles: Int = 2): List<Prescription> {
        val s = schedule(StoredProgram("p", routine, start.toString(), tests = strong + tests))
        return (0 until cycles * 28).mapNotNull(s::sessionAt).filter { !it.isDeload }
            .mapNotNull { session -> session.prescriptions.firstOrNull { it.key == "squats-classiques" || it.variant?.whole == true } }
    }

    @Test
    fun `legs are mostly variants, the classic squat once every two cycles`() {
        val days = legDays(mapOf("squats" to 40))
        assertEquals(6, days.size)
        assertEquals(1, days.count { it.variant == null })
        val block = days.first { it.variant != null }
        assertTrue(block.variant!!.full && block.variant!!.whole)
        // Le bloc prend la place de l'exercice de base, au même rang.
        val s = schedule(StoredProgram("p", routine, start.toString(), tests = strong))
        val session = (0 until 28).mapNotNull(s::sessionAt).first { it.variantIndex != null && it.prescriptions[it.variantIndex!!].variant!!.whole }
        assertEquals(session.variantBaseIndex, session.variantIndex)
        assertEquals(session.day.exercises.size, session.exerciseCount)
    }

    @Test
    fun `a low squat test means easy variants one week out of two, or none at all`() {
        val mid = legDays(mapOf("squats" to 20))
        assertEquals(3, mid.count { it.variant != null })
        assertTrue(mid.filter { it.variant != null }.all { !it.variant!!.full && it.exercise.name.contains("facile") })
        assertTrue(legDays(mapOf("squats" to 10)).none { it.variant != null })
    }
}
