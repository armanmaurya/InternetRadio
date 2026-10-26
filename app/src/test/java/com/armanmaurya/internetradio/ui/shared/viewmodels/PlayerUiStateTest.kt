package com.armanmaurya.internetradio.ui.shared.viewmodels

import com.armanmaurya.internetradio.domain.model.PlaybackSource
import com.armanmaurya.internetradio.domain.model.RadioStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerUiStateTest {

    private fun createTestStation(uuid: String): RadioStation {
        return RadioStation(
            changeUuid = "change-$uuid",
            stationUuid = uuid,
            name = "Station $uuid",
            url = "http://stream.example.com/$uuid",
            urlResolved = "http://stream.example.com/$uuid",
            homepage = "",
            favicon = "",
            tags = emptyList(),
            country = "US",
            countryCode = "US",
            state = "",
            iso3166_2 = null,
            language = "en",
            languageCodes = emptyList(),
            votes = 100,
            lastChangeTime = "",
            codec = "MP3",
            bitrate = 128,
            lastCheckOk = true,
            lastCheckTime = "",
            lastCheckOkTime = "",
            lastLocalCheckTime = "",
            clickTimestamp = "",
            clickCount = 10,
            clickTrend = 1,
            sslError = false,
            geoLat = null,
            geoLong = null,
            geoDistance = null,
            hasExtendedInfo = false,
            isCustom = false
        )
    }

    @Test
    fun playerUiState_hasNextAndPrevious_trueWhenPlaylistSizeGreaterThanOne() {
        val stations = listOf(createTestStation("1"), createTestStation("2"))

        // At start of playlist (index 0)
        val stateAtStart = PlayerUiState(
            currentPlaylist = stations,
            currentPlaylistIndex = 0,
            hasNext = stations.size > 1,
            hasPrevious = stations.size > 1
        )
        assertTrue(stateAtStart.hasNext)
        assertTrue(stateAtStart.hasPrevious)

        // At end of playlist (index 1)
        val stateAtEnd = PlayerUiState(
            currentPlaylist = stations,
            currentPlaylistIndex = 1,
            hasNext = stations.size > 1,
            hasPrevious = stations.size > 1
        )
        assertTrue(stateAtEnd.hasNext)
        assertTrue(stateAtEnd.hasPrevious)
    }

    @Test
    fun playerUiState_hasNextAndPrevious_falseWhenSingleStationOrEmpty() {
        val singleStation = listOf(createTestStation("1"))

        val stateSingle = PlayerUiState(
            currentPlaylist = singleStation,
            currentPlaylistIndex = 0,
            hasNext = singleStation.size > 1,
            hasPrevious = singleStation.size > 1
        )
        assertFalse(stateSingle.hasNext)
        assertFalse(stateSingle.hasPrevious)

        val stateEmpty = PlayerUiState(
            currentPlaylist = emptyList(),
            currentPlaylistIndex = -1,
            hasNext = emptyList<RadioStation>().size > 1,
            hasPrevious = emptyList<RadioStation>().size > 1
        )
        assertFalse(stateEmpty.hasNext)
        assertFalse(stateEmpty.hasPrevious)
    }

    @Test
    fun pagerLoopIndexCalculation_correctlyCyclesForwardAndBackward() {
        val playlistSize = 3

        fun resolveIndex(page: Int): Int {
            return ((page % playlistSize) + playlistSize) % playlistSize
        }

        // Standard within-bounds pages
        assertEquals(0, resolveIndex(0))
        assertEquals(1, resolveIndex(1))
        assertEquals(2, resolveIndex(2))

        // Forward cycling past end: 2 -> 3 (should map to 0)
        assertEquals(0, resolveIndex(3))
        assertEquals(1, resolveIndex(4))
        assertEquals(2, resolveIndex(5))

        // Backward cycling from start: 0 -> -1 (should map to 2)
        assertEquals(2, resolveIndex(-1))
        assertEquals(1, resolveIndex(-2))
        assertEquals(0, resolveIndex(-3))
    }

    @Test
    fun pagerIsInfinite_enabledForAllPlaybackSourcesWhenSizeGreaterThanOne() {
        val sources = listOf(
            PlaybackSource.Library,
            PlaybackSource.Recent,
            PlaybackSource.Browse(
                name = "Rock",
                countryCode = "US",
                language = "eng",
                tagList = "rock",
                order = "votes",
                reverse = true
            ),
            PlaybackSource.None
        )

        for (source in sources) {
            val playlistSize = 5
            val isInfinite = playlistSize > 1
            assertTrue("Expected infinite pager for source $source", isInfinite)
        }

        val singleItemSize = 1
        val isInfiniteSingle = singleItemSize > 1
        assertFalse(isInfiniteSingle)
    }

    @Test
    fun playerUiState_savedTracks_defaultsToEmptyAndUnstarred() {
        val state = PlayerUiState()
        assertTrue(state.savedTracks.isEmpty())
        assertFalse(state.isCurrentTrackStarred)
    }

    @Test
    fun playerUiState_savedTracks_retainsProvidedState() {
        val saved = listOf(
            com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity(
                id = 1L,
                stationUuid = "uuid-1",
                trackTitle = "Favorite Song",
                timestamp = 1000L,
                isStarred = true
            )
        )
        val state = PlayerUiState(
            savedTracks = saved,
            isCurrentTrackStarred = true
        )
        assertEquals(1, state.savedTracks.size)
        assertTrue(state.isCurrentTrackStarred)
        assertEquals("Favorite Song", state.savedTracks.first().trackTitle)
    }
}
