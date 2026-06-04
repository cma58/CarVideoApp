package com.example.carvideo.carapp

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.example.carvideo.extractor.SearchResultItem
import com.example.carvideo.extractor.YouTubeExtractorService
import com.example.carvideo.player.PlaybackState
import com.example.carvideo.player.PlayerHolder
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@UnstableApi
class NowPlayingCarScreen(carContext: CarContext) : Screen(carContext) {

    private var loadingMessage: String? = null
    private var errorMessage: String? = null

    private fun icon(resId: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).build()

    override fun onGetTemplate(): Template {
        val current = PlaybackState.current.value
        val playlist = PlaybackState.playlist.value
        val listBuilder = ItemList.Builder()

        if (current == null) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Nog niets gestart")
                    .addText("Zoek of start eerst een liedje")
                    .build()
            )
        } else {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(current.title.ifBlank { "Onbekende titel" })
                    .addText(current.uploader ?: "YouTube")
                    .addText(if (PlayerHolder.isPlaying()) "Wordt nu afgespeeld" else "Gepauzeerd")
                    .build()
            )
        }

        loadingMessage?.let {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Laden...")
                    .addText(it)
                    .build()
            )
        }

        errorMessage?.let {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Fout")
                    .addText(it)
                    .build()
            )
        }

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Vorige")
                .addText("Speel vorige item")
                .setOnClickListener { playPrevious() }
                .build()
        )

        if (playlist.isNotEmpty()) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Wachtrij")
                    .addText("${playlist.size} items")
                    .build()
            )

            playlist.take(8).forEach { item ->
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(item.title.ifBlank { "Onbekende titel" })
                        .addText(item.uploader ?: "YouTube")
                        .setOnClickListener { playItem(item) }
                        .build()
                )
            }
        }

        val playPause = Action.Builder()
            .setIcon(icon(if (PlayerHolder.isPlaying()) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play))
            .setOnClickListener {
                PlayerHolder.togglePlayPause()
                invalidate()
            }
            .build()

        val next = Action.Builder()
            .setIcon(icon(android.R.drawable.ic_media_next))
            .setOnClickListener { playNext() }
            .build()

        val actionStrip = ActionStrip.Builder()
            .addAction(playPause)
            .addAction(next)
            .build()

        return ListTemplate.Builder()
            .setTitle("Wordt nu afgespeeld")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .setActionStrip(actionStrip)
            .build()
    }

    private fun playNext() {
        val list = PlaybackState.playlist.value
        val currentUrl = PlaybackState.current.value?.originalUrl
        val index = list.indexOfFirst { it.url == currentUrl }
        val next = if (index >= 0 && index < list.lastIndex) list[index + 1] else list.firstOrNull()
        if (next != null) playItem(next)
    }

    private fun playPrevious() {
        val list = PlaybackState.playlist.value
        val currentUrl = PlaybackState.current.value?.originalUrl
        val index = list.indexOfFirst { it.url == currentUrl }
        val previous = if (index > 0) list[index - 1] else list.lastOrNull()
        if (previous != null) playItem(previous)
    }

    private fun playItem(item: SearchResultItem) {
        loadingMessage = item.title
        errorMessage = null
        invalidate()

        lifecycleScope.launch {
            try {
                val stream = withTimeoutOrNull(15_000L) {
                    YouTubeExtractorService.resolveUrl(item.url)
                }

                if (stream == null) {
                    errorMessage = "Stream laden duurde te lang."
                    return@launch
                }

                PlayerHolder.play(stream)
            } catch (e: Exception) {
                Log.e("CarVideoApp", "NowPlayingCarScreen: playItem failed", e)
                errorMessage = "Afspelen mislukt"
            } finally {
                loadingMessage = null
                invalidate()
            }
        }
    }
}
