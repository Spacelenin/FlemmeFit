package com.spacelenin.flemmefit.watch

import com.spacelenin.flemmefit.model.ExerciseLog
import com.spacelenin.flemmefit.model.FriendCard
import com.spacelenin.flemmefit.model.GuidedExercise
import com.spacelenin.flemmefit.model.ProgramSchedule
import com.spacelenin.flemmefit.model.Session
import com.spacelenin.flemmefit.model.SessionStatus
import com.spacelenin.flemmefit.model.Social
import com.spacelenin.flemmefit.model.Stats
import com.spacelenin.flemmefit.model.StoredProgram
import com.spacelenin.flemmefit.ui.formatTotalSeconds
import com.spacelenin.flemmefit.ui.levelStars
import com.spacelenin.flemmefit.ui.position
import com.spacelenin.flemmefit.ui.shortLabel
import com.spacelenin.flemmefit.ui.title
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Traduit l'état du téléphone pour la montre (tout est calculé ici), et ce que la montre renvoie en saisies. */
object WatchBuilder {
    /** Version montre minimale : celle qui sait recevoir ses mises à jour du téléphone. */
    const val MIN_WATCH = 27

    private val Letters = listOf("L", "M", "M", "J", "V", "S", "D")

    /** Le programme à montrer : celui qui a une séance aujourd'hui pas finie, sinon une séance aujourd'hui, sinon le premier en cours. */
    fun pick(programs: List<StoredProgram>, today: LocalDate): Pair<StoredProgram, ProgramSchedule>? {
        val all = programs.map { it to ProgramSchedule(it, today) }.filter { (_, s) -> s.isInProgram(today) }
        return all.firstOrNull { (_, s) -> s.sessionOn(today)?.let { !s.isComplete(it) } == true }
            ?: all.firstOrNull { (_, s) -> s.sessionOn(today) != null }
            ?: all.firstOrNull()
    }

    fun today(
        programs: List<StoredProgram>,
        today: LocalDate,
        restBonus: Int,
        friends: List<FriendCard>,
        me: FriendCard?,
        myId: String,
    ): WatchToday {
        val picked = pick(programs, today)
        val stats = runCatching { Stats.merge(programs.map { Stats.of(ProgramSchedule(it, today)) }, today) }.getOrNull()
        val common = WatchToday(
            date = today.toString(),
            streak = stats?.currentStreak ?: 0,
            friends = friendsOf(friends, me, myId, today),
            friendNews = newsOf(friends, myId, today),
            stats = statsOf(stats),
            feelings = StoredProgram.FEELINGS,
            minWatch = MIN_WATCH,
            sentAt = System.currentTimeMillis(),
        )
        if (picked == null) return common.copy(mode = WatchToday.MODE_NONE)
        val (program, schedule) = picked
        val session = schedule.sessionOn(today)
        val base = common.copy(
            program = program.plan.name,
            programId = program.id,
            week = weekOf(schedule, today),
            bossTomorrow = schedule.sessionOn(today.plusDays(1))?.isBoss == true,
        )
        if (session == null) {
            val next = schedule.nextSession()
            return base.copy(
                mode = WatchToday.MODE_REST,
                next = next?.let { "Prochaine : ${it.date.shortLabel()} · ${it.title()}" },
            )
        }
        return base.copy(
            mode = when {
                session.isBoss -> WatchToday.MODE_BOSS
                session.isBurst -> WatchToday.MODE_BURST
                session.isChill -> WatchToday.MODE_CHILL
                session.form != null -> WatchToday.MODE_FORM
                session.isDeload -> WatchToday.MODE_DELOAD
                else -> WatchToday.MODE_NORMAL
            },
            title = session.title(),
            position = session.position(program.plan.repeat),
            session = session.index,
            exercises = session.order.mapNotNull { i -> exerciseOf(program, session, i, restBonus) },
        )
    }

    private fun exerciseOf(program: StoredProgram, session: Session, i: Int, restBonus: Int): WatchExercise? {
        val pr = session.prescriptions.getOrNull(i) ?: return null
        val g = GuidedExercise(pr, restBonus)
        val planned = g.planned
        return WatchExercise(
            i = i,
            name = pr.exercise.name,
            how = pr.exercise.description?.trim()?.take(400),
            sets = g.sets,
            reps = planned?.takeIf { !g.inSeconds },
            seconds = planned?.takeIf { g.inSeconds },
            restSec = g.restSeconds ?: 0,
            allOut = pr.allOut,
            target = pr.target,
            // Même échelle que le téléphone : les paliers ramenés sur 4 étoiles.
            level = levelStars(pr.palier, pr.levelCount),
            levelCount = levelStars(pr.palier, pr.levelCount)?.let { 4 },
            optional = pr.exercise.optional,
            sided = g.sided != null,
            tag = when {
                pr.eccentric -> "excentrique"
                pr.tired -> "crevé"
                pr.formPercent != null -> "forme ${pr.formPercent} %"
                pr.isEasy -> "version facile"
                pr.weighted == true -> "avec poids"
                else -> null
            },
            done = program.isDone(session.index, i),
        )
    }

