package com.armanmaurya.internetradio.domain.model

data class LrcLine(val timestampMs: Long, val text: String)

data class Lyrics(
    val plainLyrics: String?,
    val syncedLyrics: List<LrcLine>?
)
