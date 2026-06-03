package com.example.carvideo.carapp

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.media3.common.util.UnstableApi

@UnstableApi
class VideoSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = MainCarScreen(carContext)
}
