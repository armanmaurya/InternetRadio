package com.armanmaurya.internetradio.data.remote.lyrics.lyricsplus

import retrofit2.http.GET
import retrofit2.http.Query

interface LyricsPlusApi {
    @GET("v2/lyrics/get")
    suspend fun getLyrics(
        @Query("title") title: String,
        @Query("artist") artist: String? = null,
        @Query("source") source: String = "apple,lyricsplus,musixmatch,spotify"
    ): LyricsPlusResponse
}
