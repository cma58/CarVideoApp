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
class MainCarScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val playlist = PlaybackState.playlist.value
        val listBuilder = ItemList.Builder()

        if (playlist.isEmpty()) {
            listBuilder.setNoItemsMessage("Laden...")
            // Trigger a load if empty
            lifecycleScope.launch {
                val trending = YouTubeExtractorService.getTrending(0)
                PlaybackState.setPlaylist(trending)
                invalidate()
            }
        } else {
            playlist.take(10).forEach { item ->
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(item.title)
                        .addText(item.uploader ?: "YouTube")
                        .setOnClickListener {
                            playAndNavigate(item.url)
                        }
                        .build()
                )
            }
        }

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Zoek")
                    .setOnClickListener {
                        screenManager.push(SearchScreen(carContext))
                    }
                    .build()
            )
            .build()

        return ListTemplate.Builder()
            .setTitle("Car Video - Ontdekken")
            .setSingleList(listBuilder.build())
            .setActionStrip(actionStrip)
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun playAndNavigate(url: String) {
        lifecycleScope.launch {
            val stream = YouTubeExtractorService.resolveUrl(url)
            PlayerHolder.play(stream)
            screenManager.push(VideoScreen(carContext))
        }
    }
}
