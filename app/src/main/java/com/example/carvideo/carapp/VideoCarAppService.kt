package com.example.carvideo.carapp

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * CarAppService entry point for Android Auto (item 3).
 *
 * Registered in the manifest with category androidx.car.app.category.VIDEO.
 * Note: the VIDEO category is restricted — a published app must be approved by
 * Google to run on production head units. For personal/dev use it works under
 * Android Auto's developer mode (Desktop Head Unit / DHU).
 */
class VideoCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        // For development, allow all hosts. For production, use the
        // ALLOW_ALL_HOSTS_VALIDATOR replacement with a proper allowlist.
        return if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }
    }

    override fun onCreateSession(): Session = VideoSession()
}
