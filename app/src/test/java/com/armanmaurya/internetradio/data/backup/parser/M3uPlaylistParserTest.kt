package com.armanmaurya.internetradio.data.backup.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class M3uPlaylistParserTest {

    private val parser = M3uPlaylistParser()

    @Test
    fun canParse_recognizesM3uHeaders() {
        assertTrue(parser.canParse("#EXTM3U\n#EXTINF:-1,Test\nhttp://example.com"))
        assertTrue(parser.canParse("#RADIOBROWSERUUID:9637967a-0601-11e8-ae97-52543be04c81\nhttp://example.com"))
        assertTrue(parser.canParse("\uFEFF#EXTM3U\nhttp://example.com"))
        assertFalse(parser.canParse("{\"schemaVersion\": 2, \"stations\": []}"))
    }

    @Test
    fun parse_radioDroidStandardFormat_uuidBeforeExtinf() {
        val content = """
            #EXTM3U
            #RADIOBROWSERUUID:9637967a-0601-11e8-ae97-52543be04c81
            #EXTINF:-1,Technikum GOLD
            http://technikumhitclassics.macjingle.at:8204/;stream/1
            #RADIOBROWSERUUID:9614b318-0601-11e8-ae97-52543be04c81
            #EXTINF:-1,Zwischen-Welten
            http://radio.zwischen-welten.info:8000/listen/
        """.trimIndent()

        val stations = parser.parse(BufferedReader(StringReader(content)))

        assertEquals(2, stations.size)

        assertEquals("9637967a-0601-11e8-ae97-52543be04c81", stations[0].stationUuid)
        assertEquals("Technikum GOLD", stations[0].name)
        assertEquals("http://technikumhitclassics.macjingle.at:8204/;stream/1", stations[0].url)
        assertFalse(stations[0].isCustom ?: true)

        assertEquals("9614b318-0601-11e8-ae97-52543be04c81", stations[1].stationUuid)
        assertEquals("Zwischen-Welten", stations[1].name)
        assertEquals("http://radio.zwischen-welten.info:8000/listen/", stations[1].url)
        assertFalse(stations[1].isCustom ?: true)
    }

    @Test
    fun parse_uuidAfterExtinf() {
        val content = """
            #EXTM3U
            #EXTINF:-1,Technikum GOLD
            #RADIOBROWSERUUID:9637967a-0601-11e8-ae97-52543be04c81
            http://technikumhitclassics.macjingle.at:8204/;stream/1
        """.trimIndent()

        val stations = parser.parse(BufferedReader(StringReader(content)))

        assertEquals(1, stations.size)
        assertEquals("9637967a-0601-11e8-ae97-52543be04c81", stations[0].stationUuid)
        assertEquals("Technikum GOLD", stations[0].name)
        assertEquals("http://technikumhitclassics.macjingle.at:8204/;stream/1", stations[0].url)
    }

    @Test
    fun parse_customStationWithoutRadioBrowserUuid_generatesDeterministicUuid() {
        val content = """
            #EXTM3U
            #EXTINF:-1,My Private Stream
            http://192.168.1.100:8000/stream
        """.trimIndent()

        val stations = parser.parse(BufferedReader(StringReader(content)))

        assertEquals(1, stations.size)
        assertTrue(stations[0].isCustom == true)
        assertEquals("My Private Stream", stations[0].name)
        assertEquals("http://192.168.1.100:8000/stream", stations[0].url)
        assertTrue(stations[0].stationUuid.isNotBlank())
    }

    @Test
    fun parse_handlesBomAndEmptyLines() {
        val content = "\uFEFF#EXTM3U\r\n\r\n#RADIOBROWSERUUID:9637967a-0601-11e8-ae97-52543be04c81\r\n\r\n#EXTINF:-1,Technikum GOLD\r\nhttp://technikum.example/stream\r\n"

        val stations = parser.parse(BufferedReader(StringReader(content)))

        assertEquals(1, stations.size)
        assertEquals("9637967a-0601-11e8-ae97-52543be04c81", stations[0].stationUuid)
        assertEquals("Technikum GOLD", stations[0].name)
    }
}
