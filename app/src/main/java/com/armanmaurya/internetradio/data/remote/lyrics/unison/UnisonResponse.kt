package com.armanmaurya.internetradio.data.remote.lyrics.unison

import com.google.gson.annotations.SerializedName

data class UnisonResponse(
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("data") val data: UnisonData? = null,
    @SerializedName("error") val error: String? = null
)

data class UnisonData(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("song") val song: String? = null,
    @SerializedName("artist") val artist: String? = null,
    @SerializedName("lyrics") val lyrics: String? = null,
    @SerializedName("format") val format: String? = null,
    @SerializedName("syncType") val syncType: String? = null
)
