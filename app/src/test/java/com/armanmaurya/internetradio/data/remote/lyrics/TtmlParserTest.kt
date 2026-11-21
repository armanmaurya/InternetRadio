package com.armanmaurya.internetradio.data.remote.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TtmlParserTest {

    @Test
    fun `parses standard ttml with lines and spans`() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body>
                <div>
                  <p begin="00:15.30" end="00:18.40">
                    <span begin="00:15.30" end="00:16.10">Never </span>
                    <span begin="00:16.10" end="00:16.80">gonna </span>
                    <span begin="00:16.80" end="00:17.50">give </span>
                    <span begin="00:17.50" end="00:18.40">you up</span>
                  </p>
                  <p begin="00:19.00">
                    <span>Never gonna let you down</span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val lines = TtmlParser.parse(ttml)
        assertEquals(2, lines.size)

        val line1 = lines[0]
        assertEquals(15300L, line1.timestampMs)
        assertEquals("Never gonna give you up", line1.text)
        assertNotNull(line1.words)
        assertEquals(4, line1.words?.size)
        assertEquals("Never ", line1.words?.get(0)?.text)
        assertEquals(15300L, line1.words?.get(0)?.startMs)
        assertEquals(16100L, line1.words?.get(0)?.endMs)

        val line2 = lines[1]
        assertEquals(19000L, line2.timestampMs)
        assertEquals("Never gonna let you down", line2.text)
    }

    @Test
    fun `parses timestamp formats accurately`() {
        assertEquals(83456L, TtmlParser.parseTimestampMs("01:23.456"))
        assertEquals(3661200L, TtmlParser.parseTimestampMs("01:01:01.200"))
        assertEquals(12340L, TtmlParser.parseTimestampMs("12.34s"))
        assertNull(TtmlParser.parseTimestampMs(""))
    }
}
