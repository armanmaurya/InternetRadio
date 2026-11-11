package com.armanmaurya.internetradio.core.media.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HlsManifestSanitizerTest {

    @Test
    fun sanitize_zeroFrameRateAtEnd_removesAttribute() {
        // Real-world multivariant playlist (Cultura FM, UOL CDN)
        val manifest = "#EXTM3U\n" +
                "#EXT-X-VERSION:3\n" +
                "#EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=716800,RESOLUTION=960x540,FRAME-RATE=0.000\n" +
                "culturafm_luhd.m3u8?vhost=player-culturafm.stream.uol.com.br\n"

        val expected = "#EXTM3U\n" +
                "#EXT-X-VERSION:3\n" +
                "#EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=716800,RESOLUTION=960x540\n" +
                "culturafm_luhd.m3u8?vhost=player-culturafm.stream.uol.com.br\n"

        assertEquals(expected, HlsManifestSanitizer.sanitize(manifest))
    }

    @Test
    fun sanitize_zeroFrameRateInMiddleOrFirst_removesAttribute() {
        assertEquals(
            "#EXT-X-STREAM-INF:BANDWIDTH=1000,CODECS=\"mp4a.40.2\"",
            HlsManifestSanitizer.sanitize("#EXT-X-STREAM-INF:BANDWIDTH=1000,FRAME-RATE=0,CODECS=\"mp4a.40.2\"")
        )
        assertEquals(
            "#EXT-X-STREAM-INF:BANDWIDTH=1000",
            HlsManifestSanitizer.sanitize("#EXT-X-STREAM-INF:FRAME-RATE=0.0,BANDWIDTH=1000")
        )
        assertEquals(
            "#EXT-X-I-FRAME-STREAM-INF:BANDWIDTH=1000,URI=\"iframe.m3u8\"",
            HlsManifestSanitizer.sanitize("#EXT-X-I-FRAME-STREAM-INF:BANDWIDTH=1000,FRAME-RATE=00.,URI=\"iframe.m3u8\"")
        )
    }

    @Test
    fun sanitize_crlfLineEndings_arePreserved() {
        val manifest = "#EXTM3U\r\n#EXT-X-STREAM-INF:BANDWIDTH=1000,FRAME-RATE=0.000\r\nlow.m3u8\r\n"
        val expected = "#EXTM3U\r\n#EXT-X-STREAM-INF:BANDWIDTH=1000\r\nlow.m3u8\r\n"

        assertEquals(expected, HlsManifestSanitizer.sanitize(manifest))
    }

    @Test
    fun sanitize_positiveFrameRate_isUntouched() {
        val manifest = "#EXTM3U\n" +
                "#EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1280x720,FRAME-RATE=29.970\n" +
                "hd.m3u8\n" +
                "#EXT-X-STREAM-INF:BANDWIDTH=800000,FRAME-RATE=0.500\n" +
                "slow.m3u8\n" +
                "#EXT-X-STREAM-INF:BANDWIDTH=900000,FRAME-RATE=10\n" +
                "ten.m3u8\n"

        assertEquals(manifest, HlsManifestSanitizer.sanitize(manifest))
    }

    @Test
    fun sanitize_mediaPlaylistWithoutFrameRate_returnsSameInstance() {
        val manifest = "#EXTM3U\n#EXT-X-TARGETDURATION:2\n#EXTINF:2.000,\nsegment_1.ts\n"

        assertSame(manifest, HlsManifestSanitizer.sanitize(manifest))
    }

    @Test
    fun sanitize_zeroFrameRateOutsideStreamInf_isUntouched() {
        val manifest = "#EXTM3U\n#EXTINF:2.000,title FRAME-RATE=0.000\nsegment_1.ts\n"

        assertEquals(manifest, HlsManifestSanitizer.sanitize(manifest))
    }
}
