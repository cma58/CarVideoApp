package com.example.carvideo.player

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Background playback service (item 4).
 *
 * Hosts the shared ExoPlayer via a MediaSession so playback survives in the
 * background and is controllable from the system / Android Auto. Video output
 * is attached/detached by PlayerHolder depending on Surface availability;
 * audio is unaffected by that, so it "naadloos doorspeelt" when the car screen's
 * Surface is taken away.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val player = PlayerHolder.getOrCreate {
            ExoPlayer.Builder(this)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    /* handleAudioFocus = */ true
                )
                // Keep playing audio even when video output is detached.
                .setHandleAudioBecomingNoisy(true)
                .build()
        }

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        // If nothing is playing, allow the service to stop.
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            release()
        }
        mediaSession = null
        // Do not release the shared player here if the Car App may still use it;
        // release explicitly when the whole app shuts down.
        super.onDestroy()
    }
}
