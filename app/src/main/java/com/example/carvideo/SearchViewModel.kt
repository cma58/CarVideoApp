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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class UiState(
    val loading: Boolean = false,
    val results: List<SearchResultItem> = emptyList(),
    val trending: List<SearchResultItem> = emptyList(),
    val forYou: List<SearchResultItem> = emptyList(),
    val error: String? = null,
    val selectedService: Int = 0, // 0: YouTube, 1: SoundCloud
    val themeMode: Int = 0 // 0: System, 1: Light, 2: Dark
)

@UnstableApi
class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    // Likes als VOLLEDIGE items (titel, url, uploader, thumbnail) -> echte For You.
    private val likedItems = mutableListOf<SearchResultItem>()
    private val prefs = app.getSharedPreferences("car_video_prefs", Context.MODE_PRIVATE)

    private val _videoMode = MutableStateFlow(false)
    val videoMode = _videoMode.asStateFlow()

    init {
        loadLikedItems()

        val savedTheme = prefs.getInt("theme_mode", 0)
        _state.value = _state.value.copy(themeMode = savedTheme)

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

        // Houd de now-playing balk synchroon bij elke (auto/handmatige) overgang.
        p.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.let { PlaybackState.setCurrent(it.toStreamResult()) }
            }
        })

        loadInitialContent()
    }

    /** "Volgende" leest rechtstreeks uit de speler-timeline. */
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
                    error = null
                )
            } catch (e: Exception) {
                // Niet stil falen: laat zien waarom de lijst leeg blijft, en val terug op likes.
                _state.value = _state.value.copy(
                    forYou = likedItems.take(25),
                    error = "Kon trending niet laden: ${e.message}"
                )
            }
        }
    }

    /**
     * SAMENGEVOEGD UIT YTAuto (idee uit PlayEvent-analytics, maar zonder database):
     * For You = je favorieten, gevolgd door NIEUWE nummers van je meest-gelikete
     * artiesten, met trending als opvulling. Hoe meer je liket, hoe persoonlijker.
     */
    private suspend fun buildForYou(trending: List<SearchResultItem>): List<SearchResultItem> {
        val topArtists = likedItems
            .mapNotNull { it.uploader?.takeIf { u -> u.isNotBlank() } }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }

        val recommendations = mutableListOf<SearchResultItem>()
        for (artist in topArtists) {
            try {
                recommendations += YouTubeExtractorService.search(
                    artist, limit = 5, serviceId = _state.value.selectedService
                )
            } catch (_: Exception) { /* een artiest die faalt mag de rest niet blokkeren */ }
        }

        return (likedItems + recommendations + trending)
            .distinctBy { it.url }
            .take(25)
    }

    fun setService(serviceId: Int) {
        _state.value = _state.value.copy(selectedService = serviceId)
        loadInitialContent()
    }

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return

        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                if (q.startsWith("http")) {
                    val stream = YouTubeExtractorService.resolveUrl(q)
                    PlayerHolder.play(stream)
                    _state.value = _state.value.copy(loading = false)
                } else {
                    val results = YouTubeExtractorService.search(q, serviceId = _state.value.selectedService)
                    _state.value = _state.value.copy(
                        loading = false,
                        results = results,
                        error = if (results.isEmpty()) "Geen resultaten" else null
                    )
                    PlaybackState.setPlaylist(results)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = "Fout: ${e.message}")
            }
        }
    }

    /**
     * Speelt een item af ALS DEEL VAN DE HELE LIJST. Dankzij de stream-cache in
     * YouTubeExtractorService gaat het opnieuw laden van een wachtrij nu snel.
     */
    fun play(item: SearchResultItem, list: List<SearchResultItem>? = null) {
        val queue = list ?: _state.value.results.ifEmpty { _state.value.trending }.ifEmpty { listOf(item) }
        _state.value = _state.value.copy(loading = true, error = null)
        PlaybackState.setPlaylist(queue)
        PlaybackState.setCurrent(item.toStreamResult())

        viewModelScope.launch {
            try {
                val mediaItems = withContext(Dispatchers.IO) {
                    queue.map { si ->
                        async {
                            val audioUrl = YouTubeExtractorService.getAudioStreamUrl(si.url)
                            if (audioUrl != null) si.toMediaItem(audioUrl) else null
                        }
                    }.awaitAll().filterNotNull()
                }

                withContext(Dispatchers.Main) {
                    if (mediaItems.isEmpty()) {
                        _state.value = _state.value.copy(loading = false, error = "Kon geen streams laden")
                        return@withContext
                    }
                    val startIndex = mediaItems.indexOfFirst { it.mediaId == item.url }.coerceAtLeast(0)
                    PlayerHolder.setQueue(mediaItems, startIndex)
                    _videoMode.value = false
                    _state.value = _state.value.copy(loading = false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(loading = false, error = "Fout: ${e.message}")
                }
            }
        }
    }

    fun toggleVideoMode() {
        _videoMode.value = !_videoMode.value
        val current = PlaybackState.current.value ?: return
        val url = current.originalUrl ?: return

        viewModelScope.launch {
            try {
                if (_videoMode.value) {
                    val stream = withContext(Dispatchers.IO) { YouTubeExtractorService.resolveUrl(url) }
                    withContext(Dispatchers.Main) { PlayerHolder.play(stream) }
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
                _state.value = _state.value.copy(error = "Fout: ${e.message}")
            }
        }
    }

    fun skipNext() = PlayerHolder.seekToNext()

    fun skipPrevious() = PlayerHolder.seekToPrevious()

    fun seekTo(positionMs: Long) = PlayerHolder.seekTo(positionMs)

    fun toggleLike(item: SearchResultItem) {
        val existing = likedItems.indexOfFirst { it.url == item.url }
        if (existing != -1) {
            likedItems.removeAt(existing)
        } else {
            likedItems.add(item)
        }
        saveLikedItems()
        // Goedkope update: favorieten meteen bovenaan For You; volledige smaak-
        // aanbevelingen worden bij de volgende loadInitialContent vernieuwd.
        val forYou = (likedItems + _state.value.forYou).distinctBy { it.url }.take(25)
        _state.value = _state.value.copy(forYou = forYou)
    }

    fun setThemeMode(mode: Int) {
        _state.value = _state.value.copy(themeMode = mode)
        prefs.edit().putInt("theme_mode", mode).apply()
    }

    fun isLiked(url: String): Boolean = likedItems.any { it.url == url }

    // ---- Likes persistentie (JSON in SharedPreferences, geen extra dependency) ----

    private fun loadLikedItems() {
        likedItems.clear()
        val json = prefs.getString("liked_items", null) ?: return
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                likedItems.add(
                    SearchResultItem(
                        title = o.optString("title"),
                        url = o.optString("url"),
                        uploader = o.optString("uploader").takeIf { it.isNotEmpty() },
                        durationSeconds = o.optLong("duration", 0),
                        thumbnailUrl = o.optString("thumb").takeIf { it.isNotEmpty() }
                    )
                )
            }
        } catch (_: Exception) { /* corrupt -> negeren */ }
    }

    private fun saveLikedItems() {
        val arr = JSONArray()
        likedItems.forEach { item ->
            arr.put(
                JSONObject()
                    .put("title", item.title)
                    .put("url", item.url)
                    .put("uploader", item.uploader ?: "")
                    .put("duration", item.durationSeconds)
                    .put("thumb", item.thumbnailUrl ?: "")
            )
        }
        prefs.edit().putString("liked_items", arr.toString()).apply()
    }

    // ---- Mappers ----

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
