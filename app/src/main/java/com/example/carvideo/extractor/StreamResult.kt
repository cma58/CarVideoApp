package com.example.carvideo.extractor

/**
 * Result of resolving a YouTube URL/search term into playable stream URLs.
 *
 * NewPipe returns separate video-only and audio-only streams for higher
 * resolutions (DASH). ExoPlayer can merge them via MergingMediaSource, or you
 * can use a muxed (combined) stream when available at lower quality.
 */
data class StreamResult(
    val title: String,
    val durationSeconds: Long,
    /** Video-only or muxed stream (.mp4 / .webm). */
    val videoStreamUrl: String?,
    /** Audio-only stream (.m4a / .webm). Null if only a muxed stream is used. */
    val audioStreamUrl: String?,
    /** Manifest for adaptive streaming (SoundCloud / YouTube HLS). */
    val hlsUrl: String? = null,
    /** Manifest for adaptive streaming (YouTube DASH). */
    val dashUrl: String? = null,
    /** True when videoStreamUrl already contains audio (no merging needed). */
    val isMuxed: Boolean,
    val thumbnailUrl: String?,
    val uploader: String? = null,
    /** The original YouTube/SoundCloud URL. */
    val originalUrl: String? = null
)
