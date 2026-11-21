package com.armanmaurya.internetradio.data.remote.lyrics.betterlyrics

import com.armanmaurya.internetradio.domain.lyrics.LyricsQuery
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BetterLyricsProviderTest {

    private class FakeBetterLyricsApi(
        private val responseHandler: (String, String?) -> BetterLyricsResponse
    ) : BetterLyricsApi {
        override suspend fun getLyrics(song: String, artist: String?): BetterLyricsResponse {
            return responseHandler(song, artist)
        }
    }

    @Test
    fun `parses synchronized lrc lyrics`() = runBlocking {
        val lrc = """
            [00:01.00]Line one
            [00:05.50]Line two
        """.trimIndent()

        val api = FakeBetterLyricsApi { song, artist ->
            BetterLyricsResponse(
                success = true,
                data = BetterLyricsData(
                    id = 1234L,
                    song = "Test Song",
                    artist = "Test Artist",
                    lyrics = lrc,
                    format = "lrc",
                    syncType = "linesync"
                )
            )
        }

        val provider = BetterLyricsProvider(api)
        val result = provider.searchLyrics(LyricsQuery(trackTitle = "Test Song", artistName = "Test Artist"))

        assertNotNull(result)
        assertEquals("betterlyrics", result?.providerInfo?.id)
        assertEquals(2, result?.syncedLyrics?.size)
        assertEquals(1000L, result?.syncedLyrics?.get(0)?.timestampMs)
        assertEquals("Line one", result?.syncedLyrics?.get(0)?.text)
        assertEquals(5500L, result?.syncedLyrics?.get(1)?.timestampMs)
        assertEquals("Test Song|Test Artist", result?.locatorToken)
        assertEquals("betterlyrics:Test Song|Test Artist", result?.locator)
    }

    @Test
    fun `parses plain lyrics when format is plain`() = runBlocking {
        val api = FakeBetterLyricsApi { _, _ ->
            BetterLyricsResponse(
                success = true,
                data = BetterLyricsData(
                    song = "Plain Song",
                    artist = "Plain Artist",
                    lyrics = "Plain words here",
                    format = "plain"
                )
            )
        }

        val provider = BetterLyricsProvider(api)
        val result = provider.searchLyrics(LyricsQuery(trackTitle = "Plain Song"))

        assertNotNull(result)
        assertEquals("Plain words here", result?.plainLyrics)
        assertNull(result?.syncedLyrics)
    }

    @Test
    fun `fetches by locator token`() = runBlocking {
        val api = FakeBetterLyricsApi { song, artist ->
            BetterLyricsResponse(
                success = true,
                data = BetterLyricsData(
                    song = song,
                    artist = artist,
                    lyrics = "[00:02.00]Locator line",
                    format = "lrc"
                )
            )
        }

        val provider = BetterLyricsProvider(api)
        val result = provider.fetchByLocator("Song Name|Artist Name")

        assertNotNull(result)
        assertEquals(1, result?.syncedLyrics?.size)
        assertEquals("Locator line", result?.syncedLyrics?.get(0)?.text)
    }
}
