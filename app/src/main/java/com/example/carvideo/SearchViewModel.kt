package com.example.carvideo

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.carvideo.data.AppDatabase
import com.example.carvideo.data.toEntity
import com.example.carvideo.extractor.SearchResultItem
import com.example.carvideo.extractor.StreamResult
import com.example.carvideo.extractor.YouTubeExtractorService
import com.example.carvideo.player.PlaybackState
import com.example.carvideo.player.PlayerHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

import java.util.Calendar

private const val PREFS_NAME = "car_video_private_prefs"
private const val MAX_HISTORY = 80
private const val PREFETCH_FAST_COUNT = 2

data class UiState(
    val loading: Boolean = false,
    val isFailover: Boolean = false,
    val results: List<SearchResultItem> = emptyList(),
    val trending: List<SearchResultItem> = emptyList(),
    val forYou: List<SearchResultItem> = emptyList(),
    val history: List<SearchResultItem> = emptyList(),
    val error: String? = null,
    val selectedService: Int = 0, // 0 YouTube, 1 SoundCloud
    val themeMode: Int = 0 // 0 System, 1 Light, 2 Dark
)

@OptIn(UnstableApi::class)
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val db = AppDatabase.getDatabase(app)
    private val trackDao = db.trackDao()

    private val likedItems = mutableListOf<SearchResultItem>()
    private val historyItems = mutableListOf<SearchResultItem>()

    private val _videoMode = MutableStateFlow(false)
    val videoMode = _videoMode.asStateFlow()

    @Volatile
    private var playGeneration = 0

    init {
        val savedTheme = prefs.getInt("theme_mode", 0)
        _state.value = _state.value.copy(themeMode = savedTheme)

        // Migratie van SharedPreferences naar Room (éénmalig)
        if (!prefs.getBoolean("room_migrated", false)) {
            migrateToRoom()
        }

        // Observeer Database voor Likes en History
        viewModelScope.launch {
            trackDao.getLikedTracks().collectLatest { entities ->
                likedItems.clear()
                likedItems.addAll(entities.map { it.toSearchResultItem() })
                updateForYou()
            }
        }

        viewModelScope.launch {
            trackDao.getHistory().collectLatest { entities ->
                historyItems.clear()
                historyItems.addAll(entities.map { it.toSearchResultItem() })
                _state.value = _state.value.copy(history = historyItems.toList())
                updateForYou()
            }
        }

        val p = PlayerHolder.getOrCreate {
            ExoPlayer.Builder(application)
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
                    triggerLookAhead()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val currentItem = p.currentMediaItem ?: return
                val position = p.currentPosition
                
                viewModelScope.launch {
                    _state.value = _state.value.copy(isFailover = true)
                    try {
                        val mirror = withContext(Dispatchers.IO) {
                            YouTubeExtractorService.findMirror(
                                currentItem.mediaMetadata.title?.toString() ?: "",
                                currentItem.mediaMetadata.artist?.toString(),
                                0,
                                _state.value.selectedService
                            )
                        }
                        
                        val newUrl = mirror?.audioStreamUrl ?: mirror?.videoStreamUrl
                        if (newUrl != null) {
                            val newMediaItem = MediaItem.Builder()
                                .setMediaId(currentItem.mediaId)
                                .setUri(newUrl)
                                .setMediaMetadata(currentItem.mediaMetadata)
                                .build()
                            
                            p.setMediaItem(newMediaItem)
                            p.seekTo(position)
                            p.prepare()
                            p.play()
                        }
                    } catch (_: Exception) {}
                    _state.value = _state.value.copy(isFailover = false)
                }
            }
        })

        loadInitialContent()
    }

    private fun migrateToRoom() {
        viewModelScope.launch(Dispatchers.IO) {
            val oldLikes = readItemsFromPrefs("liked_items")
            val oldHistory = readItemsFromPrefs("play_history")
            
            oldLikes.forEach { trackDao.insertTrack(it.toEntity(isLiked = true)) }
            oldHistory.forEach { 
                val existing = trackDao.getTrack(it.url)
                if (existing != null) {
                    trackDao.updateLikeStatus(it.url, existing.isLiked)
                    trackDao.recordPlay(it.url, System.currentTimeMillis(), -1)
                } else {
                    trackDao.insertTrack(it.toEntity(lastPlayed = System.currentTimeMillis()))
                }
            }
            prefs.edit().putBoolean("room_migrated", true).apply()
        }
    }

    private fun updateForYou() {
        _state.value = _state.value.copy(forYou = buildForYou(_state.value.trending))
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
                _state.value = _state.value.copy(
                    trending = trending,
                    error = null
                )
                updateForYou()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Kon startlijst niet laden: ${e.message}"
                )
                updateForYou()
            }
        }
    }

    /**
     * Contextueel aanbevelingsalgoritme.
     * Houdt rekening met likes, historie én het tijdstip van de dag.
     */
    private fun buildForYou(trending: List<SearchResultItem>): List<SearchResultItem> {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isMorning = hour in 6..11
        val isEvening = hour in 18..23 || hour in 0..4

        val artistScores = mutableMapOf<String, Int>()
        
        // Basis scores uit Likes (hoogste gewicht)
        likedItems.forEach { it.uploader?.let { artist -> 
            artistScores[artist] = (artistScores[artist] ?: 0) + 50 
        } }
        
        // Historie gewicht (recenter is belangrijker)
        historyItems.take(20).forEachIndexed { index, item ->
            item.uploader?.let { artist -> 
                artistScores[artist] = (artistScores[artist] ?: 0) + (20 - index) 
            }
        }

        // Tijdstip bias: In de avond sorteren we meer op 'vertrouwde' muziek (Likes)
        // In de ochtend geven we 'Trending' (nieuwe content) een boost
        return (likedItems + historyItems + trending)
            .distinctBy { it.url }
            .map { item ->
                var score = artistScores[item.uploader] ?: 0
                
                // Bonus voor Trending in de ochtend
                if (isMorning && trending.any { it.url == item.url }) score += 30
                
                // Bonus voor Likes in de avond
                if (isEvening && likedItems.any { it.url == item.url }) score += 40
                
                item to score
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(40)
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
                    // SMART SEARCH: Zoek parallel lokaal en online
                    val localTask = viewModelScope.launch(Dispatchers.IO) {
                        val localResults = trackDao.searchLocal(q).map { it.toSearchResultItem() }
                        if (localResults.isNotEmpty()) {
                            // Voeg lokale resultaten direct toe voor snelheid
                            withContext(Dispatchers.Main) {
                                _state.value = _state.value.copy(results = localResults)
                            }
                        }
                    }

                    val onlineResults = withContext(Dispatchers.IO) { 
                        YouTubeExtractorService.search(q, serviceId = _state.value.selectedService) 
                    }
                    
                    localTask.join() // Wacht tot lokale zoekopdracht klaar is (meestal al klaar)
                    
                    // Combineer: Lokale resultaten eerst, dan online (zonder dubbelen)
                    val localCurrent = _state.value.results
                    val finalResults = (localCurrent + onlineResults).distinctBy { it.url }

                    PlaybackState.setPlaylist(finalResults)
                    _state.value = _state.value.copy(
                        loading = false,
                        results = finalResults,
                        error = if (finalResults.isEmpty()) "Geen resultaten" else null
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = "Fout: ${e.message}")
            }
        }
    }

    fun play(item: SearchResultItem, list: List<SearchResultItem>? = null) {
        val baseQueue = (list ?: _state.value.results.ifEmpty { _state.value.trending }).ifEmpty { listOf(item) }
        val clickedIndex = baseQueue.indexOfFirst { it.url == item.url }.coerceAtLeast(0)
        val orderedQueue = baseQueue.drop(clickedIndex) + baseQueue.take(clickedIndex)
        val generation = ++playGeneration
        val currentService = _state.value.selectedService

        _state.value = _state.value.copy(loading = true, isFailover = false, error = null)
        _videoMode.value = false
        PlaybackState.setPlaylist(orderedQueue)
        PlaybackState.setCurrent(item.toStreamResult())
        recordPlay(item)

        viewModelScope.launch {
            try {
                var streamUrl = withContext(Dispatchers.IO) { YouTubeExtractorService.getAudioStreamUrl(item.url) }
                
                if (streamUrl == null && generation == playGeneration) {
                    _state.value = _state.value.copy(isFailover = true)
                    val mirror = withContext(Dispatchers.IO) {
                        YouTubeExtractorService.findMirror(
                            item.title, 
                            item.uploader, 
                            item.durationSeconds, 
                            currentService
                        )
                    }
                    streamUrl = mirror?.audioStreamUrl ?: mirror?.videoStreamUrl
                }

                if (generation != playGeneration) return@launch

                if (streamUrl == null) {
                    _state.value = _state.value.copy(loading = false, isFailover = false, error = "Stream kon niet geladen worden")
                    return@launch
                }

                PlayerHolder.setQueue(listOf(item.toMediaItem(streamUrl)), 0)
                _state.value = _state.value.copy(loading = false, isFailover = false)

                val nextTwo = orderedQueue.drop(1).take(2)
                if (nextTwo.isNotEmpty()) {
                    val resolvedNext = withContext(Dispatchers.IO) { resolveMediaItems(nextTwo) }
                    if (generation == playGeneration) {
                        PlayerHolder.appendToQueue(resolvedNext)
                    }
                }
            } catch (e: Exception) {
                if (generation == playGeneration) {
                    _state.value = _state.value.copy(loading = false, isFailover = false, error = "Fout bij afspelen: ${e.message}")
                }
            }
        }
    }

    private fun triggerLookAhead() {
        val p = PlayerHolder.get() ?: return
        val playlist = PlaybackState.playlist.value
        if (playlist.isEmpty()) return

        val currentIndexInPlayer = p.currentMediaItemIndex
        if (currentIndexInPlayer == C.INDEX_UNSET) return

        val currentMediaItem = p.getMediaItemAt(currentIndexInPlayer)
        val currentIndexInPlaylist = playlist.indexOfFirst { it.url == currentMediaItem.mediaId }
        
        if (currentIndexInPlaylist == -1) return

        val nextIdx1 = (currentIndexInPlaylist + 1) % playlist.size
        val nextIdx2 = (currentIndexInPlaylist + 2) % playlist.size
        
        val itemsToEnsure = listOf(playlist[nextIdx1], playlist[nextIdx2])
        
        viewModelScope.launch {
            val existingIds = mutableListOf<String>()
            for (i in 0 until p.mediaItemCount) {
                existingIds.add(p.getMediaItemAt(i).mediaId)
            }
            
            val needed = itemsToEnsure.filter { !existingIds.contains(it.url) }
            
            if (needed.isNotEmpty()) {
                val resolved = resolveMediaItems(needed)
                PlayerHolder.appendToQueue(resolved)
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
                _state.value = _state.value.copy(error = "Video kon niet laden: ${e.message}")
            }
        }
    }

    fun skipNext() = PlayerHolder.seekToNext()
    fun skipPrevious() = PlayerHolder.seekToPrevious()
    fun seekTo(positionMs: Long) = PlayerHolder.seekTo(positionMs)

    fun toggleLike(item: SearchResultItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = trackDao.getTrack(item.url)
            if (existing != null) {
                trackDao.updateLikeStatus(item.url, !existing.isLiked)
            } else {
                trackDao.insertTrack(item.toEntity(isLiked = true))
            }
        }
    }

    fun setThemeMode(mode: Int) {
        _state.value = _state.value.copy(themeMode = mode)
        prefs.edit().putInt("theme_mode", mode).apply()
    }

    fun isLiked(url: String): Boolean = likedItems.any { it.url == url }

    private fun recordPlay(item: SearchResultItem) {
        if (item.url.isBlank()) return
        val now = System.currentTimeMillis()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        
        viewModelScope.launch(Dispatchers.IO) {
            val existing = trackDao.getTrack(item.url)
            if (existing != null) {
                trackDao.recordPlay(item.url, now, hour)
            } else {
                trackDao.insertTrack(item.toEntity(lastPlayed = now, lastHour = hour))
            }
        }
    }

    private fun readItemsFromPrefs(key: String): List<SearchResultItem> {
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
