package com.example.carvideo.carapp

import android.util.Log
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

@UnstableApi
class VideoScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            Log.d("CarVideoApp", "VideoScreen: onSurfaceAvailable")
            surfaceContainer.surface?.let { 
                PlayerHolder.attachSurface(it) 
                invalidate()
            }
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            Log.d("CarVideoApp", "VideoScreen: onSurfaceDestroyed")
            PlayerHolder.detachSurface()
        }
    }

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        Log.d("CarVideoApp", "VideoScreen: onCreate")
        carContext.getCarService(AppManager::class.java)
            .setSurfaceCallback(surfaceCallback)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        Log.d("CarVideoApp", "VideoScreen: onDestroy")
        PlayerHolder.detachSurface()
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
    }

    private fun icon(resId: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).build()

    override fun onGetTemplate(): Template {
        Log.d("CarVideoApp", "VideoScreen: onGetTemplate")
        val playing = PlayerHolder.isPlaying()
        val current = PlaybackState.current.value

        // Standard Transport Controls
        val playPause = Action.Builder()
            .setIcon(icon(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play))
            .setOnClickListener {
                Log.d("CarVideoApp", "VideoScreen: Play/Pause clicked")
                PlayerHolder.togglePlayPause()
                invalidate()
            }
            .build()

        val next = Action.Builder()
            .setIcon(icon(android.R.drawable.ic_media_next))
            .setOnClickListener {
                Log.d("CarVideoApp", "VideoScreen: Next clicked")
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
                Log.d("CarVideoApp", "VideoScreen: Prev clicked")
                PlayerHolder.skipPrevious { item ->
                    lifecycleScope.launch {
                        val stream = YouTubeExtractorService.resolveUrl(item.url)
                        PlayerHolder.play(stream)
                        invalidate()
                    }
                }
            }
            .build()

        // Persistent ActionStrip for controls
        val actionStrip = ActionStrip.Builder()
            .addAction(prev)
            .addAction(playPause)
            .addAction(next)
            .build()

        // For VIDEO category apps, NavigationTemplate is used to claim the surface.
        // It provides a full-screen background for our ExoPlayer.
        return NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .build()
    }
}
