package com.spacelenin.flemmefit.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/** Une séance placée dans le calendrier, avec ce qui y est demandé. */
data class Session(
    /** Index de jour dans le programme (répétitions comprises). */
    val index: Int,
    /** 1-based. */
    val cycle: Int,
    /** 1-based, dans le cycle. */
    val week: Int,
    val day: PlanDay,
    val weekLabel: String?,
    val date: LocalDate,
    val isBoss: Boolean,
    val isDeload: Boolean,
    val prescriptions: List<Prescription>,
) {
    val exerciseCount get() = day.exercises.size
    /** Semaine du programme depuis le début (0-based), répétitions comprises. */
    val programWeek get() = index / DAYS_PER_WEEK
}

enum class SessionStatus { DONE, PARTIAL, MISSED, PENDING }

enum class ExerciseState { DONE, PARTIAL, TODO }

data class Progress(
    val doneExercises: Int,
    val totalExercises: Int,
    val doneSessions: Int,
    /** Programme illimité : séances prévues jusqu'à aujourd'hui. */
    val totalSessions: Int,
    /** Séances terminées d'affilée, en remontant depuis aujourd'hui. */
    val streak: Int,
    val missedSessions: Int,
    val nextSession: Session?,
) {
    val ratio: Float get() = if (totalExercises == 0) 0f else doneExercises.toFloat() / totalExercises
}

/**
 * Projection d'un [StoredProgram] sur le calendrier.
 *
 * Les séances sont résolues à la demande et dans l'ordre (la progression d'une séance dépend des
 * précédentes), ce qui permet les programmes illimités. Un objet par état du programme et par jour :
 * il n'est pas partagé entre threads.
 */
class ProgramSchedule(val program: StoredProgram, val today: LocalDate = LocalDate.now()) {
    private val plan = program.plan
    private val postpones = program.postpones.sorted()
    private val deloadEvery = plan.effectiveDeloadEvery
    private val seed = program.id.hashCode().toLong()

    val cycleLength = plan.weeks.size * DAYS_PER_WEEK
    val isForever = plan.isForever
    /** null si illimité. */
    val totalDays: Int? = if (isForever) null else cycleLength * plan.repeat
    val startDate: LocalDate = program.start
    val endDate: LocalDate? = totalDays?.let { dateOf(it - 1) }

    private fun shift(dayIndex: Int): Int {
        var n = 0
        for (p in postpones) if (p <= dayIndex) n++ else break
        return n
    }

    fun dateOf(dayIndex: Int): LocalDate = startDate.plusDays((dayIndex + shift(dayIndex)).toLong())

    private fun inRange(dayIndex: Int) = dayIndex >= 0 && (totalDays == null || dayIndex < totalDays)

    /** Index du jour de programme tombant à [date], ou null (avant le début, après la fin, ou jour de décalage). */
    fun indexOn(date: LocalDate): Int? {
        val offset = ChronoUnit.DAYS.between(startDate, date).toInt()
        if (offset < 0) return null
        for (i in maxOf(0, offset - postpones.size)..offset) {
            if (i + shift(i) == offset) return i.takeIf(::inRange)
        }
        return null
    }

    /** Plus grand index de jour dont la date est ≤ [date] (-1 si avant le début). */
    fun lastIndexOnOrBefore(date: LocalDate): Int {
        var i = ChronoUnit.DAYS.between(startDate, date).toInt()
        if (i < 0) return -1
        while (i >= 0 && dateOf(i).isAfter(date)) i--
        return totalDays?.let { minOf(i, it - 1) } ?: i
    }

