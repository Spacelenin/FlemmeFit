package com.spacelenin.flemmefit.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.spacelenin.flemmefit.FlemmeFitApp
import com.spacelenin.flemmefit.MainActivity
import com.spacelenin.flemmefit.R
import com.spacelenin.flemmefit.model.ReminderMessage
import com.spacelenin.flemmefit.model.Reminders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Réglages des rappels, stockés à part de l'état des programmes. */
data class ReminderSettings(
    val enabled: Boolean = false,
    val dailyMinutes: Int = 18 * 60,
    val eveningEnabled: Boolean = true,
    val eveningMinutes: Int = 21 * 60,
    /** Programmes pour lesquels les rappels sont coupés. */
    val mutedPrograms: Set<String> = emptySet(),
)

/**
 * Rappels locaux : une alarme par type (jour, soir), reprogrammée à chaque déclenchement,
 * au démarrage de l'app, après un redémarrage du téléphone et après une mise à jour.
 * Le contenu est calculé au moment où l'alarme sonne, à partir de l'état réel des séances.
 */
class ReminderScheduler(private val context: Context) {

    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
    private val alarms = context.getSystemService(AlarmManager::class.java)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<ReminderSettings> = _settings.asStateFlow()

    private fun load() = ReminderSettings(
        enabled = prefs.getBoolean("enabled", false),
        dailyMinutes = prefs.getInt("daily", 18 * 60),
        eveningEnabled = prefs.getBoolean("evening", true),
        eveningMinutes = prefs.getInt("eveningTime", 21 * 60),
        mutedPrograms = prefs.getStringSet("muted", emptySet()).orEmpty(),
    )

    fun update(settings: ReminderSettings) {
        prefs.edit()
            .putBoolean("enabled", settings.enabled)
            .putInt("daily", settings.dailyMinutes)
            .putBoolean("evening", settings.eveningEnabled)
            .putInt("eveningTime", settings.eveningMinutes)
            .putStringSet("muted", settings.mutedPrograms)
            .apply()
        _settings.value = settings
        reschedule()
    }

    fun canNotify(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun reschedule() {
        val s = _settings.value
        schedule(ACTION_DAILY, if (s.enabled) s.dailyMinutes else null)
        schedule(ACTION_EVENING, if (s.enabled && s.eveningEnabled) s.eveningMinutes else null)
        scheduleFriendsCheck()
    }

    /** Coup d'œil au canal des potes toutes les 3 h en journée, pour annoncer les Boss battus (à peu près à l'heure suffit). */
    private fun scheduleFriendsCheck() {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        val next = (0..1).flatMap { d -> FRIENDS_HOURS.map { LocalDateTime.of(now.toLocalDate().plusDays(d.toLong()), LocalTime.of(it, 0)) } }
            .first { it.isAfter(now.plusMinutes(1)) }
        val at = next.atZone(zone).toInstant().toEpochMilli()
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent(ACTION_FRIENDS, at))
    }

    /** Potes qui viennent de battre un Boss : une notification à part. */
    fun announceBosses(names: List<String>) {
        Reminders.bossMessage(names)?.let { notify(it, withSnooze = false) }
    }

    /** Rappel du jour décalé d'une heure (« Plus tard »). */
    fun snooze() {
        val at = System.currentTimeMillis() + SNOOZE_MS
        setExact(at, alarmIntent(ACTION_SNOOZE, at))
    }

    private fun schedule(action: String, minutes: Int?) {
        alarms.cancel(alarmIntent(action, 0L))
        if (minutes == null) return
        val at = nextOccurrence(minutes)
        setExact(at, alarmIntent(action, at))
    }

