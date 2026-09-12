package com.cadence.player

import android.app.Application
import com.cadence.player.playback.PlayerConnection

class MusicApplication : Application() {
    lateinit var playerConnection: PlayerConnection
        private set

    override fun onCreate() {
        super.onCreate()
        playerConnection = PlayerConnection(applicationContext)
    }
}
