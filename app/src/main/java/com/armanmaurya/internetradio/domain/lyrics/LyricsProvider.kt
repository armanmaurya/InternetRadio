package com.armanmaurya.internetradio.domain.lyrics

import com.armanmaurya.internetradio.domain.model.Lyrics

interface LyricsProvider {
    val info: LyricsProviderInfo
    val priority: Int
    val supportsSyncedLyrics: Boolean

    suspend fun searchLyrics(query: LyricsQuery): Lyrics?
    suspend fun fetchByLocator(token: String): Lyrics?
}
