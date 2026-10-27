package com.armanmaurya.internetradio.core.media.prober.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistResolverTest {

    @Test
    fun testIsPlaylistUrl() {
        assertTrue(PlaylistResolver.isPlaylistUrl("http://example.com/listen.pls"))
        assertTrue(PlaylistResolver.isPlaylistUrl("http://example.com/stream.m3u"))
        assertTrue(PlaylistResolver.isPlaylistUrl("http://example.com/live.asx"))
        assertTrue(PlaylistResolver.isPlaylistUrl("http://example.com/radio.xspf"))
        assertTrue(!PlaylistResolver.isPlaylistUrl("http://example.com/stream.m3u8"))
    }

    @Test
    fun testParsePlsContent() {
        val plsContent = """
            [playlist]
            NumberOfEntries=1
            File1=http://stream.example.com:8000/live
            Title1=Rock Radio
            Length1=-1
            Version=2
        """.trimIndent()

        val result = PlaylistResolver.parsePlaylistContent(plsContent, "http://stream.example.com/listen.pls")
        assertNotNull(result)
        assertEquals("http://stream.example.com:8000/live", result?.streamUrl)
        assertEquals("Rock Radio", result?.name)
    }

    @Test
    fun testParseM3uContent() {
        val m3uContent = """
            #EXTM3U
            #EXTINF:-1,Classic FM
            http://stream.example.com/classic.mp3
        """.trimIndent()

        val result = PlaylistResolver.parsePlaylistContent(m3uContent, "http://stream.example.com/listen.m3u")
        assertNotNull(result)
        assertEquals("http://stream.example.com/classic.mp3", result?.streamUrl)
        assertEquals("Classic FM", result?.name)
    }

    @Test
    fun testParseAsxContent() {
        val asxContent = """
            <asx version="3.0">
              <entry>
                <title>Jazz 24</title>
                <ref href="http://stream.example.com/jazz.aac"/>
              </entry>
            </asx>
        """.trimIndent()

        val result = PlaylistResolver.parsePlaylistContent(asxContent, "http://stream.example.com/listen.asx")
        assertNotNull(result)
        assertEquals("http://stream.example.com/jazz.aac", result?.streamUrl)
        assertEquals("Jazz 24", result?.name)
    }

    @Test
    fun testParsePlaylist_relativeUrlResolution() {
        val plsContent = """
            [playlist]
            File1=stream_live
            Title1=Local Stream
        """.trimIndent()

        val result = PlaylistResolver.parsePlaylistContent(plsContent, "http://stream.example.com/sub/radio.pls")
        assertNotNull(result)
        assertEquals("http://stream.example.com/sub/stream_live", result?.streamUrl)
    }

    @Test
    fun testParseSomaFmPlsContent() {
        val somaPlsContent = """
            [playlist]
            numberofentries=3
            File1=https://ice2.somafm.com/groovesalad-128-mp3
            Title1=SomaFM: Groove Salad (#1): A nicely chilled plate of ambient/downtempo beats and grooves.
            Length1=-1
            File2=https://ice6.somafm.com/groovesalad-128-mp3
            Title2=SomaFM: Groove Salad (#2): A nicely chilled plate of ambient/downtempo beats and grooves.
            Length2=-1
            File3=https://ice5.somafm.com/groovesalad-128-mp3
            Title3=SomaFM: Groove Salad (#3): A nicely chilled plate of ambient/downtempo beats and grooves.
            Length3=-1
            Version=2
        """.trimIndent()

        val result = PlaylistResolver.parsePlaylistContent(somaPlsContent, "https://somafm.com/groovesalad.pls")
        assertNotNull(result)
        assertEquals("https://ice2.somafm.com/groovesalad-128-mp3", result?.streamUrl)
        assertEquals("SomaFM: Groove Salad (#1): A nicely chilled plate of ambient/downtempo beats and grooves.", result?.name)
    }

    @Test
    fun testHlsManifestIsNotTreatedAsSimpleM3uRedirect() {
        val hlsManifest = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXTINF:10,
            segment1.ts
        """.trimIndent()

        val result = PlaylistResolver.parsePlaylistContent(hlsManifest, "http://stream.example.com/stream")
        org.junit.Assert.assertNull(result)
    }
}
