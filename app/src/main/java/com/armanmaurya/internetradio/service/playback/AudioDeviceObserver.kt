package com.armanmaurya.internetradio.service.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.media3.common.Player
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes audio device hardware events including becoming noisy (headphone disconnection)
 * and physical volume changes (auto-pausing on volume 0 / mute).
 */
@Singleton
class AudioDeviceObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : Player.Listener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var attachedPlayer: Player? = null

    private var stopOnAudioBecomingNoisy: Boolean = true
    private var pauseOnVolumeZero: Boolean = false
    private var previousVolume: Int = -1
    private var ignoreNextVolumeZero: Boolean = false
    private var isReceiverRegistered: Boolean = false

    private val audioNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (stopOnAudioBecomingNoisy) {
                    attachedPlayer?.pause()
                }
            }
        }
    }

    init {
        scope.launch {
            settingsRepository.appPreferencesFlow.collect { prefs ->
                stopOnAudioBecomingNoisy = prefs.stopOnAudioBecomingNoisy
                pauseOnVolumeZero = prefs.pauseOnVolumeZero
            }
        }
    }

    fun attachPlayer(player: Player) {
        detachPlayer()
        attachedPlayer = player
        player.addListener(this)

        if (!isReceiverRegistered) {
            try {
                context.registerReceiver(
                    audioNoisyReceiver,
                    IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                )
                isReceiverRegistered = true
            } catch (e: Exception) {
                // Ignored
            }
        }
    }

    fun detachPlayer() {
        attachedPlayer?.removeListener(this)
        attachedPlayer = null

        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(audioNoisyReceiver)
            } catch (e: Exception) {
                // Ignored
            }
            isReceiverRegistered = false
        }
    }

    fun ignoreNextVolumeZero() {
        ignoreNextVolumeZero = true
    }

    override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
        val isZero = volume == 0 || muted
        val wasNonZero = previousVolume > 0

        if (isZero && wasNonZero) {
            if (ignoreNextVolumeZero) {
                ignoreNextVolumeZero = false
            } else if (pauseOnVolumeZero) {
                attachedPlayer?.pause()
            }
        } else if (!isZero) {
            ignoreNextVolumeZero = false
        }

        previousVolume = if (muted) 0 else volume
    }
}
