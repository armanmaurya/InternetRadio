package com.armanmaurya.internetradio.ui.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.domain.model.LyricsState
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.repository.LibraryRepository
import com.armanmaurya.internetradio.domain.repository.RecentRepository
import com.armanmaurya.internetradio.domain.repository.TrackHistoryRepository
import com.armanmaurya.internetradio.domain.model.PlaybackSource
import com.armanmaurya.internetradio.domain.controller.PlayerController
import com.armanmaurya.internetradio.domain.controller.RecordingController
import com.armanmaurya.internetradio.domain.usecase.recording.GetActiveRecordingsUseCase
import com.armanmaurya.internetradio.domain.usecase.recording.StartRecordingUseCase
import com.armanmaurya.internetradio.domain.usecase.recording.StopRecordingUseCase
import com.armanmaurya.internetradio.domain.usecase.player.PlayStationUseCase
import com.armanmaurya.internetradio.domain.usecase.player.TogglePlayPauseUseCase
import com.armanmaurya.internetradio.domain.usecase.player.PlayNextStationUseCase
import com.armanmaurya.internetradio.domain.usecase.player.PlayPreviousStationUseCase
import com.armanmaurya.internetradio.domain.usecase.player.SetSleepTimerUseCase
import com.armanmaurya.internetradio.domain.usecase.player.SetPlayerVolumeUseCase
import com.armanmaurya.internetradio.domain.usecase.player.StopPlaybackUseCase
import com.armanmaurya.internetradio.core.provider.SvgProxyProvider
import com.armanmaurya.internetradio.service.playback.engine.RetryStateTracker
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.armanmaurya.internetradio.domain.controller.CastController
import com.armanmaurya.internetradio.domain.model.CastDevice
import com.armanmaurya.internetradio.domain.model.CastPlaybackState
import com.armanmaurya.internetradio.domain.usecase.cast.ConnectCastDeviceUseCase
import com.armanmaurya.internetradio.domain.usecase.cast.DisconnectCastDeviceUseCase

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val playerController: PlayerController,
    private val castController: CastController,
    private val connectCastDeviceUseCase: ConnectCastDeviceUseCase,
    private val disconnectCastDeviceUseCase: DisconnectCastDeviceUseCase,
    private val playStationUseCase: PlayStationUseCase,
    private val togglePlayPauseUseCase: TogglePlayPauseUseCase,
    private val playNextStationUseCase: PlayNextStationUseCase,
    private val playPreviousStationUseCase: PlayPreviousStationUseCase,
    private val setSleepTimerUseCase: SetSleepTimerUseCase,
    private val setPlayerVolumeUseCase: SetPlayerVolumeUseCase,
    private val stopPlaybackUseCase: StopPlaybackUseCase,
    private val libraryRepository: LibraryRepository,
    private val recentRepository: RecentRepository,
    private val stationRepository: com.armanmaurya.internetradio.domain.repository.StationRepository,
    private val trackHistoryRepository: TrackHistoryRepository,
    private val recordingController: RecordingController,
    private val getActiveRecordingsUseCase: GetActiveRecordingsUseCase,
    private val startRecordingUseCase: StartRecordingUseCase,
    private val stopRecordingUseCase: StopRecordingUseCase,
    private val recordingRepository: com.armanmaurya.internetradio.domain.repository.RecordingRepository,
    private val lyricsRepository: com.armanmaurya.internetradio.domain.repository.LyricsRepository,
    retryStateTracker: RetryStateTracker
) : ViewModel() {

    val retryCountdown = retryStateTracker.retryCountdown
    val retryToastEvent = retryStateTracker.retryToastEvent

    val playbackState = playerController.playbackState
    
    private data class LyricsRequestData(
        val track: String?,
        val cleanTrack: String?,
        val cleanArtist: String?,
        val isFetching: Boolean
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val lyricsState = combine(
        playbackState.map { it.currentTrack }.distinctUntilChanged(),
        playbackState.map { it.cleanTrackName }.distinctUntilChanged(),
        playbackState.map { it.cleanArtistName }.distinctUntilChanged(),
        playbackState.map { it.isFetchingArtwork }.distinctUntilChanged()
    ) { track, cleanTrack, cleanArtist, isFetching ->
        LyricsRequestData(track, cleanTrack, cleanArtist, isFetching)
    }
        .flatMapLatest { data ->
            if (data.track.isNullOrBlank()) {
                flowOf(LyricsState.NotAvailable)
            } else if (data.isFetching) {
                flowOf(LyricsState.Loading)
            } else {
                if (data.cleanTrack != null) {
                    lyricsRepository.getLyricsForTrack(data.cleanTrack, data.cleanArtist)
                } else {
                    lyricsRepository.getLyricsForTrack(data.track, null)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LyricsState.Loading
        )

    val activeSessions = getActiveRecordingsUseCase()

    val isCurrentStationRecording = combine(playbackState.map { it.currentStation }, activeSessions) { station, sessions ->
        station != null && sessions.containsKey(station.stationUuid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentRecordingDuration = combine(playbackState.map { it.currentStation }, activeSessions) { station, sessions ->
        if (station != null) sessions[station.stationUuid] else null
    }.flatMapLatest { session ->
        session?.durationSeconds ?: kotlinx.coroutines.flow.flowOf(0L)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val amplitude = playerController.amplitude
    val recordingSavedEvent = recordingController.recordingSavedEvent

    val discoveredCastDevices = castController.discoveredDevices
    val connectedCastDevice = castController.connectedDevice
    val castPlaybackState = castController.playbackState
    val castVolume = castController.volume

    val currentPosition: Long
        get() = playerController.currentPosition

    init {
        playbackState
            .onEach { state ->
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

                        // Re-trigger playback, preserving the full playlist so ExoPlayer
                        // doesn't collapse to a single item and trigger linkSingleItemToContext.
                        val fullPlaylist = playerController.currentPlaylistSnapshot
                        val idx = fullPlaylist.indexOfFirst { it.stationUuid == freshStation.stationUuid }
                        if (idx != -1) {
                            val updatedPlaylist = fullPlaylist.map {
                                if (it.stationUuid == freshStation.stationUuid) freshStation else it
                            }
                            play(updatedPlaylist, idx)
                        } else {
                            play(listOf(freshStation), 0)
                        }
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
        recordingRepository.recordingsChangedEvent.onStart { emit(Unit) }
    ) { stationName, _ -> 
        stationName
    }.flatMapLatest { stationName ->
        if (stationName == null) flowOf(emptyList()) // Fetch recordings immediately
        else flowOf(recordingRepository.getRecordingsForStation(stationName))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun deleteRecording(recording: com.armanmaurya.internetradio.domain.model.RecordingFile) {
        viewModelScope.launch {
            recordingRepository.deleteRecording(recording)
        }
    }

    fun toggleFavorite() {
        val state = playbackState.value
        val station = state.currentStation ?: return
        viewModelScope.launch {
            if (isFavorite.value) {
                libraryRepository.removeStationFromLibrary(station.stationUuid)
            } else {
                val codecToSave = state.streamCodec ?: station.codec
                val bitrateToSave = state.streamBitrate ?: station.bitrate
                val stationToSave = station.copy(
                    codec = codecToSave,
                    bitrate = bitrateToSave
                )
                libraryRepository.addStationToLibrary(stationToSave)
                playerController.updateCurrentStation(stationToSave)
            }
        }
    }

    private val _permissionRequestEvent = kotlinx.coroutines.flow.MutableSharedFlow<RadioStation>()
    val permissionRequestEvent = _permissionRequestEvent.asSharedFlow()
    
    var pendingRecordingStation: RadioStation? = null

    fun stopRecording(uuid: String) {
        stopRecordingUseCase(uuid)
    }

    fun proceedWithRecording() {
        pendingRecordingStation?.let {
            startRecordingUseCase(it)
            pendingRecordingStation = null
        }
    }

    fun toggleRecording(station: RadioStation? = playbackState.value.currentStation) {
        val st = station ?: return
        if (activeSessions.value.containsKey(st.stationUuid)) {
            stopRecordingUseCase(st.stationUuid)
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val permissionStatus = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.POST_NOTIFICATIONS
                )
                if (permissionStatus == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    startRecordingUseCase(st)
                } else {
                    pendingRecordingStation = st
                    viewModelScope.launch {
                        _permissionRequestEvent.emit(st)
                    }
                }
            } else {
                startRecordingUseCase(st)
            }
        }
    }

    fun play(stations: List<RadioStation>, startIndex: Int, source: PlaybackSource = PlaybackSource.None) {
        val station = stations.getOrNull(startIndex) ?: return
        
        if (playbackState.value.currentStation?.stationUuid == station.stationUuid) {
            togglePlayPause()
            return
        }

        viewModelScope.launch {
            playStationUseCase(
                stations = stations,
                startIndex = startIndex,
                source = source,
                playWhenReady = true
            )
        }
    }

    fun playIndex(index: Int) {
        playerController.playIndex(index)
    }

    fun next() {
        playNextStationUseCase()
    }

    fun previous() {
        playPreviousStationUseCase()
    }

    fun setLyricsSyncOffset(offsetMs: Long) {
        playerController.setLyricsSyncOffset(offsetMs)
    }

    fun togglePlayPause() {
        if (connectedCastDevice.value != null) {
            val state = castPlaybackState.value
            if (state.isPlaying || state.isBuffering) {
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
            togglePlayPauseUseCase()
        }
    }

    fun stop() {
        if (connectedCastDevice.value != null) {
            castController.stop()
        }
        stopPlaybackUseCase()
    }

    fun setSleepTimer(durationMillis: Long) {
        setSleepTimerUseCase(durationMillis)
    }

    fun cancelSleepTimer() {
        setSleepTimerUseCase.cancel()
    }

    fun connectToCastDevice(device: CastDevice) {
        connectCastDeviceUseCase(device)
        playerController.setVolume(0f)
    }

    fun disconnectCastDevice() {
        disconnectCastDeviceUseCase()
        playerController.setVolume(1f)
        val station = playbackState.value.currentStation
        if (station != null) {
            playerController.play(listOf(station), 0)
        }
    }

    fun setCastVolume(volume: Float) {
        castController.setVolume(volume.toDouble())
    }

    fun setVolume(volume: Float) {
        if (connectedCastDevice.value != null) {
            setCastVolume(volume.coerceIn(0f, 1f))
        } else {
            setPlayerVolumeUseCase(volume)
        }
    }
}