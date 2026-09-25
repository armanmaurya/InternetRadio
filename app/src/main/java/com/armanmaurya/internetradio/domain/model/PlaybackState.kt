package com.armanmaurya.internetradio.domain.model

data class PlaybackState(
    val currentStation: RadioStation? = null,
    val currentPlaylist: List<RadioStation> = emptyList(),
    val currentPlaylistIndex: Int = -1,
    val currentTrack: String? = null,
    val cleanTrackName: String? = null,
    val cleanArtistName: String? = null,
    val rawTrackName: String? = null,
    val trackStartTime: Long? = null,
    val lyricsSyncOffsetMs: Long = 0L,
    val trackCoverArtUri: String? = null,
    val isFetchingArtwork: Boolean = false,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val sleepTimerEndTime: Long? = null,
    val sleepTimerTotalDuration: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val volume: Float = 1f,
    val sessionActiveDurationMs: Long = 0L,
    val sessionResumeTimeMs: Long? = null,
    val playbackSource: PlaybackSource = PlaybackSource.None,
    val streamCodec: String? = null,
    val streamBitrate: Int? = null
)
