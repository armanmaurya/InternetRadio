package com.armanmaurya.internetradio.data.remote.lyrics.betterlyrics

import com.google.gson.annotations.SerializedName

data class BetterLyricsResponse(
    val success: Boolean? = true,
    val data: BetterLyricsData? = null
)

data class BetterLyricsData(
    val id: Long? = null,
    val videoId: String? = null,
    val song: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val lyrics: String? = null,
    val format: String? = null, // "lrc", "ttml", "plain"
    val syncType: String? = null, // "richsync", "linesync", "plain"
    val confidence: String? = null
)
