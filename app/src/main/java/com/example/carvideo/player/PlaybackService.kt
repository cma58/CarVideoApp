package com.example.carvideo.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.carvideo.extractor.YouTubeExtractorService
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Media playback service for phone, notification and Android Auto media mode.
 *
 * This is now a MediaLibraryService instead of a plain MediaSessionService. That
 * gives Android Auto a real media-library entry point, which is the direction
 * needed for the native Spotify-like player surface.
 */
@UnstableApi
class PlaybackService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val NOTIFICATION_ID = 101
    private val CHANNEL_ID = "car_video_playback_channel"
    private val COMMAND_LIKE = "com.example.carvideo.LIKE"

    private val ROOT_ID = "root"
    private val QUEUE_ID = "queue"
    private val NOW_PLAYING_ID = "now_playing"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val player = PlayerHolder.getOrCreate {
            ExoPlayer.Builder(this)
                .setAudioAttributes(
                    androidx.media3.common.AudioAttributes.Builder()
                        .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                        .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
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

        @Suppress("DEPRECATION")
        val likeButton = CommandButton.Builder()
            .setDisplayName("Like")
            .setSessionCommand(SessionCommand(COMMAND_LIKE, Bundle.EMPTY))
            .setIconResId(android.R.drawable.btn_star)
            .build()

        mediaSession = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(intent!!)
            .setCustomLayout(listOf(likeButton))
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateForegroundNotification()
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                updateForegroundNotification()
            }
        })

        updateForegroundNotification()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            return if (customCommand.customAction == COMMAND_LIKE) {
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            } else {
                Futures.immediateFuture(SessionResult(ERROR_NOT_SUPPORTED))
            }
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem(), params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val items = when (parentId) {
                ROOT_ID -> listOf(nowPlayingFolder(), queueFolder())
                QUEUE_ID -> PlaybackState.playlist.value.map { it.toLibraryMediaItem() }
                NOW_PLAYING_ID -> PlaybackState.current.value?.let { listOf(it.toNowPlayingMediaItem()) } ?: emptyList()
                else -> emptyList()
            }
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = when (mediaId) {
                ROOT_ID -> rootItem()
                QUEUE_ID -> queueFolder()
                NOW_PLAYING_ID -> nowPlayingFolder()
                else -> PlaybackState.playlist.value.firstOrNull { it.url == mediaId }?.toLibraryMediaItem()
            }
            return if (item != null) {
                Futures.immediateFuture(LibraryResult.ofItem(item, null))
            } else {
                Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            val future = SettableFuture.create<List<MediaItem>>()
            serviceScope.launch {
                val resolved = mediaItems.mapNotNull { item ->
                    val originalUrl = item.mediaId.ifBlank { item.localConfiguration?.uri?.toString().orEmpty() }
                    if (originalUrl.startsWith("http", ignoreCase = true)) {
                        try {
                            val audioUrl = YouTubeExtractorService.getAudioStreamUrl(originalUrl)
                            if (audioUrl != null) {
                                item.buildUpon()
                                    .setUri(audioUrl)
                                    .setMediaId(originalUrl)
                                    .build()
                            } else null
                        } catch (_: Exception) {
                            null
                        }
                    } else {
                        item
                    }
                }
                future.set(resolved)
            }
            return future
        }
    }

    private fun rootItem(): MediaItem = folder(ROOT_ID, "Car Video")
    private fun queueFolder(): MediaItem = folder(QUEUE_ID, "Wachtrij")
    private fun nowPlayingFolder(): MediaItem = folder(NOW_PLAYING_ID, "Wordt nu afgespeeld")

    private fun folder(id: String, title: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()

    private fun com.example.carvideo.extractor.SearchResultItem.toLibraryMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(uploader)
                    .setArtworkUri(thumbnailUrl?.let { Uri.parse(it) })
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()

    private fun com.example.carvideo.extractor.StreamResult.toNowPlayingMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(originalUrl ?: title)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(uploader)
                    .setArtworkUri(thumbnailUrl?.let { Uri.parse(it) })
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()

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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        private const val ERROR_NOT_SUPPORTED = -1003
    }
}
