package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlanParserTest {

    private fun fail(text: String): List<String> {
        val result = PlanParser.parse(text)
        assertTrue("attendu un échec : $result", result is ParseResult.Failure)
        return (result as ParseResult.Failure).errors
    }

    private fun ok(text: String): Plan {
        val result = PlanParser.parse(text)
        assertTrue("attendu un succès : $result", result is ParseResult.Success)
        return (result as ParseResult.Success).plan
    }

    @Test
    fun `example file is valid`() {
        val file = listOf("../examples/exemple-full-body.json", "examples/exemple-full-body.json")
            .map(::File).first { it.exists() }
        val plan = ok(file.readText())
        assertEquals("Full body débutant", plan.name)
        assertEquals(4, plan.repeat)
        assertEquals(2, plan.weeks.size)
    }

    @Test
    fun `examples in FORMAT md are valid`() {
        val doc = listOf("../FORMAT.md", "FORMAT.md").map(::File).first { it.exists() }.readText()
        val blocks = Regex("```json\\n(.*?)```", RegexOption.DOT_MATCHES_ALL).findAll(doc).map { it.groupValues[1] }.toList()
        assertTrue(blocks.isNotEmpty())
        blocks.forEach { ok(it) }
    }

    @Test
    fun `minimal plan with defaults`() {
        val plan = ok("""{"version":1,"name":"  Mini ","weeks":[{"days":[{"day":2,"exercises":[{"name":"Pompes","reps":12}]}]}]}""")
        assertEquals("Mini", plan.name)
        assertEquals(1, plan.repeat)
        assertEquals("12", plan.weeks[0].days[0].exercises[0].reps)
    }

    @Test
    fun `unknown keys are ignored and days are sorted`() {
        val plan = ok(
            """{"version":1,"name":"X","bonus":true,"weeks":[{"days":[
              {"day":5,"exercises":[{"name":"B","extra":1}]},
              {"day":1,"exercises":[{"name":"A"}]}]}]}""",
        )
        assertEquals(listOf(1, 5), plan.weeks[0].days.map { it.day })
    }

    @Test
    fun `wrong version is rejected`() {
        assertTrue(fail("""{"version":2,"name":"X","weeks":[]}""").single().contains("Version 2"))
        assertTrue(fail("""{"name":"X","weeks":[]}""").single().contains("version"))
    }

    @Test
    fun `wrong format id is rejected`() {
        assertTrue(fail("""{"format":"autre","version":1,"name":"X","weeks":[]}""").single().contains("format"))
    }

    @Test
    fun `invalid json is reported`() {
        assertTrue(fail("""{"version":1,""").single().startsWith("JSON invalide"))
        assertTrue(fail("").single().contains("vide"))
        assertTrue(fail("[1,2]").single().contains("objet"))
    }

    @Test
    fun `missing field is reported`() {
        val errors = fail("""{"version":1,"name":"X","weeks":[{"days":[{"day":1,"exercises":[{"sets":3}]}]}]}""")
        assertTrue(errors.single(), errors.single().contains("name"))
    }

    @Test
    fun `validation errors are listed`() {
        val errors = fail(
            """{"version":1,"name":" ","repeat":0,"weeks":[{"days":[
              {"day":8,"exercises":[{"name":"A"}]},
              {"day":2,"exercises":[]},
              {"day":2,"exercises":[{"name":"","sets":0}]}]}]}""",
        )
        assertEquals(errors.toString(), 7, errors.size)
    }

    @Test
    fun `plan without sessions is rejected`() {
        assertTrue(fail("""{"version":1,"name":"X","weeks":[{"days":[]}]}""").single().contains("aucune séance"))
    }

    @Test
    fun `plan round trips through storage`() {
        val plan = ok("""{"version":1,"name":"X","weeks":[{"days":[{"day":1,"exercises":[{"name":"A","sets":3}]}]}]}""")
        val encoded = PlanParser.json.encodeToString(Plan.serializer(), plan)
        assertEquals(plan, ok(encoded))
    }
}
