package com.spacelenin.flemmefit.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.spacelenin.flemmefit.FlemmeFitApp
import com.spacelenin.flemmefit.MainActivity

/** Alarmes des rappels et bouton « Plus tard » (non exporté). */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminders = (context.applicationContext as FlemmeFitApp).reminders
        when (intent.action) {
            ReminderScheduler.ACTION_SNOOZE_TAP -> {
                intent.getStringExtra(MainActivity.EXTRA_PROGRAM_ID)?.let(reminders::dismiss)
                reminders.snooze()
            }
            ReminderScheduler.ACTION_DAILY, ReminderScheduler.ACTION_EVENING, ReminderScheduler.ACTION_SNOOZE,
            ReminderScheduler.ACTION_FRIENDS -> {
                // Le rappel lit le canal des potes : hors du fil principal, l'alarme reste active le temps qu'il faut.
                val pending = goAsync()
                val action = intent.action!!
                val scheduledAt = intent.getLongExtra(ReminderScheduler.EXTRA_AT, 0L)
                Thread {
                    try {
                        reminders.fire(action, scheduledAt)
                    } finally {
                        pending.finish()
                    }
                }.start()
            }
        }
    }
}

/** Les alarmes sont effacées au redémarrage du téléphone et à la mise à jour de l'app : on les reprogramme. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val app = context.applicationContext as FlemmeFitApp
                app.reminders.reschedule()
                app.challenges.reschedule()
            }
        }
    }
}
