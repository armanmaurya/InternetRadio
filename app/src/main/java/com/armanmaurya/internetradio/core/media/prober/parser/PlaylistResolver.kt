package com.armanmaurya.internetradio.core.media.prober.parser

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI

internal data class ResolvedPlaylist(
    val streamUrl: String,
    val name: String? = null
)

internal object PlaylistResolver {

    private const val MAX_DEPTH = 2

    fun isPlaylistUrl(url: String): Boolean {
        val lower = url.lowercase().substringBefore("?")
        return lower.endsWith(".pls") || (lower.endsWith(".m3u") && !lower.endsWith(".m3u8")) ||
                lower.endsWith(".asx") || lower.endsWith(".xspf")
    }

    fun isPlaylistContentType(contentType: String): Boolean {
        val ct = contentType.lowercase()
        return ct.contains("scpls") ||
                (ct.contains("mpegurl") && !ct.contains("m3u8")) ||
                ct.contains("x-ms-asf") ||
                ct.contains("xspf")
    }

    suspend fun resolve(url: String, client: OkHttpClient, depth: Int = 0): ResolvedPlaylist? {
        if (depth > MAX_DEPTH || !url.startsWith("http", ignoreCase = true)) return null

        val lowerUrl = url.lowercase().substringBefore("?")
        if (!isPlaylistUrl(lowerUrl)) {
            return null
        }

        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android) InternetRadio")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                parsePlaylistContent(body, url, client, depth)
            }
        } catch (_: Exception) {
            null
        }
    }

    internal fun parsePlaylistContent(
        content: String,
        baseUrl: String,
        client: OkHttpClient? = null,
        depth: Int = 0
    ): ResolvedPlaylist? {
        val trimmed = content.trim()

        // 1. PLS format
        if (trimmed.contains("[playlist]", ignoreCase = true) || baseUrl.lowercase().contains(".pls")) {
            val fileMatch = Regex("""(?i)File\d+\s*=\s*([^\r\n]+)""").find(content)
            val titleMatch = Regex("""(?i)Title\d+\s*=\s*([^\r\n]+)""").find(content)
            if (fileMatch != null) {
                val resolvedUrl = resolveRelativeUrl(baseUrl, fileMatch.groupValues[1].trim())
                val title = titleMatch?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
                return ResolvedPlaylist(resolvedUrl, title)
            }
        }

        // 2. ASX format
        if (trimmed.contains("<asx", ignoreCase = true) || baseUrl.lowercase().contains(".asx")) {
            val refMatch = Regex("""(?i)<ref\s+href\s*=\s*["']([^"']+)["']""").find(content)
            val titleMatch = Regex("""(?i)<title>([^<]+)</title>""").find(content)
            if (refMatch != null) {
                val resolvedUrl = resolveRelativeUrl(baseUrl, refMatch.groupValues[1].trim())
                val title = titleMatch?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
                return ResolvedPlaylist(resolvedUrl, title)
            }
        }

        // 3. XSPF format
        if (trimmed.contains("<playlist", ignoreCase = true) || baseUrl.lowercase().contains(".xspf")) {
            val locMatch = Regex("""(?i)<location>([^<]+)</location>""").find(content)
            val titleMatch = Regex("""(?i)<title>([^<]+)</title>""").find(content)
            if (locMatch != null) {
                val resolvedUrl = resolveRelativeUrl(baseUrl, locMatch.groupValues[1].trim())
                val title = titleMatch?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
                return ResolvedPlaylist(resolvedUrl, title)
            }
        }

        // 4. M3U format (non-HLS)
        if (trimmed.startsWith("#EXTM3U", ignoreCase = true) || baseUrl.lowercase().contains(".m3u")) {
            var title: String? = null
            for (line in content.lines()) {
                val trimmedLine = line.trim()
                if (trimmedLine.startsWith("#EXTINF:", ignoreCase = true)) {
                    title = trimmedLine.substringAfter(",").trim().takeIf { it.isNotBlank() }
                } else if (trimmedLine.isNotBlank() && !trimmedLine.startsWith("#")) {
                    val resolvedUrl = resolveRelativeUrl(baseUrl, trimmedLine)
                    return ResolvedPlaylist(resolvedUrl, title)
                }
            }
        }

        return null
    }

    private fun resolveRelativeUrl(baseUrl: String, targetUrl: String): String {
        return try {
            if (targetUrl.startsWith("http://", ignoreCase = true) || targetUrl.startsWith("https://", ignoreCase = true)) {
                targetUrl
            } else {
                URI(baseUrl).resolve(targetUrl).toString()
            }
        } catch (_: Exception) {
            targetUrl
        }
    }
}
