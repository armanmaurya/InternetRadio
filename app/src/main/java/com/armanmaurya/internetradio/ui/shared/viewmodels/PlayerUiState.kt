package com.armanmaurya.internetradio.ui.shared.viewmodels

import com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity
import com.armanmaurya.internetradio.domain.model.CastDevice
import com.armanmaurya.internetradio.domain.model.PlaybackSource
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.model.RecordingFile
import com.armanmaurya.internetradio.domain.model.RecordingSession

data class PlayerUiState(
    val currentStation: RadioStation? = null,
    val currentPlaylist: List<RadioStation> = emptyList(),
    val currentPlaylistIndex: Int = -1,
    val currentTrack: String? = null,
    val cleanTrackName: String? = null,
    val cleanArtistName: String? = null,
    val rawTrackName: String? = null,
    val trackStartTime: Long? = null,
    val trackCoverArtUri: String? = null,
    val isFetchingArtwork: Boolean = false,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val volume: Float = 1f,
    val sleepTimerEndTime: Long? = null,
    val sleepTimerTotalDuration: Long = 0L,
    val lyricsSyncOffsetMs: Long = 0L,
    val streamCodec: String? = null,
    val streamBitrate: Int? = null,
    val sessionActiveDurationMs: Long = 0L,
    val sessionResumeTimeMs: Long? = null,
    val playbackSource: PlaybackSource = PlaybackSource.None,
    val isFavorite: Boolean = false,
    val isRecording: Boolean = false,
    val recordingDuration: Long = 0L,
    val connectedCastDevice: CastDevice? = null,
    val isCasting: Boolean = false,
    val castVolume: Float = 1f,
    val amplitude: Float = 0f,
    val retryCountdown: Int? = null,
    val lyricsUiState: PlayerViewModel.LyricsUiState = PlayerViewModel.LyricsUiState.Loading,
    val trackHistory: List<TrackHistoryEntity> = emptyList(),
    val stationRecordings: List<RecordingFile>? = null,
    val activeSessions: Map<String, RecordingSession> = emptyMap()
)