    /** À l'heure pile : une alarme approximative peut être repoussée de plusieurs heures quand le téléphone dort. */
    private fun setExact(at: Long, pending: PendingIntent) {
        try {
            if (alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                return
            }
        } catch (_: SecurityException) {
            // Alarmes exactes refusées : l'approximative fait de son mieux.
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    private fun nextOccurrence(minutes: Int): Long {
        val zone = ZoneId.systemDefault()
        val time = LocalTime.of(minutes / 60, minutes % 60)
        var at = LocalDateTime.of(LocalDate.now(zone), time)
        if (!at.isAfter(LocalDateTime.now(zone).plusSeconds(30))) at = at.plusDays(1)
        return at.atZone(zone).toInstant().toEpochMilli()
    }

    private fun alarmIntent(action: String, at: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        Intent(context, ReminderReceiver::class.java).setAction(action).putExtra(EXTRA_AT, at),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Appelé par [ReminderReceiver]. */
    internal fun fire(action: String, scheduledAt: Long) {
        val s = _settings.value
        if (action == ACTION_FRIENDS) {
            try {
                (context.applicationContext as FlemmeFitApp).social.checkInBackground()
            } finally {
                scheduleFriendsCheck()
            }
            return
        }
        // Rappel arrivé bien trop tard (téléphone endormi) : il parlerait de la mauvaise séance, on le saute.
        val late = scheduledAt > 0 && System.currentTimeMillis() - scheduledAt > MAX_LATE_MS
        try {
            if (s.enabled && !late) {
                val programs = (context.applicationContext as FlemmeFitApp).repository.state.value.programs
                    .filterNot { it.id in s.mutedPrograms }
                val today = LocalDate.now()
                val messages = if (action == ACTION_EVENING) Reminders.evening(programs, today) else Reminders.daily(programs, today)
                messages.forEach { notify(it, withSnooze = action != ACTION_EVENING) }
                // Les potes qui ont déjà fini, en notification à part : seulement à côté d'une séance pas finie.
                if (Reminders.evening(programs, today).isNotEmpty()) {
                    val friends = (context.applicationContext as FlemmeFitApp).social.friendsForReminder()
                    Reminders.friendsDone(friends, today)?.let { notify(Reminders.friendsMessage(it), withSnooze = false) }
                }
            }
        } catch (e: Exception) {
            // Un rappel raté ne doit pas couper les suivants (notifications refusées, programme illisible…).
            Log.e("ReminderScheduler", "Rappel impossible", e)
        } finally {
            if (action != ACTION_SNOOZE) reschedule()
        }
    }

    /** Notification d'essai depuis les réglages. */
    fun test() {
        notify(
            ReminderMessage("test", "💪 Test FlemmeFit", "Si tu lis ça, les rappels marchent. Maintenant, au boulot."),
            withSnooze = false,
        )
    }

    private fun notify(message: ReminderMessage, withSnooze: Boolean) {
        if (!canNotify()) return
        ensureChannel()
        val open = PendingIntent.getActivity(
            context,
            message.programId.hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_PROGRAM_ID, message.programId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(message.title)
            .setContentText(message.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(0, "Ouvrir", open)
            .apply { if (withSnooze) addAction(0, "Plus tard", snoozeIntent(message.programId)) }
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId(message.programId), notification)
        } catch (e: SecurityException) {
            // Autorisation retirée entre-temps : rien à faire.
        }
    }

    private fun snoozeIntent(programId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        ("snooze$programId").hashCode(),
        Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_SNOOZE_TAP)
            .putExtra(MainActivity.EXTRA_PROGRAM_ID, programId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun dismiss(programId: String) = NotificationManagerCompat.from(context).cancel(notificationId(programId))

    private fun notificationId(programId: String) = programId.hashCode()

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Rappels d'entraînement", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Le rappel du jour et la relance du soir"
                },
            )
        }
    }

    companion object {
        const val ACTION_DAILY = "com.spacelenin.flemmefit.REMINDER_DAILY"
        const val ACTION_EVENING = "com.spacelenin.flemmefit.REMINDER_EVENING"
        const val ACTION_SNOOZE = "com.spacelenin.flemmefit.REMINDER_SNOOZE"
        const val ACTION_SNOOZE_TAP = "com.spacelenin.flemmefit.REMINDER_SNOOZE_TAP"
        const val ACTION_FRIENDS = "com.spacelenin.flemmefit.REMINDER_FRIENDS"
        const val EXTRA_AT = "at"
        /** Au-delà de ce retard, un rappel n'est plus envoyé. */
        private const val MAX_LATE_MS = 2 * 60 * 60 * 1000L
        /** Heures du coup d'œil aux potes. */
        private val FRIENDS_HOURS = listOf(9, 12, 15, 18, 21)
        private const val CHANNEL_ID = "reminders"
        private const val SNOOZE_MS = 60 * 60 * 1000L
    }
}
