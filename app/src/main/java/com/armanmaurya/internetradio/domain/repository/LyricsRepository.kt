package com.armanmaurya.internetradio.domain.repository

import com.armanmaurya.internetradio.domain.model.Lyrics

interface LyricsRepository {
    /**
     * Lyrics for the track, or null when none exist.
     * @throws LyricsFetchException when they couldn't be fetched right now (rate limit, network).
     */
    suspend fun getLyricsForTrack(trackName: String, artistName: String? = null): Lyrics?
}

/** Temporary failure fetching lyrics; unlike a null result, it says nothing about whether lyrics exist. */
class LyricsFetchException(message: String, cause: Throwable? = null) : Exception(message, cause)
