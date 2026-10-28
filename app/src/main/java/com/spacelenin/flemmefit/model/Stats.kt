package com.spacelenin.flemmefit.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class ExerciseStats(
    val key: String,
    val name: String,
    /** Nombre de fois fait (en entier ou en partie). */
    val times: Int,
    val totalSets: Int,
    val totalReps: Int,
    val totalSeconds: Int,
    val bestReps: Int?,
    val bestSeconds: Int?,
    val firstSummary: String?,
    val lastSummary: String?,
    val lastDate: LocalDate?,
    /** Toujours facultatif (échauffement, étirements) : pas montré exercice par exercice. */
    val optional: Boolean = false,
)

/** Max noté sur une série au moins deux fois supérieur au prévu. */
data class Exploit(val name: String, val value: Int, val planned: Int, val isSeconds: Boolean, val date: LocalDate) {
    fun label(): String {
        val fmt: (Int) -> String = if (isSeconds) Measures::formatSeconds else { n -> "$n reps" }
        return "$name : ${fmt(value)} (prévu ${fmt(planned)})"
    }

    companion object {
        const val FACTOR = 2
        /** Écart minimal en plus du facteur, pour que 4 reps au lieu de 2 ne compte pas. */
        const val MIN_GAIN_REPS = 5
        const val MIN_GAIN_SECONDS = 15

        fun isExploit(max: Int, planned: Int, isSeconds: Boolean) =
            planned > 0 && max >= FACTOR * planned && max - planned >= if (isSeconds) MIN_GAIN_SECONDS else MIN_GAIN_REPS
    }
}

/** Volume d'une semaine calendaire (du lundi au dimanche). */
data class WeekVolume(val monday: LocalDate, val sets: Int, val boss: Boolean, val deload: Boolean)

data class HistoryEntry(
    val programName: String,
    val colorIndex: Int,
    val title: String,
    val date: LocalDate,
    val doneExercises: Int,
    val totalExercises: Int,
    val isBoss: Boolean,
)

