package com.example.carvideo.carapp

import android.util.Log
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.example.carvideo.logging.CrashLogger

class VideoCarAppService : CarAppService() {

    override fun onCreate() {
        super.onCreate()
        Log.d("CarVideoApp", "VideoCarAppService: onCreate")
        CrashLogger.logEvent(this, "Android Auto service onCreate")
    }

    override fun createHostValidator(): HostValidator {
        Log.d("CarVideoApp", "VideoCarAppService: createHostValidator")
        CrashLogger.logEvent(this, "Android Auto host validator: allow all hosts for private build")

        // Private/sideload build:
        // Release APKs are not debuggable, so the previous sample allowlist could reject
        // real headunits/Android Auto hosts and show only an unknown error on the car screen.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        Log.d("CarVideoApp", "VideoCarAppService: onCreateSession")
        CrashLogger.logEvent(this, "Android Auto create session")
        return VideoSession()
    }

    override fun onDestroy() {
        Log.d("CarVideoApp", "VideoCarAppService: onDestroy")
        CrashLogger.logEvent(this, "Android Auto service destroyed")
        super.onDestroy()
    }
}
