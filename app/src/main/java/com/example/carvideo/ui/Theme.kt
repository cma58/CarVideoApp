package com.example.carvideo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.carvideo.player.PlaybackState

private val SpotifyDarkScheme = darkColorScheme(
    primary = Color(0xFF1DB954),
    onPrimary = Color(0xFF001F0B),
    primaryContainer = Color(0xFF0B3D1D),
    onPrimaryContainer = Color(0xFFB9F6CA),
    secondary = Color(0xFFB7F7C8),
    onSecondary = Color(0xFF062112),
    tertiary = Color(0xFF6EE7F9),
    background = Color(0xFF050505),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF101010),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF181818),
    onSurfaceVariant = Color(0xFFB8B8B8),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1E1E1E),
    outline = Color(0xFF3A3A3A),
    error = Color(0xFFFFB4AB)
)

private val SpotifyLightScheme = lightColorScheme(
    primary = Color(0xFF16883D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC7F8D6),
    onPrimaryContainer = Color(0xFF00210E),
    secondary = Color(0xFF3F6B4B),
    tertiary = Color(0xFF006879),
    background = Color(0xFFF8FAF7),
    onBackground = Color(0xFF111411),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111411),
    surfaceVariant = Color(0xFFE8ECE6),
    onSurfaceVariant = Color(0xFF444943),
    surfaceContainer = Color(0xFFF0F3EE),
    surfaceContainerHigh = Color(0xFFEAEDE8),
    outline = Color(0xFF747A72)
)

@Composable
fun CarVideoTheme(
    themeMode: Int = 0,
    content: @Composable () -> Unit
) {
    val dominantColorInt by PlaybackState.dominantColor.collectAsState()
    
    val darkTheme = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }

    val baseScheme = if (darkTheme) SpotifyDarkScheme else SpotifyLightScheme
    
    // Dynamic color depth logic
    val finalScheme = if (dominantColorInt != null) {
        val seedColor = Color(dominantColorInt!!)
        if (darkTheme) {
            baseScheme.copy(
                primary = seedColor,
                surfaceContainer = seedColor.copy(alpha = 0.1f),
                background = Color(0xFF050505) // Keep background dark for contrast
            )
        } else {
            baseScheme.copy(
                primary = seedColor,
                primaryContainer = seedColor.copy(alpha = 0.2f),
                background = seedColor.copy(alpha = 0.05f)
            )
        }
    } else {
        baseScheme
    }

    MaterialTheme(
        colorScheme = finalScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
