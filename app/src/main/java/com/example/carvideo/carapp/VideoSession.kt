package com.example.carvideo.carapp

import android.content.Intent
import android.util.Log
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.media3.common.util.UnstableApi
import com.example.carvideo.player.PlaybackState

@UnstableApi
class VideoSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        Log.d("CarVideoApp", "VideoSession: onCreateScreen with intent: $intent")

        return if (PlaybackState.current.value != null) {
            Log.d("CarVideoApp", "VideoSession: Already playing, launching NowPlayingCarScreen")
            NowPlayingCarScreen(carContext)
        } else {
            Log.d("CarVideoApp", "VideoSession: Not playing, launching MainCarScreen")
            MainCarScreen(carContext)
        }
    }

    override fun onNewIntent(intent: Intent) {
        Log.d("CarVideoApp", "VideoSession: onNewIntent: $intent")
        super.onNewIntent(intent)
    }
}
