package com.example.carvideo.player

import com.example.carvideo.extractor.SearchResultItem
import com.example.carvideo.extractor.StreamResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lightweight shared state so the Car App screen can reflect what the player is
 * doing and trigger new playback. Both phone UI and Car App read/write here.
 */
object PlaybackState {
    private val _current = MutableStateFlow<StreamResult?>(null)
    val current: StateFlow<StreamResult?> = _current

    private val _playlist = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val playlist: StateFlow<List<SearchResultItem>> = _playlist

    fun setCurrent(stream: StreamResult?) {
        _current.value = stream
    }

    fun setPlaylist(list: List<SearchResultItem>) {
        _playlist.value = list
    }
}
