package com.example.carvideo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.carvideo.extractor.SearchResultItem
import com.example.carvideo.extractor.YouTubeExtractorService
import com.example.carvideo.player.PlayerHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val loading: Boolean = false,
    val results: List<SearchResultItem> = emptyList(),
    val error: String? = null
)

@UnstableApi
class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        // Zorg dat de gedeelde speler bestaat (telefoon-weergave + achtergrond).
        PlayerHolder.ensureCreated {
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
                    _state.value = UiState(loading = false)
                } else {
                    val results = YouTubeExtractorService.search(q)
                    _state.value = UiState(
                        loading = false,
                        results = results,
                        error = if (results.isEmpty()) "Geen resultaten" else null
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = "Fout: ${e.message}"
                )
            }
        }
    }

    fun play(item: SearchResultItem) {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            try {
                val stream = YouTubeExtractorService.resolveUrl(item.url)
                PlayerHolder.play(stream)
                _state.value = _state.value.copy(loading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = "Fout: ${e.message}"
                )
            }
        }
    }
}