    private fun weekOf(schedule: ProgramSchedule, today: LocalDate): WatchWeek {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        var done = 0
        var total = 0
        val days = (0 until 7).map { d ->
            val date = monday.plusDays(d.toLong())
            val s = schedule.sessionOn(date)
            if (s != null) {
                total++
                if (schedule.isComplete(s)) done++
            }
            WatchDay(
                Letters[d],
                when {
                    s == null -> "rest"
                    schedule.isComplete(s) -> "done"
                    date == today -> "today"
                    date.isBefore(today) -> if (schedule.status(s) == SessionStatus.MISSED) "missed" else "planned"
                    // Le Boss ne se dévoile que la veille, comme sur le téléphone.
                    s.isBoss && !date.isAfter(today.plusDays(1)) -> "boss"
                    else -> "planned"
                },
            )
        }
        val programWeek = schedule.sessionOn(today)?.week ?: schedule.nextSession()?.week
        return WatchWeek(label = programWeek?.let { "sem. $it" } ?: "", days = days, done = done, total = total)
    }

    private fun friendsOf(friends: List<FriendCard>, me: FriendCard?, myId: String, today: LocalDate): List<WatchFriend> =
        Social.ranking(friends.filter { it.id != myId } + listOfNotNull(me), today)
            .take(10)
            .map {
                WatchFriend(
                    name = if (it.id == myId) "Toi" else it.pseudo,
                    points = it.score,
                    me = it.id == myId,
                    today = when {
                        !it.isToday(today) -> "Pas de nouvelles aujourd'hui"
                        it.todayTotal == 0 -> "Repos aujourd'hui"
                        it.todayDone >= it.todayTotal -> "Séance faite ✅"
                        else -> "Aujourd'hui : ${it.todayDone}/${it.todayTotal} exos"
                    },
                    streak = it.streak,
                    boss = it.bossDefeated,
                    exploits = it.exploits,
                    lastExploit = it.lastExploit,
                )
            }

    private fun newsOf(friends: List<FriendCard>, myId: String, today: LocalDate): String? =
        friends.filter { it.id != myId && it.isToday(today) && it.todayTotal > 0 && it.todayDone >= it.todayTotal }
            .maxByOrNull { it.updatedAt }
            ?.let { "${it.pseudo} : séance faite" }

    private fun statsOf(stats: Stats?): List<WatchStat> {
        if (stats == null) return emptyList()
        val base = listOf(
            WatchStat("série", "🔥 ${stats.currentStreak}"),
            WatchStat("séances", "${stats.sessionsDone}"),
            WatchStat("Boss vaincus", "💀 ${stats.bossDefeated}"),
            WatchStat("exploits", "🏆 ${stats.exploits.size}"),
        )
        val counters = stats.counters
            .sortedByDescending { if (it.isTime) it.seconds / 6 else it.reps }
            .take(2)
            .map { WatchStat(it.name, if (it.isTime) formatTotalSeconds(it.seconds) else "${it.reps}") }
        return base + counters
    }

    /** Ce qu'une saisie de la montre devient sur le téléphone. */
    sealed interface Applied {
        data class Exercise(val programId: String, val dayIndex: Int, val exerciseIndex: Int, val log: ExerciseLog, val complete: Boolean) : Applied
        data class Feeling(val programId: String, val dayIndex: Int, val feeling: Int) : Applied
        data class ExerciseFeeling(val programId: String, val dayIndex: Int, val exerciseIndex: Int, val feeling: Int) : Applied
        /** Case de la liste des exos : l'exo doit être fait ([done]) ou non. */
        data class Check(val programId: String, val dayIndex: Int, val exerciseIndex: Int, val done: Boolean) : Applied
    }

    /** La saisie à enregistrer pour [log], ou null si elle ne correspond plus à rien (programme supprimé, exo passé). */
    fun apply(log: WatchLog, programs: List<StoredProgram>, restBonus: Int): Applied? {
        val date = runCatching { LocalDate.parse(log.date) }.getOrNull() ?: return null
        val program = programs.firstOrNull { it.id == log.programId } ?: pick(programs, date)?.first ?: return null
        if (log.exercise == null) {
            return log.feeling?.takeIf { it in StoredProgram.FEELINGS.indices }?.let { Applied.Feeling(program.id, log.session, it) }
        }
        if (log.onlyFeeling) {
            return log.feeling?.takeIf { it in StoredProgram.FEELINGS.indices }
                ?.let { Applied.ExerciseFeeling(program.id, log.session, log.exercise, it) }
        }
        log.check?.let { return Applied.Check(program.id, log.session, log.exercise, it) }
        if (log.skipped) return null
        val session = ProgramSchedule(program, date).sessionAt(log.session) ?: return null
        val pr = session.prescriptions.getOrNull(log.exercise) ?: return null
        val (saved, complete) = GuidedExercise(pr, restBonus).result(log.values)
        return Applied.Exercise(program.id, log.session, log.exercise, saved, complete)
    }
}
