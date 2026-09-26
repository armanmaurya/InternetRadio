package com.armanmaurya.internetradio.domain.repository

import com.armanmaurya.internetradio.domain.model.Lyrics

interface LyricsRepository {
    suspend fun getLyricsForTrack(trackName: String, artistName: String? = null): Lyrics?
}
