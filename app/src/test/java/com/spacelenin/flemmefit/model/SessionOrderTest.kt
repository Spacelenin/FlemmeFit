package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SessionOrderTest {

    @Test
    fun `order follows the chosen keys, warm-up and stretches stay in place`() {
        val day = listOf(
            Exercise("Échauffement", optional = true), Exercise("A"), Exercise("B"), Exercise("C"), Exercise("Étirements", optional = true),
        )
        assertEquals(listOf(0, 3, 1, 2, 4), SessionOrder.arrange(day, listOf("c", "a", "b"), null, null, null))
        // Clé inconnue ou manquante : le reste garde son ordre.
        assertEquals(listOf(0, 2, 1, 3, 4), SessionOrder.arrange(day, listOf("b"), null, null, null))
        // La variante (index 5) reste juste après son exercice de base.
        assertEquals(listOf(0, 2, 5, 1, 3, 4), SessionOrder.arrange(day, listOf("b", "a", "c"), 5, 2, null))
    }

    private val start = LocalDate.of(2026, 11, 2) // lundi
    private val pushups = Exercise("Pompes", id = "pompes", sets = 3, reps = "10", muscles = listOf("pectoraux", "triceps"), assists = listOf("épaules"))
    private val dips = Exercise("Dips", id = "dips", sets = 3, reps = "10", muscles = listOf("triceps"), assists = listOf("épaules", "pectoraux"))

    private fun plan(withDips: Boolean) = Plan(
        version = 1, name = "R", repeat = REPEAT_FOREVER, deloadEvery = 0, boss = false,
        weeks = listOf(PlanWeek(days = listOf(PlanDay(1, exercises = listOfNotNull(pushups, dips.takeIf { withDips }))))),
    )

    @Test
    fun `no more ramp nor fatigue, whatever the order or when the exercise arrives`() {
        // Trois semaines de pompes, puis la routine gagne les dips : ils arrivent à leur dose pleine.
        var p = StoredProgram("p", plan(false), start.toString())
        for (w in 0 until 3) p = p.toggle(w * 7, 0)
        p = p.copy(plan = plan(true))
        val s = ProgramSchedule(p, start.plusDays(21)).sessionAt(21)!!
        assertEquals(listOf(10, 10), s.prescriptions.map { it.reps })
        // Dips d'abord : rien n'est réduit non plus, seul l'ordre change.
        val reordered = p.withOrder(21, 1, listOf("dips", "pompes"), start.plusDays(21), keep = true)
        val r = ProgramSchedule(reordered, start.plusDays(21)).sessionAt(21)!!
        assertEquals(listOf(1, 0), r.order)
        assertEquals(listOf(10, 10), r.prescriptions.map { it.reps })
    }

    @Test
    fun `tired cuts the dose of the day, counts as done and shows on the friend card`() {
        val saber = Exercise("Beat Saber", id = "beatsaber", duration = "40min", steady = true, tired = 20)
        val plan = Plan(
            version = 1, name = "R", repeat = REPEAT_FOREVER, deloadEvery = 0, boss = false,
            weeks = listOf(PlanWeek(days = listOf(PlanDay(1, exercises = listOf(saber))))),
        )
        val p = StoredProgram("p", plan, start.toString())
        val normal = ProgramSchedule(p, start).sessionAt(0)!!.prescriptions[0]
        assertEquals(2400, normal.seconds)
        assertEquals(20, normal.tiredOffer)
        val tired = p.withTired(0, 0, true).toggle(0, 0)
        val sched = ProgramSchedule(tired, start)
        val pr = sched.sessionAt(0)!!.prescriptions[0]
        assertTrue(pr.tired)
        assertEquals(1920, pr.seconds) // 32 min
        assertTrue(sched.isComplete(sched.sessionAt(0)!!))
        // Changement de routine : la marque suit son exo.
        assertTrue(tired.withPlan(plan).isTired(0, 0))
    }
}

