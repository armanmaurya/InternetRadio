package com.armanmaurya.internetradio.data.remote.lyrics.lyricsplus

import com.google.gson.annotations.SerializedName

data class LyricsPlusResponse(
    @SerializedName("type") val type: String? = null,
    @SerializedName("lyrics") val lyrics: List<LyricsPlusLine>? = null,
    @SerializedName("error") val error: String? = null
)

data class LyricsPlusLine(
    @SerializedName("time") val time: Long? = null,
    @SerializedName("text") val text: String? = null,
    @SerializedName("duration") val duration: Long? = null,
    @SerializedName("syllabus") val syllabus: List<LyricsPlusSyllable>? = null
)

data class LyricsPlusSyllable(
    @SerializedName("text") val text: String? = null,
    @SerializedName("time") val time: Long? = null,
    @SerializedName("duration") val duration: Long? = null
)
