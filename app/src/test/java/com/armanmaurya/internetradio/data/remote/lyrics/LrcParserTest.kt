package com.armanmaurya.internetradio.data.remote.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LrcParserTest {

    @Test
    fun `parses standard lrc lines`() {
        val lrc = """
            [00:10.50]First line
            [00:15.200]Second line
        """.trimIndent()

        val lines = LrcParser.parseLrc(lrc)
        assertEquals(2, lines.size)
        assertEquals(10500L, lines[0].timestampMs)
        assertEquals("First line", lines[0].text)
        assertNull(lines[0].words)

        assertEquals(15200L, lines[1].timestampMs)
        assertEquals("Second line", lines[1].text)
        assertNull(lines[1].words)
    }

    @Test
    fun `parses enhanced lrc lines with word timestamps`() {
        val enhancedLrc = """
            [00:12.00]<00:12.00>Never <00:12.80>gonna <00:14.00>give <00:14.80>you up
        """.trimIndent()

        val lines = LrcParser.parseLrc(enhancedLrc)
        assertEquals(1, lines.size)
        val line = lines[0]
        assertEquals(12000L, line.timestampMs)
        assertEquals("Never gonna give you up", line.text)
        assertNotNull(line.words)
        assertEquals(4, line.words?.size)

        assertEquals("Never ", line.words?.get(0)?.text)
        assertEquals(12000L, line.words?.get(0)?.startMs)
        assertEquals(12800L, line.words?.get(0)?.endMs)

        assertEquals("gonna ", line.words?.get(1)?.text)
        assertEquals(12800L, line.words?.get(1)?.startMs)
        assertEquals(14000L, line.words?.get(1)?.endMs)

        assertEquals("give ", line.words?.get(2)?.text)
        assertEquals(14000L, line.words?.get(2)?.startMs)
        assertEquals(14800L, line.words?.get(2)?.endMs)

        assertEquals("you up", line.words?.get(3)?.text)
        assertEquals(14800L, line.words?.get(3)?.startMs)
    }
}
