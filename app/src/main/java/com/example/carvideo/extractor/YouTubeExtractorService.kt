package com.example.carvideo.extractor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap

/**
 * Private-use resolver for YouTube/SoundCloud streams.
 *
 * The goal is stability for a personal Android Auto/head-unit app:
 * - cache direct stream URLs for a short time;
 * - prefer car-friendly qualities instead of always the heaviest stream;
 * - retry once or twice when NewPipe/network calls fail;
 * - provide an audio-only fallback whenever video resolving fails.
 */
object YouTubeExtractorService {

    @Volatile
    private var initialized = false

    private data class Cached<T>(val value: T, val expiryMs: Long)

    private val audioUrlCache = ConcurrentHashMap<String, Cached<String>>()
    private val streamCache = ConcurrentHashMap<String, Cached<StreamResult>>()

    // YouTube direct URLs expire. Keep this shorter than the real expiry.
    private const val CACHE_TTL_MS = 4L * 60L * 60L * 1000L
    private const val MAX_VIDEO_HEIGHT = 720

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

    private suspend fun <T> retry(
        attempts: Int = 2,
        block: suspend () -> T
    ): T {
        var last: Throwable? = null
        repeat(attempts) { index ->
            try {
                return block()
            } catch (t: Throwable) {
                last = t
                if (index < attempts - 1) delay(350L * (index + 1))
            }
        }
        throw last ?: IllegalStateException("Onbekende extractor-fout")
    }

    private fun cacheValid(expiryMs: Long): Boolean = System.currentTimeMillis() < expiryMs

    private fun serviceForUrl(url: String) = ServiceList.all().firstOrNull { service ->
        try {
            service.getStreamExtractor(url) != null
        } catch (_: Throwable) {
            false
        }
    } ?: youtube

    private fun parseHeight(resolution: String?): Int {
        if (resolution.isNullOrBlank()) return 0
        return Regex("(\\d+)").find(resolution)?.value?.toIntOrNull() ?: 0
    }

    private fun isMp4(mime: String?): Boolean = mime?.contains("mp4", ignoreCase = true) == true

    /**
     * Best audio stream for queue playback. This should be fast and reliable.
     */
    suspend fun getAudioStreamUrl(videoUrl: String): String? {
        audioUrlCache[videoUrl]?.let { cached ->
            if (cacheValid(cached.expiryMs)) return cached.value
        }

        return withContext(Dispatchers.IO) {
            retry(attempts = 2) {
                init()
                val service = serviceForUrl(videoUrl)
                val info = StreamInfo.getInfo(service, videoUrl)

                val audio = info.audioStreams
                    .filter { it.content != null }
                    // Prefer normal/medium bitrates for stable car playback, but fall back to best.
                    .sortedWith(
                        compareByDescending<org.schabi.newpipe.extractor.stream.AudioStream> {
                            val br = it.averageBitrate
                            if (br in 96..256) 1 else 0
                        }.thenByDescending { it.averageBitrate }
                    )
                    .firstOrNull()
                    ?.content
                    ?: info.videoStreams
                        .filter { it.content != null }
                        .minByOrNull { parseHeight(it.resolution) }
                        ?.content

                audio?.also { url ->
                    audioUrlCache[videoUrl] = Cached(url, System.currentTimeMillis() + CACHE_TTL_MS)
                }
            }
        }
    }

