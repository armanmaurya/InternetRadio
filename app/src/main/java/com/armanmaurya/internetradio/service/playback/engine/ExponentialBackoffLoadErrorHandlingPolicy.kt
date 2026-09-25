package com.armanmaurya.internetradio.service.playback.engine

import androidx.media3.common.C
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

class ExponentialBackoffLoadErrorHandlingPolicy(
    private val retryStateTracker: RetryStateTracker
) : DefaultLoadErrorHandlingPolicy() {

    @Volatile
    var maxRetryDurationMs: Long = 300_000L // Default 5 minutes

    // Maximum delay between retries
    private val maxDelayMs = 30000L
    private val minDelayMs = 1000L

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val errorCount = loadErrorInfo.errorCount
        
        val delay = if (maxRetryDurationMs < 0) {
            // Indefinite
            getExponentialBackoffDelay(errorCount)
        } else {
            val elapsedMs = getEstimatedElapsedMs(errorCount)
            if (elapsedMs >= maxRetryDurationMs) {
                retryStateTracker.reset()
                return C.TIME_UNSET // Stop retrying
            }
            getExponentialBackoffDelay(errorCount)
        }
        
        retryStateTracker.startRetryCountdown(delay)
        return delay
    }

    private fun getExponentialBackoffDelay(errorCount: Int): Long {
        if (errorCount > 30) return maxDelayMs // Prevent shift overflow
        val delay = (1L shl (errorCount - 1)) * minDelayMs
        return if (delay <= 0 || delay > maxDelayMs) maxDelayMs else delay
    }

    private fun getEstimatedElapsedMs(errorCount: Int): Long {
        var elapsed = 0L
        for (i in 1 until errorCount) {
            elapsed += getExponentialBackoffDelay(i)
        }
        return elapsed
    }

    override fun getMinimumLoadableRetryCount(dataType: Int): Int {
        return Int.MAX_VALUE
    }
}
