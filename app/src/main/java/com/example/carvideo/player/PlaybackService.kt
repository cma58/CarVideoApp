package com.example.carvideo.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Robust PlaybackService for Phone and Car.
 * Includes Like button and Next/Previous controls in the notification.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val NOTIFICATION_ID = 101
    private val CHANNEL_ID = "car_video_playback_channel"

    private val COMMAND_LIKE = "com.example.carvideo.LIKE"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val player = PlayerHolder.getOrCreate {
            ExoPlayer.Builder(this)
                .setAudioAttributes(
                    androidx.media3.common.AudioAttributes.Builder()
                        .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                        .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true
                )
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
                .build()
        }

        val intent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        // Custom command for Like button
        @Suppress("DEPRECATION")
        val likeButton = CommandButton.Builder()
            .setDisplayName("Like")
            .setSessionCommand(SessionCommand(COMMAND_LIKE, Bundle.EMPTY))
            .setIconResId(android.R.drawable.btn_star) // Default star icon
            .build()

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(intent!!)
            .setCustomLayout(listOf(likeButton))
            .setCallback(object : MediaSession.Callback {
                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == COMMAND_LIKE) {
                        // For a real app, you would pass this to your ViewModel/Repository.
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                }
            })
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateForegroundNotification()
            }
            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                updateForegroundNotification()
            }
        })
        
        updateForegroundNotification()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Media Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Controls for car video playback"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun updateForegroundNotification() {
        val session = mediaSession ?: return
        val player = session.player
        val metadata = player.mediaMetadata
        
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(metadata.title ?: "Car Video")
            .setContentText(metadata.artist ?: "Aan het afspelen...")
            .setSubText("CarVideoApp")
            .setOngoing(player.isPlaying)
            .setContentIntent(intent)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(session))
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
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
        super.onDestroy()
    }
}

// Add a helper for Media3 errors if not available
private object SessionError {
    const val ERROR_NOT_SUPPORTED = -1003
}
