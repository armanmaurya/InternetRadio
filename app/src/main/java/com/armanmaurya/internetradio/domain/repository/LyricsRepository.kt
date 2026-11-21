package com.armanmaurya.internetradio.domain.repository

import com.armanmaurya.internetradio.domain.lyrics.LyricsProviderInfo
import com.armanmaurya.internetradio.domain.model.Lyrics

data class LyricsSearchResult(
    val selectedLyrics: Lyrics?,
    val allCandidates: List<Lyrics> = emptyList()
)

interface LyricsRepository {
    /**
     * Lyrics for the track, or null when none exist.
     * @throws LyricsFetchException when they couldn't be fetched right now (rate limit, network).
     */
    suspend fun getLyricsForTrack(trackName: String, artistName: String? = null): Lyrics?

    /**
     * Searches all providers in parallel and returns both the best candidate and all candidates found.
     */
    suspend fun getLyricsSearchResult(trackName: String, artistName: String? = null): LyricsSearchResult {
        val selected = getLyricsForTrack(trackName, artistName)
        return LyricsSearchResult(selected, listOfNotNull(selected))
    }

    /**
     * Fetches lyrics explicitly from a specific provider.
     */
    suspend fun getLyricsFromProvider(providerId: String, trackName: String, artistName: String? = null): Lyrics? = null

    /**
     * Retrieves lyrics using a previously stored provider locator (e.g. "lrclib:2392").
     * Returns null if the locator cannot be resolved.
     */
    suspend fun getLyricsByLocator(locator: String): Lyrics?

    /**
     * Returns the list of all available lyrics providers.
     */
    fun getAllProviders(): List<LyricsProviderInfo> = emptyList()
}

/** Temporary failure fetching lyrics; unlike a null result, it says nothing about whether lyrics exist. */
class LyricsFetchException(message: String, cause: Throwable? = null) : Exception(message, cause)
