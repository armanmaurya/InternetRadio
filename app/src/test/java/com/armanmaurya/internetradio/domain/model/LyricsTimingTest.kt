package com.armanmaurya.internetradio.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsTimingTest {

    private val wordLine = LrcLine(
        timestampMs = 1000,
        text = "ab cd",
        endMs = 3000,
        words = listOf(
            LrcWord(startMs = 1000, endMs = 2000, text = "ab "), // 3 chars
            LrcWord(startMs = 2000, endMs = null, text = "cd")   // 2 chars, ends with the line
        )
    )

    @Test
    fun `word line is empty before it starts and full after it ends`() {
        assertEquals(0f, wordLine.sungFraction(500, null), 0.001f)
        assertEquals(1f, wordLine.sungFraction(3500, null), 0.001f)
    }

    @Test
    fun `word line follows word timings`() {
        // Halfway through "ab " = 1.5 of 5 chars
        assertEquals(0.3f, wordLine.sungFraction(1500, null), 0.001f)
        // "ab " done, halfway through "cd" (ends at line end 3000) = 4 of 5 chars
        assertEquals(0.8f, wordLine.sungFraction(2500, null), 0.001f)
    }

    @Test
    fun `line without words fills evenly until the next line`() {
        val line = LrcLine(timestampMs = 0, text = "plain")
        assertEquals(0.25f, line.sungFraction(1000, nextLineStartMs = 4000), 0.001f)
    }

    @Test
    fun `last line without end uses the fallback duration`() {
        val line = LrcLine(timestampMs = 0, text = "last")
        assertEquals(0.5f, line.sungFraction(2500, nextLineStartMs = null), 0.001f)
    }

    @Test
    fun `started word count`() {
        assertEquals(0, wordLine.startedWordCount(999))
        assertEquals(1, wordLine.startedWordCount(1000))
        assertEquals(2, wordLine.startedWordCount(2000))
        assertEquals(0, LrcLine(0, "no words").startedWordCount(5000))
    }
}
