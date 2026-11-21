package com.armanmaurya.internetradio.data.remote.lyrics.kugou

import retrofit2.http.GET
import retrofit2.http.Query

interface KuGouApi {
    @GET("search?ver=1&man=yes&client=mobi")
    suspend fun search(
        @Query("keyword") keyword: String,
        @Query("duration") duration: String = "",
        @Query("hash") hash: String = ""
    ): KuGouSearchResponse

    @GET("download?ver=1&client=mobi&fmt=lrc&charset=utf8")
    suspend fun download(
        @Query("id") id: String,
        @Query("accesskey") accessKey: String
    ): KuGouDownloadResponse
}