class ExerciseInfoTest {
    @Test
    fun `the exercise sheet knows level, dose, days and history`() {
        val start = LocalDate.of(2026, 11, 2)
        val routine = (PlanParser.parse(java.io.File("../examples/routine-maison.json").readText()) as ParseResult.Success).plan
        var p = StoredProgram("p", routine, start.toString(), tests = mapOf("pompes" to 11, "tractions" to 3, "abdos" to 7, "gainage" to 45, "squats" to 20))
        p = p.saveLog(4, 1, ExerciseLog.ofSets(listOf(7, 7, 9), allOut = true), complete = true)
        val info = ProgramSchedule(p, start.plusDays(5)).exerciseInfo("pompes")!!
        assertEquals(listOf("pectoraux", "triceps"), info.muscles)
        assertEquals(2, info.palier) // 11 au test : palier 3
        assertEquals(21, info.nextLevelAt)
        assertEquals(listOf(java.time.DayOfWeek.FRIDAY), info.weekdays)
        assertEquals(listOf(9), info.history.map { it.value })
        assertTrue(info.progression!!.startsWith("+1 rep"))
        assertTrue(info.dose != null && info.nextDate != null)
        assertTrue(Surprise.due(false, 0, start))
        assertFalse(Surprise.due(true, 0, start))
        assertTrue(Surprise.due(true, 2026, LocalDate.of(2027, 4, 1)))
        assertFalse(Surprise.due(true, 2027, LocalDate.of(2027, 4, 1)))
    }
}

class BossAndFriendsTest {
    private val start = LocalDate.of(2026, 11, 2)

    @Test
    fun `winning a Boss raises the next session by one notch at most`() {
        val pushups = Exercise("Pompes", sets = 3, reps = "3", amrap = true, progression = Progression(reps = 1, every = 100))
        val plan = Plan(
            version = 1, name = "R", repeat = REPEAT_FOREVER, deloadEvery = 0, boss = true,
            weeks = listOf(PlanWeek(days = listOf(PlanDay(1, exercises = listOf(pushups))))),
        )
        val p0 = StoredProgram("p", plan, start.toString())
        val s0 = ProgramSchedule(p0, start.plusDays(60))
        val boss = (0 until 60).mapNotNull { s0.sessionAt(it) }.first { it.isBoss }
        val pr = boss.prescriptions[0]
        val normal = s0.sessionAt(boss.index - 7)?.prescriptions?.get(0)?.reps ?: 3
        assertTrue(pr.reps!! > normal)
        // Boss fait pile, bien au-dessus du prévu normal.
        val p = p0.saveLog(boss.index, 0, ExerciseLog.ofSets(List(3) { pr.reps!! }.toMutableList().also { it[2] = pr.target ?: pr.reps!! }, true), complete = true)
        val next = ProgramSchedule(p, start.plusDays(120)).sessionAt(boss.index + 7)!!.prescriptions[0].reps!!
        assertTrue("$normal → $next", next - normal <= 1)
    }

    @Test
    fun `the Boss objective is never below the dose needed to win it`() {
        val dips = Exercise("Dips", sets = 3, reps = "8")
        val pr = Prescription.of(dips, 0, false, boss = true, lastMax = 5)
        assertEquals(pr.perSetPlanned, pr.target) // 5 + 1 = 6 < dose du jour : l'objectif suit la dose
        val need = pr.perSetPlanned!!
        assertTrue(pr.isMetBy(ExerciseLog.ofSets(List(3) { need }, true)))
        assertFalse(pr.isMetBy(ExerciseLog.ofSets(listOf(need, need, need - 1), true)))
        // Dernier max plus haut : il faut le battre sur la série à fond.
        val high = Prescription.of(dips, 0, false, boss = true, lastMax = 20)
        assertEquals(21, high.target)
        assertFalse(high.isMetBy(ExerciseLog.ofSets(List(3) { high.perSetPlanned!! }, true)))
        assertTrue(high.isMetBy(ExerciseLog.ofSets(listOf(high.perSetPlanned!!, high.perSetPlanned!!, 21), true)))
    }

