package com.armanmaurya.internetradio.data.remote.lyrics.betterlyrics

import retrofit2.http.GET
import retrofit2.http.Query

interface BetterLyricsApi {
    @GET("getLyrics")
    suspend fun getLyrics(
        @Query("s") song: String,
        @Query("a") artist: String? = null
    ): BetterLyricsResponse
}
