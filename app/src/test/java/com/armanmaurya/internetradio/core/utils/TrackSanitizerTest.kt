package com.armanmaurya.internetradio.core.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackSanitizerTest {

    @Test
    fun testIsDiscardable() {
        // Blank
        assertTrue(TrackSanitizer.isDiscardable(""))
        assertTrue(TrackSanitizer.isDiscardable("   "))

        // UUID
        assertTrue(TrackSanitizer.isDiscardable("522b37ba-e2b7-4769-96e4-fde1c44c9da9"))
        assertTrue(TrackSanitizer.isDiscardable("c67d8cfb-c431-4878-9368-6a0886c793fc"))

        // Hashes
        assertTrue(TrackSanitizer.isDiscardable("e4d909c290d0fb1ca068ffaddf22cbd0")) // MD5
        assertTrue(TrackSanitizer.isDiscardable("a1b2c3d4e5f6789012345678")) // 24-char hex

        // URLs & domains
        assertTrue(TrackSanitizer.isDiscardable("http://streaming.hotmixradio.com"))
        assertTrue(TrackSanitizer.isDiscardable("https://hotmixradio.com/"))
        assertTrue(TrackSanitizer.isDiscardable("www.hotmixradio.com"))
        assertTrue(TrackSanitizer.isDiscardable("station.fm"))
        assertTrue(TrackSanitizer.isDiscardable("radio.net"))

        // Multi-segment specific (numeric IDs and single-letter segment codes)
        assertTrue(TrackSanitizer.isDiscardable("21930", isMultiSegmentDelimiter = true))
        assertTrue(TrackSanitizer.isDiscardable("S", isMultiSegmentDelimiter = true))
        assertTrue(TrackSanitizer.isDiscardable("AD", isMultiSegmentDelimiter = true))
        assertTrue(TrackSanitizer.isDiscardable("JINGLE", isMultiSegmentDelimiter = true))

        // Legitimate song and artist names must NOT be discarded
        assertFalse(TrackSanitizer.isDiscardable("NORAH DAVIS"))
        assertFalse(TrackSanitizer.isDiscardable("All Of Me - Acoustic"))
        assertFalse(TrackSanitizer.isDiscardable("Queen"))
        assertFalse(TrackSanitizer.isDiscardable("Bohemian Rhapsody"))
        assertFalse(TrackSanitizer.isDiscardable("Spider-Man"))
    }

    @Test
    fun testGeorgeMichaelBroadcastMetadata() {
        val raw = "GEORGE MICHAEL - Older || 21930 || S || 2fad6c44-df66-411d-89d9-09ee88efa6bd"
        val cleaned = TrackSanitizer.sanitize(raw)
        assertEquals("GEORGE MICHAEL - Older", cleaned)
    }

    @Test
    fun testHotmixRadioMetadata() {
        val raw = "NORAH DAVIS - All Of Me - Acoustic ||  || S || 522b37ba-e2b7-4769-96e4-fde1c44c9da9"
        val cleaned = TrackSanitizer.sanitize(raw)
        assertEquals("NORAH DAVIS - All Of Me - Acoustic", cleaned)
    }

    @Test
    fun testHotmixKyleLionhartMetadata() {
        val raw = "KYLE LIONHART & EMILY REID - Sorry I'm Gone ||  || S || c67d8cfb-c431-4878-9368-6a0886c793fc"
        val cleaned = TrackSanitizer.sanitize(raw)
        assertEquals("KYLE LIONHART & EMILY REID - Sorry I'm Gone", cleaned)
    }

    @Test
    fun testStandardTrackWithoutJunk() {
        val raw = "Queen - Bohemian Rhapsody"
        val cleaned = TrackSanitizer.sanitize(raw)
        assertEquals("Queen - Bohemian Rhapsody", cleaned)
    }

    @Test
    fun testSongWithNumericTitlePreserved() {
        val raw = "Prince - 1999"
        val cleaned = TrackSanitizer.sanitize(raw)
        assertEquals("Prince - 1999", cleaned)
    }

    @Test
    fun testTrailingUrlRemoved() {
        val raw = "Pink Floyd - Comfortably Numb - http://pinkfloyd.com"
        val cleaned = TrackSanitizer.sanitize(raw)
        assertEquals("Pink Floyd - Comfortably Numb", cleaned)
    }

    @Test
    fun testTrailingUuidRemoved() {
        val raw = "Radiohead - Creep - 522b37ba-e2b7-4769-96e4-fde1c44c9da9"
        val cleaned = TrackSanitizer.sanitize(raw)
        assertEquals("Radiohead - Creep", cleaned)
    }

    @Test
    fun testSanitizeTrackInfoForSearch() {
        val raw = "Coldplay (feat. BTS) - My Universe"
        val searchClean = TrackSanitizer.sanitizeTrackInfo(raw)
        assertEquals("Coldplay My Universe", searchClean)
    }

    @Test
    fun testVirginRadioTildeDelimitedMetadata() {
        val raw = "SIMPLE MINDS~SOMEONE SOMEWHERE IN SUMMERTIME~NEW GOLD DREAM (81–82–83–84)~1982~~260~2026-11-21T18:07:46~2026-11-21T18:07:46~Virgin Radio"
        val cleaned = TrackSanitizer.sanitize(raw)
        assertEquals("SIMPLE MINDS - SOMEONE SOMEWHERE IN SUMMERTIME", cleaned)
    }

    @Test
    fun testVirginRadioParsedTrackFields() {
        val raw = "SIMPLE MINDS~SOMEONE SOMEWHERE IN SUMMERTIME~NEW GOLD DREAM (81–82–83–84)~1982~~260~2026-11-21T18:07:46~2026-11-21T18:07:46~Virgin Radio"
        val parsed = com.armanmaurya.internetradio.core.metadata.TrackMetadataEngine.parse(raw)
        assertEquals("SIMPLE MINDS", parsed.artist)
        assertEquals("SOMEONE SOMEWHERE IN SUMMERTIME", parsed.title)
        assertEquals("NEW GOLD DREAM (81–82–83–84)", parsed.album)
    }

    @Test
    fun testTwoSegmentTildeMetadata() {
        val raw = "Queen~Bohemian Rhapsody"
        val cleaned = TrackSanitizer.sanitize(raw)
        assertEquals("Queen - Bohemian Rhapsody", cleaned)
    }
}
