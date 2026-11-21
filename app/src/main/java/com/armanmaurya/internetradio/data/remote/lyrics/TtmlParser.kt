package com.armanmaurya.internetradio.data.remote.lyrics

import com.armanmaurya.internetradio.domain.model.LrcLine
import com.armanmaurya.internetradio.domain.model.LrcWord

object TtmlParser {
    private val pTagRegex = Regex("""<p\s+[^>]*begin="([^"]+)"[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
    private val spanTagRegex = Regex("""<span\s+[^>]*begin="([^"]+)"(?:[^>]*end="([^"]+)")?[^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
    private val xmlTagRegex = Regex("""<[^>]+>""")

    fun parse(ttmlContent: String): List<LrcLine> {
        if (ttmlContent.isBlank()) return emptyList()
        val lines = mutableListOf<LrcLine>()
        val pMatches = pTagRegex.findAll(ttmlContent)
        for (match in pMatches) {
            val beginStr = match.groupValues[1]
            val innerContent = match.groupValues[2]
            val lineStartMs = parseTimestampMs(beginStr) ?: continue

            val words = mutableListOf<LrcWord>()
            val spanMatches = spanTagRegex.findAll(innerContent).toList()
            if (spanMatches.isNotEmpty()) {
                for (span in spanMatches) {
                    val spanBegin = span.groupValues[1]
                    val spanEnd = span.groupValues[2].takeIf { it.isNotBlank() }
                    val wordText = unescapeXml(span.groupValues[3].replace(xmlTagRegex, ""))
                    val wStart = parseTimestampMs(spanBegin) ?: lineStartMs
                    val wEnd = spanEnd?.let { parseTimestampMs(it) }
                    if (wordText.isNotEmpty()) {
                        words.add(LrcWord(startMs = wStart, endMs = wEnd, text = wordText))
                    }
                }
            }

            val rawLineText = if (words.isNotEmpty()) {
                words.joinToString("") { it.text }.trim()
            } else {
                unescapeXml(innerContent.replace(xmlTagRegex, "")).trim()
            }
            if (rawLineText.isNotBlank()) {
                lines.add(
                    LrcLine(
                        timestampMs = lineStartMs,
                        text = rawLineText,
                        words = words.takeIf { it.isNotEmpty() }
                    )
                )
            }
        }
        return lines.sortedBy { it.timestampMs }
    }

    fun parseTimestampMs(timeStr: String): Long? {
        val trimmed = timeStr.trim().removeSuffix("s")
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split(":")
        return try {
            when (parts.size) {
                3 -> {
                    val h = parts[0].toLong()
                    val m = parts[1].toLong()
                    val s = parts[2].toDouble()
                    ((h * 3600 + m * 60 + s) * 1000).toLong()
                }
                2 -> {
                    val m = parts[0].toLong()
                    val s = parts[2 - 1].toDouble()
                    ((m * 60 + s) * 1000).toLong()
                }
                1 -> {
                    (parts[0].toDouble() * 1000).toLong()
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun unescapeXml(text: String): String =
        text.replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
}
