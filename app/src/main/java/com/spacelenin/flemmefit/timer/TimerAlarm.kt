package com.spacelenin.flemmefit.timer

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.spacelenin.flemmefit.MainActivity
import com.spacelenin.flemmefit.R
import com.spacelenin.flemmefit.model.TimerRun

/** L'appli est-elle à l'écran ? Sinon c'est aux notifications de prévenir à la fin d'une étape. */
object AppVisibility {
    @Volatile
    var foreground = false

    /** Mode séance ouvert ou minuteur qui tourne. */
    @Volatile
    var sessionRunning = false
}

/**
 * Le minuteur hors de l'appli : une notification qui décompte, et une alarme exacte à chaque fin d'étape
 * qui sonne et vibre si l'écran est verrouillé ou l'appli en arrière-plan.
 */
object TimerAlarm {
    private const val CHANNEL_RUNNING = "timer_running"
    private const val CHANNEL_END = "timer_end"
    internal const val ID_RUNNING = 4100
    private const val ID_END = 4101
    private const val REQUEST_BASE = 4200
    /** Étapes prévenues d'avance au plus (au-delà, l'appli reprend la main à son retour). */
    private const val MAX_ALARMS = 40

    internal const val EXTRA_TITLE = "title"
    internal const val EXTRA_ENDED = "ended"
    internal const val EXTRA_ENDED_REST = "endedRest"
    internal const val EXTRA_NEXT = "next"
    internal const val EXTRA_NEXT_END = "nextEnd"

    /** Met à jour la notification et les alarmes pour ce minuteur (null ou fini : tout s'efface). */
    fun update(context: Context, run: TimerRun?) {
        cancelAlarms(context)
        if (run == null || run.finished) {
            NotificationManagerCompat.from(context).cancel(ID_RUNNING)
            return
        }
        val now = SystemClock.elapsedRealtime()
        val wall = System.currentTimeMillis()
        val ends = run.upcomingEnds(now)
        showRunning(
            context,
            title = run.title,
            label = run.phase.label + if (run.paused) " · en pause" else "",
            endsAt = ends.firstOrNull()?.let { wall + it.inMs },
            startedAt = wall - run.elapsedMs(now),
        )
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        ends.take(MAX_ALARMS).forEachIndexed { i, end ->
            val nextEnd = ends.getOrNull(i + 1)?.let { wall + it.inMs }
            val intent = Intent(context, TimerAlarmReceiver::class.java)
                .putExtra(EXTRA_TITLE, run.title)
                .putExtra(EXTRA_ENDED, end.ended.label)
                .putExtra(EXTRA_ENDED_REST, end.ended.rest)
                .putExtra(EXTRA_NEXT, end.next?.label)
                .putExtra(EXTRA_NEXT_END, nextEnd ?: 0L)
            val pending = PendingIntent.getBroadcast(context, REQUEST_BASE + i, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val at = wall + end.inMs
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                } else {
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                }
            } catch (_: SecurityException) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        }
    }

    fun clear(context: Context) = update(context, null)

    private fun cancelAlarms(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        repeat(MAX_ALARMS) { i ->
            PendingIntent.getBroadcast(
                context, REQUEST_BASE + i, Intent(context, TimerAlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.let {
                alarms.cancel(it)
                it.cancel()
            }
        }
    }

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, ID_RUNNING,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Notification discrète qui décompte (ou compte, pour un chrono) jusqu'à la fin de l'étape. */
    internal fun showRunning(context: Context, title: String, label: String, endsAt: Long?, startedAt: Long) {
        ensureChannels(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⏱ $label")
            .setContentText(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp(context))
            .setUsesChronometer(true)
            .setWhen(endsAt ?: startedAt)
            .setChronometerCountDown(endsAt != null)
        notify(context, ID_RUNNING, builder)
    }

    /** Fin d'étape alors que l'appli n'est pas à l'écran : ça sonne et ça vibre. */
    internal fun showEnded(context: Context, title: String, ended: String, endedRest: Boolean, next: String?) {
        ensureChannels(context)
        val text = when {
            endedRest -> "C'est reparti" + (next?.let { " : $it" } ?: "")
            next == null -> "C'est fini, bien joué."
            else -> "Ensuite : $next"
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_END)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (endedRest) "Fin du repos" else "$ended : terminé")
            .setContentText("$title · $text")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .setContentIntent(openApp(context))
        notify(context, ID_END, builder)
    }

    private fun notify(context: Context, id: Int, builder: NotificationCompat.Builder) {
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) {
            // Notifications refusées : le minuteur reste à l'écran.
        }
    }

    private fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_RUNNING) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_RUNNING, "Minuteur en cours", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Le décompte du repos ou de la série, quand l'appli n'est pas à l'écran"
                    setShowBadge(false)
                },
            )
        }
        if (manager.getNotificationChannel(CHANNEL_END) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_END, "Fin du minuteur", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Sonne et vibre à la fin d'un repos ou d'une série, même écran verrouillé"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 400, 150, 400, 150, 400)
                },
            )
        }
    }
}

/** Une étape vient de finir : on prévient si l'appli n'est pas à l'écran, et la notification passe à l'étape suivante. */
class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(TimerAlarm.EXTRA_TITLE) ?: return
        val next = intent.getStringExtra(TimerAlarm.EXTRA_NEXT)
        val nextEnd = intent.getLongExtra(TimerAlarm.EXTRA_NEXT_END, 0L)
        if (!AppVisibility.foreground) {
            TimerAlarm.showEnded(
                context, title,
                ended = intent.getStringExtra(TimerAlarm.EXTRA_ENDED).orEmpty(),
                endedRest = intent.getBooleanExtra(TimerAlarm.EXTRA_ENDED_REST, false),
                next = next,
            )
        }
        val now = System.currentTimeMillis()
        when {
            next == null -> NotificationManagerCompat.from(context).cancel(TimerAlarm.ID_RUNNING)
            else -> TimerAlarm.showRunning(context, title, next, endsAt = nextEnd.takeIf { it > 0 }, startedAt = now)
        }
    }
}
