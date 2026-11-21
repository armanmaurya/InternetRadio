package com.armanmaurya.internetradio.data.remote.lyrics.lyricsplus

import com.armanmaurya.internetradio.domain.lyrics.LyricsQuery
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LyricsPlusProviderTest {

    private class FakeLyricsPlusApi(
        private val responseHandler: (String, String?) -> LyricsPlusResponse
    ) : LyricsPlusApi {
        override suspend fun getLyrics(title: String, artist: String?, source: String): LyricsPlusResponse {
            return responseHandler(title, artist)
        }
    }

    @Test
    fun `parses word and line synced lyrics from lyricsplus`() = runBlocking {
        val api = FakeLyricsPlusApi { title, artist ->
            LyricsPlusResponse(
                type = "Word",
                lyrics = listOf(
                    LyricsPlusLine(
                        time = 14200L,
                        text = "Hello world",
                        duration = 3000L,
                        syllabus = listOf(
                            LyricsPlusSyllable(text = "Hello ", time = 14200L, duration = 1000L),
                            LyricsPlusSyllable(text = "world", time = 15200L, duration = 2000L)
                        )
                    )
                )
            )
        }

        val provider = LyricsPlusProvider(api)
        val result = provider.searchLyrics(LyricsQuery(trackTitle = "Hello", artistName = "Adele"))

        assertNotNull(result)
        assertEquals("lyricsplus", result?.providerInfo?.id)
        assertEquals("lyricsplus:Hello|Adele", result?.locator)
        assertEquals(1, result?.syncedLyrics?.size)

        val line = result?.syncedLyrics?.first()
        assertEquals(14200L, line?.timestampMs)
        assertEquals("Hello world", line?.text)
        assertEquals(2, line?.words?.size)
        assertEquals("Hello ", line?.words?.get(0)?.text)
        assertEquals(14200L, line?.words?.get(0)?.startMs)
        assertEquals(15200L, line?.words?.get(0)?.endMs)
    }
}
