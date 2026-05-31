package com.example.carvideo

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.example.carvideo.databinding.ActivityMainBinding
import com.example.carvideo.extractor.YouTubeExtractorService
import com.example.carvideo.player.PlayerHolder
import kotlinx.coroutines.launch

/**
 * Minimal phone UI: enter a YouTube URL or search term, resolve it, and start
 * playback on the shared player. On the car screen, VideoScreen will attach the
 * Surface automatically. On the phone, audio plays via the MediaSession.
 */
@UnstableApi
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ensure the playback service (and shared player) is created.
        startService(
            android.content.Intent(this, com.example.carvideo.player.PlaybackService::class.java)
        )

        binding.playButton.setOnClickListener {
            val input = binding.inputField.text.toString().trim()
            if (input.isEmpty()) {
                Toast.makeText(this, "Voer een URL of zoekterm in", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            resolveAndPlay(input)
        }
    }

    private fun resolveAndPlay(input: String) {
        binding.playButton.isEnabled = false
        lifecycleScope.launch {
            try {
                val result = if (input.startsWith("http")) {
                    YouTubeExtractorService.resolveUrl(input)
                } else {
                    YouTubeExtractorService.resolveSearch(input)
                }
                if (result == null) {
                    Toast.makeText(this@MainActivity, "Geen resultaat", Toast.LENGTH_SHORT).show()
                } else {
                    PlayerHolder.play(result)
                    Toast.makeText(this@MainActivity, "Speelt: ${result.title}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Fout: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.playButton.isEnabled = true
            }
        }
    }
}
