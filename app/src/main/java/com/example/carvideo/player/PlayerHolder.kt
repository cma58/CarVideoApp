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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single shared ExoPlayer instance. Both the MediaSession (PlaybackService) and
 * the Android Auto Screen (which owns the car's Surface) reference the same
 * player so video output can be attached/detached without interrupting audio.
 *
 * BELANGRIJK: de speler bezit nu zelf de wachtrij (setQueue). Daardoor heeft de
 * MediaSession een echte timeline met meerdere items en tonen Android Auto /
 * Automotive automatisch de Volgende/Vorige-knoppen.
 */
object PlayerHolder {

    @Volatile
    private var player: ExoPlayer? = null

    private var currentSurface: Surface? = null

    // Eén nette scope die we netjes kunnen cancellen (geen GlobalScope-lek meer).
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    fun getOrCreate(create: () -> ExoPlayer): ExoPlayer {
        return player ?: synchronized(this) {
            player ?: create().also {
                player = it
                it.addListener(object : Player.Listener {
                    override fun onEvents(p: Player, events: Player.Events) {
                        _duration.value = p.duration.coerceAtLeast(0)
                    }
                })
                startProgressMonitor()
            }
        }
    }

    private fun startProgressMonitor() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val p = player
                if (p != null && p.isPlaying) {
                    _currentPosition.value = p.currentPosition
                }
                delay(1000)
            }
        }
    }

    fun get(): ExoPlayer? = player

    /** Attach the car screen's Surface. Video renders here. */
    fun attachSurface(surface: Surface) {
        currentSurface = surface
        player?.setVideoSurface(surface)
    }

    /** Surface gone: stop video maar laat audio doorlopen. */
    fun detachSurface() {
        currentSurface = null
        player?.clearVideoSurface()
    }

    fun hasSurface(): Boolean = currentSurface != null

    /**
     * Speel een hele lijst af als één ExoPlayer-timeline. Dit is wat de
     * Volgende/Vorige-knoppen laat verschijnen op het autoscherm en in de
     * notificatie. De items moeten al een afspeelbare URI hebben.
     */
    fun setQueue(items: List<MediaItem>, startIndex: Int) {
        val p = player ?: return
        if (items.isEmpty()) return
        val safe = startIndex.coerceIn(0, items.size - 1)
        p.setMediaItems(items, safe, 0L)
        p.playWhenReady = true
        p.prepare()
        p.play()
    }

    /**
     * Speel één los item af (gebruikt voor de Video-modus, waar we een muxed of
     * gemergde video+audio stream nodig hebben). Vervangt tijdelijk de wachtrij.
     */
    @androidx.media3.common.util.UnstableApi
    fun play(stream: StreamResult) {
        val p = player ?: return
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )

        val metadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(stream.title)
            .setArtist(stream.uploader)
            .setArtworkUri(stream.thumbnailUrl?.let { android.net.Uri.parse(it) })
            .build()

        if (stream.isMuxed && stream.videoStreamUrl != null) {
            p.setMediaItem(
                MediaItem.Builder()
                    .setUri(stream.videoStreamUrl)
                    .setMediaId(stream.originalUrl ?: stream.videoStreamUrl)
                    .setMediaMetadata(metadata)
                    .build()
            )
        } else if (stream.videoStreamUrl != null && stream.audioStreamUrl != null) {
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
                    .setMediaId(stream.originalUrl ?: stream.audioStreamUrl)
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

    fun seekToNext() {
        player?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    }

    fun seekToPrevious() {
        player?.let { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() }
    }

    // Behouden voor de car-schermen (oude API) zodat die blijven compileren.
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
            progressJob?.cancel()
            currentSurface = null
            player?.release()
            player = null
        }
    }
}
