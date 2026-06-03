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
            override fun onSearchTextChanged(searchText: String) {
                query = searchText
            }

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

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Nieuwe zoek")
                    .setOnClickListener {
                        results = emptyList()
                        errorMessage = null
                        loading = false
                        invalidate()
                    }
                    .build()
            )
            .build()

        return ListTemplate.Builder()
            .setTitle(if (query.isBlank()) "Zoeken" else "Resultaten: $query")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .setActionStrip(actionStrip)
            .build()
    }

    private fun performSearch(searchQuery: String) {
        if (searchQuery.isBlank()) {
            errorMessage = "Typ eerst iets om te zoeken."
            invalidate()
            return
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
                errorMessage = if (found == null) {
                    "Zoeken duurde te lang. Probeer opnieuw of zoek op je telefoon."
                } else if (found.isEmpty()) {
                    "Geen resultaten gevonden."
                } else null
            } catch (e: Exception) {
                Log.e("CarVideoApp", "SearchScreen: Error searching", e)
                errorMessage = "Zoeken mislukt: ${e.message ?: "onbekende fout"}"
            } finally {
                loading = false
                invalidate()
            }
        }
    }

    private fun playAndNavigate(item: SearchResultItem) {
        loading = true
        errorMessage = null
        invalidate()

        lifecycleScope.launch {
            try {
                PlaybackState.setPlaylist(results.ifEmpty { listOf(item) })
                val stream = withTimeoutOrNull(15_000L) {
                    YouTubeExtractorService.resolveUrl(item.url)
                }

                if (stream == null) {
                    errorMessage = "Stream laden duurde te lang. Probeer opnieuw."
                    loading = false
                    invalidate()
                    return@launch
                }

                PlayerHolder.play(stream)
                loading = false
                screenManager.push(VideoScreen(carContext))
            } catch (e: Exception) {
                Log.e("CarVideoApp", "SearchScreen: Error playing", e)
                loading = false
                errorMessage = "Afspelen mislukt: ${e.message ?: "onbekende fout"}"
                invalidate()
            }
        }
    }
}