    fun isInProgram(date: LocalDate) = !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate))

    fun isDeloadWeek(programWeek: Int) = deloadEvery > 0 && (programWeek + 1) % deloadEvery == 0

    // --- Jours Boss : une semaine sur 3 à 4, tirée au sort de façon reproductible. ---

    private val bossRandom = Random(seed)
    private val bossWeeks = ArrayList<Int>()
    private val bossWeekSet = HashSet<Int>()

    private fun planDaysOfWeek(programWeek: Int): List<PlanDay> = plan.weeks[programWeek % plan.weeks.size].days

    private fun isBossWeek(programWeek: Int): Boolean {
        if (!plan.boss) return false
        while (bossWeeks.lastOrNull().let { it == null || it < programWeek }) {
            val prev = bossWeeks.lastOrNull()
            var next = if (prev == null) 2 + bossRandom.nextInt(2) else prev + 3 + bossRandom.nextInt(2)
            while (isDeloadWeek(next) || planDaysOfWeek(next).isEmpty()) next++
            if (totalDays != null && next * DAYS_PER_WEEK >= totalDays) next = Int.MAX_VALUE
            bossWeeks += next
            bossWeekSet += next
        }
        return programWeek in bossWeekSet
    }

    private fun bossIndexOfWeek(programWeek: Int): Int {
        val days = planDaysOfWeek(programWeek)
        val pick = Random(seed xor (programWeek * 7919L)).nextInt(days.size)
        return programWeek * DAYS_PER_WEEK + days[pick].day - 1
    }

    // --- Résolution des séances ---

    private val resolved = HashMap<Int, Session>()
    private var builtUpTo = -1
    private val validated = HashMap<String, Int>()
    private val bonus = HashMap<String, Int>()
    private val lastMax = HashMap<String, Int>()
    private val testResults = HashMap(program.tests)
    private val levelOf = HashMap<String, Int>()
    private val levelSince = HashMap<String, Int>()

    private fun ensure(dayIndex: Int) {
        val limit = totalDays?.let { minOf(dayIndex, it - 1) } ?: dayIndex
        while (builtUpTo < limit) build(++builtUpTo)
    }

    private fun build(i: Int) {
        val week = i / DAYS_PER_WEEK
        val cycleWeek = week % plan.weeks.size
        val planWeek = plan.weeks[cycleWeek]
        val day = planWeek.days.firstOrNull { it.day == i % DAYS_PER_WEEK + 1 } ?: return
        val date = dateOf(i)
        val deload = isDeloadWeek(week)
        // Le Boss ne se dévoile que la veille : avant, la séance est affichée normalement.
        val boss = !deload && isBossWeek(week) && bossIndexOfWeek(week) == i && !date.isAfter(today.plusDays(1))
        val weeksBefore = if (deloadEvery > 0) week - week / deloadEvery else week

        val prescriptions = day.exercises.map { ex ->
            if (ex.retest) return@map Prescription.test(ex, plan.test(ex.test), ex.test?.let(testResults::get))
            val palier = if (ex.levels.isEmpty()) null else ex.levelIndexFor(ex.test?.let(testResults::get))
            // Nouveau palier : la progression repart de son départ.
            if (palier != null && levelOf[ex.key] != palier) {
                levelOf[ex.key] = palier
                validated[ex.key] = 0
                bonus[ex.key] = 0
                levelSince[ex.key] = weeksBefore
            }
            val p = ex.progression
            val steps = when {
                p == null -> 0
                p.basis == Progression.BASIS_WEEK -> (weeksBefore - (levelSince[ex.key] ?: 0)) / p.every
                else -> (validated[ex.key] ?: 0) / p.every
            }
            val level = (steps + (bonus[ex.key] ?: 0)).coerceAtLeast(0) + if (boss && p != null) 1 else 0
            Prescription.of(
                palier?.let(ex::atLevel) ?: ex, level, deload, boss = boss, lastMax = lastMax[ex.key], palier = palier,
            )
        }
        resolved[i] = Session(i, week / plan.weeks.size + 1, cycleWeek + 1, day, planWeek.label, date, boss, deload, prescriptions)

        prescriptions.forEachIndexed { e, pr ->
            val key = pr.key
            val max = program.logOf(i, e)?.max
            if (pr.isTest) {
                val test = pr.exercise.test
                if (max != null && test != null) testResults[test] = max
                return@forEachIndexed
            }
            if (program.isDone(i, e) && !deload) validated[key] = (validated[key] ?: 0) + 1
            if (max == null) return@forEachIndexed
            lastMax[key] = max
            adjustBonus(pr, max)
        }
    }

    /** Dernier résultat connu de chaque test : celui de départ, ou celui du dernier test refait. */
    fun currentTestResults(): Map<String, Int> {
        ensure(lastIndexOnOrBefore(today))
        return testResults.toMap()
    }

    /** Un max nettement au-dessus du prévu accélère la progression, en dessous il la ralentit (prudemment). */
    private fun adjustBonus(pr: Prescription, max: Int) {
        val p = pr.exercise.progression ?: return
        val (planned, step) = if (pr.maxIsSeconds) pr.seconds to Measures.seconds(p.duration) else pr.reps to p.reps
        if (planned == null || step == null || step <= 0) return
        val diff = max - planned
        val delta = when {
            diff >= 6 * step -> 2
            diff >= 3 * step -> 1
            diff < 0 -> -1
            else -> 0
        }
        bonus[pr.key] = ((bonus[pr.key] ?: 0) + delta).coerceIn(-2, 4)
    }

    fun sessionAt(dayIndex: Int): Session? {
        if (!inRange(dayIndex)) return null
        ensure(dayIndex)
        return resolved[dayIndex]
    }

    fun sessionOn(date: LocalDate): Session? = indexOn(date)?.let(::sessionAt)

    /** Séances des index 0 à [lastIndex] inclus. */
    fun sessionsThrough(lastIndex: Int): List<Session> {
        ensure(lastIndex)
        return (0..minOf(lastIndex, builtUpTo)).mapNotNull { resolved[it] }
    }

    fun allSessionsOrUpTo(date: LocalDate): List<Session> =
        if (totalDays != null) sessionsThrough(totalDays - 1) else sessionsThrough(lastIndexOnOrBefore(date))

    fun exerciseState(session: Session, e: Int): ExerciseState {
        if (program.isDone(session.index, e)) return ExerciseState.DONE
        val log = program.logOf(session.index, e)
        return if (log != null && (log.sets != null || log.reps != null || log.seconds != null)) ExerciseState.PARTIAL
        else ExerciseState.TODO
    }

    fun doneCount(session: Session) = (0 until session.exerciseCount).count { program.isDone(session.index, it) }

    fun status(session: Session): SessionStatus {
        val done = doneCount(session)
        val started = done > 0 || (0 until session.exerciseCount).any { exerciseState(session, it) == ExerciseState.PARTIAL }
        return when {
            done == session.exerciseCount -> SessionStatus.DONE
            started -> SessionStatus.PARTIAL
            session.date.isBefore(today) -> SessionStatus.MISSED
            else -> SessionStatus.PENDING
        }
    }

    fun nextSession(): Session? {
        var i = maxOf(0, lastIndexOnOrBefore(today.minusDays(1)) + 1)
        val stop = i + cycleLength * 2 + postpones.size + DAYS_PER_WEEK
        while (i < stop && inRange(i)) {
            val s = sessionAt(i)
            if (s != null && !s.date.isBefore(today) && doneCount(s) < s.exerciseCount) return s
            i++
        }
        return null
    }

    fun progress(): Progress {
        val sessions = allSessionsOrUpTo(today)
        var doneEx = 0
        var totalEx = 0
        var doneSessions = 0
        var missed = 0
        for (s in sessions) {
            val d = doneCount(s)
            doneEx += d
            totalEx += s.exerciseCount
            if (d == s.exerciseCount) doneSessions++
            else if (status(s) == SessionStatus.MISSED) missed++
        }

        var streak = 0
        for (s in sessions.asReversed()) {
            if (s.date.isAfter(today)) continue
            val complete = doneCount(s) == s.exerciseCount
            if (complete) streak++
            else if (s.date == today) continue // la séance du jour ne casse pas la série
            else break
        }
        return Progress(doneEx, totalEx, doneSessions, sessions.size, streak, missed, nextSession())
    }
}
