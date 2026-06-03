package com.example.carvideo.carapp

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.example.carvideo.extractor.YouTubeExtractorService
import com.example.carvideo.player.PlaybackState
import com.example.carvideo.player.PlayerHolder
import kotlinx.coroutines.launch

@UnstableApi
class SearchScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        return SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {}
            override fun onSearchSubmitted(searchText: String) {
                performSearch(searchText)
            }
        })
        .setSearchHint("Zoek video\u0027s...")
        .setHeaderAction(Action.BACK)
        .setShowKeyboardByDefault(false)
        .build()
    }

    private fun performSearch(query: String) {
        lifecycleScope.launch {
            val results = YouTubeExtractorService.search(query)
            if (results.isNotEmpty()) {
                PlaybackState.setPlaylist(results)
                val stream = YouTubeExtractorService.resolveUrl(results[0].url)
                PlayerHolder.play(stream)
                screenManager.push(VideoScreen(carContext))
            }
        }
    }
}
