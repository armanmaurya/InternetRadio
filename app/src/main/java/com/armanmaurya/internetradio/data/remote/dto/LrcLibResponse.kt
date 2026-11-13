package com.armanmaurya.internetradio.data.remote.dto

import com.google.gson.annotations.SerializedName

data class LrcLibResponse(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val duration: Double,
    val instrumental: Boolean,
    val plainLyrics: String?,
    val syncedLyrics: String?,
    /** True when [lyricsfile] carries word-level timings. Missing in older responses. */
    val hasWordSync: Boolean? = null,
    /** Raw Lyricsfile YAML (see LyricsfileParser). */
    val lyricsfile: String? = null
)
