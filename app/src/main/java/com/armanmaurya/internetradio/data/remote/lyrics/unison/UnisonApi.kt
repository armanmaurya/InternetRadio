package com.armanmaurya.internetradio.data.remote.lyrics.unison

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface UnisonApi {
    @GET("lyrics")
    suspend fun getLyrics(
        @Query("song") song: String,
        @Query("artist") artist: String? = null
    ): UnisonResponse

    @GET("lyrics/{id}")
    suspend fun getLyricsById(
        @Path("id") id: Long
    ): UnisonResponse
}
