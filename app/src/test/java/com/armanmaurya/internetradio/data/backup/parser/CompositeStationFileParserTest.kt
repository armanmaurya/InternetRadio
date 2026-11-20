package com.armanmaurya.internetradio.data.backup.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class CompositeStationFileParserTest {

    private val parser = CompositeStationFileParser()

    @Test
    fun parse_jsonBackup() {
        val json = """
            {
              "schemaVersion": 2,
              "stations": [
                {
                  "stationUuid": "test-uuid-1",
                  "name": "Station One",
                  "url": "https://stream.one"
                }
              ]
            }
        """.trimIndent()

        val result = parser.parse(ByteArrayInputStream(json.toByteArray(Charsets.UTF_8)))

        assertNotNull(result)
        assertEquals(1, result!!.size)
        assertEquals("test-uuid-1", result[0].stationUuid)
        assertEquals("Station One", result[0].name)
    }

    @Test
    fun parse_m3uPlaylist() {
        val m3u = """
            #EXTM3U
            #RADIOBROWSERUUID:test-uuid-2
            #EXTINF:-1,Station Two
            http://stream.two
        """.trimIndent()

        val result = parser.parse(ByteArrayInputStream(m3u.toByteArray(Charsets.UTF_8)))

        assertNotNull(result)
        assertEquals(1, result!!.size)
        assertEquals("test-uuid-2", result[0].stationUuid)
        assertEquals("Station Two", result[0].name)
    }

    @Test
    fun parse_unsupportedFormat_returnsNull() {
        val unknown = "INVALID CONTENT THAT IS NEITHER JSON NOR M3U"

        val result = parser.parse(ByteArrayInputStream(unknown.toByteArray(Charsets.UTF_8)))

        assertNull(result)
    }
}
