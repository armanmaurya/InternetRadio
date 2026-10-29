package com.armanmaurya.internetradio.domain.controller

import com.armanmaurya.internetradio.domain.model.RecordingFile
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class RecordingRouteTarget(
    val stationName: String,
    val recordingUri: String
)

interface RecordingPlaybackController {
    val currentRecording: StateFlow<RecordingFile?>
    val isPlaying: StateFlow<Boolean>
    val currentPositionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>
    val routeToRecordingTarget: StateFlow<RecordingRouteTarget?>
    val playbackSpeed: StateFlow<Float>
    val isLooping: StateFlow<Boolean>

    fun play(recording: RecordingFile)
    fun pause()
    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun setPlaybackSpeed(speed: Float)
    fun setLooping(enabled: Boolean)
    fun toggleLoop()
    fun stop()
    fun requestRouteToPlayingRecording()
    fun routeToRecording(stationName: String, recordingUri: String)
    fun clearRouteTarget()

    /**
     * Swaps the current recording for its entry in a freshly loaded list, so the UI keeps matching
     * it when its URI changes (e.g. listing switches to the granted folder) or its note/tracks update.
     */
    fun syncCurrentRecording(recordings: List<RecordingFile>)
}