    @Test
    fun `long names are cut on a word, short ones stay whole`() {
        assertEquals("Battements de jambes gainés (flutter kicks)", shareName("Battements de jambes gainés (flutter kicks)"))
        val long = shareName("Un nom d'exercice vraiment beaucoup trop long pour tenir sur la carte des potes")
        assertTrue(long, long.length <= 60 && long.endsWith("…") && !long.endsWith(" …"))
    }
}

class BossBlocksTest {
    private val routine = (PlanParser.parse(java.io.File("../examples/routine-maison.json").readText()) as ParseResult.Success).plan

    /** L'ancienne règle, recopiée : un Boss toutes les 3 à 4 semaines. */
    private fun oldBossDates(p: StoredProgram, s: ProgramSchedule, weeks: Int): List<LocalDate> {
        val random = kotlin.random.Random(p.id.hashCode().toLong())
        val seed = p.id.hashCode().toLong()
        val out = ArrayList<LocalDate>()
        var prev: Int? = null
        while (true) {
            var next = if (prev == null) 2 + random.nextInt(2) else prev + 3 + random.nextInt(2)
            while ((next + 1) % 4 == 0) next++
            if (next >= weeks) return out
            val days = routine.weeks[next % routine.weeks.size].days
            val pick = kotlin.random.Random(seed xor (next * 7919L)).nextInt(days.size)
            out += s.dateOf(next * 7 + days[pick].day - 1)
            prev = next
        }
    }

    @Test
    fun `bosses already met or announced stay, then one Boss per block of three working weeks`() {
        for (id in listOf("levi", "ninjarque", "spacelenin", "exynos", "a", "b", "c", "d")) {
            for (start in listOf(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 2))) {
                val p = StoredProgram(id, routine, start.toString())
                val s = ProgramSchedule(p, start.plusDays(400))
                val bosses = (0 until 52 * 7).mapNotNull { s.sessionAt(it) }.filter { it.isBoss }
                // Avant le changement : exactement les mêmes jours qu'avant.
                val old = oldBossDates(p, s, 52).filter { it.isBefore(BOSS_BLOCKS_FROM) }
                assertEquals("$id $start", old, bosses.map { it.date }.filter { it.isBefore(BOSS_BLOCKS_FROM) })
                // Après : un Boss par bloc de 4 semaines (3 de travail + 1 allégée), jamais en semaine allégée.
                assertTrue(bosses.none { it.isDeload })
                val blocks = bosses.filter { !it.date.isBefore(BOSS_BLOCKS_FROM) }.map { (it.index / 7) / 4 }
                assertEquals("$id $start $blocks", blocks.distinct(), blocks)
                val firstFull = ((java.time.temporal.ChronoUnit.DAYS.between(start, BOSS_BLOCKS_FROM) / 7 + 3) / 4).toInt() + 1
                (firstFull until 12).forEach { b -> assertTrue("$id $start bloc $b", b in blocks) }
            }
        }
    }
}

class RankingTieTest {
    @Test
    fun `equal points - more Bosses first, then exploits, then streak, then name`() {
        val today = LocalDate.of(2026, 11, 12)
        fun card(name: String, boss: Int, exploits: Int, streak: Int, sessions: Int) = FriendCard(
            id = name, pseudo = name, date = today.toString(), updatedAt = 0, bossDefeated = boss, exploits = exploits,
            streak = streak, sessionsDone = sessions,
        )
        // Tous à 30 points.
        val levi = card("Levi", 1, 4, 13, 13)
        val me = card("Spacelenin", 0, 6, 9, 12)
        val ex = card("Zed", 0, 6, 10, 12)
        val same = card("Anna", 0, 6, 10, 12)
        assertTrue(listOf(levi, me, ex, same).all { it.score == 30 })
        assertEquals(listOf("Levi", "Anna", "Zed", "Spacelenin"), Social.ranking(listOf(me, ex, same, levi), today).map { it.pseudo })
    }
}
