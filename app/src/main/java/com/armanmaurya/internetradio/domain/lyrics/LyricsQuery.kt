package com.armanmaurya.internetradio.domain.lyrics

data class LyricsQuery(
    val trackTitle: String,
    val artistName: String? = null,
    val albumName: String? = null,
    val durationMs: Long? = null
)
