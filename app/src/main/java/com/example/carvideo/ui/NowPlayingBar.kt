package com.example.carvideo.ui

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.carvideo.player.PlaybackState
import com.example.carvideo.player.PlayerHolder
import java.util.Locale

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingBar(
    nextUpTitle: String?,
    isLiked: Boolean,
    isFailover: Boolean = false,
    onLikeClick: () -> Unit,
    onNextClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onSeek: (Long) -> Unit,
    videoMode: Boolean,
    onToggleVideo: () -> Unit
) {
    val currentStream by PlaybackState.current.collectAsState()
    val stream = currentStream ?: return

    val isPlaying = PlayerHolder.isPlaying()
    val cornerSize by animateDpAsState(
        targetValue = if (isPlaying) 28.dp else 20.dp,
        animationSpec = tween(800),
        label = "morph"
    )
    
    var playingState by remember { mutableStateOf(isPlaying) }
    var showFullPlayer by remember { mutableStateOf(false) }

    if (showFullPlayer) {
        FullPlayerSheet(
            stream = stream,
            nextUpTitle = nextUpTitle,
            isLiked = isLiked,
            isFailover = isFailover,
            onDismiss = { showFullPlayer = false },
            onLikeClick = onLikeClick,
            onNextClick = onNextClick,
            onPreviousClick = onPreviousClick,
            onSeek = onSeek,
            videoMode = videoMode,
            onToggleVideo = onToggleVideo
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .height(72.dp)
            .clip(RoundedCornerShape(cornerSize))
            .graphicsLayer {
                shadowElevation = 8f
                shape = RoundedCornerShape(cornerSize)
                clip = true
            }
            .clickable(onClick = { showFullPlayer = true }),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
        tonalElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = stream.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = stream.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stream.uploader ?: "YouTube",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onLikeClick) {
                Icon(
                    if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    "Like",
                    tint = if (isLiked) MaterialTheme.colorScheme.primary else LocalContentColor.current
                )
            }

            IconButton(onClick = {
                val newState = PlayerHolder.togglePlayPause()
                playingState = newState
            }) {
                Icon(
                    if (playingState) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playingState) "Pause" else "Play",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerSheet(
    stream: com.example.carvideo.extractor.StreamResult,
    nextUpTitle: String?,
    isLiked: Boolean,
    isFailover: Boolean = false,
    onDismiss: () -> Unit,
    onLikeClick: () -> Unit,
    onNextClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onSeek: (Long) -> Unit,
    videoMode: Boolean,
    onToggleVideo: () -> Unit
) {
    val context = LocalContext.current
    val currentPos by PlayerHolder.currentPosition.collectAsState()
    val duration by PlayerHolder.duration.collectAsState()

    var dominantColor by remember { mutableStateOf(Color.DarkGray) }
    val animatedBackground by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(durationMillis = 1000),
        label = "bg"
    )

    LaunchedEffect(stream.thumbnailUrl) {
        if (stream.thumbnailUrl != null) {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(stream.thumbnailUrl)
                .allowHardware(false)
                .build()

            val result = loader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as BitmapDrawable).bitmap
                Palette.from(bitmap).generate { palette ->
                    palette?.dominantSwatch?.let {
                        dominantColor = Color(it.rgb)
                        PlaybackState.setDominantColor(it.rgb)
                    }
                }
            }
        } else {
            PlaybackState.setDominantColor(null)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            animatedBackground.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        ) {
            // SCROLLBAAR + video met begrensde hoogte: hierdoor blijven de
            // Vorige/Play/Volgende-knoppen altijd bereikbaar, ook op een
            // breed/laag autoscherm in landscape.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black)
                        .graphicsLayer { shadowElevation = 16f }
                ) {
                    if (videoMode) {
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = PlayerHolder.get()
                                    useController = false
                                }
                            },
                            onRelease = { playerView -> playerView.player = null },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AsyncImage(
                            model = stream.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    FilterChip(
                        selected = videoMode,
                        onClick = onToggleVideo,
                        label = { Text(if (videoMode) "MP4" else "MP3") },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                        )
                    )
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = stream.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = stream.uploader ?: "YouTube",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isFailover) {
                    Spacer(Modifier.height(8.dp))
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Failover: Bezig met herstellen...", style = MaterialTheme.typography.labelSmall) },
                        icon = { Icon(Icons.Default.Sync, null, modifier = Modifier.size(16.dp)) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            labelColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    )
                }

                Spacer(Modifier.height(24.dp))

                Column(Modifier.fillMaxWidth()) {
                    Slider(
                        value = if (duration > 0) currentPos.toFloat() / duration else 0f,
                        onValueChange = { onSeek((it * duration).toLong()) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(currentPos), style = MaterialTheme.typography.labelMedium)
                        Text(formatTime(duration), style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onLikeClick) {
                        Icon(
                            if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            "Like",
                            modifier = Modifier.size(36.dp),
                            tint = if (isLiked) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }

                    IconButton(onClick = onPreviousClick) {
                        Icon(Icons.Default.SkipPrevious, "Previous", modifier = Modifier.size(48.dp))
                    }

                    FilledIconButton(
                        onClick = { PlayerHolder.togglePlayPause() },
                        modifier = Modifier.size(80.dp)
                    ) {
                        Icon(
                            if (PlayerHolder.isPlaying()) Icons.Default.Pause else Icons.Default.PlayArrow,
                            null,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    IconButton(onClick = onNextClick) {
                        Icon(Icons.Default.SkipNext, "Skip", modifier = Modifier.size(48.dp))
                    }
                }

                if (nextUpTitle != null) {
                    Spacer(Modifier.height(24.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = 0.9f },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text(
                                "VOLGENDE",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                nextUpTitle,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
