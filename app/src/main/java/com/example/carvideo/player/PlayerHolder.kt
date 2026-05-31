package com.example.carvideo.player

import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.datasource.DefaultHttpDataSource
import com.example.carvideo.extractor.StreamResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single shared ExoPlayer instance. Both the MediaSession (PlaybackService) and
 * the Android Auto Screen (which owns the car's Surface) reference the same
 * player so video output can be attached/detached without interrupting audio.
 */
object PlayerHolder {

    @Volatile
    private var player: ExoPlayer? = null

    private var currentSurface: Surface? = null
    private var crossfadeJob: kotlinx.coroutines.Job? = null

    private val _currentPosition = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    private val _duration = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    fun getOrCreate(create: () -> ExoPlayer): ExoPlayer {
        return player ?: synchronized(this) {
            player ?: create().also { 
                player = it
                it.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        _duration.value = player.duration.coerceAtLeast(0)
                    }
                })
                startProgressMonitor()
                startCrossfadeMonitor()
            }
        }
    }

    private fun startProgressMonitor() {
        val p = player ?: return
        GlobalScope.launch(Dispatchers.Main) {
            while (isActive) {
                if (p.isPlaying) {
                    _currentPosition.value = p.currentPosition
                }
                delay(1000) // Increase delay to reduce CPU usage
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun startCrossfadeMonitor() {
        val p = player ?: return
        crossfadeJob?.cancel()
        crossfadeJob = GlobalScope.launch(Dispatchers.Main) {
            while (isActive) {
                if (p.isPlaying && p.duration > 0) {
                    val remaining = p.duration - p.currentPosition
                    if (remaining in 1..5000) {
                        // Start fading out
                        val volume = (remaining / 5000f).coerceIn(0f, 1f)
                        p.volume = volume
                    } else if (p.volume < 1f) {
                        p.volume = 1f
                    }
                }
                delay(200)
            }
        }
    }

    fun get(): ExoPlayer? = player

    /**
     * Attach the car screen's Surface. Video renders here.
     */
    fun attachSurface(surface: Surface) {
        currentSurface = surface
        player?.setVideoSurface(surface)
    }

    /**
     * Surface gone (e.g. car started moving / template hidden). Stop video
     * rendering but DO NOT pause — audio keeps playing in the background.
     */
    fun detachSurface() {
        currentSurface = null
        player?.clearVideoSurface()
    }

    fun hasSurface(): Boolean = currentSurface != null

    @androidx.media3.common.util.UnstableApi
    fun play(stream: StreamResult) {
        val p = player ?: return
        val httpFactory = DefaultHttpDataSource.Factory()

        val metadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(stream.title)
            .setArtist(stream.uploader)
            .setArtworkUri(android.net.Uri.parse(stream.thumbnailUrl))
            .build()

        if (stream.isMuxed && stream.videoStreamUrl != null) {
            p.setMediaItem(
                MediaItem.Builder()
                    .setUri(stream.videoStreamUrl)
                    .setMediaMetadata(metadata)
                    .build()
            )
        } else if (stream.videoStreamUrl != null && stream.audioStreamUrl != null) {
            // Merge separate video-only + audio-only streams.
            val videoSource = ProgressiveMediaSource.Factory(httpFactory)
                .createMediaSource(
                    MediaItem.Builder()
                        .setUri(stream.videoStreamUrl)
                        .setMimeType(MimeTypes.VIDEO_MP4)
                        .setMediaMetadata(metadata)
                        .build()
                )
            val audioSource = ProgressiveMediaSource.Factory(httpFactory)
                .createMediaSource(
                    MediaItem.Builder()
                        .setUri(stream.audioStreamUrl)
                        .setMimeType(MimeTypes.AUDIO_MP4)
                        .setMediaMetadata(metadata)
                        .build()
                )
            p.setMediaSource(MergingMediaSource(videoSource, audioSource))
        } else if (stream.audioStreamUrl != null) {
            p.setMediaItem(
                MediaItem.Builder()
                    .setUri(stream.audioStreamUrl)
                    .setMediaMetadata(metadata)
                    .build()
            )
        } else {
            return
        }

        p.playWhenReady = true
        p.prepare()
        p.play()
        PlaybackState.setCurrent(stream)
    }

    fun skipNext(onResult: (com.example.carvideo.extractor.SearchResultItem) -> Unit) {
        val current = PlaybackState.current.value ?: return
        val list = PlaybackState.playlist.value
        val currentIndex = list.indexOfFirst { it.url == current.originalUrl }
        if (currentIndex != -1 && currentIndex < list.size - 1) {
            onResult(list[currentIndex + 1])
        }
    }

    fun skipPrevious(onResult: (com.example.carvideo.extractor.SearchResultItem) -> Unit) {
        val current = PlaybackState.current.value ?: return
        val list = PlaybackState.playlist.value
        val currentIndex = list.indexOfFirst { it.url == current.originalUrl }
        if (currentIndex > 0) {
            onResult(list[currentIndex - 1])
        }
    }

    /** Toggle play/pause. Returns the new playWhenReady state. */
    fun togglePlayPause(): Boolean {
        val p = player ?: return false
        p.playWhenReady = !p.playWhenReady
        return p.playWhenReady
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    fun isPlaying(): Boolean = player?.playWhenReady == true

    fun release() {
        synchronized(this) {
            currentSurface = null
            player?.release()
            player = null
        }
    }
}
