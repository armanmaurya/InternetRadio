package com.armanmaurya.internetradio.domain.model

data class CastDevice(
    val id: String,
    val name: String,
    val host: String = "",
    val port: Int = 0
)

enum class CastPlaybackState {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED;

    val isPlaying: Boolean get() = this == PLAYING
    val isBuffering: Boolean get() = this == BUFFERING
}