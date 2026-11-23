package com.armanmaurya.internetradio.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackModelTest {

    @Test
    fun playbackSession_defaultValues_areCorrect() {
        val session = PlaybackSession()

        assertNull(session.currentStation)
        assertTrue(session.currentPlaylist.isEmpty())
        assertEquals(-1, session.currentPlaylistIndex)
        assertFalse(session.isPlaying)
        assertEquals(1f, session.volume)
        assertEquals(PlaybackSource.None, session.playbackSource)
    }

    @Test
    fun playbackSource_types_instantiateCorrectly() {
        val browse = PlaybackSource.Browse(
            name = "Jazz",
            countryCode = "US",
            language = "eng",
            tagList = "jazz,blues",
            order = "votes",
            reverse = true
        )

        assertEquals("Jazz", browse.name)
        assertEquals("US", browse.countryCode)
        assertEquals(PlaybackSource.Library, PlaybackSource.Library)
        assertEquals(PlaybackSource.Recent, PlaybackSource.Recent)
        assertEquals(PlaybackSource.None, PlaybackSource.None)
    }

    @Test
    fun appPreferences_autoSwitchOnStreamEnd_defaultsToFalse() {
        val prefs = AppPreferences()
        assertFalse("autoSwitchOnStreamEnd should default to false to prevent unexpected station hopping", prefs.autoSwitchOnStreamEnd)
    }

    @Test
    fun appPreferences_autoSwitchOnStreamEnd_canBeEnabled() {
        val prefs = AppPreferences(autoSwitchOnStreamEnd = true)
        assertTrue(prefs.autoSwitchOnStreamEnd)
    }
}
