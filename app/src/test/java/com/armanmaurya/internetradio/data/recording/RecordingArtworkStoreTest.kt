package com.armanmaurya.internetradio.data.recording

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingArtworkStoreTest {

    private val favicon = "https://example.com/logo.png"

    @Test
    fun needsArtworkUpdate_withoutArtwork_downloads() {
        assertTrue(RecordingArtworkStore.needsArtworkUpdate(null, false, favicon))
    }

    @Test
    fun needsArtworkUpdate_sameFavicon_skips() {
        assertFalse(RecordingArtworkStore.needsArtworkUpdate(favicon, true, favicon))
    }

    @Test
    fun needsArtworkUpdate_changedFavicon_downloads() {
        assertTrue(RecordingArtworkStore.needsArtworkUpdate("https://example.com/old.png", true, favicon))
    }

    @Test
    fun needsArtworkUpdate_artworkWithoutStationInfo_downloads() {
        assertTrue(RecordingArtworkStore.needsArtworkUpdate(null, true, favicon))
    }

    @Test
    fun needsArtworkUpdate_blankFavicon_skips() {
        assertFalse(RecordingArtworkStore.needsArtworkUpdate(null, false, ""))
    }

    @Test
    fun isArtworkFileName_matchesOnlyArtworkFiles() {
        assertTrue(RecordingArtworkStore.isArtworkFileName("folder.jpg"))
        assertTrue(RecordingArtworkStore.isArtworkFileName("station.json"))
        assertFalse(RecordingArtworkStore.isArtworkFileName("2026-10-29_14-00.mp3"))
        assertFalse(RecordingArtworkStore.isArtworkFileName("2026-10-29_14-00.mp3.json"))
    }
}
