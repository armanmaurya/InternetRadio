package com.armanmaurya.internetradio.core.media.prober

import com.armanmaurya.internetradio.core.media.prober.engine.HlsEngine
import com.armanmaurya.internetradio.core.media.prober.engine.IcyEngine
import com.armanmaurya.internetradio.core.media.prober.parser.PlaylistResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NativeProber {

    private const val PROBE_TIMEOUT_MS = 6000L

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun probe(url: String): ProbeResult? = withContext(Dispatchers.IO) {
        if (!url.startsWith("http", ignoreCase = true)) return@withContext null

        try {
            withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                // 1. Resolve playlist URL if needed (e.g. .pls, .m3u, .asx)
                val resolved = PlaylistResolver.resolve(url, httpClient)
                val targetUrl = resolved?.streamUrl ?: url
                val initialName = resolved?.name

                // 2. Delegate to HlsEngine or IcyEngine
                if (HlsEngine.isHlsOrDash(targetUrl)) {
                    HlsEngine.probe(targetUrl, initialName, httpClient)
                } else {
                    IcyEngine.probe(targetUrl, initialName, httpClient)
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
