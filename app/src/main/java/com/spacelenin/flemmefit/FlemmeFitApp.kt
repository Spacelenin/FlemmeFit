package com.spacelenin.flemmefit

import android.app.Application
import com.spacelenin.flemmefit.data.ProgramRepository
import com.spacelenin.flemmefit.reminders.ReminderScheduler
import com.spacelenin.flemmefit.social.SocialSync
import com.spacelenin.flemmefit.update.AppUpdater

class FlemmeFitApp : Application() {
    lateinit var repository: ProgramRepository
        private set
    lateinit var updater: AppUpdater
        private set
    lateinit var reminders: ReminderScheduler
        private set
    lateinit var social: SocialSync
        private set

    override fun onCreate() {
        super.onCreate()
        repository = ProgramRepository(filesDir)
        updater = AppUpdater(this)
        reminders = ReminderScheduler(this)
        reminders.reschedule()
        social = SocialSync(this, repository)
        social.publishIfNeeded()
    }
}
