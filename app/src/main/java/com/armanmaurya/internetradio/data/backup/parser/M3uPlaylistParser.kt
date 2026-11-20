package com.armanmaurya.internetradio.data.backup.parser

import com.armanmaurya.internetradio.data.backup.BackupStation
import java.io.BufferedReader
import java.util.UUID

/**
 * Parses Extended M3U playlist files exported from RadioDroid and AMARadio.
 *
 * Supported format:
 * ```m3u
 * #EXTM3U
 * #RADIOBROWSERUUID:9637967a-0601-11e8-ae97-52543be04c81
 * #EXTINF:-1,Technikum GOLD
 * http://technikumhitclassics.macjingle.at:8204/;stream/1
 * ```
 */
class M3uPlaylistParser : StationFileParser {

    override fun canParse(header: String): Boolean {
        val trimmed = header.trimStart().removePrefix("\uFEFF")
        return trimmed.startsWith("#EXTM3U", ignoreCase = true) ||
                trimmed.contains("#RADIOBROWSERUUID", ignoreCase = true) ||
                trimmed.contains("#EXTINF", ignoreCase = true)
    }

    override fun parse(reader: BufferedReader): List<BackupStation> {
        val stations = mutableListOf<BackupStation>()
        var currentUuid: String? = null
        var currentName: String? = null

        reader.forEachLine { rawLine ->
            val line = rawLine.trim().removePrefix("\uFEFF")
            when {
                line.isEmpty() -> {}
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    // Header line, ignore
                }
                line.startsWith("#RADIOBROWSERUUID:", ignoreCase = true) -> {
                    currentUuid = line.substringAfter(":").trim().takeIf { it.isNotBlank() }
                }
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val afterColon = line.substringAfter(":")
                    val commaIndex = afterColon.indexOf(',')
                    val title = if (commaIndex != -1) afterColon.substring(commaIndex + 1).trim() else ""
                    if (title.isNotBlank()) {
                        currentName = title
                    }
                }
                line.startsWith("#") -> {
                    // Other comments or unknown directives, ignore
                }
                else -> {
                    // Stream URL line
                    val url = line.trim()
                    if (isValidStreamUrl(url)) {
                        val stationUuid = currentUuid ?: UUID.nameUUIDFromBytes("m3u:$url".toByteArray()).toString()
                        val name = currentName ?: extractFallbackName(url)
                        stations.add(
                            BackupStation(
                                stationUuid = stationUuid,
                                name = name,
                                url = url,
                                urlResolved = url,
                                isCustom = (currentUuid == null),
                                addedAt = System.currentTimeMillis()
                            )
                        )
                    }
                    currentUuid = null
                    currentName = null
                }
            }
        }

        return stations
    }

    private fun isValidStreamUrl(url: String): Boolean {
        return url.startsWith("http://", ignoreCase = true) ||
                url.startsWith("https://", ignoreCase = true) ||
                url.startsWith("mms://", ignoreCase = true) ||
                url.startsWith("rtsp://", ignoreCase = true)
    }

    private fun extractFallbackName(url: String): String {
        return url.substringAfter("://")
            .substringBefore("/")
            .substringBefore(":")
            .ifBlank { "Radio Station" }
    }
}
