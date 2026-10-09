package com.armanmaurya.internetradio.core.media.prober.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsEngineTest {

    @Test
    fun testIsHlsOrDash() {
        assertTrue(HlsEngine.isHlsOrDash("http://example.com/live/playlist.m3u8"))
        assertTrue(HlsEngine.isHlsOrDash("http://example.com/live/manifest.mpd"))
        assertTrue(HlsEngine.isHlsOrDash("http://example.com/live", "application/vnd.apple.mpegurl"))
        assertTrue(HlsEngine.isHlsOrDash("http://example.com/live", "application/dash+xml"))
        assertTrue(!HlsEngine.isHlsOrDash("http://example.com/live.mp3", "audio/mpeg"))
    }

    @Test
    fun testParseHlsContent_masterPlaylist() {
        val masterPlaylist = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-STREAM-INF:BANDWIDTH=128000,CODECS="mp4a.40.2"
            chunklist_w128.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=320000,CODECS="mp4a.40.2"
            chunklist_w320.m3u8
        """.trimIndent()

        val result = HlsEngine.parseHlsContent(masterPlaylist, "http://example.com/master.m3u8", "BBC Radio 1")
        assertEquals(128, result.bitrate)
        assertEquals("AAC", result.codec)
        assertEquals("BBC Radio 1", result.name)
    }

    @Test
    fun testParseHlsContent_urlBitrateFallback() {
        val mediaPlaylist = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXTINF:10.0,
            segment1.aac
        """.trimIndent()

        val result = HlsEngine.parseHlsContent(mediaPlaylist, "http://example.com/stream_b128000.m3u8")
        assertEquals(128, result.bitrate)
        assertEquals("AAC", result.codec)
    }

    @Test
    fun testParseDashContent() {
        val dashManifest = """
            <?xml version="1.0"?>
            <MPD>
              <Period>
                <AdaptationSet mimeType="audio/mp4">
                  <Representation id="1" bandwidth="192000" codecs="mp4a.40.2"/>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        val result = HlsEngine.parseDashContent(dashManifest, "Dash Radio")
        assertEquals(192, result.bitrate)
        assertEquals("AAC", result.codec)
        assertEquals("Dash Radio", result.name)
    }
}
