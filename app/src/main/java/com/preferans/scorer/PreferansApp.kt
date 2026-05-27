package com.preferans.scorer

import android.app.Application
import com.preferans.scorer.data.GameRepository

class PreferansApp : Application() {

    lateinit var gameRepository: GameRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        gameRepository = GameRepository(applicationContext)
    }

    companion object {
        lateinit var instance: PreferansApp
            private set
    }
}
