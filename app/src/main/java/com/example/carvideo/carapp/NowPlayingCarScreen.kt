package com.example.carvideo.carapp

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
import androidx.media3.common.util.UnstableApi
import com.example.carvideo.player.PlaybackState
import com.example.carvideo.player.PlayerHolder

@UnstableApi
class NowPlayingCarScreen(carContext: CarContext) : Screen(carContext) {

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

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Vorige")
                .addText("Speel vorige item")
                .setOnClickListener {
                    PlayerHolder.skipPrevious { }
                    invalidate()
                }
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
            .setOnClickListener {
                PlayerHolder.skipNext { }
                invalidate()
            }
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
}
