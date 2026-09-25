package com.armanmaurya.internetradio.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackModelTest {

    @Test
    fun playbackState_defaultValues_areCorrect() {
        val state = PlaybackState()

        assertNull(state.currentStation)
        assertTrue(state.currentPlaylist.isEmpty())
        assertEquals(-1, state.currentPlaylistIndex)
        assertFalse(state.isPlaying)
        assertFalse(state.isLoading)
        assertFalse(state.isError)
        assertEquals(1f, state.volume)
        assertEquals(PlaybackSource.None, state.playbackSource)
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
}