data class Stats(
    val sessionsDone: Int,
    val totalSets: Int,
    val totalReps: Int,
    val totalSeconds: Int,
    val bestStreak: Int,
    val currentStreak: Int,
    val bossDefeated: Int,
    val bossMet: Int,
    val exercises: List<ExerciseStats>,
    val weeks: List<WeekVolume>,
    val history: List<HistoryEntry>,
    val exploits: List<Exploit> = emptyList(),
    /** Compteurs cachés, par famille d'exercices. */
    val counters: List<Counter> = emptyList(),
    /** Séances Burst terminées. */
    val burstsDone: Int = 0,
) {
    val isEmpty get() = sessionsDone == 0 && exercises.isEmpty()

    companion object {
        const val CHART_WEEKS = 8
        const val HISTORY_SIZE = 30

        /** Additionne les stats de plusieurs programmes ; un même exercice (même clé) est fusionné. */
        fun merge(all: List<Stats>, today: LocalDate): Stats {
            val exercises = all.flatMap { it.exercises }.groupBy { it.key }.map { (key, list) ->
                val firstWithSummary = list.filter { it.firstSummary != null }
                ExerciseStats(
                    key = key,
                    name = list.first().name,
                    times = list.sumOf { it.times },
                    totalSets = list.sumOf { it.totalSets },
                    totalReps = list.sumOf { it.totalReps },
                    totalSeconds = list.sumOf { it.totalSeconds },
                    bestReps = list.mapNotNull { it.bestReps }.maxOrNull(),
                    bestSeconds = list.mapNotNull { it.bestSeconds }.maxOrNull(),
                    firstSummary = firstWithSummary.firstOrNull()?.firstSummary,
                    lastSummary = list.filter { it.lastDate != null }.maxByOrNull { it.lastDate!! }?.lastSummary,
                    lastDate = list.mapNotNull { it.lastDate }.maxOrNull(),
                    optional = list.all { it.optional },
                )
            }.sortedByDescending { it.times }
            val weeks = chartMondays(today).map { monday ->
                val same = all.mapNotNull { s -> s.weeks.firstOrNull { it.monday == monday } }
                WeekVolume(monday, same.sumOf { it.sets }, same.any { it.boss }, same.isNotEmpty() && same.all { it.deload })
            }
            return Stats(
                sessionsDone = all.sumOf { it.sessionsDone },
                totalSets = all.sumOf { it.totalSets },
                totalReps = all.sumOf { it.totalReps },
                totalSeconds = all.sumOf { it.totalSeconds },
                bestStreak = all.maxOfOrNull { it.bestStreak } ?: 0,
                currentStreak = all.maxOfOrNull { it.currentStreak } ?: 0,
                bossDefeated = all.sumOf { it.bossDefeated },
                bossMet = all.sumOf { it.bossMet },
                exercises = exercises,
                weeks = weeks,
                history = all.flatMap { it.history }.sortedByDescending { it.date }.take(HISTORY_SIZE),
                exploits = all.flatMap { it.exploits }.sortedByDescending { it.date },
                counters = Counter.merge(all.map { it.counters }),
                burstsDone = all.sumOf { it.burstsDone },
            )
        }

        fun chartMondays(today: LocalDate): List<LocalDate> {
            val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return (CHART_WEEKS - 1 downTo 0).map { thisMonday.minusWeeks(it.toLong()) }
        }

        fun of(schedule: ProgramSchedule): Stats {
            val program = schedule.program
            val today = schedule.today
            val horizon = maxOf(schedule.lastIndexOnOrBefore(today), program.lastTrackedIndex())
            val sessions = schedule.sessionsThrough(horizon)

            class Acc(val name: String) {
                var times = 0; var sets = 0; var reps = 0; var seconds = 0
                var bestReps: Int? = null; var bestSeconds: Int? = null
                var first: String? = null; var last: String? = null; var lastDate: LocalDate? = null
                var required = false
            }
            val acc = LinkedHashMap<String, Acc>()
            val weekSets = HashMap<LocalDate, Int>()
            val weekBoss = HashSet<LocalDate>()
            val weekDeload = HashSet<LocalDate>()
            var totalSets = 0; var totalReps = 0; var totalSeconds = 0
            var sessionsDone = 0; var bossDefeated = 0; var bossMet = 0; var burstsDone = 0
            var bestStreak = 0; var run = 0
            val history = ArrayList<HistoryEntry>()
            val exploits = ArrayList<Exploit>()
            val counters = LinkedHashMap<String, Counter>()

            for (s in sessions) {
                val monday = s.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val complete = schedule.isComplete(s)
                if (complete) sessionsDone++
                if (complete && s.isBurst) burstsDone++
                if (s.isBoss && !s.date.isAfter(today)) {
                    bossMet++
                    weekBoss += monday
                    if (complete) bossDefeated++
                }
                if (s.isDeload) weekDeload += monday
                if (!s.date.isAfter(today)) {
                    if (complete) run++ else if (s.date != today && !schedule.isSaved(s)) run = 0
                    bestStreak = maxOf(bestStreak, run)
                }

                var touched = false
                s.prescriptions.forEachIndexed { e, pr ->
                    val state = schedule.exerciseState(s, e)
                    if (state == ExerciseState.TODO) return@forEachIndexed
                    touched = true
                    val log = program.logOf(s.index, e)
                    val max = log?.max
                    val perSet = log?.perSet
                    val sets = if (perSet != null) perSet.count { it > 0 } else log?.sets ?: pr.sets ?: 1
                    val repsPerSet = log?.reps ?: pr.reps
                    val secPerSet = log?.seconds ?: pr.seconds
                    val maxReps = max?.takeIf { !pr.maxIsSeconds }
                    val maxSec = max?.takeIf { pr.maxIsSeconds }
                    val planned = if (pr.maxIsSeconds) pr.seconds else pr.reps
                    if (max != null && planned != null && Exploit.isExploit(max, planned, pr.maxIsSeconds)) {
                        exploits += Exploit(pr.exercise.name, max, planned, pr.maxIsSeconds, s.date)
                    }
                    // Sur une série à fond, la dernière série vaut le max noté.
                    val reps = when {
                        perSet != null -> if (pr.maxIsSeconds) 0 else perSet.sum()
                        repsPerSet != null && maxReps != null && pr.allOut -> (sets - 1) * repsPerSet + maxReps
                        repsPerSet != null -> sets * repsPerSet
                        else -> maxReps ?: 0
                    }
                    val secs = when {
                        perSet != null -> if (pr.maxIsSeconds) perSet.sum() else (secPerSet ?: 0) * sets
                        secPerSet != null && maxSec != null && pr.allOut -> (sets - 1) * secPerSet + maxSec
                        secPerSet != null -> sets * secPerSet
                        else -> maxSec ?: 0
                    }
                    val a = acc.getOrPut(pr.key) { Acc(pr.exercise.name) }
                    a.times++
                    if (!pr.exercise.optional) a.required = true
                    a.sets += sets; a.reps += reps; a.seconds += secs
                    val bestSet = perSet?.maxOrNull()?.takeIf { it > 0 }
                    listOfNotNull(if (pr.maxIsSeconds) null else bestSet ?: repsPerSet, maxReps).maxOrNull()
                        ?.let { a.bestReps = maxOf(a.bestReps ?: 0, it) }
                    listOfNotNull(if (pr.maxIsSeconds) bestSet ?: secPerSet else secPerSet, maxSec).maxOrNull()
                        ?.let { a.bestSeconds = maxOf(a.bestSeconds ?: 0, it) }
                    val summary = pr.summary().ifEmpty { null }
                    if (a.first == null) a.first = summary
                    a.last = summary
                    a.lastDate = s.date
                    totalSets += sets; totalReps += reps; totalSeconds += secs
                    val variant = pr.variant
                    (variant?.counter ?: pr.exercise.counter)?.takeIf { it.isNotBlank() }?.let { name ->
                        val add = if (variant != null) {
                            // Une variante compte en équivalent de sa famille (1 pompe à une main = 5 pompes).
                            val equivalent = variant.equivalent(if (variant.isSeconds) secs else reps) ?: 0
                            if (variant.testSeconds) Counter(name, seconds = equivalent) else Counter(name, reps = equivalent)
                        } else {
                            Counter(name, reps, secs, if (pr.weighted == true) secs else 0)
                        }
                        counters[name] = counters[name]?.plus(add) ?: add
                    }
                    weekSets[monday] = (weekSets[monday] ?: 0) + sets
                }
                if (touched) {
                    history += HistoryEntry(
                        program.plan.name, program.colorIndex, s.day.title ?: "Séance", s.date, schedule.requiredDone(s), s.requiredCount, s.isBoss,
                    )
                }
            }

            val currentStreak = schedule.progress().streak
            return Stats(
                sessionsDone = sessionsDone,
                totalSets = totalSets,
                totalReps = totalReps,
                totalSeconds = totalSeconds,
                bestStreak = maxOf(bestStreak, currentStreak),
                currentStreak = currentStreak,
                bossDefeated = bossDefeated,
                bossMet = bossMet,
                exercises = acc.map { (key, a) ->
                    ExerciseStats(key, a.name, a.times, a.sets, a.reps, a.seconds, a.bestReps, a.bestSeconds, a.first, a.last, a.lastDate, optional = !a.required)
                }.sortedByDescending { it.times },
                weeks = chartMondays(today).map {
                    WeekVolume(it, weekSets[it] ?: 0, it in weekBoss, it in weekDeload)
                },
                history = history.sortedByDescending { it.date }.take(HISTORY_SIZE),
                exploits = exploits.sortedByDescending { it.date },
                counters = counters.values.sortedBy { it.isTime },
                burstsDone = burstsDone,
            )
        }
    }
}
