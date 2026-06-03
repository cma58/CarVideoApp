package com.example.carvideo.carapp

import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.*
import androidx.car.app.navigation.model.PlaceListNavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.example.carvideo.extractor.YouTubeExtractorService
import com.example.carvideo.player.PlaybackState
import com.example.carvideo.player.PlayerHolder
import kotlinx.coroutines.launch

/**
 * Integrated Player Screen for Android Auto.
 * Shows the video on the full background surface and provides a sidebar
 * with the current title and upcoming playlist items.
 */
@UnstableApi
class VideoScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            surfaceContainer.surface?.let { PlayerHolder.attachSurface(it) }
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            PlayerHolder.detachSurface()
        }
    }

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        carContext.getCarService(AppManager::class.java)
            .setSurfaceCallback(surfaceCallback)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        PlayerHolder.detachSurface()
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
    }

    private fun icon(resId: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).build()

    override fun onGetTemplate(): Template {
        val playing = PlayerHolder.isPlaying()
        val current = PlaybackState.current.value
        val playlist = PlaybackState.playlist.value

        // Primary transport controls in the ActionStrip (Floating)
        val playPause = Action.Builder()
            .setIcon(icon(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play))
            .setOnClickListener {
                PlayerHolder.togglePlayPause()
                invalidate()
            }
            .build()

        val next = Action.Builder()
            .setIcon(icon(android.R.drawable.ic_media_next))
            .setOnClickListener {
                PlayerHolder.skipNext { item ->
                    lifecycleScope.launch {
                        val stream = YouTubeExtractorService.resolveUrl(item.url)
                        PlayerHolder.play(stream)
                        invalidate()
                    }
                }
            }
            .build()

        val prev = Action.Builder()
            .setIcon(icon(android.R.drawable.ic_media_previous))
            .setOnClickListener {
                PlayerHolder.skipPrevious { item ->
                    lifecycleScope.launch {
                        val stream = YouTubeExtractorService.resolveUrl(item.url)
                        PlayerHolder.play(stream)
                        invalidate()
                    }
                }
            }
            .build()

        val actionStrip = ActionStrip.Builder()
            .addAction(prev)
            .addAction(playPause)
            .addAction(next)
            .build()

        // Sidebar list for "Next Up"
        val listBuilder = ItemList.Builder()
        val currentIndex = playlist.indexOfFirst { it.url == current?.originalUrl }
        val upcoming = if (currentIndex != -1) playlist.drop(currentIndex + 1) else playlist
        
        if (upcoming.isEmpty()) {
            listBuilder.setNoItemsMessage("Geen andere video's")
        } else {
            upcoming.take(5).forEach { item ->
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(item.title)
                        .addText(item.uploader ?: "YouTube")
                        .setOnClickListener {
                            lifecycleScope.launch {
                                val stream = YouTubeExtractorService.resolveUrl(item.url)
                                PlayerHolder.play(stream)
                                invalidate()
                            }
                        }
                        .build()
                )
            }
        }

        // PlaceListNavigationTemplate allows background surface (video) + UI overlay
        @Suppress("DEPRECATION")
        val builder = PlaceListNavigationTemplate.Builder()
            .setTitle(current?.title ?: "Car Video")
            .setHeaderAction(Action.BACK)
            .setActionStrip(actionStrip)
            .setItemList(listBuilder.build())

        return builder.build()
    }
}
