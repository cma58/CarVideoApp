package com.example.carvideo.player

import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.carvideo.extractor.StreamResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One shared ExoPlayer instance for phone UI, notification and car UI.
 *
 * Private-use changes:
 * - supports lazy queue append, so the app does not resolve 20 streams at once;
 * - skips to next item on many playback errors instead of stopping completely;
 * - keeps audio alive when a car video surface disappears.
 */
object PlayerHolder {

    @Volatile
    private var player: ExoPlayer? = null

    private var currentSurface: Surface? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    fun getOrCreate(create: () -> ExoPlayer): ExoPlayer {
        return player ?: synchronized(this) {
            player ?: create().also { exo ->
                player = exo
                exo.addListener(object : Player.Listener {
                    override fun onEvents(p: Player, events: Player.Events) {
                        _duration.value = p.duration.coerceAtLeast(0)
                        _currentPosition.value = p.currentPosition.coerceAtLeast(0)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        PlaybackState.setError("Playback-fout: ${error.errorCodeName}")
                        if (exo.hasNextMediaItem()) {
                            exo.seekToNextMediaItem()
                            exo.prepare()
                            exo.play()
                        }
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
                player?.let { p ->
                    _currentPosition.value = p.currentPosition.coerceAtLeast(0)
                    _duration.value = p.duration.coerceAtLeast(0)
                }
                delay(1000)
            }
        }
    }

    fun get(): ExoPlayer? = player

    fun attachSurface(surface: Surface) {
        currentSurface = surface
        player?.setVideoSurface(surface)
    }

    fun detachSurface() {
        currentSurface = null
        player?.clearVideoSurface()
    }

    fun hasSurface(): Boolean = currentSurface != null

    fun setQueue(items: List<MediaItem>, startIndex: Int = 0) {
        val p = player ?: return
        if (items.isEmpty()) return
        val safe = startIndex.coerceIn(0, items.size - 1)
        try {
            p.setMediaItems(items, safe, 0L)
            p.playWhenReady = true
            p.prepare()
            p.play()
        } catch (t: Throwable) {
            PlaybackState.setError("Queue laden mislukt: ${t.message}")
        }
    }

    fun appendToQueue(items: List<MediaItem>) {
        val p = player ?: return
        if (items.isEmpty()) return
        items.forEach { item ->
            try {
                p.addMediaItem(item)
            } catch (t: Throwable) {
                PlaybackState.setError("Item overslaan: ${t.message}")
            }
        }
    }

    fun queueSize(): Int = player?.mediaItemCount ?: 0

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

        try {
            when {
                stream.isMuxed && stream.videoStreamUrl != null -> {
                    p.setMediaItem(
                        MediaItem.Builder()
                            .setUri(stream.videoStreamUrl)
                            .setMediaId(stream.originalUrl ?: stream.videoStreamUrl)
                            .setMediaMetadata(metadata)
                            .build()
                    )
                }
                stream.videoStreamUrl != null && stream.audioStreamUrl != null -> {
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
                }
                stream.audioStreamUrl != null -> {
                    p.setMediaItem(
                        MediaItem.Builder()
                            .setUri(stream.audioStreamUrl)
                            .setMediaId(stream.originalUrl ?: stream.audioStreamUrl)
                            .setMediaMetadata(metadata)
                            .build()
                    )
                }
                else -> return
            }

            p.playWhenReady = true
            p.prepare()
            p.play()
            PlaybackState.setCurrent(stream)
        } catch (t: Throwable) {
            PlaybackState.setError("Afspelen mislukt: ${t.message}")
        }
    }

    fun seekToNext() {
        player?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    }

    fun seekToPrevious() {
        player?.let { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() }
    }

    fun skipNext(onResult: (com.example.carvideo.extractor.SearchResultItem) -> Unit) {
        val current = PlaybackState.current.value ?: return
        val list = PlaybackState.playlist.value
        val currentIndex = list.indexOfFirst { it.url == current.originalUrl }
        if (currentIndex != -1 && currentIndex < list.size - 1) onResult(list[currentIndex + 1])
    }

    fun skipPrevious(onResult: (com.example.carvideo.extractor.SearchResultItem) -> Unit) {
        val current = PlaybackState.current.value ?: return
        val list = PlaybackState.playlist.value
        val currentIndex = list.indexOfFirst { it.url == current.originalUrl }
        if (currentIndex > 0) onResult(list[currentIndex - 1])
    }

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
