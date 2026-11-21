package com.armanmaurya.internetradio.data.remote.lyrics.lrclib

import com.armanmaurya.internetradio.data.remote.LrcLibApi
import com.armanmaurya.internetradio.data.remote.dto.LrcLibResponse
import com.armanmaurya.internetradio.domain.lyrics.LyricsQuery
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LrcLibLyricsProviderTest {

    private class FakeLrcLibApi : LrcLibApi {
        var queryTrack: String? = null
        var queryArtist: String? = null
        var queryGetId: Long? = null

        override suspend fun searchLyrics(query: String): List<LrcLibResponse> {
            queryTrack = query
            return listOf(
                LrcLibResponse(
                    id = 101L,
                    trackName = query,
                    artistName = "Artist",
                    albumName = "Album",
                    duration = 180.0,
                    instrumental = false,
                    plainLyrics = "Plain lyrics",
                    syncedLyrics = "[00:10.00]Synced lyrics"
                )
            )
        }

        override suspend fun searchLyricsExplicit(trackName: String, artistName: String?): List<LrcLibResponse> {
            queryTrack = trackName
            queryArtist = artistName
            return listOf(
                LrcLibResponse(
                    id = 102L,
                    trackName = trackName,
                    artistName = artistName.orEmpty(),
                    albumName = "Album",
                    duration = 200.0,
                    instrumental = false,
                    plainLyrics = null,
                    syncedLyrics = "[00:15.00]Explicit line"
                )
            )
        }

        override suspend fun getLyricsById(id: Long): LrcLibResponse {
            queryGetId = id
            return LrcLibResponse(
                id = id,
                trackName = "Direct Track",
                artistName = "Direct Artist",
                albumName = null,
                duration = 150.0,
                instrumental = false,
                plainLyrics = "Direct plain",
                syncedLyrics = "[00:05.00]Direct line"
            )
        }
    }

    @Test
    fun `searchLyrics returns synced lyrics with locator`() = runBlocking {
        val api = FakeLrcLibApi()
        val provider = LrcLibLyricsProvider(api)

        val result = provider.searchLyrics(LyricsQuery(trackTitle = "Song Title", artistName = "Artist Name"))

        assertNotNull(result)
        assertEquals("lrclib", result?.providerInfo?.id)
        assertEquals("102", result?.locatorToken)
        assertEquals("lrclib:102", result?.locator)
        assertEquals(1, result?.syncedLyrics?.size)
        assertEquals(15000L, result?.syncedLyrics?.first()?.timestampMs)
    }

    @Test
    fun `fetchByLocator resolves by id`() = runBlocking {
        val api = FakeLrcLibApi()
        val provider = LrcLibLyricsProvider(api)

        val result = provider.fetchByLocator("999")

        assertNotNull(result)
        assertEquals(999L, api.queryGetId)
        assertEquals("999", result?.locatorToken)
        assertEquals("lrclib:999", result?.locator)
        assertEquals("Direct Track", result?.trackTitle)
    }
}
