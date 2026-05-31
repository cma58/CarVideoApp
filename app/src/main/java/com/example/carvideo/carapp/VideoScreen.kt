package com.example.carvideo.carapp

import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
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
 * Car screen (Android Auto / API 37): claims the Surface for video and exposes
 * play/pause controls plus a way to load a video by search term.
 *
 * Surface lifecycle:
 *  - onSurfaceAvailable -> attach -> video on car screen
 *  - onSurfaceDestroyed -> detach -> video stops, audio keeps playing
 */
@UnstableApi
class VideoScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    // Demo query; wire to voice/search input in a real build.
    private var lastQuery: String = "lofi hip hop"

    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            surfaceContainer.surface?.let { PlayerHolder.attachSurface(it) }
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            PlayerHolder.detachSurface()
        }

        override fun onVisibleAreaChanged(visibleArea: android.graphics.Rect) {}
        override fun onStableAreaChanged(stableArea: android.graphics.Rect) {}
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

    private fun loadAndPlay(query: String) {
        lifecycleScope.launch {
            try {
                val result = YouTubeExtractorService.resolveSearch(query)
                if (result == null) {
                    CarToast.makeText(carContext, "Geen resultaat", CarToast.LENGTH_SHORT).show()
                } else {
                    PlayerHolder.play(result)
                    invalidate()
                }
            } catch (e: Exception) {
                CarToast.makeText(carContext, "Fout: ${e.message}", CarToast.LENGTH_LONG).show()
            }
        }
    }

    private fun icon(resId: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).build()

    override fun onGetTemplate(): Template {
        val playing = PlayerHolder.isPlaying()
        val current = PlaybackState.current.value

        val playPause = Action.Builder()
            .setIcon(
                icon(
                    if (playing) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play
                )
            )
            .setOnClickListener {
                PlayerHolder.togglePlayPause()
                invalidate()
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

        val actionStrip = ActionStrip.Builder()
            .addAction(prev)
            .addAction(playPause)
            .addAction(next)
            .build()

        return NavigationTemplate.Builder()
            .setBackgroundColor(CarColor.PRIMARY)
            .setActionStrip(actionStrip)
            .build()
    }
}
