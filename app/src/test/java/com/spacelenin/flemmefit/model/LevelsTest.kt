package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LevelsTest {

    private val start = LocalDate.of(2026, 10, 19)

    private val pushups = Exercise(
        "Pompes", test = "pompes", amrap = true,
        levels = listOf(Level(0, sets = 5, reps = "2", note = "Sur les genoux"), Level(6, sets = 5, reps = "5"), Level(11, sets = 5, reps = "8")),
        progression = Progression(reps = 1),
    )

    private val plan = Plan(
        version = 1, name = "Test", repeat = REPEAT_FOREVER, boss = false, deloadEvery = 0,
        tests = listOf(PlanTest("pompes", "Max de pompes")),
        weeks = listOf(
            PlanWeek(days = listOf(PlanDay(1, exercises = listOf(pushups)))),
            PlanWeek(days = listOf(PlanDay(1, exercises = listOf(Exercise("Test pompes", test = "pompes", retest = true))))),
        ),
    )

    @Test
    fun `initial test picks the level and progression starts from it`() {
        val none = ProgramSchedule(StoredProgram("p", plan, start.toString()), start)
        val first = none.sessionAt(0)!!.prescriptions[0]
        assertEquals(0, first.palier)
        assertEquals(2, first.reps)
        assertTrue(first.exercise.description!!.contains("genoux"))
        assertTrue(first.allOut && !first.isBoss)
        assertEquals(2, first.target)

        val program = StoredProgram("p", plan, start.toString(), tests = mapOf("pompes" to 7)).toggle(0, 0)
        val s = ProgramSchedule(program, start.plusDays(30))
        assertEquals(5, s.sessionAt(0)!!.prescriptions[0].reps)
        // Une séance validée : un cran.
        assertEquals(6, s.sessionAt(14)!!.prescriptions[0].reps)
    }

    @Test
    fun `retest result moves to a new level and resets progression`() {
        val program = StoredProgram("p", plan, start.toString(), tests = mapOf("pompes" to 7))
            .toggle(0, 0)
            .saveLog(7, 0, ExerciseLog(max = 12), complete = true)
        val s = ProgramSchedule(program, start.plusDays(30))
        val test = s.sessionAt(7)!!.prescriptions[0]
        assertTrue(test.isTest)
        assertEquals(8, test.target)
        val after = s.sessionAt(14)!!.prescriptions[0]
        assertEquals(2, after.palier)
        assertEquals(8, after.reps)
        assertEquals(mapOf("pompes" to 12), s.currentTestResults())
    }

    @Test
    fun `validation catches unknown tests and unordered levels`() {
        val bad = plan.copy(
            weeks = listOf(
                PlanWeek(
                    days = listOf(
                        PlanDay(1, exercises = listOf(Exercise("A", test = "nope"), Exercise("B", levels = listOf(Level(5), Level(2))))),
                    ),
                ),
            ),
        )
        val errors = PlanParser.validate(bad)
        assertTrue(errors.any { "nope" in it })
        assertTrue(errors.any { "croissants" in it })
        assertTrue(errors.any { "il faut aussi" in it })
        assertTrue(PlanParser.validate(plan).isEmpty())
    }

    @Test
    fun `small gains are not exploits`() {
        assertTrue(!Exploit.isExploit(4, 2, isSeconds = false))
        assertTrue(Exploit.isExploit(10, 5, isSeconds = false))
        assertTrue(!Exploit.isExploit(20, 10, isSeconds = true))
        assertTrue(Exploit.isExploit(60, 30, isSeconds = true))
    }

    @Test
    fun `routine maison example parses and starts at the tested level`() {
        val file = listOf("../examples/routine-maison.json", "examples/routine-maison.json").map { java.io.File(it) }.first { it.exists() }
        val result = PlanParser.parse(file.readText())
        assertTrue(result.toString(), result is ParseResult.Success)
        val routine = (result as ParseResult.Success).plan
        assertEquals(5, routine.tests.size)

        val program = StoredProgram("r", routine, start.toString(), tests = mapOf("pompes" to 8, "gainage" to 50))
        val s = ProgramSchedule(program, start)
        val day1 = s.sessionAt(0)!!.prescriptions
        assertEquals(listOf("Échauffement", "Pompes lentes (tempo)", "Shadow boxing léger", "Étirements"), day1.map { it.exercise.name })
        assertEquals("4 × 2 · repos 90s", day1[1].summary())
        val gainage = s.sessionAt(1)!!.prescriptions.first { it.key == "gainage" }
        assertEquals(25, gainage.seconds)
        assertTrue(gainage.maxIsSeconds)

        // Sous 20 au test d'abdos, pas de V-up : l'exercice choisi au test, sous son nom.
        fun tuesday(abdos: Int, option: String?): Prescription {
            var p = StoredProgram("r", routine, start.toString(), tests = mapOf("pompes" to 8, "gainage" to 50, "abdos" to abdos))
            if (option != null) p = p.changeTestOption("abdos", option, start)
            return ProgramSchedule(p, start.plusDays(1)).sessionAt(1)!!.prescriptions.first { it.key == "abdos-bras-tendus" }
        }
        tuesday(12, "situp").let {
            assertEquals("Abdos (sit-up)", it.exercise.name)
            assertEquals(5, it.reps) // séries : 4, moins un jour variante
        }
        tuesday(25, "crunch").let {
            assertEquals("Abdos bras tendus (V-up)", it.exercise.name)
            assertEquals(2, it.reps)
        }
        // Shadow : toujours 2 rounds, +30 s par séance validée de 3 à 4 min.
        val shadow = day1[2].exercise
        assertEquals(2 to 180, Prescription.of(shadow, 0, false, false, null).let { it.sets to it.seconds })
        assertEquals(2 to 210, Prescription.of(shadow, 1, false, false, null).let { it.sets to it.seconds })
        assertEquals(2 to 240, Prescription.of(shadow, 2, false, false, null).let { it.sets to it.seconds })
        assertEquals(2 to 240, Prescription.of(shadow, 20, false, false, null).let { it.sets to it.seconds })
        // Beat Saber : de 40 à 90 min, +10 min toutes les 2 séances.
        val beatSaber = s.sessionAt(4)!!.prescriptions.first { it.key == "beatsaber" }.exercise
        assertEquals(40 * 60, Prescription.of(beatSaber, 0, false, false, null).seconds)
        assertEquals(90 * 60, Prescription.of(beatSaber, 9, false, false, null).seconds)
        // Semaine 4 : décharge, et les tests sont refaits.
        val testDay = s.sessionAt(21)!!
        assertTrue(testDay.isDeload)
        assertTrue(testDay.prescriptions[1].isTest)
        assertTrue(s.sessionAt(22)!!.prescriptions[2].maxIsSeconds)
    }
}
