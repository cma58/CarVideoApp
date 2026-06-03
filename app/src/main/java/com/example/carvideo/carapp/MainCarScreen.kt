package com.example.carvideo.carapp

import android.util.Log
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
        Log.d("CarVideoApp", "MainCarScreen: onGetTemplate")
        val playlist = PlaybackState.playlist.value
        val listBuilder = ItemList.Builder()

        if (playlist.isEmpty()) {
            listBuilder.setNoItemsMessage("Laden...")
            lifecycleScope.launch {
                try {
                    val trending = YouTubeExtractorService.getTrending(0)
                    PlaybackState.setPlaylist(trending)
                    invalidate()
                } catch (e: Exception) {
                    Log.e("CarVideoApp", "MainCarScreen: Error loading trending", e)
                }
            }
        } else {
            playlist.take(10).forEach { item ->
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(item.title)
                        .addText(item.uploader ?: "YouTube")
                        .setOnClickListener {
                            Log.d("CarVideoApp", "MainCarScreen: Item clicked: ${item.title}")
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
            .setSingleList(listBuilder.build())
            .setActionStrip(actionStrip)
            .setHeaderAction(Action.APP_ICON)
            .setTitle("Ontdekken")
            .build()
    }

    private fun playAndNavigate(url: String) {
        lifecycleScope.launch {
            try {
                Log.d("CarVideoApp", "MainCarScreen: Resolving URL: $url")
                val stream = YouTubeExtractorService.resolveUrl(url)
                PlayerHolder.play(stream)
                Log.d("CarVideoApp", "MainCarScreen: Play successful, pushing VideoScreen")
                screenManager.push(VideoScreen(carContext))
            } catch (e: Exception) {
                Log.e("CarVideoApp", "MainCarScreen: Error playing", e)
            }
        }
    }
}
