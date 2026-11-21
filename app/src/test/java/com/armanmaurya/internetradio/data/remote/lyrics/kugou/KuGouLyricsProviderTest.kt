package com.armanmaurya.internetradio.data.remote.lyrics.kugou

import com.armanmaurya.internetradio.domain.lyrics.LyricsQuery
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.Base64

class KuGouLyricsProviderTest {

    private class FakeKuGouApi(
        private val searchHandler: (String) -> KuGouSearchResponse,
        private val downloadHandler: (String, String) -> KuGouDownloadResponse
    ) : KuGouApi {
        override suspend fun search(keyword: String, duration: String, hash: String): KuGouSearchResponse {
            return searchHandler(keyword)
        }

        override suspend fun download(id: String, accessKey: String): KuGouDownloadResponse {
            return downloadHandler(id, accessKey)
        }
    }

    @Test
    fun `searches and decodes base64 lrc lyrics from kugou`() = runBlocking {
        val rawLrc = "[00:10.50]KuGou lyrics line\n[00:15.00]Second line"
        val base64Content = Base64.getEncoder().encodeToString(rawLrc.toByteArray(Charsets.UTF_8))

        val api = FakeKuGouApi(
            searchHandler = {
                KuGouSearchResponse(
                    status = 200,
                    candidates = listOf(
                        KuGouCandidate(id = "1001", accessKey = "KEY123", song = "Song", singer = "Artist")
                    )
                )
            },
            downloadHandler = { id, key ->
                KuGouDownloadResponse(status = 200, content = base64Content, fmt = "lrc")
            }
        )

        val provider = KuGouLyricsProvider(api)
        val result = provider.searchLyrics(LyricsQuery(trackTitle = "Song", artistName = "Artist"))

        assertNotNull(result)
        assertEquals("kugou", result?.providerInfo?.id)
        assertEquals("kugou:1001|KEY123", result?.locator)
        assertEquals(2, result?.syncedLyrics?.size)
        assertEquals(10500L, result?.syncedLyrics?.get(0)?.timestampMs)
        assertEquals("KuGou lyrics line", result?.syncedLyrics?.get(0)?.text)
    }

    @Test
    fun `fetches directly by locator`() = runBlocking {
        val rawLrc = "[00:05.00]Direct locator line"
        val base64Content = Base64.getEncoder().encodeToString(rawLrc.toByteArray(Charsets.UTF_8))

        val api = FakeKuGouApi(
            searchHandler = { KuGouSearchResponse() },
            downloadHandler = { id, key ->
                assertEquals("2002", id)
                assertEquals("KEYABC", key)
                KuGouDownloadResponse(status = 200, content = base64Content, fmt = "lrc")
            }
        )

        val provider = KuGouLyricsProvider(api)
        val result = provider.fetchByLocator("2002|KEYABC")

        assertNotNull(result)
        assertEquals(1, result?.syncedLyrics?.size)
        assertEquals("Direct locator line", result?.syncedLyrics?.get(0)?.text)
        assertEquals("kugou:2002|KEYABC", result?.locator)
    }
}
