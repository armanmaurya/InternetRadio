package com.armanmaurya.internetradio.ui.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.data.repository.LibraryRepository
import com.armanmaurya.internetradio.data.repository.RecentRepository
import com.armanmaurya.internetradio.data.repository.StationRepository
import com.armanmaurya.internetradio.data.repository.TrackHistoryRepository
import com.armanmaurya.internetradio.player.PlaybackSource
import com.armanmaurya.internetradio.player.PlayerController
import com.armanmaurya.internetradio.player.RecordingManager
import com.armanmaurya.internetradio.player.SvgProxyProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import org.fcast.sender_sdk.DeviceInfo

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val playerController: PlayerController,
    private val castController: com.armanmaurya.internetradio.player.CastController,
    private val libraryRepository: LibraryRepository,
    private val recentRepository: RecentRepository,
    private val stationRepository: com.armanmaurya.internetradio.data.repository.StationRepository,
    private val trackHistoryRepository: TrackHistoryRepository,
    private val recordingManager: RecordingManager,
    private val recordingRepository: com.armanmaurya.internetradio.data.repository.RecordingRepository,
    retryStateTracker: com.armanmaurya.internetradio.player.RetryStateTracker
) : ViewModel() {

    val retryCountdown = retryStateTracker.retryCountdown
    val retryToastEvent = retryStateTracker.retryToastEvent

    val playbackState = playerController.playbackState

    val isRecording = recordingManager.isRecording
    val recordingDuration = recordingManager.recordingDuration
    val amplitude = recordingManager.amplitude

    val discoveredCastDevices = castController.discoveredDevices
    val connectedCastDevice = castController.connectedDevice
    val castPlaybackState = castController.playbackState
    val castVolume = castController.volume

    init {
        playbackState
            .onEach { state ->
                recordingManager.setPlaying(state.isPlaying)
                
                // Auto-stop and save recording if station changes or player is completely stopped
                val isDifferentStation = recordingManager.currentStation?.stationUuid != state.currentStation?.stationUuid
                if (isRecording.value && (state.currentStation == null || isDifferentStation)) {
                    recordingManager.stopRecording()
                }

                if (state.isError) {
                    handlePlaybackFailure()
                }
            }
            .launchIn(viewModelScope)

        kotlinx.coroutines.flow.combine(
            playbackState.map { it.currentStation }.distinctUntilChanged { old, new -> old?.stationUuid == new?.stationUuid },
            connectedCastDevice
        ) { station, device ->
            if (station != null && device != null) {
                val proxyFavicon = if (station.favicon.endsWith(".svg", true)) {
                    SvgProxyProvider.createProxyUri(context, station.favicon)
                } else {
                    station.favicon
                }
                castController.load(
                    url = station.urlResolved,
                    contentType = "audio/mpeg",
                    title = station.name,
                    thumbnailUrl = proxyFavicon
                )
            }
        }.launchIn(viewModelScope)
        
        var wasCasting = false
        connectedCastDevice
            .onEach { device ->
                val isCasting = device != null
                if (isCasting) playerController.setVolume(0f)
                if (wasCasting && !isCasting) {
                    playerController.setVolume(1f)
                    val station = playbackState.value.currentStation
                    if (station != null) {
                        playerController.play(listOf(station), 0, playWhenReady = true)
                    }
                }
                wasCasting = isCasting
            }
            .launchIn(viewModelScope)
    }

    private fun handlePlaybackFailure() {
        val currentStation = playbackState.value.currentStation ?: return
        viewModelScope.launch {
            stationRepository.getStationsByUuid(listOf(currentStation.stationUuid))
                .onSuccess { freshStations ->
                    val freshStation = freshStations.firstOrNull() ?: return@onSuccess

                    val hasChanged = freshStation.name != currentStation.name ||
                            freshStation.url != currentStation.url ||
                            freshStation.urlResolved != currentStation.urlResolved ||
                            freshStation.favicon != currentStation.favicon ||
                            freshStation.tags != currentStation.tags ||
                            freshStation.country != currentStation.country ||
                            freshStation.language != currentStation.language ||
                            freshStation.codec != currentStation.codec ||
                            freshStation.bitrate != currentStation.bitrate

                    if (hasChanged) {
                        // Update Favorite if it exists
                        if (libraryRepository.isStationInLibraryDirect(currentStation.stationUuid)) {
                            libraryRepository.addStationToLibrary(freshStation)
                        }

                        // Update Recent
                        recentRepository.addRecentStation(freshStation)

                        // Re-trigger playback with fresh station
                        play(listOf(freshStation), 0)
                    }
                }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val isFavorite = playbackState
        .map { it.currentStation?.stationUuid }
        .distinctUntilChanged()
        .flatMapLatest { uuid ->
            if (uuid == null) flowOf(false)
            else libraryRepository.isStationInLibrary(uuid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val trackHistory = playbackState
        .map { it.currentStation?.stationUuid }
        .distinctUntilChanged()
        .flatMapLatest { uuid ->
            if (uuid == null) flowOf(emptyList())
            else trackHistoryRepository.getTrackHistory(uuid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val stationRecordings = kotlinx.coroutines.flow.combine(
        playbackState.map { it.currentStation?.name }.distinctUntilChanged(),
        isRecording // Re-fetch when recording stops
    ) { stationName, recording -> 
        stationName to recording
    }.flatMapLatest { (stationName, recording) ->
        if (stationName == null || recording) flowOf(emptyList()) // Wait until recording finishes to refresh
        else flowOf(recordingRepository.getRecordingsForStation(stationName))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleFavorite() {
        val station = playbackState.value.currentStation ?: return
        viewModelScope.launch {
            if (isFavorite.value) {
                libraryRepository.removeStationFromLibrary(station.stationUuid)
            } else {
                libraryRepository.addStationToLibrary(station)
            }
        }
    }

    fun toggleRecording() {
        if (isRecording.value) {
            recordingManager.stopRecording()
        } else {
            playbackState.value.currentStation?.let {
                recordingManager.startRecording(it)
            }
        }
    }

    fun play(stations: List<RadioStation>, startIndex: Int, source: PlaybackSource = PlaybackSource.None) {
        val station = stations[startIndex]
        playerController.play(
            stations = stations,
            startIndex = startIndex,
            source = source,
            playWhenReady = true
        )
        viewModelScope.launch {
            recentRepository.addRecentStation(station)
            stationRepository.registerClick(station.stationUuid)
        }
    }

    fun next() {
        playerController.next()
    }

    fun previous() {
        playerController.previous()
    }

    fun togglePlayPause() {
        if (connectedCastDevice.value != null) {
            val state = castPlaybackState.value
            val stateName = state.toString().uppercase()
            if (stateName.contains("PLAY") || stateName.contains("BUFFER")) {
                castController.pause()
                playerController.pause()
            } else {
                castController.play()
                val station = playbackState.value.currentStation
                if (station != null) {
                    playerController.play(listOf(station), 0, playWhenReady = true)
                }
            }
        } else {
            playerController.togglePlayPause()
        }
    }

    fun stop() {
        if (connectedCastDevice.value != null) {
            castController.stop()
        }
        playerController.stop()
    }

    fun setSleepTimer(durationMillis: Long) {
        playerController.setSleepTimer(durationMillis)
    }

    fun cancelSleepTimer() {
        playerController.cancelSleepTimer()
    }

    fun connectToCastDevice(deviceInfo: DeviceInfo) {
        castController.connectToDevice(deviceInfo)
        playerController.setVolume(0f)
    }

    fun disconnectCastDevice() {
        castController.disconnect()
        playerController.setVolume(1f)
        val station = playbackState.value.currentStation
        if (station != null) {
            playerController.play(listOf(station), 0)
        }
    }

    fun setCastVolume(volume: Float) {
        castController.setVolume(volume.toDouble())
    }
}