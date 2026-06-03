package com.example.carvideo.carapp

import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.*
import androidx.car.app.navigation.model.NavigationTemplate
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
 * Uses NavigationTemplate to render video on the full background surface
 * and provides clear, accessible controls.
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

        // Big, easy-to-hit controls
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

        // Create an ActionStrip for secondary actions if needed, 
        // but put primary controls in the MapActionStrip for visibility.
        val actionStrip = ActionStrip.Builder()
            .addAction(Action.BACK)
            .build()

        // MapActionStrip is often rendered more prominently over the surface
        val mapActionStrip = ActionStrip.Builder()
            .addAction(prev)
            .addAction(playPause)
            .addAction(next)
            .build()

        return NavigationTemplate.Builder()
            .setMapActionStrip(mapActionStrip)
            .setActionStrip(actionStrip)
            .setBackgroundColor(CarColor.PRIMARY)
            .build()
    }
}
