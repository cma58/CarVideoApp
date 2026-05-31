package com.example.carvideo.extractor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Resolves a YouTube video URL or search term into directly playable stream URLs.
 *
 * NOTE: NewPipeExtractor scrapes YouTube's web pages. This violates YouTube's
 * Terms of Service and breaks whenever YouTube changes its internals — pin the
 * library version and expect to update it periodically. Intended for personal use.
 *
 * All calls are network-bound and MUST run off the main thread (handled here
 * via Dispatchers.IO).
 */
object YouTubeExtractorService {

    @Volatile
    private var initialized = false

    /** Call once before any extraction (e.g. from Application.onCreate). */
    fun init() {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                NewPipe.init(OkHttpDownloader.instance)
                initialized = true
            }
        }
    }

    private val youtube get() = ServiceList.YouTube

    /**
     * Resolve a direct YouTube video URL into stream URLs.
     */
    suspend fun resolveUrl(videoUrl: String): StreamResult = withContext(Dispatchers.IO) {
        init()
        val info: StreamInfo = StreamInfo.getInfo(youtube, videoUrl)

        // Prefer a muxed video stream (has audio) for simplicity; fall back to
        // separate video + audio streams (DASH) for higher quality.
        val muxed = info.videoStreams
            .filter { it.url != null }
            .maxByOrNull { it.getResolution()?.removeSuffix("p")?.toIntOrNull() ?: 0 }

        if (muxed?.url != null) {
            return@withContext StreamResult(
                title = info.name,
                durationSeconds = info.duration,
                videoStreamUrl = muxed.url,
                audioStreamUrl = null,
                isMuxed = true,
                thumbnailUrl = info.thumbnails.firstOrNull()?.url
            )
        }

        // Video-only + audio-only (merge in ExoPlayer)
        val videoOnly = info.videoOnlyStreams
            .filter { it.url != null }
            .maxByOrNull { it.getResolution()?.removeSuffix("p")?.toIntOrNull() ?: 0 }
        val audioOnly = info.audioStreams
            .filter { it.url != null }
            .maxByOrNull { it.averageBitrate }

        StreamResult(
            title = info.name,
            durationSeconds = info.duration,
            videoStreamUrl = videoOnly?.url,
            audioStreamUrl = audioOnly?.url,
            isMuxed = false,
            thumbnailUrl = info.thumbnails.firstOrNull()?.url
        )
    }

    /**
     * Search YouTube and return the first matching video's stream URLs.
     */
    suspend fun resolveSearch(query: String): StreamResult? = withContext(Dispatchers.IO) {
        init()
        val searchInfo = SearchInfo.getInfo(
            youtube,
            youtube.searchQHFactory.fromQuery(query)
        )
        val firstVideo = searchInfo.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .firstOrNull { it.url != null }
            ?: return@withContext null

        resolveUrl(firstVideo.url)
    }
}
