package com.example.carvideo

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.carvideo.extractor.SearchResultItem
import com.example.carvideo.extractor.YouTubeExtractorService
import com.example.carvideo.player.PlaybackState
import com.example.carvideo.player.PlayerHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.content.Intent
import com.example.carvideo.player.PlaybackService

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

    private val likedUrls = mutableSetOf<String>()
    private val prefs = app.getSharedPreferences("car_video_prefs", Context.MODE_PRIVATE)

    private val _videoMode = MutableStateFlow(true)
    val videoMode = _videoMode.asStateFlow()

    init {
        // Load liked URLs from storage
        prefs.getStringSet("liked_urls", emptySet<String>())?.let {
            likedUrls.addAll(it)
        }
        
        val savedTheme = prefs.getInt("theme_mode", 0)
        _state.value = _state.value.copy(themeMode = savedTheme)
        
        // Zorg dat de gedeelde speler bestaat (telefoon-weergave + achtergrond).
        val p = PlayerHolder.getOrCreate {
            ExoPlayer.Builder(getApplication())
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true
                )
                .setHandleAudioBecomingNoisy(true)
                .build()
        }
        
        // Listen for track completion to play next
        p.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    // Logic for "Next Up" preview update could go here
                }
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    playNext()
                }
            }
        })
        
        loadInitialContent()
    }

    private fun playNext() {
        val current = PlaybackState.current.value ?: return
        val list = if (_state.value.results.isNotEmpty()) _state.value.results else _state.value.trending
        val currentIndex = list.indexOfFirst { it.url == current.originalUrl }
        if (currentIndex != -1 && currentIndex < list.size - 1) {
            play(list[currentIndex + 1])
        }
    }

    fun getNextItem(): SearchResultItem? {
        val current = PlaybackState.current.value ?: return null
        val list = if (_state.value.results.isNotEmpty()) _state.value.results else _state.value.trending
        val currentIndex = list.indexOfFirst { it.url == current.originalUrl }
        return if (currentIndex != -1 && currentIndex < list.size - 1) list[currentIndex + 1] else null
    }

    private fun loadInitialContent() {
        viewModelScope.launch {
            try {
                val trending = YouTubeExtractorService.getTrending(_state.value.selectedService)
                _state.value = _state.value.copy(trending = trending)
                // For now, "For You" is just trending or based on a simple mock algorithm
                _state.value = _state.value.copy(forYou = trending.shuffled().take(10))
                
                if (PlaybackState.playlist.value.isEmpty()) {
                    PlaybackState.setPlaylist(trending)
                }
            } catch (e: Exception) {
                // Silently fail or log
            }
        }
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
                    // Directe URL: meteen afspelen, geen lijst.
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
                _state.value = _state.value.copy(
                    loading = false,
                    error = "Fout: ${e.message}"
                )
            }
        }
    }

    fun play(item: SearchResultItem, list: List<SearchResultItem>? = null) {
        // Update UI immediately for responsiveness
        _state.value = _state.value.copy(loading = true)
        if (list != null) {
            PlaybackState.setPlaylist(list)
        }
        viewModelScope.launch {
            try {
                // Perform extraction in IO thread (already handled by resolveUrl)
                val stream = YouTubeExtractorService.resolveUrl(item.url)
                // Switch to Main to interact with Player
                withContext(Dispatchers.Main) {
                    PlayerHolder.play(stream)
                    // Explicitly start service as foreground for notification visibility
                    val context = getApplication<android.app.Application>()
                    val intent = Intent(context, PlaybackService::class.java)
                    context.startForegroundService(intent)
                    _state.value = _state.value.copy(loading = false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = "Fout: ${e.message}"
                    )
                }
            }
        }
    }

    fun toggleVideoMode() {
        _videoMode.value = !_videoMode.value
        val current = PlaybackState.current.value ?: return
        val url = current.originalUrl ?: return
        
        // Re-play current item to apply mode immediately
        play(SearchResultItem(
            title = current.title,
            url = url,
            uploader = current.uploader,
            thumbnailUrl = current.thumbnailUrl,
            durationSeconds = current.durationSeconds
        ))
    }

    fun skipNext() {
        playNext()
    }

    fun skipPrevious() {
        val current = PlaybackState.current.value ?: return
        val list = if (_state.value.results.isNotEmpty()) _state.value.results else _state.value.trending
        val currentIndex = list.indexOfFirst { it.url == current.originalUrl }
        if (currentIndex > 0) {
            play(list[currentIndex - 1])
        }
    }

    fun seekTo(positionMs: Long) {
        PlayerHolder.seekTo(positionMs)
    }

    fun toggleLike(item: SearchResultItem) {
        if (likedUrls.contains(item.url)) {
            likedUrls.remove(item.url)
        } else {
            likedUrls.add(item.url)
        }
        prefs.edit().putStringSet("liked_urls", likedUrls).apply()
        // Trigger UI update
        _state.value = _state.value.copy()
    }

    fun setThemeMode(mode: Int) {
        _state.value = _state.value.copy(themeMode = mode)
        prefs.edit().putInt("theme_mode", mode).apply()
    }

    fun isLiked(url: String): Boolean = likedUrls.contains(url)
}
