package com.armanmaurya.internetradio.domain.controller

import com.armanmaurya.internetradio.domain.model.PlaybackSource
import com.armanmaurya.internetradio.domain.model.PlaybackState
import com.armanmaurya.internetradio.domain.model.RadioStation
import kotlinx.coroutines.flow.StateFlow

interface PlayerController {
    val playbackState: StateFlow<PlaybackState>
    val currentPlaylistSnapshot: List<RadioStation>
    val currentPosition: Long
    val amplitude: StateFlow<Float>

    fun syncAndroidAutoContext(stations: List<RadioStation>, startIndex: Int, source: PlaybackSource)
    fun play(
        stations: List<RadioStation>,
        startIndex: Int,
        source: PlaybackSource = PlaybackSource.None,
        playWhenReady: Boolean = true
    )
    fun updateCurrentStation(updatedStation: RadioStation, oldUuid: String? = null)
    fun playIndex(index: Int)
    fun next()
    fun previous()
    fun pause()
    fun togglePlayPause()
    fun setVolume(volume: Float)
    fun stop()
    fun setSleepTimer(durationMillis: Long)
    fun cancelSleepTimer()
    fun setLyricsSyncOffset(offsetMs: Long)
    fun updateAmplitude(rms: Float)
}
