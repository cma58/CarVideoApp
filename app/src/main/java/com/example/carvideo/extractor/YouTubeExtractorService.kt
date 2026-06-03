package com.example.carvideo.extractor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves a YouTube/SoundCloud URL or search term into directly playable stream URLs.
 * NewPipe v0.26.1, Localization.DEFAULT, en .content voor stream-URLs.
 *
 * SAMENGEVOEGD UIT YTAuto: een stream-URL-cache met TTL. YouTube-stream-URL's zijn
 * ~6 uur geldig; we cachen ze 5 uur zodat we niet bij elke play opnieuw hoeven te
 * resolven. Dit maakt het laden van een wachtrij veel sneller.
 */
object YouTubeExtractorService {

    @Volatile
    private var initialized = false

    // videoUrl -> Pair(streamUrl, expiryTimeMs). Thread-safe want we resolven parallel.
    private val audioUrlCache = ConcurrentHashMap<String, Pair<String, Long>>()
    private const val CACHE_TTL_MS = 5L * 60L * 60L * 1000L // 5 uur

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
    private val soundcloud get() = ServiceList.SoundCloud

    /**
     * Beste AUDIO-stream-URL (een afspeelbare URI), met cache.
     * Dit is het betrouwbare pad voor de wachtrij: een URI per item, werkt op de
     * achtergrond en laat ExoPlayer een echte timeline opbouwen (next/prev).
     */
    suspend fun getAudioStreamUrl(videoUrl: String): String? {
        // 1) Cache-hit?
        audioUrlCache[videoUrl]?.let { (url, expiry) ->
            if (System.currentTimeMillis() < expiry) return url
        }
        // 2) Anders resolven en cachen.
        return withContext(Dispatchers.IO) {
            init()
            try {
                val service = ServiceList.all().firstOrNull {
                    try { it.getStreamExtractor(videoUrl) != null } catch (e: Exception) { false }
                } ?: youtube
                val info = StreamInfo.getInfo(service, videoUrl)

                val best = info.audioStreams
                    .filter { it.content != null }
                    .maxByOrNull { it.averageBitrate }
                    ?.content
                    ?: info.videoStreams
                        .filter { it.content != null }
                        .firstOrNull()
                        ?.content

                best?.also { url ->
                    audioUrlCache[videoUrl] = url to (System.currentTimeMillis() + CACHE_TTL_MS)
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun resolveUrl(videoUrl: String): StreamResult = withContext(Dispatchers.IO) {
        init()
        val service = ServiceList.all().firstOrNull {
            try { it.getStreamExtractor(videoUrl) != null } catch (e: Exception) { false }
        } ?: youtube

        val info: StreamInfo = StreamInfo.getInfo(service, videoUrl)

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
                thumbnailUrl = info.thumbnails.firstOrNull()?.url,
                uploader = info.uploaderName,
                originalUrl = videoUrl
            )
        }

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
            thumbnailUrl = info.thumbnails.firstOrNull()?.url,
            uploader = info.uploaderName,
            originalUrl = videoUrl
        )
    }

    suspend fun resolveSearch(query: String, serviceId: Int = 0): StreamResult? = withContext(Dispatchers.IO) {
        init()
        val first = search(query, 1, serviceId).firstOrNull() ?: return@withContext null
        resolveUrl(first.url)
    }

    suspend fun search(query: String, limit: Int = 20, serviceId: Int = 0): List<SearchResultItem> =
        withContext(Dispatchers.IO) {
            init()
            val service = if (serviceId == 1) soundcloud else youtube
            val searchInfo = SearchInfo.getInfo(service, service.searchQHFactory.fromQuery(query))
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

    suspend fun getTrending(serviceId: Int = 0): List<SearchResultItem> = withContext(Dispatchers.IO) {
        init()
        val service = if (serviceId == 1) soundcloud else youtube
        val kiosk = service.kioskList.getDefaultKioskId()
        val info = org.schabi.newpipe.extractor.kiosk.KioskInfo.getInfo(service, kiosk)
        info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .take(20)
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
