package com.armanmaurya.internetradio.core.utils

import com.armanmaurya.internetradio.domain.lyrics.LyricsProviderInfo
import com.armanmaurya.internetradio.domain.model.LrcLine
import com.armanmaurya.internetradio.domain.model.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsExporterTest {

    @Test
    fun `formats synced lyrics as lrc payload`() {
        val lyrics = Lyrics(
            plainLyrics = null,
            syncedLyrics = listOf(
                LrcLine(1500L, "First line"),
                LrcLine(65200L, "Second line")
            ),
            providerInfo = LyricsProviderInfo("test", "Test"),
            trackTitle = "Radio Ga Ga",
            artistName = "Queen"
        )

        val payload = LyricsExporter.prepareExport(lyrics)

        assertEquals("Queen - Radio Ga Ga.lrc", payload.defaultFileName)
        assertTrue(payload.content.contains("[ti:Radio Ga Ga]"))
        assertTrue(payload.content.contains("[ar:Queen]"))
        assertTrue(payload.content.contains("[00:01.50]First line"))
        assertTrue(payload.content.contains("[01:05.20]Second line"))
    }

    @Test
    fun `formats plain lyrics as txt payload`() {
        val lyrics = Lyrics(
            plainLyrics = "Line 1\nLine 2",
            syncedLyrics = null,
            providerInfo = LyricsProviderInfo("test", "Test"),
            trackTitle = "Bohemian Rhapsody",
            artistName = "Queen"
        )

        val payload = LyricsExporter.prepareExport(lyrics)

        assertEquals("Queen - Bohemian Rhapsody.txt", payload.defaultFileName)
        assertEquals("Line 1\nLine 2", payload.content)
    }

    @Test
    fun `sanitizes illegal file name characters`() {
        val lyrics = Lyrics(
            plainLyrics = "Some lyrics",
            syncedLyrics = null,
            providerInfo = LyricsProviderInfo("test", "Test"),
            trackTitle = "Song/With:Illegal?Chars*",
            artistName = "AC/DC"
        )

        val payload = LyricsExporter.prepareExport(lyrics)

        assertEquals("AC_DC - Song_With_Illegal_Chars_.txt", payload.defaultFileName)
    }
}
