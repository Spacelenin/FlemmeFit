package com.spacelenin.flemmefit

import android.app.Application
import com.spacelenin.flemmefit.data.ProgramRepository

class FlemmeFitApp : Application() {
    lateinit var repository: ProgramRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = ProgramRepository(filesDir)
    }
}
