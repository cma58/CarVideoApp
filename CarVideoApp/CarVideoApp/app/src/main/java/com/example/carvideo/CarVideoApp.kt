package com.example.carvideo

import android.app.Application
import com.example.carvideo.extractor.YouTubeExtractorService

class CarVideoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        YouTubeExtractorService.init()
    }
}
