package com.armanmaurya.internetradio.core.media.player

import com.armanmaurya.internetradio.core.utils.AudioFormatUtils
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Removes attributes from HLS multivariant playlists that are valid enough for other players
 * but make Media3's HlsPlaylistParser throw.
 *
 * Recent Media3 versions (seen with 1.11.1) make `Format.Builder.setFrameRate` reject
 * non-positive values, and the HLS
 * parser forwards the raw `FRAME-RATE` attribute to it. Some encoders (e.g. audio-only radio
 * streams published through a video pipeline) advertise `FRAME-RATE=0.000`, which then fails
 * with an IllegalArgumentException and is retried forever. Dropping the attribute is safe: it
 * is optional and Media3 falls back to "unknown frame rate".
 */
object HlsManifestSanitizer {

    private const val STREAM_INF = "#EXT-X-STREAM-INF:"
    private const val I_FRAME_STREAM_INF = "#EXT-X-I-FRAME-STREAM-INF:"

    private const val ZERO = """0+(?:\.0*)?"""
    private val zeroFrameRateAfterComma = Regex(""",FRAME-RATE=$ZERO(?=,|\r?$)""")
    private val zeroFrameRateFirst = Regex("""(?<=:)FRAME-RATE=$ZERO(?:,|(?=\r?$))""")

    /** Returns [manifest] without non-positive `FRAME-RATE` attributes; unchanged otherwise. */
    fun sanitize(manifest: String): String {
        if (!manifest.contains("FRAME-RATE=")) return manifest
        return manifest.split("\n").joinToString("\n") { line ->
            if (line.startsWith(STREAM_INF) || line.startsWith(I_FRAME_STREAM_INF)) {
                line.replace(zeroFrameRateAfterComma, "").replace(zeroFrameRateFirst, "")
            } else {
                line
            }
        }
    }
}

/**
 * Application interceptor that applies [HlsManifestSanitizer] to HLS playlist responses before
 * they reach ExoPlayer. Non-HLS responses and playlists that need no change pass through
 * untouched (the body is only peeked, never consumed).
 */
class HlsManifestSanitizerInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (!response.isSuccessful) return response

        val contentType = response.header("Content-Type", "") ?: ""
        val path = response.request.url.encodedPath
        val isHlsPlaylist = path.endsWith(".m3u8", ignoreCase = true) ||
                (AudioFormatUtils.isHlsContentType(contentType) && !path.endsWith(".m3u", ignoreCase = true))
        if (!isHlsPlaylist) return response

        val peeked = try {
            response.peekBody(MAX_MANIFEST_BYTES)
        } catch (_: Exception) {
            return response
        }
        // A manifest that fills the whole peek window may be truncated; leave it alone.
        if (peeked.contentLength() >= MAX_MANIFEST_BYTES) return response

        val original = peeked.string()
        val sanitized = HlsManifestSanitizer.sanitize(original)
        if (sanitized == original) return response

        val mediaType = response.body.contentType()
        response.close()
        return response.newBuilder()
            .removeHeader("Content-Length")
            .body(sanitized.toResponseBody(mediaType))
            .build()
    }

    private companion object {
        const val MAX_MANIFEST_BYTES = 1024L * 1024L
    }
}
