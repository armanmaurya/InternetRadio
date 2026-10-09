package com.armanmaurya.internetradio.service.playback.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages and broadcasts real-time audio amplitude (RMS) calculated from the stream audio sink.
 * Decouples the audio processing pipeline from PlayerController and eliminates the
 * circular client-server dependency between PlaybackService and PlayerController.
 */
@Singleton
class AudioAmplitudeManager @Inject constructor() {

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    fun updateAmplitude(rms: Float) {
        _amplitude.value = rms
    }

    fun reset() {
        _amplitude.value = 0f
    }
}
