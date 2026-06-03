package com.example.carvideo.carapp

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
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
class MainCarScreen(carContext: CarContext) : Screen(carContext) {

    private var startedLoading = false
    private var loading = false
    private var errorMessage: String? = null

    override fun onGetTemplate(): Template {
        Log.d("CarVideoApp", "MainCarScreen: onGetTemplate")
        val playlist = PlaybackState.playlist.value
        val current = PlaybackState.current.value
        val listBuilder = ItemList.Builder()

        if (current != null) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Nu speelt")
                    .addText(current.title.ifBlank { "Onbekende titel" })
                    .addText(current.uploader ?: "")
                    .build()
            )
        }

        listBuilder.addItem(
            Row.Builder()
                .setTitle(if (PlayerHolder.isPlaying()) "Pauze" else "Afspelen")
                .addText("Play / pause")
                .setOnClickListener {
                    PlayerHolder.togglePlayPause()
                    invalidate()
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Volgende")
                .addText("Speel volgende item")
                .setOnClickListener { playNext() }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Vorige")
                .addText("Speel vorige item")
                .setOnClickListener { playPrevious() }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Refresh lijst")
                .addText("Opnieuw laden")
                .setOnClickListener {
                    startedLoading = false
                    errorMessage = null
                    PlaybackState.setPlaylist(emptyList())
                    loadTrendingOnce(force = true)
                    invalidate()
                }
                .build()
        )

        when {
            playlist.isNotEmpty() -> {
                errorMessage = null
                playlist.take(8).forEach { item ->
                    listBuilder.addItem(
                        Row.Builder()
                            .setTitle(item.title.ifBlank { "Onbekende titel" })
                            .addText(item.uploader ?: "YouTube")
                            .setOnClickListener { playAndNavigate(item) }
                            .build()
                    )
                }
            }
            loading -> {
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("Laden...")
                        .addText("Dit kan even duren")
                        .build()
                )
            }
            errorMessage != null -> {
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("Kon niet laden")
                        .addText(errorMessage ?: "Onbekende fout")
                        .build()
                )
            }
            else -> {
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("Geen lijst geladen")
                        .addText("Gebruik Refresh of Zoek")
                        .build()
                )
                loadTrendingOnce()
            }
        }

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Zoek")
                    .setOnClickListener { screenManager.push(SearchScreen(carContext)) }
                    .build()
            )
            .build()

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setActionStrip(actionStrip)
            .setHeaderAction(Action.APP_ICON)
            .setTitle("Ontdekken")
            .build()
    }

    private fun loadTrendingOnce(force: Boolean = false) {
        if (startedLoading && !force) return
        startedLoading = true
        loading = true
        errorMessage = null
        invalidate()

        lifecycleScope.launch {
            try {
                val trending = withTimeoutOrNull(12_000L) {
                    YouTubeExtractorService.getTrending(0)
                }

                if (trending.isNullOrEmpty()) {
                    errorMessage = "Kon trending niet laden. Probeer Zoek of start eerst iets op je telefoon."
                } else {
                    PlaybackState.setPlaylist(trending)
                    errorMessage = null
                }
            } catch (e: Exception) {
                Log.e("CarVideoApp", "MainCarScreen: Error loading trending", e)
                errorMessage = "Laden mislukt: ${e.message ?: "onbekende fout"}."
            } finally {
                loading = false
                invalidate()
            }
        }
    }

    private fun playNext() {
        PlayerHolder.skipNext { item -> playAndNavigate(item) }
        invalidate()
    }

    private fun playPrevious() {
        PlayerHolder.skipPrevious { item -> playAndNavigate(item) }
        invalidate()
    }

    private fun playAndNavigate(item: SearchResultItem) {
        lifecycleScope.launch {
            try {
                val stream = withTimeoutOrNull(15_000L) {
                    YouTubeExtractorService.resolveUrl(item.url)
                }

                if (stream == null) {
                    errorMessage = "Stream laden duurde te lang."
                    invalidate()
                    return@launch
                }

                PlayerHolder.play(stream)
                invalidate()
            } catch (e: Exception) {
                Log.e("CarVideoApp", "MainCarScreen: Error playing", e)
                errorMessage = "Afspelen mislukt: ${e.message ?: "onbekende fout"}"
                invalidate()
            }
        }
    }
}
