package com.armanmaurya.internetradio.core.media.prober.parser

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI

data class ResolvedPlaylist(
    val streamUrl: String,
    val name: String? = null
)

object PlaylistResolver {

    private const val MAX_DEPTH = 2

    fun isPlaylistUrl(url: String): Boolean {
        val lower = url.lowercase().substringBefore("?")
        return lower.endsWith(".pls") || (lower.endsWith(".m3u") && !lower.endsWith(".m3u8")) ||
                lower.endsWith(".asx") || lower.endsWith(".xspf")
    }

    fun isPlaylistContentType(contentType: String): Boolean {
        val ct = contentType.lowercase()
        return ct.contains("scpls") ||
                ct.contains("x-pls") ||
                (ct.contains("mpegurl") && !ct.contains("m3u8") && !ct.contains("apple")) ||
                ct.contains("x-ms-asf") ||
                ct.contains("xspf")
    }

    fun resolveBlocking(url: String, client: OkHttpClient, depth: Int = 0): ResolvedPlaylist? {
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
                val body = response.body.string()
                parsePlaylistContent(body, url, client, depth)
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun resolve(url: String, client: OkHttpClient, depth: Int = 0): ResolvedPlaylist? {
        return resolveBlocking(url, client, depth)
    }

    fun parsePlaylistContent(
        content: String,
        baseUrl: String,
        client: OkHttpClient? = null,
        depth: Int = 0
    ): ResolvedPlaylist? {
        val trimmed = content.trim()
        var extracted: ResolvedPlaylist? = null

        // 1. PLS format
        if (trimmed.contains("[playlist]", ignoreCase = true) || baseUrl.lowercase().contains(".pls")) {
            val fileMatch = Regex("""(?i)File\d+\s*=\s*([^\r\n]+)""").find(content)
            val titleMatch = Regex("""(?i)Title\d+\s*=\s*([^\r\n]+)""").find(content)
            if (fileMatch != null) {
                val resolvedUrl = resolveRelativeUrl(baseUrl, fileMatch.groupValues[1].trim())
                val title = titleMatch?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
                extracted = ResolvedPlaylist(resolvedUrl, title)
            }
        }

        // 2. ASX format
        if (extracted == null && (trimmed.contains("<asx", ignoreCase = true) || baseUrl.lowercase().contains(".asx"))) {
            val refMatch = Regex("""(?i)<ref\s+href\s*=\s*["']([^"']+)["']""").find(content)
            val titleMatch = Regex("""(?i)<title>([^<]+)</title>""").find(content)
            if (refMatch != null) {
                val resolvedUrl = resolveRelativeUrl(baseUrl, refMatch.groupValues[1].trim())
                val title = titleMatch?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
                extracted = ResolvedPlaylist(resolvedUrl, title)
            }
        }

        // 3. XSPF format
        if (extracted == null && (trimmed.contains("<playlist", ignoreCase = true) || baseUrl.lowercase().contains(".xspf"))) {
            val locMatch = Regex("""(?i)<location>([^<]+)</location>""").find(content)
            val titleMatch = Regex("""(?i)<title>([^<]+)</title>""").find(content)
            if (locMatch != null) {
                val resolvedUrl = resolveRelativeUrl(baseUrl, locMatch.groupValues[1].trim())
                val title = titleMatch?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
                extracted = ResolvedPlaylist(resolvedUrl, title)
            }
        }

        // 4. M3U format (non-HLS)
        if (extracted == null && (trimmed.startsWith("#EXTM3U", ignoreCase = true) || baseUrl.lowercase().contains(".m3u"))) {
            // Avoid treating an actual HLS media/master manifest as a simple M3U redirect
            val isHlsManifest = trimmed.contains("#EXT-X-", ignoreCase = true) ||
                    Regex("""(?i)#EXTINF:\s*\d""").containsMatchIn(trimmed)
            if (!isHlsManifest) {
                var title: String? = null
                for (line in content.lines()) {
                    val trimmedLine = line.trim()
                    if (trimmedLine.startsWith("#EXTINF:", ignoreCase = true)) {
                        title = trimmedLine.substringAfter(",").trim().takeIf { it.isNotBlank() }
                    } else if (trimmedLine.isNotBlank() && !trimmedLine.startsWith("#")) {
                        val lowerTarget = trimmedLine.lowercase().substringBefore("?")
                        if (!lowerTarget.endsWith(".ts") && !lowerTarget.endsWith(".aac") && !lowerTarget.endsWith(".m4s")) {
                            val resolvedUrl = resolveRelativeUrl(baseUrl, trimmedLine)
                            extracted = ResolvedPlaylist(resolvedUrl, title)
                        }
                        break
                    }
                }
            }
        }

        if (extracted != null && client != null && depth < MAX_DEPTH && isPlaylistUrl(extracted.streamUrl)) {
            val nested = resolveBlocking(extracted.streamUrl, client, depth + 1)
            if (nested != null) {
                return ResolvedPlaylist(nested.streamUrl, nested.name ?: extracted.name)
            }
        }

        return extracted
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
