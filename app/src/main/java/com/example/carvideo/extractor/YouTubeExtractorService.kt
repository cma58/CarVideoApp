package com.example.carvideo.extractor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Resolves a YouTube video URL or search term into directly playable stream URLs.
 * Gebruikt de werkende aanpak: NewPipe v0.26.1, Localization.DEFAULT, en .content
 * voor stream-URLs (i.p.v. .url).
 */
object YouTubeExtractorService {

    @Volatile
    private var initialized = false

    fun init() {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                NewPipe.init(OkHttpDownloader.instance, Localization.DEFAULT)
                initialized = true
            }
        }
    }

    private val youtube get() = ServiceList.YouTube

    suspend fun resolveUrl(videoUrl: String): StreamResult = withContext(Dispatchers.IO) {
        init()
        val info: StreamInfo = StreamInfo.getInfo(youtube, videoUrl)

        // Muxed video (heeft audio) op de hoogste resolutie via .content
        val muxed = info.videoStreams
            .filter { it.content != null && it.format?.mimeType?.contains("mp4") == true }
            .maxByOrNull { it.resolution?.removeSuffix("p")?.toIntOrNull() ?: 0 }

        if (muxed?.content != null) {
            return@withContext StreamResult(
                title = info.name,
                durationSeconds = info.duration,
                videoStreamUrl = muxed.content,
                audioStreamUrl = null,
                isMuxed = true,
                thumbnailUrl = info.thumbnails.firstOrNull()?.url
            )
        }

        // Anders video-only + audio-only mergen
        val videoOnly = info.videoOnlyStreams
            .filter { it.content != null }
            .maxByOrNull { it.resolution?.removeSuffix("p")?.toIntOrNull() ?: 0 }
        val audioOnly = info.audioStreams
            .filter { it.content != null }
            .maxByOrNull { it.averageBitrate }

        StreamResult(
            title = info.name,
            durationSeconds = info.duration,
            videoStreamUrl = videoOnly?.content,
            audioStreamUrl = audioOnly?.content,
            isMuxed = false,
            thumbnailUrl = info.thumbnails.firstOrNull()?.url
        )
    }

    suspend fun resolveSearch(query: String): StreamResult? = withContext(Dispatchers.IO) {
        init()
        val first = search(query, 1).firstOrNull() ?: return@withContext null
        resolveUrl(first.url)
    }

    suspend fun search(query: String, limit: Int = 20): List<SearchResultItem> =
        withContext(Dispatchers.IO) {
            init()
            val searchInfo = SearchInfo.getInfo(youtube, youtube.searchQHFactory.fromQuery(query))
            searchInfo.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .filter { it.url != null }
                .take(limit)
                .map { item ->
                    SearchResultItem(
                        title = item.name,
                        url = item.url,
                        uploader = item.uploaderName,
                        durationSeconds = item.duration,
                        thumbnailUrl = item.thumbnails.firstOrNull()?.url
                    )
                }
        }
}
