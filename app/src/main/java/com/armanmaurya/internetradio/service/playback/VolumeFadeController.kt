package com.armanmaurya.internetradio.service.playback

import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.media3.common.Player
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Manages system audio stream volume adjustments and gradual volume fade-in transitions for alarms.
 * Decouples hardware volume calculations and coroutine fading loops from PlaybackService.
 */
@Singleton
class VolumeFadeController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var fadeJob: Job? = null
    private var pendingListener: Player.Listener? = null
    private var attachedPlayer: Player? = null

    /**
     * Applies target system stream volume and triggers a volume fade transition if configured.
     *
     * @param player The active Media3 player.
     * @param volumeLevel Target volume [0.0f..1.0f], or negative to leave system volume untouched.
     * @param transitionSeconds Duration in seconds for fading the player volume from 0 to 1.
     * @param isSameStation Whether the requested station is already loaded in the player.
     * @param onSetVolumeZero Callback invoked when target volume evaluates to 0 to bypass auto-pause guards.
     */
    fun startAlarmVolumeTransition(
        player: Player,
        volumeLevel: Float,
        transitionSeconds: Int,
        isSameStation: Boolean,
        onSetVolumeZero: () -> Unit
    ) {
        cancelFade(player)
        attachedPlayer = player

        val applySystemVolume = {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val minVolume = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
            } else {
                0
            }
            val targetVolume = if (volumeLevel > 0f) {
                (volumeLevel * maxVolume).roundToInt()
                    .coerceIn(minVolume.coerceAtLeast(1), maxVolume)
            } else {
                minVolume
            }
            if (targetVolume == 0) {
                onSetVolumeZero()
            }
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
        }

        if (volumeLevel >= 0f) {
            if (isSameStation && player.playbackState == Player.STATE_READY) {
                applySystemVolume()
            } else {
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            applySystemVolume()
                            player.removeListener(this)
                            if (pendingListener == this) {
                                pendingListener = null
                            }
                        }
                    }
                }
                pendingListener = listener
                player.addListener(listener)
            }

            if (transitionSeconds > 0) {
                player.volume = 0f
                fadeJob = scope.launch {
                    val steps = transitionSeconds * 10
                    val volumeStep = 1.0f / steps
                    for (i in 1..steps) {
                        delay(100)
                        player.volume = (volumeStep * i).coerceIn(0f, 1f)
                    }
                    player.volume = 1.0f
                }
            } else {
                player.volume = 1f
            }
        } else {
            player.volume = 1f
        }
    }

    /**
     * Cancels any active fade job, removes any pending ready-listener, and restores player volume to 1.0f.
     */
    fun cancelFade(player: Player? = attachedPlayer) {
        fadeJob?.cancel()
        fadeJob = null
        pendingListener?.let { listener ->
            player?.removeListener(listener)
        }
        pendingListener = null
        player?.volume = 1f
    }

    /**
     * Releases references on service destruction.
     */
    fun release() {
        cancelFade(attachedPlayer)
        attachedPlayer = null
    }
}
