package com.spacelenin.flemmefit.challenge

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.spacelenin.flemmefit.FlemmeFitApp
import com.spacelenin.flemmefit.MainActivity
import com.spacelenin.flemmefit.R
import com.spacelenin.flemmefit.data.ProgramRepository
import com.spacelenin.flemmefit.model.Challenge
import com.spacelenin.flemmefit.model.ChallengeSlot
import com.spacelenin.flemmefit.model.Challenges
import com.spacelenin.flemmefit.model.ProgramSchedule
import com.spacelenin.flemmefit.model.Stats
import com.spacelenin.flemmefit.timer.AppVisibility
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Défis surprise : une alarme au prochain créneau (jour de séance, heure tirée au sort), une notification
 * « Accepter / Refuser », une heure pour le faire, et des points s'il est réussi.
 */
class ChallengeManager(private val context: Context, private val repository: ProgramRepository, private val seed: Long) {

    private val prefs = context.getSharedPreferences("challenges", Context.MODE_PRIVATE)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private val _all = MutableStateFlow(load())
    /** Tous les défis reçus, du plus ancien au plus récent. */
    val all: StateFlow<List<Challenge>> = _all.asStateFlow()

    /** Appelé quand un défi est gagné (les points changent). */
    @Volatile
    var onWon: () -> Unit = {}

    private fun load(): List<Challenge> = try {
        json.decodeFromString(ListSerializer(Challenge.serializer()), prefs.getString(KEY_ALL, null) ?: "[]")
    } catch (e: Exception) {
        emptyList()
    }

    @Synchronized
    private fun save(list: List<Challenge>) {
        val kept = list.takeLast(MAX_KEPT)
        prefs.edit().putString(KEY_ALL, json.encodeToString(ListSerializer(Challenge.serializer()), kept)).apply()
        _all.value = kept
    }

    fun current(now: Long = System.currentTimeMillis()): Challenge? = _all.value.lastOrNull()?.takeIf { it.open(now) }

    fun wonCount() = Challenges.wonCount(_all.value)

    // --- Créneaux ---

    private fun sessionDays(monday: LocalDate): List<LocalDate> {
        val programs = repository.state.value.programs
        return (0L..6L).map(monday::plusDays).filter { d -> programs.any { ProgramSchedule(it, d).sessionOn(d) != null } }
    }

    private fun nextSlot(now: LocalDateTime): Pair<ChallengeSlot, LocalDateTime>? {
        // Un défi repoussé (séance en cours) garde sa place.
        postponed()?.let { (slot, at) -> if (at.isAfter(now) && _all.value.none { it.id == slot.id }) return slot to at }
        val used = _all.value.map { it.id }.toSet()
        val monday = Challenges.monday(now.toLocalDate())
        return listOf(monday, monday.plusWeeks(1)).asSequence()
            .flatMap { Challenges.slots(it, sessionDays(it), seed) }
            .filter { it.id !in used }
            .map { it to LocalDateTime.of(it.date, LocalTime.of(it.minuteOfDay / 60, it.minuteOfDay % 60)) }
            .firstOrNull { (_, at) -> at.isAfter(now) }
    }

    /** Programme l'alarme du prochain créneau (au lancement, au redémarrage, après chaque défi). */
    fun reschedule() {
        try {
            val zone = ZoneId.systemDefault()
            val (slot, at) = nextSlot(LocalDateTime.now(zone)) ?: return
            setExact(at.atZone(zone).toInstant().toEpochMilli(), offerIntent(slot.id))
        } catch (e: Exception) {
            Log.e(TAG, "Défi impossible à programmer", e)
        }
    }

    // --- Déroulé ---

    private fun postponed(): Pair<ChallengeSlot, LocalDateTime>? {
        val raw = prefs.getString(KEY_POSTPONED, null) ?: return null
        val at = LocalDateTime.parse(raw.substringAfter('|'))
        return ChallengeSlot(raw.substringBefore('|'), at.toLocalDate(), at.hour * 60 + at.minute) to at
    }

    /** L'alarme du créneau sonne : on lance le défi si tout s'y prête. */
    internal fun fire(slotId: String) {
        prefs.edit().remove(KEY_POSTPONED).apply()
        try {
            offer(slotId)
        } catch (e: Exception) {
            Log.e(TAG, "Défi raté", e)
        } finally {
            reschedule()
        }
    }

    private fun offer(slotId: String) {
        val now = System.currentTimeMillis()
        if (_all.value.any { it.id == slotId } || current(now) != null) return
        val today = LocalDate.now()
        val programs = repository.state.value.programs
        // Les défis commencent au 7e jour d'entraînement.
        val done = Stats.merge(programs.map { Stats.of(ProgramSchedule(it, today)) }, today).sessionsDone
        if (done < Challenges.SESSIONS_BEFORE) return
        // Séance en cours : on repasse un peu plus tard (pas après 21 h 30).
        if (AppVisibility.sessionRunning) {
            val later = LocalDateTime.now().plusMinutes(POSTPONE_MS / 60_000)
            if (later.toLocalDate() == today && later.toLocalTime().isBefore(LocalTime.of(21, 30))) {
                prefs.edit().putString(KEY_POSTPONED, "$slotId|$later").apply()
            }
            return
        }
        val session = programs.firstNotNullOfOrNull { ProgramSchedule(it, today).sessionOn(today) } ?: return
        val (name, dose) = Challenges.pick(session.prescriptions, seed * 13 + slotId.hashCode()) ?: return
        val challenge = Challenge(slotId, today.toString(), name, dose.first, dose.second, offeredAt = now)
        save(_all.value + challenge)
        notifyOffer(challenge)
    }

