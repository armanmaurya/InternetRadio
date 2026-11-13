package com.armanmaurya.internetradio.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsfileParserTest {

    // Shape as returned by LRCLIB (/api/get/7725680), trimmed.
    private val lrclibSample = """
        version: '1.0'
        metadata:
          title: Blinding Lights
          artist: Teddy Swims
          duration_ms: 215000
          instrumental: false
        lines:
        - text: I been on my own
          start_ms: 32330
          end_ms: 35500
          words:
          - text: 'I '
            start_ms: 32330
          - text: 'been '
            start_ms: 32816
          - text: 'on '
            start_ms: 33030
          - text: 'my '
            start_ms: 33166
          - text: own
            start_ms: 33361
        - text: I'm going through withdrawals
          start_ms: 41130
          words:
          - text: 'I''m '
            start_ms: 41130
          - text: withdrawals
            start_ms: 42147
            end_ms: 44000
        - text: Line without words
          start_ms: 50000
    """.trimIndent()

    @Test
    fun `parses lines and words from LRCLIB lyricsfile`() {
        val lines = LyricsfileParser.parseLines(lrclibSample)!!

        assertEquals(3, lines.size)
        assertEquals(32330L, lines[0].timestampMs)
        assertEquals(35500L, lines[0].endMs)
        assertEquals("I been on my own", lines[0].words!!.joinToString("") { it.text })
        assertEquals("I'm ", lines[1].words!![0].text)
        assertEquals(44000L, lines[1].words!![1].endMs)
        assertNull(lines[1].endMs)
        assertNull(lines[2].words)
    }

    @Test
    fun `accepts flow style mappings`() {
        val yaml = """
            version: "1.0"
            metadata: {title: T, artist: A}
            lines:
              - {text: "Hello world", start_ms: 1200, words: [{text: "Hello ", start_ms: 1200}, {text: world, start_ms: 1900}]}
        """.trimIndent()

        val lines = LyricsfileParser.parseLines(yaml)!!
        assertEquals(2, lines[0].words!!.size)
    }

    @Test
    fun `sorts lines by start time`() {
        val yaml = """
            version: '1.0'
            metadata: {title: T, artist: A}
            lines:
            - {text: second, start_ms: 2000}
            - {text: first, start_ms: 1000}
        """.trimIndent()

        assertEquals(listOf("first", "second"), LyricsfileParser.parseLines(yaml)!!.map { it.text })
    }

    @Test
    fun `rejects unknown version`() {
        assertNull(LyricsfileParser.parseLines("version: '2.0'\nmetadata: {title: T, artist: A}\nlines:\n- {text: x, start_ms: 1}"))
    }

    @Test
    fun `returns null when there are no timed lines`() {
        assertNull(LyricsfileParser.parseLines("version: '1.0'\nmetadata: {title: T, artist: A}\nplain: |\n  hi"))
    }

    @Test
    fun `skips lines with missing or negative start`() {
        val yaml = """
            version: '1.0'
            metadata: {title: T, artist: A}
            lines:
            - {text: no start}
            - {text: negative, start_ms: -5}
            - {text: ok, start_ms: 10}
        """.trimIndent()

        assertEquals(listOf("ok"), LyricsfileParser.parseLines(yaml)!!.map { it.text })
    }

    @Test
    fun `returns null for invalid yaml and duplicate keys`() {
        assertNull(LyricsfileParser.parseLines("version: '1.0'\nlines: [unclosed"))
        assertNull(LyricsfileParser.parseLines("version: '1.0'\nversion: '1.0'\nmetadata: {title: T, artist: A}"))
    }
}
