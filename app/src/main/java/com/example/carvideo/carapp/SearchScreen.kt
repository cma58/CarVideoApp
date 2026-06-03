package com.example.carvideo.carapp

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.example.carvideo.extractor.SearchResultItem
import com.example.carvideo.extractor.YouTubeExtractorService
import com.example.carvideo.player.PlaybackState
import com.example.carvideo.player.PlayerHolder
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@UnstableApi
class SearchScreen(carContext: CarContext) : Screen(carContext) {
    private var query: String = ""
    private var loading = false
    private var errorMessage: String? = null
    private var results: List<SearchResultItem> = emptyList()

    override fun onGetTemplate(): Template {
        if (loading || errorMessage != null || results.isNotEmpty()) {
            return buildResultsTemplate()
        }

        return SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) { query = searchText }
            override fun onSearchSubmitted(searchText: String) {
                query = searchText.trim()
                performSearch(query)
            }
        })
            .setSearchHint("Zoek video's...")
            .setHeaderAction(Action.BACK)
            .setShowKeyboardByDefault(true)
            .build()
    }

    private fun buildResultsTemplate(): Template {
        val listBuilder = ItemList.Builder()
        when {
            loading -> listBuilder.setNoItemsMessage("Zoeken...")
            errorMessage != null -> listBuilder.setNoItemsMessage(errorMessage ?: "Zoeken mislukt")
            results.isEmpty() -> listBuilder.setNoItemsMessage("Geen resultaten gevonden")
            else -> results.take(12).forEach { item ->
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(item.title.ifBlank { "Onbekende titel" })
                        .addText(item.uploader ?: "YouTube")
                        .setOnClickListener { playAndNavigate(item) }
                        .build()
                )
            }
        }

        return ListTemplate.Builder()
            .setTitle(if (query.isBlank()) "Zoeken" else "Resultaten: $query")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }

    private fun performSearch(searchQuery: String) {
        if (searchQuery.isBlank()) {
            errorMessage = "Typ eerst iets om te zoeken."
            invalidate(); return
        }

        loading = true
        errorMessage = null
        results = emptyList()
        invalidate()

        lifecycleScope.launch {
            try {
                val found = withTimeoutOrNull(15_000L) {
                    YouTubeExtractorService.search(searchQuery, limit = 12, serviceId = 0)
                }
                results = found ?: emptyList()
                errorMessage = if (found == null) "Zoeken duurde te lang." else if (found.isEmpty()) "Geen resultaten gevonden." else null
            } catch (e: Exception) {
                Log.e("CarVideoApp", "SearchScreen", e)
                errorMessage = "Zoeken mislukt"
            } finally {
                loading = false
                invalidate()
            }
        }
    }

    private fun playAndNavigate(item: SearchResultItem) {
        lifecycleScope.launch {
            try {
                PlaybackState.setPlaylist(results.ifEmpty { listOf(item) })
                val stream = withTimeoutOrNull(15_000L) {
                    YouTubeExtractorService.resolveUrl(item.url)
                } ?: return@launch

                PlayerHolder.play(stream)
                invalidate()
            } catch (e: Exception) {
                errorMessage = "Afspelen mislukt"
                invalidate()
            }
        }
    }
}