    /**
     * Resolve a full video-capable stream result. Prefer muxed MP4 <=720p for a car screen.
     */
    suspend fun resolveUrl(videoUrl: String): StreamResult = withContext(Dispatchers.IO) {
        streamCache[videoUrl]?.let { cached ->
            if (cacheValid(cached.expiryMs)) return@withContext cached.value
        }

        retry(attempts = 2) {
            init()
            val service = serviceForUrl(videoUrl)
            val info = StreamInfo.getInfo(service, videoUrl)

            val muxed = info.videoStreams
                .filter { it.content != null && isMp4(it.format?.mimeType) }
                .sortedWith(
                    compareBy<org.schabi.newpipe.extractor.stream.VideoStream> {
                        val h = parseHeight(it.resolution)
                        if (h in 1..MAX_VIDEO_HEIGHT) 0 else 1
                    }.thenByDescending { parseHeight(it.resolution) }
                )
                .firstOrNull()

            val audioOnly = info.audioStreams
                .filter { it.content != null }
                .sortedWith(
                    compareByDescending<org.schabi.newpipe.extractor.stream.AudioStream> {
                        val br = it.averageBitrate
                        if (br in 96..256) 1 else 0
                    }.thenByDescending { it.averageBitrate }
                )
                .firstOrNull()

            val result = if (muxed?.content != null) {
                StreamResult(
                    title = info.name,
                    durationSeconds = info.duration,
                    videoStreamUrl = muxed.content,
                    audioStreamUrl = null,
                    hlsUrl = info.hlsUrl,
                    dashUrl = info.dashMpdUrl,
                    isMuxed = true,
                    thumbnailUrl = info.thumbnails.firstOrNull()?.url,
                    uploader = info.uploaderName,
                    originalUrl = videoUrl
                )
            } else {
                val videoOnly = info.videoOnlyStreams
                    .filter { it.content != null }
                    .sortedWith(
                        compareBy<org.schabi.newpipe.extractor.stream.VideoStream> {
                            val h = parseHeight(it.resolution)
                            if (h in 1..MAX_VIDEO_HEIGHT) 0 else 1
                        }.thenByDescending { parseHeight(it.resolution) }
                    )
                    .firstOrNull()

                StreamResult(
                    title = info.name,
                    durationSeconds = info.duration,
                    videoStreamUrl = videoOnly?.content,
                    audioStreamUrl = audioOnly?.content,
                    hlsUrl = info.hlsUrl,
                    dashUrl = info.dashMpdUrl,
                    isMuxed = false,
                    thumbnailUrl = info.thumbnails.firstOrNull()?.url,
                    uploader = info.uploaderName,
                    originalUrl = videoUrl
                )
            }

            streamCache[videoUrl] = Cached(result, System.currentTimeMillis() + CACHE_TTL_MS)
            audioOnly?.content?.let { audio ->
                audioUrlCache[videoUrl] = Cached(audio, System.currentTimeMillis() + CACHE_TTL_MS)
            }
            result
        }
    }

    suspend fun resolveSearch(query: String, serviceId: Int = 0): StreamResult? = withContext(Dispatchers.IO) {
        init()
        val first = search(query, 1, serviceId).firstOrNull() ?: return@withContext null
        resolveUrl(first.url)
    }

    /**
     * Zoekt een vergelijkbaar nummer op de andere service als de huidige faalt.
     * Dit zorgt voor de "Automatic Failover" tussen YouTube en SoundCloud.
     */
    suspend fun findMirror(
        title: String,
        uploader: String?,
        durationSeconds: Long,
        currentServiceId: Int
    ): StreamResult? = withContext(Dispatchers.IO) {
        val targetServiceId = if (currentServiceId == 0) 1 else 0
        // Clean title: remove common tags that hinder matching
        val cleanTitle = title
            .replace(Regex("(?i)\\(official.*?\\)"), "")
            .replace(Regex("(?i)\\[official.*?\\]"), "")
            .trim()
        
        val query = if (uploader != null) "$cleanTitle $uploader" else cleanTitle
        
        try {
            val results = search(query, limit = 5, serviceId = targetServiceId)
            
            // Zoek de beste match op basis van duur (marge van 20 seconden of 15%)
            val match = results.find { item ->
                val diff = Math.abs(item.durationSeconds - durationSeconds)
                diff < 20 || diff < (durationSeconds * 0.15)
            } ?: results.firstOrNull() // Fallback naar eerste resultaat
            
            match?.let { resolveUrl(it.url) }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun search(query: String, limit: Int = 20, serviceId: Int = 0): List<SearchResultItem> =
        withContext(Dispatchers.IO) {
            retry(attempts = 2) {
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
        }

    suspend fun getTrending(serviceId: Int = 0): List<SearchResultItem> = withContext(Dispatchers.IO) {
        retry(attempts = 2) {
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
}
