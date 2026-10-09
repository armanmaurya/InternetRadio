package com.armanmaurya.internetradio.service.playback

import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages system audio effects (LoudnessEnhancer) and volume boost gain.
 * Decouples hardware audio session ID observing and effect lifecycle from PlaybackService.
 */
@Singleton
class AudioEffectsManager @Inject constructor() {

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentBoostFactor: Float = 0f
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    private val analyticsListener = object : AnalyticsListener {
        override fun onAudioSessionIdChanged(
            eventTime: AnalyticsListener.EventTime,
            audioSessionId: Int
        ) {
            if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                updateAudioSessionId(audioSessionId)
            }
        }
    }

    private var attachedPlayer: ExoPlayer? = null

    @OptIn(UnstableApi::class)
    fun attachToPlayer(exoPlayer: ExoPlayer) {
        attachedPlayer?.removeAnalyticsListener(analyticsListener)
        attachedPlayer = exoPlayer
        exoPlayer.addAnalyticsListener(analyticsListener)
        if (exoPlayer.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            updateAudioSessionId(exoPlayer.audioSessionId)
        }
    }

    @OptIn(UnstableApi::class)
    fun detachFromPlayer() {
        release()
    }

    fun setVolumeBoost(boost: Float) {
        currentBoostFactor = boost
        applyGain(loudnessEnhancer, boost)
    }

    private fun updateAudioSessionId(audioSessionId: Int) {
        if (audioSessionId == currentAudioSessionId && loudnessEnhancer != null) return
        currentAudioSessionId = audioSessionId
        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                applyGain(this, currentBoostFactor)
            }
        } catch (e: Exception) {
            Log.e("AudioEffectsManager", "Failed to initialize LoudnessEnhancer", e)
            loudnessEnhancer = null
        }
    }

    private fun applyGain(enhancer: LoudnessEnhancer?, boost: Float) {
        if (enhancer == null) return
        try {
            if (boost > 0f) {
                val gainmB = (boost * 1000).toInt() // Up to +1000 mB (+10 dB) at 200%
                enhancer.setTargetGain(gainmB)
                enhancer.enabled = true
            } else {
                enhancer.setTargetGain(0)
                enhancer.enabled = false
            }
        } catch (e: Exception) {
            Log.e("AudioEffectsManager", "Failed to apply LoudnessEnhancer gain", e)
        }
    }

    @OptIn(UnstableApi::class)
    fun release() {
        try {
            attachedPlayer?.removeAnalyticsListener(analyticsListener)
            loudnessEnhancer?.release()
        } catch (e: Exception) {
            // Ignored
        } finally {
            attachedPlayer = null
            loudnessEnhancer = null
            currentAudioSessionId = C.AUDIO_SESSION_ID_UNSET
        }
    }
}
