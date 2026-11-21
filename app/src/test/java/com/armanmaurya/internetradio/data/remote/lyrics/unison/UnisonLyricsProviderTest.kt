package com.armanmaurya.internetradio.data.remote.lyrics.unison

import com.armanmaurya.internetradio.domain.lyrics.LyricsQuery
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class UnisonLyricsProviderTest {

    private class FakeUnisonApi(
        private val searchHandler: (String, String?) -> UnisonResponse,
        private val getByIdHandler: (Long) -> UnisonResponse
    ) : UnisonApi {
        override suspend fun getLyrics(song: String, artist: String?): UnisonResponse {
            return searchHandler(song, artist)
        }

        override suspend fun getLyricsById(id: Long): UnisonResponse {
            return getByIdHandler(id)
        }
    }

    @Test
    fun `searches and parses synced lyrics from unison`() = runBlocking {
        val lrc = "[00:03.20]Unison line"
        val api = FakeUnisonApi(
            searchHandler = { song, artist ->
                UnisonResponse(
                    success = true,
                    data = UnisonData(
                        id = 7777L,
                        song = song,
                        artist = artist,
                        lyrics = lrc,
                        format = "lrc"
                    )
                )
            },
            getByIdHandler = { UnisonResponse() }
        )

        val provider = UnisonLyricsProvider(api)
        val result = provider.searchLyrics(LyricsQuery(trackTitle = "Unison Song", artistName = "Unison Artist"))

        assertNotNull(result)
        assertEquals("unison", result?.providerInfo?.id)
        assertEquals("unison:7777", result?.locator)
        assertEquals(3200L, result?.syncedLyrics?.first()?.timestampMs)
        assertEquals("Unison line", result?.syncedLyrics?.first()?.text)
    }

    @Test
    fun `fetches directly by numeric id locator`() = runBlocking {
        val api = FakeUnisonApi(
            searchHandler = { _, _ -> UnisonResponse() },
            getByIdHandler = { id ->
                assertEquals(8888L, id)
                UnisonResponse(
                    success = true,
                    data = UnisonData(
                        id = id,
                        song = "Found Song",
                        artist = "Found Artist",
                        lyrics = "[00:10.00]Found by id",
                        format = "lrc"
                    )
                )
            }
        )

        val provider = UnisonLyricsProvider(api)
        val result = provider.fetchByLocator("8888")

        assertNotNull(result)
        assertEquals("unison:8888", result?.locator)
        assertEquals("Found by id", result?.syncedLyrics?.first()?.text)
    }
}
