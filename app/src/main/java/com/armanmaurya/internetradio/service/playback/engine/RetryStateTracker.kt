package com.armanmaurya.internetradio.service.playback.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetryStateTracker @Inject constructor() {

    private val _retryCountdown = MutableStateFlow<Int?>(null)
    val retryCountdown = _retryCountdown.asStateFlow()

    private val _retryToastEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val retryToastEvent = _retryToastEvent.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.Main)
    private var countdownJob: Job? = null

    fun startRetryCountdown(delayMs: Long) {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            var secondsLeft = (delayMs / 1000).toInt()
            while (secondsLeft > 0) {
                _retryCountdown.value = secondsLeft
                delay(1000)
                secondsLeft--
            }
            _retryCountdown.value = null
            _retryToastEvent.tryEmit(Unit)
        }
    }

    fun reset() {
        countdownJob?.cancel()
        _retryCountdown.value = null
    }
}
