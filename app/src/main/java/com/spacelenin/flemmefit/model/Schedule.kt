package com.spacelenin.flemmefit.model

import java.time.LocalDate

/** Une séance placée dans le calendrier. */
data class Session(
    /** Index de jour dans le programme complet (répétitions comprises). */
    val index: Int,
    /** 1-based. */
    val cycle: Int,
    /** 1-based, dans le cycle. */
    val week: Int,
    val day: PlanDay,
    val weekLabel: String?,
    val date: LocalDate,
) {
    val exerciseCount get() = day.exercises.size
}

enum class SessionStatus { DONE, PARTIAL, MISSED, PENDING }

data class Progress(
    val doneExercises: Int,
    val totalExercises: Int,
    val doneSessions: Int,
    val totalSessions: Int,
    /** Séances terminées d'affilée, en remontant depuis aujourd'hui. */
    val streak: Int,
    val missedSessions: Int,
    val nextSession: Session?,
) {
    val ratio: Float get() = if (totalExercises == 0) 0f else doneExercises.toFloat() / totalExercises
}

/** Projection d'un [StoredProgram] sur le calendrier. Calcul pur, sans état. */
class ProgramSchedule(val program: StoredProgram) {
    private val plan = program.plan
    private val sortedPostpones = program.postpones.sorted()

    val cycleLength = plan.weeks.size * DAYS_PER_WEEK
    val totalDays = cycleLength * plan.repeat
    val startDate: LocalDate = program.start

    val sessions: List<Session> = buildList {
        for (cycle in 0 until plan.repeat) {
            plan.weeks.forEachIndexed { w, week ->
                week.days.forEach { day ->
                    val index = cycle * cycleLength + w * DAYS_PER_WEEK + (day.day - 1)
                    add(Session(index, cycle + 1, w + 1, day, week.label, dateOf(index)))
                }
            }
        }
    }

    private val byDate: Map<LocalDate, Session> = sessions.associateBy { it.date }

    val endDate: LocalDate = dateOf(totalDays - 1)

    fun dateOf(dayIndex: Int): LocalDate {
        val shift = sortedPostpones.count { it <= dayIndex }
        return startDate.plusDays((dayIndex + shift).toLong())
    }

    fun sessionOn(date: LocalDate): Session? = byDate[date]

    fun isInProgram(date: LocalDate) = !date.isBefore(startDate) && !date.isAfter(endDate)

    fun doneCount(session: Session) = (0 until session.exerciseCount).count { program.isDone(session.index, it) }

    fun status(session: Session, today: LocalDate): SessionStatus {
        val done = doneCount(session)
        return when {
            done == session.exerciseCount -> SessionStatus.DONE
            done > 0 -> SessionStatus.PARTIAL
            session.date.isBefore(today) -> SessionStatus.MISSED
            else -> SessionStatus.PENDING
        }
    }

    fun progress(today: LocalDate): Progress {
        var doneEx = 0
        var totalEx = 0
        var doneSessions = 0
        var missed = 0
        for (s in sessions) {
            val d = doneCount(s)
            doneEx += d
            totalEx += s.exerciseCount
            if (d == s.exerciseCount) doneSessions++
            else if (d == 0 && s.date.isBefore(today)) missed++
        }

        var streak = 0
        for (s in sessions.asReversed()) {
            if (s.date.isAfter(today)) continue
            val complete = doneCount(s) == s.exerciseCount
            if (complete) streak++
            else if (s.date == today) continue // la séance du jour ne casse pas la série
            else break
        }

        val next = sessions.firstOrNull { !it.date.isBefore(today) && doneCount(it) < it.exerciseCount }
        return Progress(doneEx, totalEx, doneSessions, sessions.size, streak, missed, next)
    }
}
