package com.example.carvideo.carapp

import android.util.Log
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class VideoCarAppService : CarAppService() {

    override fun onCreate() {
        super.onCreate()
        Log.d("CarVideoApp", "VideoCarAppService: onCreate")
    }

    override fun createHostValidator(): HostValidator {
        Log.d("CarVideoApp", "VideoCarAppService: createHostValidator")
        return if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }
    }

    override fun onCreateSession(): Session {
        Log.d("CarVideoApp", "VideoCarAppService: onCreateSession")
        return VideoSession()
    }

    override fun onDestroy() {
        Log.d("CarVideoApp", "VideoCarAppService: onDestroy")
        super.onDestroy()
    }
}
