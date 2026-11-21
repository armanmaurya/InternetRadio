package com.armanmaurya.internetradio.domain.model

import com.armanmaurya.internetradio.domain.lyrics.LyricsProviderInfo

/** A timed word (or segment) inside a lyric line. [text] includes its trailing space, if any. */
data class LrcWord(val startMs: Long, val endMs: Long?, val text: String)

/**
 * A synced lyric line. [words] is present only for word-synced lyrics (LRCLIB Lyricsfile, etc.);
 * joining their texts gives [text].
 */
data class LrcLine(
    val timestampMs: Long,
    val text: String,
    val endMs: Long? = null,
    val words: List<LrcWord>? = null
)

data class Lyrics(
    val plainLyrics: String?,
    val syncedLyrics: List<LrcLine>?,
    val rawLrc: String? = null,
    val providerInfo: LyricsProviderInfo = LyricsProviderInfo("lrclib", "LRCLIB", "https://lrclib.net"),
    val locatorToken: String? = null,
    val trackTitle: String? = null,
    val artistName: String? = null,
    val isInstrumental: Boolean = false
) {
    val hasWordSync: Boolean get() = syncedLyrics?.any { !it.words.isNullOrEmpty() } == true

    /**
     * Opaque locator string for persistence: "<providerId>:<locatorToken>".
     * E.g. "lrclib:2392" or "betterlyrics:a=Queen&s=Radio+Ga+Ga"
     */
    val locator: String? get() = locatorToken?.let { "${providerInfo.id}:$it" }

    /**
     * Formats lyrics into standard .lrc file format with metadata tags.
     */
    fun toLrcContent(): String {
        if (!rawLrc.isNullOrBlank()) return rawLrc
        val lines = syncedLyrics ?: return plainLyrics.orEmpty()
        val sb = StringBuilder()
        trackTitle?.let { sb.append("[ti:$it]\n") }
        artistName?.let { sb.append("[ar:$it]\n") }
        sb.append("[by:InternetRadio]\n")
        for (line in lines) {
            val totalSec = line.timestampMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            val cs = (line.timestampMs % 1000) / 10
            sb.append(String.format(java.util.Locale.US, "[%02d:%02d.%02d]%s\n", min, sec, cs, line.text))
        }
        return sb.toString()
    }

    /**
     * Formats plain lyrics text without timestamps.
     */
    fun toPlainContent(): String {
        if (!plainLyrics.isNullOrBlank()) return plainLyrics
        return syncedLyrics?.joinToString("\n") { it.text }.orEmpty()
    }
}

/** Display time for the last line when nothing says when it ends. */
private const val FALLBACK_LINE_DURATION_MS = 5_000L

/** Display time for a word with no end and nothing after it. */
private const val FALLBACK_WORD_DURATION_MS = 500L

/**
 * How much of this line has been sung at [timeMs], as a fraction (0..1) of its characters.
 * Word-synced lines follow the word timings; other lines progress evenly until they end.
 */
fun LrcLine.sungFraction(timeMs: Long, nextLineStartMs: Long?): Float {
    val lineEnd = endMs ?: nextLineStartMs ?: (timestampMs + FALLBACK_LINE_DURATION_MS)
    val words = words
    if (words.isNullOrEmpty()) {
        val duration = (lineEnd - timestampMs).coerceAtLeast(1L)
        return ((timeMs - timestampMs).toFloat() / duration).coerceIn(0f, 1f)
    }

    val totalChars = words.sumOf { it.text.length }.coerceAtLeast(1)
    var sungChars = 0f
    for (i in words.indices) {
        val word = words[i]
        if (timeMs < word.startMs) break
        val wordEnd = word.endMs
            ?: words.getOrNull(i + 1)?.startMs
            ?: lineEnd.takeIf { it > word.startMs }
            ?: (word.startMs + FALLBACK_WORD_DURATION_MS)
        val wordDuration = (wordEnd - word.startMs).coerceAtLeast(1L)
        val wordProgress = ((timeMs - word.startMs).toFloat() / wordDuration).coerceIn(0f, 1f)
        sungChars += word.text.length * wordProgress
    }
    return (sungChars / totalChars).coerceIn(0f, 1f)
}

/** Number of words in this line that have started by [timeMs] (0 when the line has no word timings). */
fun LrcLine.startedWordCount(timeMs: Long): Int =
    words?.count { timeMs >= it.startMs } ?: 0
