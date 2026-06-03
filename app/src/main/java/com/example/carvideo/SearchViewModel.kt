package com.example.carvideo

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.carvideo.extractor.SearchResultItem
import com.example.carvideo.extractor.StreamResult
import com.example.carvideo.extractor.YouTubeExtractorService
import com.example.carvideo.player.PlaybackState
import com.example.carvideo.player.PlayerHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS_NAME = "car_video_private_prefs"
private const val MAX_HISTORY = 80
private const val PREFETCH_FAST_COUNT = 2

data class UiState(
    val loading: Boolean = false,
    val results: List<SearchResultItem> = emptyList(),
    val trending: List<SearchResultItem> = emptyList(),
    val forYou: List<SearchResultItem> = emptyList(),
    val history: List<SearchResultItem> = emptyList(),
    val error: String? = null,
    val selectedService: Int = 0, // 0 YouTube, 1 SoundCloud
    val themeMode: Int = 0 // 0 System, 1 Light, 2 Dark
)

@UnstableApi
class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val likedItems = mutableListOf<SearchResultItem>()
    private val historyItems = mutableListOf<SearchResultItem>()

    private val _videoMode = MutableStateFlow(false)
    val videoMode = _videoMode.asStateFlow()

    @Volatile
    private var playGeneration = 0

    init {
        loadLikedItems()
        loadHistoryItems()

        val savedTheme = prefs.getInt("theme_mode", 0)
        _state.value = _state.value.copy(themeMode = savedTheme, history = historyItems.toList())

        val p = PlayerHolder.getOrCreate {
            ExoPlayer.Builder(getApplication())
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true
                )
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build()
        }

        p.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.let { item ->
                    val stream = item.toStreamResult()
                    PlaybackState.setCurrent(stream)
                    recordPlay(
                        SearchResultItem(
                            title = stream.title,
                            url = stream.originalUrl ?: item.mediaId,
                            uploader = stream.uploader,
                            durationSeconds = stream.durationSeconds,
                            thumbnailUrl = stream.thumbnailUrl
                        )
                    )
                }
            }
        })

        loadInitialContent()
    }

    fun getNextItem(): SearchResultItem? {
        val p = PlayerHolder.get() ?: return null
        if (!p.hasNextMediaItem()) return null
        val nextIdx = p.nextMediaItemIndex
        if (nextIdx == C.INDEX_UNSET) return null
        val mi = p.getMediaItemAt(nextIdx)
        return SearchResultItem(
            title = mi.mediaMetadata.title?.toString() ?: "",
            url = mi.mediaId,
            uploader = mi.mediaMetadata.artist?.toString(),
            durationSeconds = 0,
            thumbnailUrl = mi.mediaMetadata.artworkUri?.toString()
        )
    }

    private fun loadInitialContent() {
        viewModelScope.launch {
            try {
                val trending = YouTubeExtractorService.getTrending(_state.value.selectedService)
                val forYou = buildForYou(trending)
                _state.value = _state.value.copy(
                    trending = trending,
                    forYou = forYou,
                    history = historyItems.toList(),
                    error = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    forYou = buildForYou(emptyList()),
                    history = historyItems.toList(),
                    error = "Kon startlijst niet laden: ${e.message}"
                )
            }
        }
    }

    /**
     * Private recommendation algorithm. No server, no account, only your local likes/history.
     */
    private fun buildForYou(trending: List<SearchResultItem>): List<SearchResultItem> {
        val artistScores = mutableMapOf<String, Int>()
        likedItems.forEach { it.uploader?.let { artist -> artistScores[artist] = (artistScores[artist] ?: 0) + 50 } }
        historyItems.take(25).forEachIndexed { index, item ->
            item.uploader?.let { artist -> artistScores[artist] = (artistScores[artist] ?: 0) + (25 - index).coerceAtLeast(1) }
        }

        return (likedItems + historyItems + trending.sortedByDescending { artistScores[it.uploader] ?: 0 })
            .distinctBy { it.url }
            .take(30)
    }

    fun setService(serviceId: Int) {
        _state.value = _state.value.copy(selectedService = serviceId, loading = true, error = null)
        loadInitialContent()
    }

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return

        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                if (q.startsWith("http", ignoreCase = true)) {
                    val stream = withContext(Dispatchers.IO) { YouTubeExtractorService.resolveUrl(q) }
                    PlayerHolder.play(stream)
                    recordPlay(
                        SearchResultItem(
                            title = stream.title,
                            url = stream.originalUrl ?: q,
                            uploader = stream.uploader,
                            durationSeconds = stream.durationSeconds,
                            thumbnailUrl = stream.thumbnailUrl
                        )
                    )
                    _videoMode.value = true
                    _state.value = _state.value.copy(loading = false)
                } else {
                    val results = YouTubeExtractorService.search(q, serviceId = _state.value.selectedService)
                    PlaybackState.setPlaylist(results)
                    _state.value = _state.value.copy(
                        loading = false,
                        results = results,
                        error = if (results.isEmpty()) "Geen resultaten" else null
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = "Fout: ${e.message}")
            }
        }
    }

    /**
     * Audio-first playback. Only the selected item is resolved before playback starts.
     * The rest of the queue is added lazily in the background.
     */
    fun play(item: SearchResultItem, list: List<SearchResultItem>? = null) {
        val baseQueue = (list ?: _state.value.results.ifEmpty { _state.value.trending }).ifEmpty { listOf(item) }
        val clickedIndex = baseQueue.indexOfFirst { it.url == item.url }.coerceAtLeast(0)
        val orderedQueue = baseQueue.drop(clickedIndex) + baseQueue.take(clickedIndex)
        val generation = ++playGeneration

        _state.value = _state.value.copy(loading = true, error = null)
        _videoMode.value = false
        PlaybackState.setPlaylist(orderedQueue)
        PlaybackState.setCurrent(item.toStreamResult())
        recordPlay(item)

        viewModelScope.launch {
            try {
                val firstAudioUrl = withContext(Dispatchers.IO) { YouTubeExtractorService.getAudioStreamUrl(item.url) }
                if (generation != playGeneration) return@launch

                if (firstAudioUrl == null) {
                    _state.value = _state.value.copy(loading = false, error = "Kon geen audio stream laden")
                    return@launch
                }

                PlayerHolder.setQueue(listOf(item.toMediaItem(firstAudioUrl)), 0)
                _state.value = _state.value.copy(loading = false, history = historyItems.toList())

                appendQueueLazily(orderedQueue.drop(1), generation)
            } catch (e: Exception) {
                if (generation == playGeneration) {
                    _state.value = _state.value.copy(loading = false, error = "Fout: ${e.message}")
                }
            }
        }
    }

    private fun appendQueueLazily(items: List<SearchResultItem>, generation: Int) {
        viewModelScope.launch {
            val fast = items.take(PREFETCH_FAST_COUNT)
            val slow = items.drop(PREFETCH_FAST_COUNT)

            val firstBatch = withContext(Dispatchers.IO) { resolveMediaItems(fast) }
            if (generation != playGeneration) return@launch
            PlayerHolder.appendToQueue(firstBatch)

            for (item in slow) {
                if (generation != playGeneration) return@launch
                val mediaItem = withContext(Dispatchers.IO) { resolveMediaItems(listOf(item)).firstOrNull() }
                if (generation != playGeneration) return@launch
                mediaItem?.let { PlayerHolder.appendToQueue(listOf(it)) }
            }
        }
    }

    private suspend fun resolveMediaItems(items: List<SearchResultItem>): List<MediaItem> {
        return items.mapNotNull { item ->
            try {
                val audioUrl = YouTubeExtractorService.getAudioStreamUrl(item.url)
                if (audioUrl != null) item.toMediaItem(audioUrl) else null
            } catch (_: Exception) {
                null
            }
        }
    }

    fun toggleVideoMode() {
        val current = PlaybackState.current.value ?: return
        val url = current.originalUrl ?: return
        val targetVideoMode = !_videoMode.value
        _videoMode.value = targetVideoMode

        viewModelScope.launch {
            try {
                if (targetVideoMode) {
                    val stream = withContext(Dispatchers.IO) { YouTubeExtractorService.resolveUrl(url) }
                    PlayerHolder.play(stream)
                } else {
                    val item = SearchResultItem(
                        title = current.title,
                        url = url,
                        uploader = current.uploader,
                        durationSeconds = current.durationSeconds,
                        thumbnailUrl = current.thumbnailUrl
                    )
                    play(item, PlaybackState.playlist.value.ifEmpty { listOf(item) })
                }
            } catch (e: Exception) {
                _videoMode.value = false
                _state.value = _state.value.copy(error = "Video kon niet laden, audio blijft veilig: ${e.message}")
            }
        }
    }

    fun skipNext() = PlayerHolder.seekToNext()
    fun skipPrevious() = PlayerHolder.seekToPrevious()
    fun seekTo(positionMs: Long) = PlayerHolder.seekTo(positionMs)

    fun toggleLike(item: SearchResultItem) {
        val existing = likedItems.indexOfFirst { it.url == item.url }
        if (existing != -1) likedItems.removeAt(existing) else likedItems.add(0, item)
        saveLikedItems()
        _state.value = _state.value.copy(forYou = buildForYou(_state.value.trending))
    }

    fun setThemeMode(mode: Int) {
        _state.value = _state.value.copy(themeMode = mode)
        prefs.edit().putInt("theme_mode", mode).apply()
    }

    fun isLiked(url: String): Boolean = likedItems.any { it.url == url }

    private fun recordPlay(item: SearchResultItem) {
        if (item.url.isBlank()) return
        historyItems.removeAll { it.url == item.url }
        historyItems.add(0, item)
        while (historyItems.size > MAX_HISTORY) historyItems.removeAt(historyItems.lastIndex)
        saveHistoryItems()
        _state.value = _state.value.copy(history = historyItems.toList(), forYou = buildForYou(_state.value.trending))
    }

    private fun loadLikedItems() {
        likedItems.clear()
        likedItems += readItems("liked_items")
    }

    private fun saveLikedItems() = writeItems("liked_items", likedItems)

    private fun loadHistoryItems() {
        historyItems.clear()
        historyItems += readItems("play_history")
    }

    private fun saveHistoryItems() = writeItems("play_history", historyItems)

    private fun readItems(key: String): List<SearchResultItem> {
        val json = prefs.getString(key, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        SearchResultItem(
                            title = o.optString("title"),
                            url = o.optString("url"),
                            uploader = o.optString("uploader").takeIf { it.isNotEmpty() },
                            durationSeconds = o.optLong("duration", 0),
                            thumbnailUrl = o.optString("thumb").takeIf { it.isNotEmpty() }
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeItems(key: String, items: List<SearchResultItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("title", item.title)
                    .put("url", item.url)
                    .put("uploader", item.uploader ?: "")
                    .put("duration", item.durationSeconds)
                    .put("thumb", item.thumbnailUrl ?: "")
            )
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun SearchResultItem.toMediaItem(streamUri: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(url)
            .setUri(streamUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(uploader)
                    .setArtworkUri(thumbnailUrl?.let { Uri.parse(it) })
                    .setIsPlayable(true)
                    .build()
            )
            .build()

    private fun SearchResultItem.toStreamResult(): StreamResult =
        StreamResult(
            title = title,
            durationSeconds = durationSeconds,
            videoStreamUrl = null,
            audioStreamUrl = null,
            isMuxed = false,
            thumbnailUrl = thumbnailUrl,
            uploader = uploader,
            originalUrl = url
        )

    private fun MediaItem.toStreamResult(): StreamResult =
        StreamResult(
            title = mediaMetadata.title?.toString() ?: "",
            durationSeconds = 0,
            videoStreamUrl = null,
            audioStreamUrl = null,
            isMuxed = false,
            thumbnailUrl = mediaMetadata.artworkUri?.toString(),
            uploader = mediaMetadata.artist?.toString(),
            originalUrl = mediaId
        )
}
