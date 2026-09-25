package com.armanmaurya.internetradio.domain.model

sealed class PlaybackSource {
    data class Browse(
        val name: String,
        val countryCode: String?,
        val language: String?,
        val tagList: String?,
        val order: String,
        val reverse: Boolean
    ) : PlaybackSource()

    object Library : PlaybackSource()
    object Recent : PlaybackSource()
    object None : PlaybackSource()
}
