package com.armanmaurya.internetradio.data.repository

import com.armanmaurya.internetradio.domain.lyrics.LyricsProvider
import com.armanmaurya.internetradio.domain.lyrics.LyricsProviderInfo
import com.armanmaurya.internetradio.domain.lyrics.LyricsQuery
import com.armanmaurya.internetradio.domain.model.LrcLine
import com.armanmaurya.internetradio.domain.model.Lyrics
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsRepositoryImplTest {

    private class FakeProvider(
        override val info: LyricsProviderInfo,
        override val priority: Int,
        override val supportsSyncedLyrics: Boolean,
        private val searchResult: (LyricsQuery) -> Lyrics?,
        private val locatorResult: (String) -> Lyrics? = { null }
    ) : LyricsProvider {
        var searchCalls = 0
        var locatorCalls = 0

        override suspend fun searchLyrics(query: LyricsQuery): Lyrics? {
            searchCalls++
            return searchResult(query)
        }

        override suspend fun fetchByLocator(token: String): Lyrics? {
            locatorCalls++
            return locatorResult(token)
        }
    }

    @Test
    fun `returns synced lyrics from highest priority provider`() = runBlocking {
        val p1 = FakeProvider(
            info = LyricsProviderInfo("p1", "Provider 1"),
            priority = 100,
            supportsSyncedLyrics = true,
            searchResult = {
                Lyrics(
                    plainLyrics = null,
                    syncedLyrics = listOf(LrcLine(1000L, "Hello")),
                    providerInfo = LyricsProviderInfo("p1", "Provider 1"),
                    locatorToken = "token1"
                )
            }
        )
        val p2 = FakeProvider(
            info = LyricsProviderInfo("p2", "Provider 2"),
            priority = 200,
            supportsSyncedLyrics = true,
            searchResult = {
                Lyrics(
                    plainLyrics = "Plain text",
                    syncedLyrics = null,
                    providerInfo = LyricsProviderInfo("p2", "Provider 2"),
                    locatorToken = "token2"
                )
            }
        )

        val repository = LyricsRepositoryImpl(setOf(p1, p2))
        val result = repository.getLyricsForTrack("Track", "Artist")

        assertNotNull(result)
        assertEquals("p1", result?.providerInfo?.id)
        assertEquals(1, p1.searchCalls)
        assertEquals(1, p2.searchCalls)
    }

    @Test
    fun `prefers word-synced lyrics over line-synced lyrics even from lower priority provider`() = runBlocking {
        val p1 = FakeProvider(
            info = LyricsProviderInfo("p1", "Provider 1"),
            priority = 100,
            supportsSyncedLyrics = true,
            searchResult = {
                Lyrics(
                    plainLyrics = null,
                    syncedLyrics = listOf(LrcLine(1000L, "Line synced only")),
                    providerInfo = LyricsProviderInfo("p1", "Provider 1"),
                    locatorToken = "token1"
                )
            }
        )
        val p2 = FakeProvider(
            info = LyricsProviderInfo("p2", "Provider 2"),
            priority = 200,
            supportsSyncedLyrics = true,
            searchResult = {
                Lyrics(
                    plainLyrics = null,
                    syncedLyrics = listOf(
                        LrcLine(
                            timestampMs = 1000L,
                            text = "Word synced",
                            words = listOf(com.armanmaurya.internetradio.domain.model.LrcWord(1000L, 2000L, "Word synced"))
                        )
                    ),
                    providerInfo = LyricsProviderInfo("p2", "Provider 2"),
                    locatorToken = "token2"
                )
            }
        )

        val repository = LyricsRepositoryImpl(setOf(p1, p2))
        val result = repository.getLyricsForTrack("Track", "Artist")

        assertNotNull(result)
        assertEquals("p2", result?.providerInfo?.id)
        assertEquals(true, result?.hasWordSync)
    }

    @Test
    fun `falls back to second provider when first returns plain lyrics but second has synced`() = runBlocking {
        val p1 = FakeProvider(
            info = LyricsProviderInfo("p1", "Provider 1"),
            priority = 100,
            supportsSyncedLyrics = false,
            searchResult = {
                Lyrics(
                    plainLyrics = "Plain only",
                    syncedLyrics = null,
                    providerInfo = LyricsProviderInfo("p1", "Provider 1"),
                    locatorToken = "token1"
                )
            }
        )
        val p2 = FakeProvider(
            info = LyricsProviderInfo("p2", "Provider 2"),
            priority = 200,
            supportsSyncedLyrics = true,
            searchResult = {
                Lyrics(
                    plainLyrics = null,
                    syncedLyrics = listOf(LrcLine(5000L, "Synced")),
                    providerInfo = LyricsProviderInfo("p2", "Provider 2"),
                    locatorToken = "token2"
                )
            }
        )

        val repository = LyricsRepositoryImpl(setOf(p1, p2))
        val result = repository.getLyricsForTrack("Track", "Artist")

        assertNotNull(result)
        assertEquals("p2", result?.providerInfo?.id) // Chose p2 because it had synced lyrics!
        assertEquals(1, p1.searchCalls)
        assertEquals(1, p2.searchCalls)
    }

    @Test
    fun `resolves exact lyrics by locator`() = runBlocking {
        val targetLyrics = Lyrics(
            plainLyrics = null,
            syncedLyrics = listOf(LrcLine(2000L, "Exact")),
            providerInfo = LyricsProviderInfo("p1", "Provider 1"),
            locatorToken = "42"
        )
        val p1 = FakeProvider(
            info = LyricsProviderInfo("p1", "Provider 1"),
            priority = 100,
            supportsSyncedLyrics = true,
            searchResult = { null },
            locatorResult = { token -> if (token == "42") targetLyrics else null }
        )

        val repository = LyricsRepositoryImpl(setOf(p1))
        val result = repository.getLyricsByLocator("p1:42")

        assertNotNull(result)
        assertEquals("p1", result?.providerInfo?.id)
        assertEquals("42", result?.locatorToken)
        assertEquals(1, p1.locatorCalls)
    }

    @Test
    fun `returns null for invalid locator`() = runBlocking {
        val repository = LyricsRepositoryImpl(emptySet())
        assertNull(repository.getLyricsByLocator("unknown:123"))
        assertNull(repository.getLyricsByLocator("invalid"))
    }

    @Test
    fun `respects custom provider order and disabled toggles from SettingsRepository`() = runBlocking {
        val p1 = FakeProvider(
            info = LyricsProviderInfo("p1", "Provider 1"),
            priority = 100,
            supportsSyncedLyrics = true,
            searchResult = {
                Lyrics(
                    plainLyrics = null,
                    syncedLyrics = listOf(LrcLine(1000L, "From P1")),
                    providerInfo = LyricsProviderInfo("p1", "Provider 1"),
                    locatorToken = "t1"
                )
            }
        )
        val p2 = FakeProvider(
            info = LyricsProviderInfo("p2", "Provider 2"),
            priority = 200,
            supportsSyncedLyrics = true,
            searchResult = {
                Lyrics(
                    plainLyrics = null,
                    syncedLyrics = listOf(LrcLine(2000L, "From P2")),
                    providerInfo = LyricsProviderInfo("p2", "Provider 2"),
                    locatorToken = "t2"
                )
            }
        )

        // Case 1: Custom order puts p2 before p1 -> p2 should be queried first
        val fakeSettings = FakeSettingsRepository().apply {
            setLyricsProviderOrder(listOf("p2", "p1"))
            setLyricsProviderEnabled("p1", true)
            setLyricsProviderEnabled("p2", true)
        }

        val repository = LyricsRepositoryImpl(setOf(p1, p2), fakeSettings)
        val result = repository.getLyricsForTrack("Custom Order Song", "Artist")

        assertNotNull(result)
        assertEquals("p2", result?.providerInfo?.id)
        assertEquals(1, p1.searchCalls)
        assertEquals(1, p2.searchCalls)

        // Case 2: p2 is disabled -> only p1 should be called
        p1.searchCalls = 0
        p2.searchCalls = 0
        fakeSettings.setLyricsProviderEnabled("p2", false)

        val result2 = repository.getLyricsForTrack("Disabled Song", "Artist")
        assertNotNull(result2)
        assertEquals("p1", result2?.providerInfo?.id)
        assertEquals(1, p1.searchCalls)
        assertEquals(0, p2.searchCalls)
    }

    @Test
    fun `getLyricsSearchResult preserves all provider candidates`() = runBlocking {
        val p1 = FakeProvider(
            info = LyricsProviderInfo("p1", "Provider 1"),
            priority = 100,
            supportsSyncedLyrics = true,
            searchResult = {
                Lyrics(
                    plainLyrics = null,
                    syncedLyrics = listOf(LrcLine(1000L, "Line from P1")),
                    providerInfo = LyricsProviderInfo("p1", "Provider 1"),
                    locatorToken = "token1"
                )
            }
        )
        val p2 = FakeProvider(
            info = LyricsProviderInfo("p2", "Provider 2"),
            priority = 200,
            supportsSyncedLyrics = true,
            searchResult = {
                Lyrics(
                    plainLyrics = "Plain from P2",
                    syncedLyrics = null,
                    providerInfo = LyricsProviderInfo("p2", "Provider 2"),
                    locatorToken = "token2"
                )
            }
        )

        val repository = LyricsRepositoryImpl(setOf(p1, p2))
        val searchResult = repository.getLyricsSearchResult("Track", "Artist")

        assertNotNull(searchResult.selectedLyrics)
        assertEquals("p1", searchResult.selectedLyrics?.providerInfo?.id)
        assertEquals(2, searchResult.allCandidates.size)
        assertEquals(setOf("p1", "p2"), searchResult.allCandidates.map { it.providerInfo.id }.toSet())
    }

    @Test
    fun `getLyricsFromProvider targets specific provider directly`() = runBlocking {
        val p1 = FakeProvider(
            info = LyricsProviderInfo("p1", "Provider 1"),
            priority = 100,
            supportsSyncedLyrics = true,
            searchResult = {
                Lyrics(
                    plainLyrics = "P1 Lyrics",
                    syncedLyrics = null,
                    providerInfo = LyricsProviderInfo("p1", "Provider 1")
                )
            }
        )
        val p2 = FakeProvider(
            info = LyricsProviderInfo("p2", "Provider 2"),
            priority = 200,
            supportsSyncedLyrics = true,
            searchResult = {
                Lyrics(
                    plainLyrics = "P2 Lyrics",
                    syncedLyrics = null,
                    providerInfo = LyricsProviderInfo("p2", "Provider 2")
                )
            }
        )

        val repository = LyricsRepositoryImpl(setOf(p1, p2))
        val lyrics = repository.getLyricsFromProvider("p2", "Direct Track", "Direct Artist")

        assertNotNull(lyrics)
        assertEquals("p2", lyrics?.providerInfo?.id)
        assertEquals(0, p1.searchCalls)
        assertEquals(1, p2.searchCalls)
    }
}
