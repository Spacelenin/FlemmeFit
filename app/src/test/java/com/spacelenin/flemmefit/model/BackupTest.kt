package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    private val plan = Plan(version = 1, name = "Routine", weeks = listOf(PlanWeek(days = listOf(PlanDay(1, exercises = listOf(Exercise("Pompes", sets = 3, reps = "10")))))))
    private val program = StoredProgram("p1", plan, "2026-10-19", done = setOf("0-0", "1-0"), logs = mapOf("0-0" to ExerciseLog(max = 14)), tests = mapOf("pompes" to 12), bursts = listOf(0))

    @Test
    fun `a backup round-trips programs and typed preferences`() {
        val prefs = Backup.prefsOf(
            mapOf(
                "settings" to mapOf("theme" to "VIOLET", "tutorial_offered" to true, "backup_uri" to "content://x"),
                "reminders" to mapOf("daily" to 1080, "muted" to setOf("p2", "p1")),
                "social" to mapOf("last_at" to 1234567890123L),
            ),
        )
        val backup = Backup(createdAt = "2026-10-22T18:04", appVersion = 42, state = AppState(programs = listOf(program)), prefs = prefs)
        val read = Backup.decode(backup.encode())!!
        assertEquals(backup, read)
        assertEquals(2, read.doneExercises)
        assertEquals(14, read.state.programs.single().logs["0-0"]?.max)
        assertEquals("VIOLET", read.prefs["settings"]!!["theme"]!!.toAny())
        assertEquals(true, read.prefs["settings"]!!["tutorial_offered"]!!.toAny())
        assertEquals(1080, read.prefs["reminders"]!!["daily"]!!.toAny())
        assertEquals(setOf("p1", "p2"), read.prefs["reminders"]!!["muted"]!!.toAny())
        assertEquals(1234567890123L, read.prefs["social"]!!["last_at"]!!.toAny())
    }

    @Test
    fun `where this phone saves is not carried over`() {
        val prefs = Backup.prefsOf(mapOf("settings" to mapOf("backup_uri" to "content://x", "backup_last" to 5L, "theme" to "BLEU")))
        assertFalse("backup_uri" in prefs["settings"]!!)
        assertFalse("backup_last" in prefs["settings"]!!)
        assertTrue("theme" in prefs["settings"]!!)
    }

    @Test
    fun `other files are not backups`() {
        assertNull(Backup.decode("""{"format":"flemmefit","version":1,"name":"Routine","weeks":[]}"""))
        assertNull(Backup.decode("pas du json"))
    }
}
