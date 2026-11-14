package com.spacelenin.flemmefit.montre

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Tourne pendant la séance : « activité en cours » (relever le poignet ramène à la séance, icône sur le cadran)
 * et vibrations de fin de repos ou d'objectif, même app en arrière-plan.
 */
class SessionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    /** Dernière vibration de fin de repos : elle se répète tant qu'on n'a pas repris. */
    private var lastBuzz = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Store.init(this)
        if (Store.run.value == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        goForeground()
        if (loop?.isActive != true) loop = scope.launch { tick() }
        return START_STICKY
    }

    private fun goForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Séance en cours", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("FlemmeFit")
            .setContentText("Séance en cours")
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setOngoing(true)
            .setContentIntent(open)
        OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_stat)
            .setTouchIntent(open)
            .setStatus(Status.Builder().addTemplate("Séance en cours").build())
            .build()
            .apply(applicationContext)
        startForeground(NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    /** Une fois par seconde : vibrations dues (repos à 10 s et à 0, objectif d'un exo en durée atteint). */
    private suspend fun tick() {
        while (scope.isActive) {
            val run = Store.run.value
            if (run == null || run.phase == Phase.RECAP) {
                stopSelf()
                return
            }
            val now = System.currentTimeMillis()
            if (run.phase == Phase.REST) {
                val left = run.restEndsAt - now
                if (left <= 0 && !run.rang) {
                    vibrate(long = true)
                    lastBuzz = now
                    Store.updateRun { it.copy(rang = true, warned = true) }
                } else if (left <= 0 && -left < REPEAT_FOR_MS && now - lastBuzz >= REPEAT_EVERY_MS) {
                    // Repos fini mais pas encore repris : on insiste (2 minutes au plus, pour la batterie).
                    vibrate(long = true)
                    lastBuzz = now
                } else if (left in 1..10_000 && !run.warned) {
                    vibrate(long = false)
                    Store.updateRun { it.copy(warned = true) }
                }
            } else if (run.phase == Phase.EXERCISE && !run.rang) {
                val today = Store.shown(Store.today.value, run.demo)
                val ex = today?.let { SessionFlow.current(run, it) }
                if (ex != null && ex.timed && ex.seconds != null && now - run.phaseStartedAt >= ex.seconds * 1000L) {
                    vibrate(long = true)
                    Store.updateRun { it.copy(rang = true) }
                }
            }
            delay(1000)
        }
    }

    private fun vibrate(long: Boolean) {
        val vibrator = getSystemService(VibratorManager::class.java).defaultVibrator
        val effect = if (long) VibrationEffect.createWaveform(longArrayOf(0, 400, 150, 400, 150, 600), -1)
        else VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE)
        vibrator.vibrate(effect)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "seance"
        private const val NOTIFICATION_ID = 7
        private const val REPEAT_EVERY_MS = 3_000L
        private const val REPEAT_FOR_MS = 120_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, SessionService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SessionService::class.java))
        }
    }
}
