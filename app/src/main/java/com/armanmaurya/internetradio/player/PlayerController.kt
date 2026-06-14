package com.armanmaurya.internetradio.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.armanmaurya.internetradio.data.model.RadioStation
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller: MediaController? get() = if (controllerFuture?.isDone == true) controllerFuture?.get() else null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState = _playbackState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var activeStation: RadioStation? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(state: Int) {
            _playbackState.update {
                it.copy(
                    isLoading = state == Player.STATE_BUFFERING,
                    isError = state == Player.STATE_IDLE && controller?.playerError != null
                )
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem == null) {
                activeStation = null
                _playbackState.update { it.copy(currentStation = null) }
                return
            }

            val mediaId = mediaItem.mediaId
            if (mediaId == activeStation?.stationUuid) {
                _playbackState.update { it.copy(currentStation = activeStation) }
                return
            }

            val tagStation = mediaItem.localConfiguration?.tag as? RadioStation
            if (tagStation != null) {
                activeStation = tagStation
                _playbackState.update { it.copy(currentStation = activeStation) }
            }
        }
    }

    init {
        initializeController()
    }

    private fun initializeController() {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            controller?.let { 
                it.addListener(playerListener)
                val currentItem = it.currentMediaItem
                if (currentItem != null) {
                    val station = currentItem.localConfiguration?.tag as? RadioStation 
                    if (station != null) {
                        activeStation = station
                        _playbackState.update { state ->
                            state.copy(
                                isPlaying = it.isPlaying,
                                currentStation = station
                            )
                        }
                    }
                } else {
                    _playbackState.update { state ->
                        state.copy(
                            isPlaying = it.isPlaying,
                            currentStation = null
                        )
                    }
                }
            }
        }, MoreExecutors.directExecutor())
    }

    fun play(station: RadioStation) {
        val player = controller ?: return
        
        if (activeStation?.stationUuid == station.stationUuid) {
            player.play()
            return
        }

        activeStation = station
        _playbackState.update { it.copy(currentStation = station) }
        val mediaItem = MediaItem.Builder()
            .setMediaId(station.stationUuid)
            .setUri(station.urlResolved)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(station.name)
                    .setArtworkUri(android.net.Uri.parse(station.favicon))
                    .build()
            )
            .setTag(station)
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun stop() {
        val player = controller ?: return
        player.stop()
        player.clearMediaItems()
    }

    private fun MediaItem.toRadioStation(): RadioStation? {
        return localConfiguration?.tag as? RadioStation
    }
}

data class PlaybackState(
    val currentStation: RadioStation? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isError: Boolean = false
)
