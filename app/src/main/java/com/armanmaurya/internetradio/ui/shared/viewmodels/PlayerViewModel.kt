package com.armanmaurya.internetradio.ui.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.domain.model.AppPreferences
import com.armanmaurya.internetradio.domain.model.LibrarySortOption
import com.armanmaurya.internetradio.domain.model.LrcLine
import com.armanmaurya.internetradio.domain.model.Lyrics
import com.armanmaurya.internetradio.domain.model.PlaybackSession
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.model.RecordingFile
import com.armanmaurya.internetradio.domain.model.RecordingSession
import com.armanmaurya.internetradio.domain.repository.LibraryRepository
import com.armanmaurya.internetradio.domain.repository.RecentRepository
import com.armanmaurya.internetradio.domain.repository.TrackHistoryRepository
import com.armanmaurya.internetradio.domain.model.PlaybackSource
import com.armanmaurya.internetradio.domain.controller.PlayerController
import com.armanmaurya.internetradio.domain.controller.RecordingController
import kotlinx.coroutines.flow.first
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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
    private val recentRepository: RecentRepository,
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: com.armanmaurya.internetradio.domain.repository.SettingsRepository,
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

    val playbackSession = playerController.playbackSession
    val playbackState: StateFlow<PlaybackSession> get() = playbackSession
    
    sealed interface LyricsUiState {
        data object Loading : LyricsUiState
        data object NotAvailable : LyricsUiState
        data class Success(
            val plainLyrics: String?,
            val syncedLyrics: List<LrcLine>?
        ) : LyricsUiState {
            constructor(lyrics: Lyrics) : this(lyrics.plainLyrics, lyrics.syncedLyrics)
        }
    }

    private data class LyricsRequestData(
        val track: String?,
        val cleanTrack: String?,
        val cleanArtist: String?,
        val isFetching: Boolean
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val lyricsUiState: StateFlow<LyricsUiState> = combine(
        playbackSession.map { it.currentTrack }.distinctUntilChanged(),
        playbackSession.map { it.cleanTrackName }.distinctUntilChanged(),
        playbackSession.map { it.cleanArtistName }.distinctUntilChanged(),
        playerController.isFetchingArtwork
    ) { track, cleanTrack, cleanArtist, isFetching ->
        LyricsRequestData(track, cleanTrack, cleanArtist, isFetching)
    }
        .flatMapLatest { data ->
            flow {
                if (data.track.isNullOrBlank()) {
                    emit(LyricsUiState.NotAvailable)
                } else if (data.isFetching) {
                    emit(LyricsUiState.Loading)
                } else {
                    emit(LyricsUiState.Loading)
                    val lyrics = if (data.cleanTrack != null) {
                        lyricsRepository.getLyricsForTrack(data.cleanTrack, data.cleanArtist)
                    } else {
                        lyricsRepository.getLyricsForTrack(data.track, null)
                    }
                    if (lyrics != null) {
                        emit(LyricsUiState.Success(lyrics))
                    } else {
                        emit(LyricsUiState.NotAvailable)
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LyricsUiState.Loading
        )

    val lyricsState: StateFlow<LyricsUiState> get() = lyricsUiState

    val activeSessions = getActiveRecordingsUseCase()

    val isCurrentStationRecording = combine(playbackSession.map { it.currentStation }, activeSessions) { station, sessions ->
        station != null && sessions.containsKey(station.stationUuid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentRecordingDuration = combine(playbackSession.map { it.currentStation }, activeSessions) { station, sessions ->
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val isFavorite = playbackSession
        .map { it.currentStation?.stationUuid }
        .distinctUntilChanged()
        .flatMapLatest { uuid ->
            if (uuid == null) flowOf(false)
            else libraryRepository.isStationInLibrary(uuid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val trackHistory = playbackSession
        .map { it.currentStation?.stationUuid }
        .distinctUntilChanged()
        .flatMapLatest { uuid ->
            if (uuid == null) flowOf(emptyList())
            else trackHistoryRepository.getTrackHistory(uuid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val stationRecordings = kotlinx.coroutines.flow.combine(
        playbackSession.map { it.currentStation?.name }.distinctUntilChanged(),
        recordingRepository.recordingsChangedEvent.onStart { emit(Unit) }
    ) { stationName, _ -> 
        stationName
    }.flatMapLatest { stationName ->
        if (stationName == null) flowOf(emptyList())
        else flowOf(recordingRepository.getRecordingsForStation(stationName))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private data class BasePlayerState(
        val session: PlaybackSession,
        val isBuffering: Boolean,
        val isError: Boolean,
        val isFetchingArtwork: Boolean,
        val amplitude: Float
    )

    private data class CastState(
        val connectedDevice: CastDevice?,
        val playbackState: CastPlaybackState,
        val volume: Float
    )

    private data class RecordingState(
        val isRecording: Boolean,
        val duration: Long,
        val activeSessions: Map<String, RecordingSession>
    )

    private data class ExtraUiState(
        val retry: Int?,
        val lyrics: LyricsUiState,
        val history: List<com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity>,
        val recordings: List<RecordingFile>?
    )

    private val basePlayerFlow = combine(
        playbackSession,
        playerController.isBuffering,
        playerController.isError,
        playerController.isFetchingArtwork,
        playerController.amplitude
    ) { session, isBuffering, isError, isFetchingArtwork, amplitude ->
        BasePlayerState(session, isBuffering, isError, isFetchingArtwork, amplitude)
    }

    private val castStateFlow = combine(
        connectedCastDevice,
        castPlaybackState,
        castVolume
    ) { device, playbackState, volume ->
        CastState(device, playbackState, volume.toFloat())
    }

    private val recordingStateFlow = combine(
        isCurrentStationRecording,
        currentRecordingDuration,
        activeSessions
    ) { isRec, duration, sessions ->
        RecordingState(isRec, duration, sessions)
    }

    private val extraUiFlow = combine(
        retryCountdown,
        lyricsUiState,
        trackHistory,
        stationRecordings
    ) { retry, lyrics, history, recordings ->
        ExtraUiState(retry, lyrics, history, recordings)
    }

    val uiState: StateFlow<PlayerUiState> = combine(
        basePlayerFlow,
        castStateFlow,
        recordingStateFlow,
        isFavorite,
        extraUiFlow
    ) { base, cast, rec, fav, extra ->
        val session = base.session
        val isCasting = cast.connectedDevice != null
        val effectiveIsPlaying = if (isCasting) cast.playbackState.isPlaying else session.isPlaying
        val effectiveIsLoading = if (isCasting) cast.playbackState.isBuffering else base.isBuffering
        val hasNext = session.currentPlaylist.isNotEmpty() && session.currentPlaylistIndex < session.currentPlaylist.size - 1
        val hasPrevious = session.currentPlaylist.isNotEmpty() && session.currentPlaylistIndex > 0

        PlayerUiState(
            currentStation = session.currentStation,
            currentPlaylist = session.currentPlaylist,
            currentPlaylistIndex = session.currentPlaylistIndex,
            currentTrack = session.currentTrack,
            cleanTrackName = session.cleanTrackName,
            cleanArtistName = session.cleanArtistName,
            rawTrackName = session.rawTrackName,
            trackStartTime = session.trackStartTime,
            trackCoverArtUri = session.trackCoverArtUri,
            isFetchingArtwork = base.isFetchingArtwork,
            isPlaying = effectiveIsPlaying,
            isLoading = effectiveIsLoading,
            isError = base.isError,
            hasNext = hasNext,
            hasPrevious = hasPrevious,
            volume = session.volume,
            sleepTimerEndTime = session.sleepTimerEndTime,
            sleepTimerTotalDuration = session.sleepTimerTotalDuration,
            lyricsSyncOffsetMs = session.lyricsSyncOffsetMs,
            streamCodec = session.streamCodec,
            streamBitrate = session.streamBitrate,
            isFavorite = fav,
            isRecording = rec.isRecording,
            recordingDuration = rec.duration,
            activeSessions = rec.activeSessions,
            connectedCastDevice = cast.connectedDevice,
            isCasting = isCasting,
            castVolume = cast.volume,
            amplitude = base.amplitude,
            retryCountdown = extra.retry,
            lyricsUiState = extra.lyrics,
            trackHistory = extra.history,
            stationRecordings = extra.recordings,
            sessionActiveDurationMs = session.sessionActiveDurationMs,
            sessionResumeTimeMs = session.sessionResumeTimeMs,
            playbackSource = session.playbackSource
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlayerUiState()
    )

    init {
        playerController.isError
            .onEach { isError ->
                if (isError) {
                    handlePlaybackFailure()
                }
            }
            .launchIn(viewModelScope)

        kotlinx.coroutines.flow.combine(
            playbackSession.map { it.currentStation }.distinctUntilChanged { old, new -> old?.stationUuid == new?.stationUuid },
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
                    val station = playbackSession.value.currentStation
                    if (station != null) {
                        playerController.play(listOf(station), 0, playWhenReady = true)
                    }
                }
                wasCasting = isCasting
            }
            .launchIn(viewModelScope)
    }

    private fun handlePlaybackFailure() {
        val currentStation = playbackSession.value.currentStation ?: return
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

    fun deleteRecording(recording: com.armanmaurya.internetradio.domain.model.RecordingFile) {
        viewModelScope.launch {
            recordingRepository.deleteRecording(recording)
        }
    }

    fun toggleFavorite() {
        val session = playbackSession.value
        val station = session.currentStation ?: return
        viewModelScope.launch {
            if (isFavorite.value) {
                libraryRepository.removeStationFromLibrary(station.stationUuid)
            } else {
                val codecToSave = session.streamCodec ?: station.codec
                val bitrateToSave = session.streamBitrate ?: station.bitrate
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

    fun toggleRecording(station: RadioStation? = playbackSession.value.currentStation) {
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
        
        if (playbackSession.value.currentStation?.stationUuid == station.stationUuid) {
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
                val station = playbackSession.value.currentStation
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
        val station = playbackSession.value.currentStation
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

    fun autoPlayRecentStationIfEnabled() {
        viewModelScope.launch {
            val prefs = settingsRepository.appPreferencesFlow.first()
            if (!prefs.autoPlayOnStart) return@launch
            if (playbackSession.value.isPlaying || playbackSession.value.currentStation != null) return@launch

            val station = recentRepository.getAllRecent().first().firstOrNull() ?: return@launch
            val libraryStations = getFilteredLibraryStations(prefs)
            val libraryIndex = libraryStations.indexOfFirst { it.stationUuid == station.stationUuid }

            if (libraryIndex != -1) {
                play(libraryStations, libraryIndex, PlaybackSource.Library)
            } else {
                val recentStations = recentRepository.getAllRecent().first()
                val recentIndex = recentStations.indexOfFirst { it.stationUuid == station.stationUuid }.coerceAtLeast(0)
                play(recentStations, recentIndex, PlaybackSource.Recent)
            }
        }
    }

    private suspend fun getFilteredLibraryStations(prefs: AppPreferences): List<RadioStation> {
        val stations = when (prefs.librarySortOption) {
            LibrarySortOption.NAME_A_Z -> libraryRepository.getStationsByName().first()
            LibrarySortOption.NAME_Z_A -> libraryRepository.getStationsByNameDescending().first()
            LibrarySortOption.RECENTLY_PLAYED -> libraryRepository.getStationsByRecentlyPlayed().first()
            LibrarySortOption.LEAST_RECENTLY_PLAYED -> libraryRepository.getStationsByLeastRecentlyPlayed().first()
            LibrarySortOption.CUSTOM -> libraryRepository.getStationsByCustomOrder().first()
            LibrarySortOption.RECENTLY_ADDED -> libraryRepository.getAllStations().first()
            LibrarySortOption.OLDEST_ADDED -> libraryRepository.getStationsByOldestAdded().first()
        }

        return if (prefs.useFilterOnFavorites) {
            val hasCountryFilter = !prefs.selectedCountryCode.isNullOrBlank()
            val hasLanguageFilter = !prefs.selectedLanguage.isNullOrBlank()
            val hasTagFilter = prefs.selectedTags.isNotEmpty()

            if (!hasCountryFilter && !hasLanguageFilter && !hasTagFilter) {
                stations
            } else {
                stations.filter { s ->
                    val countryMatch = !hasCountryFilter || s.countryCode == prefs.selectedCountryCode
                    val languageMatch = !hasLanguageFilter || s.language == prefs.selectedLanguage
                    val tagsMatch = !hasTagFilter || prefs.selectedTags.any { it in s.tags }
                    countryMatch && languageMatch && tagsMatch
                }
            }
        } else {
            stations
        }
    }
}