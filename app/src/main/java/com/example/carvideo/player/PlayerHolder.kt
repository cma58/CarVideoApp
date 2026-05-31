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

/**
 * Single shared ExoPlayer instance. Both the MediaSession (PlaybackService) and
 * the Android Auto Screen (which owns the car's Surface) reference the same
 * player so video output can be attached/detached without interrupting audio.
 */
object PlayerHolder {

    @Volatile
    private var player: ExoPlayer? = null

    private var currentSurface: Surface? = null

    fun getOrCreate(create: () -> ExoPlayer): ExoPlayer {
        return player ?: synchronized(this) {
            player ?: create().also { player = it }
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

        if (stream.isMuxed && stream.videoStreamUrl != null) {
            p.setMediaItem(
                MediaItem.Builder()
                    .setUri(stream.videoStreamUrl)
                    .setMediaMetadata(
                        androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(stream.title)
                            .build()
                    )
                    .build()
            )
        } else if (stream.videoStreamUrl != null && stream.audioStreamUrl != null) {
            // Merge separate video-only + audio-only streams.
            val videoSource = ProgressiveMediaSource.Factory(httpFactory)
                .createMediaSource(
                    MediaItem.Builder()
                        .setUri(stream.videoStreamUrl)
                        .setMimeType(MimeTypes.VIDEO_MP4)
                        .build()
                )
            val audioSource = ProgressiveMediaSource.Factory(httpFactory)
                .createMediaSource(
                    MediaItem.Builder()
                        .setUri(stream.audioStreamUrl)
                        .setMimeType(MimeTypes.AUDIO_MP4)
                        .build()
                )
            p.setMediaSource(MergingMediaSource(videoSource, audioSource))
        } else if (stream.audioStreamUrl != null) {
            p.setMediaItem(MediaItem.fromUri(stream.audioStreamUrl))
        } else {
            return
        }

        p.playWhenReady = true
        p.prepare()
        PlaybackState.setCurrent(stream)
    }

    /** Toggle play/pause. Returns the new playWhenReady state. */
    fun togglePlayPause(): Boolean {
        val p = player ?: return false
        p.playWhenReady = !p.playWhenReady
        return p.playWhenReady
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
