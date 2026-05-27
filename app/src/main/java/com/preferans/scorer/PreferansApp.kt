package com.preferans.scorer

import android.app.Application
import com.preferans.scorer.data.GameRepository
import com.preferans.scorer.data.SettingsRepository

class PreferansApp : Application() {

    lateinit var gameRepository: GameRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        gameRepository = GameRepository(applicationContext)
        settingsRepository = SettingsRepository(applicationContext)
    }

    companion object {
        lateinit var instance: PreferansApp
            private set
    }
}
