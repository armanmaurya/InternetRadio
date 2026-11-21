package com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.lyrics

import androidx.compose.ui.graphics.Color
import com.armanmaurya.internetradio.domain.model.LrcLine
import com.armanmaurya.internetradio.domain.model.LrcWord
import org.junit.Assert.assertEquals
import org.junit.Test

class WordSyncedLyricsTest {

    @Test
    fun `highlights words progressively based on song time`() {
        val line = LrcLine(
            timestampMs = 1000L,
            text = "Never gonna give",
            words = listOf(
                LrcWord(startMs = 1000L, endMs = 1500L, text = "Never "),
                LrcWord(startMs = 1500L, endMs = 2000L, text = "gonna "),
                LrcWord(startMs = 2000L, endMs = 3000L, text = "give")
            )
        )

        val primary = Color.Red
        val dim = Color.Gray

        // Before any word starts
        val resultBefore = buildWordSyncedAnnotatedString(line, currentSongTime = 500L, primaryColor = primary, dimColor = dim)
        assertEquals("Never gonna give", resultBefore.text)
        assertEquals(3, resultBefore.spanStyles.size)
        // All words should have dim color
        resultBefore.spanStyles.forEach { span ->
            assertEquals(dim, span.item.color)
        }

        // When first word is sung (time = 1200L)
        val result1 = buildWordSyncedAnnotatedString(line, currentSongTime = 1200L, primaryColor = primary, dimColor = dim)
        assertEquals(primary, result1.spanStyles[0].item.color)
        assertEquals(dim, result1.spanStyles[1].item.color)
        assertEquals(dim, result1.spanStyles[2].item.color)

        // When second word is sung (time = 1800L)
        val result2 = buildWordSyncedAnnotatedString(line, currentSongTime = 1800L, primaryColor = primary, dimColor = dim)
        assertEquals(primary, result2.spanStyles[0].item.color)
        assertEquals(primary, result2.spanStyles[1].item.color)
        assertEquals(dim, result2.spanStyles[2].item.color)

        // When all words are sung (time = 2500L)
        val result3 = buildWordSyncedAnnotatedString(line, currentSongTime = 2500L, primaryColor = primary, dimColor = dim)
        assertEquals(primary, result3.spanStyles[0].item.color)
        assertEquals(primary, result3.spanStyles[1].item.color)
        assertEquals(primary, result3.spanStyles[2].item.color)
    }

    @Test
    fun `handles space concatenation when words lack trailing spaces`() {
        val line = LrcLine(
            timestampMs = 1000L,
            text = "Never gonna give",
            words = listOf(
                LrcWord(startMs = 1000L, endMs = 1500L, text = "Never"),
                LrcWord(startMs = 1500L, endMs = 2000L, text = "gonna"),
                LrcWord(startMs = 2000L, endMs = 3000L, text = "give")
            )
        )

        val result = buildWordSyncedAnnotatedString(line, currentSongTime = 1600L, primaryColor = Color.Blue, dimColor = Color.Gray)
        assertEquals("Never gonna give", result.text)
        assertEquals(Color.Blue, result.spanStyles[0].item.color)
        assertEquals(Color.Blue, result.spanStyles[1].item.color)
        assertEquals(Color.Gray, result.spanStyles[2].item.color)
    }
}
