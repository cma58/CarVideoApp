package com.example.carvideo.extractor

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response as NpResponse
import java.util.concurrent.TimeUnit

/**
 * NewPipeExtractor needs a concrete Downloader. This wraps OkHttp.
 * Singleton — NewPipe.init() should be called once with this instance.
 */
class OkHttpDownloader private constructor() : Downloader() {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(request: NpRequest): NpResponse {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBody = dataToSend?.toRequestBody()

        val builder = Request.Builder()
            .method(httpMethod, requestBody)
            .url(url)
            .addHeader("User-Agent", USER_AGENT)

        headers.forEach { (name, values) ->
            // Replace any default, then add each value
            builder.removeHeader(name)
            values.forEach { value -> builder.addHeader(name, value) }
        }

        client.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string()
            return NpResponse(
                response.code,
                response.message,
                response.headers.toMultimap(),
                body,
                response.request.url.toString()
            )
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        val instance: OkHttpDownloader by lazy { OkHttpDownloader() }
    }
}
