package com.armanmaurya.internetradio.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.data.repository.SettingsRepository
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
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
            scope.launch {
                if (isPlaying) {
                    activeStation?.let { station ->
                        val stationJson = Gson().toJson(station)
                        settingsRepository.setResumeStation(stationJson)
                    }
                } else {
                    settingsRepository.setResumeStation(null)
                }
            }
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
                _playbackState.update { it.copy(currentStation = activeStation, currentTrack = null) }
            }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            val title = mediaMetadata.title?.toString() ?: mediaMetadata.displayTitle?.toString()
            if (title != null && title != activeStation?.name) {
                _playbackState.update { it.copy(currentTrack = title) }
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
                    
                    scope.launch {
                        val resumeStationJson = settingsRepository.appPreferencesFlow.first().resumeStation
                        if (resumeStationJson != null) {
                            try {
                                val station = Gson().fromJson(resumeStationJson, RadioStation::class.java)
                                if (station != null) {
                                    activeStation = station
                                    _playbackState.update { state ->
                                        state.copy(
                                            isPlaying = false,
                                            currentStation = station
                                        )
                                    }
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
                                    it.setMediaItem(mediaItem)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        }, MoreExecutors.directExecutor())
    }

    fun play(station: RadioStation) {
        val player = controller ?: return
        
        if (activeStation?.stationUuid == station.stationUuid) {
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
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
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            player.play()
        }
    }

    fun stop() {
        val player = controller ?: return
        player.stop()
        player.clearMediaItems()
        cancelSleepTimer()
    }

    private var timerJob: Job? = null

    fun setSleepTimer(durationMillis: Long) {
        timerJob?.cancel()
        val endTime = System.currentTimeMillis() + durationMillis
        _playbackState.update { it.copy(sleepTimerEndTime = endTime, sleepTimerTotalDuration = durationMillis) }
        
        timerJob = scope.launch {
            delay(durationMillis)
            stop()
            _playbackState.update { it.copy(sleepTimerEndTime = null, sleepTimerTotalDuration = 0L) }
        }
    }

    fun cancelSleepTimer() {
        timerJob?.cancel()
        timerJob = null
        _playbackState.update { it.copy(sleepTimerEndTime = null, sleepTimerTotalDuration = 0L) }
    }

    private fun MediaItem.toRadioStation(): RadioStation? {
        return localConfiguration?.tag as? RadioStation
    }
}

data class PlaybackState(
    val currentStation: RadioStation? = null,
    val currentTrack: String? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val sleepTimerEndTime: Long? = null,
    val sleepTimerTotalDuration: Long = 0L
)
