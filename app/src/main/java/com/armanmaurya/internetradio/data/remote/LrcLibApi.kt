package com.armanmaurya.internetradio.data.remote

import com.armanmaurya.internetradio.data.remote.dto.LrcLibResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface LrcLibApi {
    @GET("api/search")
    suspend fun searchLyrics(
        @Query("q") query: String
    ): List<LrcLibResponse>

    @GET("api/search")
    suspend fun searchLyricsExplicit(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String? = null
    ): List<LrcLibResponse>

    @GET("api/get/{id}")
    suspend fun getLyricsById(
        @retrofit2.http.Path("id") id: Long
    ): LrcLibResponse
}
