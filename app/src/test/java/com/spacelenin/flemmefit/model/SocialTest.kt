package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SocialTest {

    private val start = LocalDate.of(2026, 10, 19)

    private val plan = Plan(
        version = 1, name = "Maison", repeat = REPEAT_FOREVER, boss = false,
        weeks = listOf(
            PlanWeek(
                days = listOf(
                    PlanDay(1, title = "Pompes", exercises = listOf(Exercise("Pompes", sets = 3, reps = "10"), Exercise("Gainage", duration = "30s"))),
                ),
            ),
        ),
    )

    @Test
    fun `card sums today and counts exploits`() {
        val program = StoredProgram("p", plan, start.toString())
            .saveLog(0, 0, ExerciseLog(max = 21), complete = true) // 21 ≥ 2 × 10 : exploit
            .saveLog(0, 1, ExerciseLog(max = 50), complete = true) // 50 < 2 × 30 s : non
        val card = Social.card("me", "  Lenin  ", listOf(program), start, now = 1000, build = 12)
        assertEquals("Lenin", card.pseudo)
        assertEquals(2, card.todayDone)
        assertEquals(2, card.todayTotal)
        assertEquals("Pompes", card.todayTitle)
        assertEquals(1, card.exploits)
        assertEquals("Pompes : 21 reps (prévu 10 reps)", card.lastExploit)
        assertEquals(1, card.sessionsDone)
        assertEquals(FriendCard.EXPLOIT_POINTS + 1, card.score)
        assertTrue(card.sameAs(card.copy(updatedAt = 5000)))
        assertFalse(card.sameAs(card.copy(todayDone = 1)))
    }

    @Test
    fun `poll parsing keeps only valid cards`() {
        val card = FriendCard(id = "a", pseudo = "Bob", date = "2026-10-19", updatedAt = 10, exploits = 2)
        val message = Social.encode(card)
        val escaped = kotlinx.serialization.json.JsonPrimitive(message).toString()
        val body = """
            {"id":"x1","time":1700000000,"event":"open","topic":"t"}
            {"id":"x2","time":1700000001,"expires":1700043201,"event":"message","topic":"t","message":$escaped}
            {"id":"x3","time":1700000002,"event":"message","topic":"t","message":"coucou"}
            {"id":"x4","time":1700000003,"event":"message","topic":"t","message":"{\"id\":\"b\",\"pseudo\":\"Al\",\"date\":\"2026-10-19\",\"updatedAt\":20,\"futur\":true}"}
            pas du json
        """.trimIndent()
        val cards = Social.parsePoll(body)
        assertEquals(listOf("a", "b"), cards.map { it.id })
        assertEquals(card, cards[0])
        assertEquals(cards, Social.decodeAll(Social.encodeAll(cards)))
        assertTrue(Social.decodeAll("oups").isEmpty())
    }

    @Test
    fun `merge keeps latest per id and ranking uses score`() {
        val old = FriendCard(id = "a", pseudo = "Bob", date = "2026-10-18", updatedAt = 1, sessionsDone = 1)
        val new = old.copy(updatedAt = 2, sessionsDone = 3)
        val boss = FriendCard(id = "b", pseudo = "Al", date = "2026-10-19", updatedAt = 1, bossDefeated = 1)
        val merged = Social.merge(listOf(old, boss), listOf(new))
        assertEquals(2, merged.size)
        assertEquals(3, merged.first { it.id == "a" }.sessionsDone)
        assertEquals(listOf("b", "a"), Social.ranking(merged, start).map { it.id })
    }
}
