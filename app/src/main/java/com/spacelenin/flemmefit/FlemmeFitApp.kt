package com.spacelenin.flemmefit

import android.app.Application
import com.spacelenin.flemmefit.data.ProgramRepository
import com.spacelenin.flemmefit.update.AppUpdater

class FlemmeFitApp : Application() {
    lateinit var repository: ProgramRepository
        private set
    lateinit var updater: AppUpdater
        private set

    override fun onCreate() {
        super.onCreate()
        repository = ProgramRepository(filesDir)
        updater = AppUpdater(this)
    }
}
