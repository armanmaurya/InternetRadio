package com.armanmaurya.internetradio.data.remote.lyrics

import com.armanmaurya.internetradio.domain.model.LrcLine
import com.armanmaurya.internetradio.domain.model.LrcWord

object LrcParser {
    private val LRC_LINE = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)""")
    private val WORD_TAG = Regex("""<(\d{2}):(\d{2})\.(\d{2,3})>""")

    /** Parses `[mm:ss.xx]` / `[mm:ss.xxx]` LRC lines, including enhanced LRC word tags `<mm:ss.xx>`. */
    fun parseLrc(lrcContent: String): List<LrcLine> =
        lrcContent.lines().mapNotNull { line ->
            val trimmed = line.trim().trim('\uFEFF')
            val match = LRC_LINE.find(trimmed) ?: return@mapNotNull null
            val (min, sec, fraction, rawText) = match.destructured
            val lineStartMs = parseTimestampMs(min, sec, fraction)

            if (!rawText.contains('<')) {
                LrcLine(timestampMs = lineStartMs, text = rawText.trim())
            } else {
                parseEnhancedLine(lineStartMs, rawText)
            }
        }

    private fun parseEnhancedLine(lineStartMs: Long, rawText: String): LrcLine {
        val wordMatches = WORD_TAG.findAll(rawText).toList()
        if (wordMatches.isEmpty()) {
            return LrcLine(timestampMs = lineStartMs, text = rawText.trim())
        }

        val words = mutableListOf<LrcWord>()
        val cleanTextBuilder = StringBuilder()

        var lastIdx = 0
        var currentWordStartMs = lineStartMs

        for (match in wordMatches) {
            val tagStart = match.range.first
            val tagEnd = match.range.last + 1
            if (tagStart > lastIdx) {
                val segText = rawText.substring(lastIdx, tagStart)
                if (segText.isNotEmpty()) {
                    val wStart = currentWordStartMs
                    cleanTextBuilder.append(segText)
                    words.add(LrcWord(startMs = wStart, endMs = null, text = segText))
                }
            }
            val (m, s, f) = match.destructured
            currentWordStartMs = parseTimestampMs(m, s, f)
            lastIdx = tagEnd
        }

        if (lastIdx < rawText.length) {
            val trailing = rawText.substring(lastIdx)
            if (trailing.isNotEmpty()) {
                cleanTextBuilder.append(trailing)
                words.add(LrcWord(startMs = currentWordStartMs, endMs = null, text = trailing))
            }
        }

        val wordsWithEnd = words.mapIndexed { i, word ->
            val nextStart = words.getOrNull(i + 1)?.startMs
            if (nextStart != null && nextStart > word.startMs) {
                word.copy(endMs = nextStart)
            } else {
                word
            }
        }

        val cleanText = cleanTextBuilder.toString().trim()
        return LrcLine(
            timestampMs = lineStartMs,
            text = cleanText,
            words = wordsWithEnd.takeIf { it.isNotEmpty() }
        )
    }

    private fun parseTimestampMs(min: String, sec: String, fraction: String): Long {
        val millis = if (fraction.length == 2) fraction.toLong() * 10 else fraction.toLong()
        return (min.toLong() * 60 + sec.toLong()) * 1000 + millis
    }
}
