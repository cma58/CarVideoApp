package com.example.carvideo.carapp

import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.Speed
import androidx.car.app.model.*
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.content.ContextCompat
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

    private var isMoving = false

    private val speedListener = OnCarDataAvailableListener<Speed> { speed ->
        val rawSpeed = speed.rawSpeedMetersPerSecond.value
        val newIsMoving = (rawSpeed ?: 0f) > 0.1f
        if (newIsMoving != isMoving) {
            isMoving = newIsMoving
            invalidate()
        }
    }

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

        try {
            val hardware = carContext.getCarService(CarHardwareManager::class.java)
            hardware.carInfo.addSpeedListener(ContextCompat.getMainExecutor(carContext), speedListener)
        } catch (e: Exception) {
            Log.w("CarVideoApp", "CarHardware not available: ${e.message}")
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        Log.d("CarVideoApp", "VideoScreen: onDestroy")
        PlayerHolder.detachSurface()
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
        
        try {
            val hardware = carContext.getCarService(CarHardwareManager::class.java)
            hardware.carInfo.removeSpeedListener(speedListener)
        } catch (_: Exception) {}
    }

    private fun icon(resId: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).build()

    override fun onGetTemplate(): Template {
        Log.d("CarVideoApp", "VideoScreen: onGetTemplate")

        // VEILIGHEID: Als de auto in beweging is, blokkeren we de video surface template
        if (isMoving) {
            return MessageTemplate.Builder("Video is geblokkeerd tijdens het rijden.")
                .setHeaderAction(Action.BACK)
                .setTitle("Veiligheid Eerst")
                .setIcon(icon(android.R.drawable.ic_dialog_alert))
                .build()
        }

        val playing = PlayerHolder.isPlaying()

        // Persistent ActionStrip for controls
        val actionStrip = ActionStrip.Builder()
            .addAction(Action.Builder()
                .setIcon(icon(android.R.drawable.ic_media_previous))
                .setOnClickListener {
                    PlayerHolder.skipPrevious { item ->
                        lifecycleScope.launch {
                            val stream = YouTubeExtractorService.resolveUrl(item.url)
                            PlayerHolder.play(stream)
                            invalidate()
                        }
                    }
                }.build())
            .addAction(Action.Builder()
                .setIcon(icon(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play))
                .setOnClickListener {
                    PlayerHolder.togglePlayPause()
                    invalidate()
                }.build())
            .addAction(Action.Builder()
                .setIcon(icon(android.R.drawable.ic_media_next))
                .setOnClickListener {
                    PlayerHolder.skipNext { item ->
                        lifecycleScope.launch {
                            val stream = YouTubeExtractorService.resolveUrl(item.url)
                            PlayerHolder.play(stream)
                            invalidate()
                        }
                    }
                }.build())
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .build()
    }
}