    fun accept() {
        val c = update { it.accept(System.currentTimeMillis()) } ?: return
        cancelNotification()
        notifyRunning(c)
        setExact(c.acceptedAt!! + Challenges.DURATION_MS, actionIntent(ACTION_TIME_UP))
    }

    fun refuse() {
        update { it.refuse(System.currentTimeMillis()) } ?: return
        cancelNotification()
    }

    fun finish(won: Boolean) {
        val c = update { it.finish(won, System.currentTimeMillis()) } ?: return
        cancelNotification()
        alarms.cancel(actionIntent(ACTION_TIME_UP))
        if (c.won == true) onWon()
    }

    /** L'heure est passée : on demande si c'est fait. */
    internal fun timeUp() {
        val c = current() ?: return
        if (c.status(System.currentTimeMillis()) == Challenge.Status.ASK_RESULT) notifyAskResult(c)
    }

    /** Applique [change] au défi en cours ; null si rien n'a changé. */
    private fun update(change: (Challenge) -> Challenge): Challenge? {
        val c = current() ?: return null
        val next = change(c)
        if (next == c) return null
        save(_all.value.dropLast(1) + next)
        return next
    }

    // --- Notifications ---

    private fun notifyOffer(c: Challenge) {
        val builder = base("🎯 Défi surprise !", "${c.label} en 1 h, en autant de fois que tu veux. Tu as 30 min pour accepter.")
            .setTimeoutAfter(Challenges.ANSWER_MS)
            .addAction(0, "Accepter", actionIntent(ACTION_ACCEPT))
            .addAction(0, "Refuser", actionIntent(ACTION_REFUSE))
        notify(builder)
    }

    private fun notifyRunning(c: Challenge) {
        val end = c.acceptedAt!! + Challenges.DURATION_MS
        val builder = base("🎯 Défi en cours", c.label)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(end)
            .setTimeoutAfter(Challenges.DURATION_MS)
            .addAction(0, "Réussi ✅", actionIntent(ACTION_WON))
        notify(builder)
    }

    private fun notifyAskResult(c: Challenge) {
        val builder = base("🎯 Temps écoulé !", "${c.label} : alors, réussi ?")
            .setTimeoutAfter(Challenges.GRACE_MS)
            .addAction(0, "Réussi ✅", actionIntent(ACTION_WON))
            .addAction(0, "Raté", actionIntent(ACTION_LOST))
        notify(builder)
    }

    private fun base(title: String, text: String): NotificationCompat.Builder {
        ensureChannel()
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent())
    }

    private fun notify(builder: NotificationCompat.Builder) {
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Notifications refusées : le défi se verra à l'ouverture de l'appli.
        }
    }

    private fun cancelNotification() = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    private fun openIntent(): PendingIntent = PendingIntent.getActivity(
        context, NOTIFICATION_ID,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_PROGRAM_ID, OPEN_ID)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun offerIntent(slotId: String): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_OFFER,
        Intent(context, ChallengeReceiver::class.java).setAction(ACTION_OFFER).putExtra(EXTRA_SLOT, slotId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun actionIntent(action: String): PendingIntent = PendingIntent.getBroadcast(
        context, action.hashCode(),
        Intent(context, ChallengeReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun setExact(at: Long, pending: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                return
            }
        } catch (_: SecurityException) {
            // Alarmes exactes refusées : l'approximative fait de son mieux.
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Défis surprise", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Un défi de temps en temps, à accepter ou non"
                },
            )
        }
    }

    companion object {
        private const val TAG = "ChallengeManager"
        private const val KEY_ALL = "all"
        private const val KEY_POSTPONED = "postponed"
        private const val MAX_KEPT = 200
        private const val CHANNEL_ID = "challenges"
        private const val NOTIFICATION_ID = 4400
        private const val REQUEST_OFFER = 4401
        private const val POSTPONE_MS = 30 * 60 * 1000L
        /** Ouvrir l'appli sur la page du défi (même canal que les rappels). */
        const val OPEN_ID = "defi"
        internal const val EXTRA_SLOT = "slot"
        internal const val ACTION_OFFER = "com.spacelenin.flemmefit.CHALLENGE_OFFER"
        internal const val ACTION_ACCEPT = "com.spacelenin.flemmefit.CHALLENGE_ACCEPT"
        internal const val ACTION_REFUSE = "com.spacelenin.flemmefit.CHALLENGE_REFUSE"
        internal const val ACTION_WON = "com.spacelenin.flemmefit.CHALLENGE_WON"
        internal const val ACTION_LOST = "com.spacelenin.flemmefit.CHALLENGE_LOST"
        internal const val ACTION_TIME_UP = "com.spacelenin.flemmefit.CHALLENGE_TIME_UP"
    }
}

/** Alarmes et boutons des notifications de défi (non exporté). */
class ChallengeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = (context.applicationContext as FlemmeFitApp).challenges
        when (intent.action) {
            ChallengeManager.ACTION_OFFER -> {
                val slot = intent.getStringExtra(ChallengeManager.EXTRA_SLOT) ?: return
                val pending = goAsync()
                Thread {
                    try {
                        manager.fire(slot)
                    } finally {
                        pending.finish()
                    }
                }.start()
            }
            ChallengeManager.ACTION_ACCEPT -> manager.accept()
            ChallengeManager.ACTION_REFUSE -> manager.refuse()
            ChallengeManager.ACTION_WON -> manager.finish(won = true)
            ChallengeManager.ACTION_LOST -> manager.finish(won = false)
            ChallengeManager.ACTION_TIME_UP -> manager.timeUp()
        }
    }
}
