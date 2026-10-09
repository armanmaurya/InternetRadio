package com.armanmaurya.internetradio.core.media.prober.engine

import android.util.Log
import com.armanmaurya.internetradio.core.media.prober.ProbeResult
import com.armanmaurya.internetradio.core.media.prober.parser.AudioFrameParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI

internal object HlsEngine {

    private const val TAG = "HlsEngine"

    fun isHlsOrDash(url: String, contentType: String? = null): Boolean {
        if (url.contains(".m3u8", ignoreCase = true) || url.contains(".mpd", ignoreCase = true)) return true
        val ct = contentType?.lowercase() ?: ""
        return ct.contains("vnd.apple.mpegurl") ||
                ct.contains("dash+xml") ||
                (ct.contains("mpegurl") && !ct.contains("scpls"))
    }

    suspend fun probe(
        url: String,
        initialName: String? = null,
        client: OkHttpClient
    ): ProbeResult? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android) InternetRadio")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyString = response.body?.string() ?: return null
                if (url.contains(".mpd", ignoreCase = true) || response.header("Content-Type")?.contains("dash+xml") == true) {
                    parseDashContent(bodyString, initialName)
                } else {
                    parseHlsContent(bodyString, url, initialName, client)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error probing HLS/DASH $url: ${e.message}")
            null
        }
    }

    internal fun parseDashContent(content: String, initialName: String? = null): ProbeResult {
        var detectedBitrate = 0
        var detectedCodec = ""

        val bwMatch = Regex("""bandwidth="(\d+)"""", RegexOption.IGNORE_CASE).find(content)
        if (bwMatch != null) {
            val bps = bwMatch.groupValues[1].toIntOrNull() ?: 0
            if (bps > 0) detectedBitrate = bps / 1000
        }

        val codecsMatch = Regex("""codecs="([^"]+)"""", RegexOption.IGNORE_CASE).find(content)
        if (codecsMatch != null) {
            val c = codecsMatch.groupValues[1].lowercase()
            detectedCodec = when {
                c.contains("mp4a") || c.contains("aac") -> "AAC"
                c.contains("mp3") -> "MP3"
                c.contains("flac") -> "FLAC"
                c.contains("opus") -> "OPUS"
                else -> ""
            }
        }

        return ProbeResult(
            codec = if (detectedCodec.isNotBlank()) detectedCodec else "AAC",
            bitrate = detectedBitrate,
            name = initialName
        )
    }

    internal fun parseHlsContent(
        content: String,
        baseUrl: String,
        initialName: String? = null,
        client: OkHttpClient? = null
    ): ProbeResult {
        var detectedCodec = ""
        var detectedBitrate = 0

        // 1. Extract BANDWIDTH from master playlist
        val bandwidthMatch = Regex("""(?i)BANDWIDTH=(\d+)""").find(content)
        if (bandwidthMatch != null) {
            val rawBps = bandwidthMatch.groupValues[1].toIntOrNull() ?: 0
            if (rawBps > 0) {
                detectedBitrate = rawBps / 1000
            }
        }

        // 2. Extract CODECS from master playlist
        val codecsMatch = Regex("""(?i)CODECS="([^"]+)"""").find(content)
        if (codecsMatch != null) {
            val c = codecsMatch.groupValues[1].lowercase()
            detectedCodec = when {
                c.contains("mp4a") || c.contains("aac") -> "AAC"
                c.contains("mp3") -> "MP3"
                c.contains("flac") -> "FLAC"
                c.contains("opus") -> "OPUS"
                else -> ""
            }
        }

        // 3. Fallback: Parse bitrate from URL patterns (e.g. _b128000.m3u8, audio=128000, _128k)
        if (detectedBitrate == 0) {
            val urlBpsMatch = Regex("""(?i)(?:audio(?:=|%3[Dd])|_b)(\d+)""").find(baseUrl)
                ?: Regex("""(?i)(?:audio(?:=|%3[Dd])|_b)(\d+)""").find(content)
            if (urlBpsMatch != null) {
                val bps = urlBpsMatch.groupValues[1].toIntOrNull() ?: 0
                detectedBitrate = if (bps > 1000) bps / 1000 else bps
            }
            if (detectedBitrate == 0) {
                val kMatch = Regex("""_(\d+)k(?:\.m3u8|\b)""", RegexOption.IGNORE_CASE).find(baseUrl)
                if (kMatch != null) {
                    detectedBitrate = kMatch.groupValues[1].toIntOrNull() ?: 0
                }
            }
        }

        // 4. If codec or bitrate missing, probe the first audio segment chunk if client available
        if ((detectedCodec.isBlank() || detectedBitrate == 0) && client != null) {
            val firstSegmentUrl = extractFirstSegmentUrl(content, baseUrl)
            if (firstSegmentUrl != null) {
                try {
                    val segReq = Request.Builder()
                        .url(firstSegmentUrl)
                        .header("Range", "bytes=0-8192")
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android) InternetRadio")
                        .build()

                    client.newCall(segReq).execute().use { segResp ->
                        if (segResp.isSuccessful) {
                            val segBytes = segResp.body?.bytes()
                            if (segBytes != null && segBytes.isNotEmpty()) {
                                val parseResult = AudioFrameParser.parse(segBytes)
                                if (detectedCodec.isBlank() && parseResult.codec.isNotBlank()) {
                                    detectedCodec = parseResult.codec
                                }
                                if (detectedBitrate == 0 && parseResult.bitrate > 0) {
                                    detectedBitrate = parseResult.bitrate
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // 5. Codec heuristics fallback
        if (detectedCodec.isBlank()) {
            detectedCodec = when {
                content.contains(".aac", ignoreCase = true) || baseUrl.contains("aac", ignoreCase = true) -> "AAC"
                content.contains(".mp3", ignoreCase = true) || baseUrl.contains("mp3", ignoreCase = true) -> "MP3"
                content.contains(".ts", ignoreCase = true) -> "AAC"
                else -> "AAC" // Standard default for HLS audio streams
            }
        }

        return ProbeResult(
            codec = detectedCodec.uppercase(),
            bitrate = detectedBitrate,
            name = initialName
        )
    }

    private fun extractFirstSegmentUrl(content: String, baseUrl: String): String? {
        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                return try {
                    if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                        trimmed
                    } else {
                        URI(baseUrl).resolve(trimmed).toString()
                    }
                } catch (_: Exception) {
                    trimmed
                }
            }
        }
        return null
    }
}
