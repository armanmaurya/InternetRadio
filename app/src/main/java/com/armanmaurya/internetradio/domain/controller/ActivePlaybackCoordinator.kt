package com.armanmaurya.internetradio.domain.controller

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ActivePlaybackMode {
    STATION,
    RECORDING
}

/**
 * Tracks which playback surface (live radio station vs. local recording) currently owns the
 * global player bottom sheet when one or both media sources are active or paused.
 */
@Singleton
class ActivePlaybackCoordinator @Inject constructor() {

    private val _activeMode = MutableStateFlow(ActivePlaybackMode.STATION)
    val activeMode: StateFlow<ActivePlaybackMode> = _activeMode.asStateFlow()

    fun setMode(mode: ActivePlaybackMode) {
        _activeMode.value = mode
    }
}
