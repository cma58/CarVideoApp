package com.example.carvideo

import android.app.Application
import com.example.carvideo.extractor.YouTubeExtractorService
import com.example.carvideo.logging.CrashLogger

class CarVideoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.init(this)
        try {
            YouTubeExtractorService.init()
            CrashLogger.logEvent(this, "Extractor init OK")
        } catch (e: Exception) {
            CrashLogger.logEvent(this, "Extractor init fout: ${e.message}")
        }
    }
}
