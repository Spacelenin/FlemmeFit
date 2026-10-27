package com.spacelenin.flemmefit

import android.app.Application
import com.spacelenin.flemmefit.challenge.ChallengeManager
import com.spacelenin.flemmefit.data.AppSettings
import com.spacelenin.flemmefit.data.BackupManager
import com.spacelenin.flemmefit.data.ProgramRepository
import com.spacelenin.flemmefit.reminders.ReminderScheduler
import com.spacelenin.flemmefit.social.SocialSync
import com.spacelenin.flemmefit.update.AppUpdater
import com.spacelenin.flemmefit.widget.TodayWidget
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FlemmeFitApp : Application() {
    lateinit var repository: ProgramRepository
        private set
    lateinit var updater: AppUpdater
        private set
    lateinit var reminders: ReminderScheduler
        private set
    lateinit var social: SocialSync
        private set
    lateinit var settings: AppSettings
        private set
    lateinit var backup: BackupManager
        private set
    lateinit var challenges: ChallengeManager
        private set

    override fun onCreate() {
        super.onCreate()
        repository = ProgramRepository(filesDir)
        settings = AppSettings(this)
        backup = BackupManager(this, repository, settings)
        backup.start()
        updater = AppUpdater(this)
        reminders = ReminderScheduler(this)
        reminders.reschedule()
        social = SocialSync(this, repository)
        social.onBossNews = { names -> reminders.announceBosses(names) }
        // Défis surprise : le jour et l'heure dépendent de cette installation.
        challenges = ChallengeManager(this, repository, social.myId.hashCode().toLong())
        social.challengesWon = challenges::wonCount
        challenges.onWon = social::publishIfNeeded
        challenges.reschedule()
        social.publishIfNeeded()
        // Le widget suit le suivi : chaque exercice coché le met à jour.
        widgetScope.launch {
            repository.state.collect { TodayWidget.refresh(this@FlemmeFitApp) }
        }
    }

    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.e("FlemmeFit", "Widget", e) })
}
