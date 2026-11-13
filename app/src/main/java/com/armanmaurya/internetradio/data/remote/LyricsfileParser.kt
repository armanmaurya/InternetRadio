package com.armanmaurya.internetradio.data.remote

import com.armanmaurya.internetradio.domain.model.LrcLine
import com.armanmaurya.internetradio.domain.model.LrcWord
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

/**
 * Reads the synced lines (with word timings) from an LRCLIB Lyricsfile.
 * Spec: https://github.com/tranxuanthang/lyricsfile/blob/main/SPECIFICATION.md
 */
object LyricsfileParser {

    private const val SUPPORTED_VERSION = "1.0"

    private val settings = LoadSettings.builder()
        .setAllowDuplicateKeys(false)
        .setMaxAliasesForCollections(10)
        .setCodePointLimit(2_000_000)
        .build()

    /**
     * Returns the synced lines, or null when the document is invalid, uses an unknown version,
     * or has no timed lines. Words are kept only when the line has at least one valid word.
     */
    fun parseLines(yaml: String): List<LrcLine>? = try {
        val root = Load(settings).loadFromString(yaml) as? Map<*, *>
        if (root == null || root["version"]?.toString() != SUPPORTED_VERSION) {
            null
        } else {
            (root["lines"] as? List<*>)
                ?.mapNotNull { parseLine(it as? Map<*, *>) }
                ?.sortedBy { it.timestampMs }
                ?.takeIf { it.isNotEmpty() }
        }
    } catch (_: Exception) {
        null
    }

    private fun parseLine(map: Map<*, *>?): LrcLine? {
        if (map == null) return null
        val start = map["start_ms"].asMillis() ?: return null
        val text = map["text"] as? String ?: return null
        val end = map["end_ms"].asMillis()?.takeIf { it >= start }
        val words = (map["words"] as? List<*>)
            ?.mapNotNull { parseWord(it as? Map<*, *>) }
            ?.sortedBy { it.startMs }
            ?.takeIf { it.isNotEmpty() }
        return LrcLine(timestampMs = start, text = text.trim(), endMs = end, words = words)
    }

    private fun parseWord(map: Map<*, *>?): LrcWord? {
        if (map == null) return null
        val start = map["start_ms"].asMillis() ?: return null
        val text = map["text"] as? String ?: return null
        val end = map["end_ms"].asMillis()?.takeIf { it >= start }
        return LrcWord(startMs = start, endMs = end, text = text)
    }

    private fun Any?.asMillis(): Long? = (this as? Number)?.toLong()?.takeIf { it >= 0 }
}
