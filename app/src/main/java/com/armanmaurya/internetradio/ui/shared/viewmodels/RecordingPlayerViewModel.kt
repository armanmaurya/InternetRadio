package com.armanmaurya.internetradio.ui.shared.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.domain.controller.ActivePlaybackCoordinator
import com.armanmaurya.internetradio.domain.controller.ActivePlaybackMode
import com.armanmaurya.internetradio.domain.controller.RecordingPlaybackController
import com.armanmaurya.internetradio.domain.lyrics.LyricsProviderInfo
import com.armanmaurya.internetradio.domain.model.RecordingFile
import com.armanmaurya.internetradio.domain.model.RecordingFolder
import com.armanmaurya.internetradio.domain.model.RecordingTrack
import com.armanmaurya.internetradio.domain.repository.LyricsFetchException
import com.armanmaurya.internetradio.domain.repository.LyricsRepository
import com.armanmaurya.internetradio.domain.repository.RecordingRepository
import com.armanmaurya.internetradio.domain.usecase.recording.DeleteRecordingUseCase
import com.armanmaurya.internetradio.domain.usecase.recording.GetRecordingFoldersUseCase
import com.armanmaurya.internetradio.ui.shared.viewmodels.PlayerViewModel.CustomLyricsQuery
import com.armanmaurya.internetradio.ui.shared.viewmodels.PlayerViewModel.LyricsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecordingPlayerUiState(
    val currentRecording: RecordingFile? = null,
    val playlist: List<RecordingFile> = emptyList(),
    val currentIndex: Int = -1,
    val folderArtworkUri: Uri? = null,
    val folderRecordings: List<RecordingFile> = emptyList(),
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val isLooping: Boolean = false,
    val amplitude: Float = 0f,
    val sleepTimerEndTimeMs: Long? = null,
    val sleepTimerTotalDurationMs: Long = 0L,
    val volume: Float = 1f,
    val activeTrack: RecordingTrack? = null,
    val lyricsUiState: LyricsUiState = LyricsUiState.NotAvailable,
    val lyricsSyncOffsetMs: Long = 0L,
    val customLyricsQueryTitle: String? = null,
    val customLyricsQueryArtist: String? = null,
    val selectedLyricsProviderId: String? = null
) {
    val hasPrevious: Boolean get() = currentIndex > 0
    val hasNext: Boolean get() = currentIndex in 0 until playlist.lastIndex
}

@HiltViewModel
class RecordingPlayerViewModel @Inject constructor(
    private val activePlaybackCoordinator: ActivePlaybackCoordinator,
    private val recordingPlaybackController: RecordingPlaybackController,
    private val getRecordingFoldersUseCase: GetRecordingFoldersUseCase,
    private val deleteRecordingUseCase: DeleteRecordingUseCase,
    private val recordingRepository: RecordingRepository,
    private val lyricsRepository: LyricsRepository
) : ViewModel() {

    val activeMode: StateFlow<ActivePlaybackMode> = activePlaybackCoordinator.activeMode

    private val folderLoadRequests = Channel<Unit>(Channel.CONFLATED)
    private val _folders = MutableStateFlow<List<RecordingFolder>>(emptyList())

    private val customLyricsQuery = MutableStateFlow<CustomLyricsQuery?>(null)
    private val selectedLyricsProviderId = MutableStateFlow<String?>(null)
    private val lyricsSyncOffsetMs = MutableStateFlow(0L)

    init {
        viewModelScope.launch {
            for (request in folderLoadRequests) {
                val loaded = getRecordingFoldersUseCase()
                _folders.value = loaded
                recordingPlaybackController.syncCurrentRecording(loaded.flatMap { it.recordings })
            }
        }
        folderLoadRequests.trySend(Unit)
        viewModelScope.launch {
            recordingRepository.recordingsChangedEvent.collect {
                folderLoadRequests.trySend(Unit)
            }
        }
        viewModelScope.launch {
            recordingPlaybackController.currentRecording
                .map { it?.uri }
                .distinctUntilChanged()
                .collect {
                    customLyricsQuery.value = null
                    selectedLyricsProviderId.value = null
                    lyricsSyncOffsetMs.value = 0L
                }
        }
    }

    private val activeTrackFlow: StateFlow<RecordingTrack?> = combine(
        recordingPlaybackController.currentRecording,
        recordingPlaybackController.currentPositionMs
    ) { rec, posMs ->
        resolveActiveTrack(rec, posMs)
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            activeTrackFlow.collect {
                customLyricsQuery.value = null
                selectedLyricsProviderId.value = null
                lyricsSyncOffsetMs.value = 0L
            }
        }
    }

    private data class RecordingLyricsKey(
        val trackTitle: String?,
        val artistName: String?,
        val customQuery: CustomLyricsQuery?,
        val selectedProviderId: String?
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val lyricsUiState: StateFlow<LyricsUiState> = combine(
        activeTrackFlow,
        customLyricsQuery,
        selectedLyricsProviderId
    ) { track, customQuery, providerId ->
        RecordingLyricsKey(
            trackTitle = track?.title,
            artistName = track?.artist,
            customQuery = customQuery,
            selectedProviderId = providerId
        )
    }
        .distinctUntilChanged()
        .flatMapLatest { key ->
            flow {
                val effectiveTitle = key.customQuery?.trackTitle ?: key.trackTitle
                val effectiveArtist = if (key.customQuery != null) key.customQuery.artistName else key.artistName
                if (effectiveTitle.isNullOrBlank()) {
                    emit(LyricsUiState.NotAvailable)
                } else {
                    emit(LyricsUiState.Loading)
                    val searchResult = try {
                        lyricsRepository.getLyricsSearchResult(effectiveTitle, effectiveArtist)
                    } catch (_: LyricsFetchException) {
                        emit(LyricsUiState.Error)
                        return@flow
                    } catch (_: Exception) {
                        emit(LyricsUiState.Error)
                        return@flow
                    }

                    val candidates = searchResult.allCandidates
                    val chosen = if (key.selectedProviderId != null) {
                        candidates.firstOrNull { it.providerInfo.id.equals(key.selectedProviderId, ignoreCase = true) }
                            ?: try {
                                lyricsRepository.getLyricsFromProvider(key.selectedProviderId, effectiveTitle, effectiveArtist)
                            } catch (_: Exception) {
                                null
                            }
                    } else {
                        searchResult.selectedLyrics
                    }

                    if (chosen != null) {
                        val allCandidates = if (candidates.none { it.providerInfo.id.equals(chosen.providerInfo.id, ignoreCase = true) }) {
                            candidates + chosen
                        } else {
                            candidates
                        }
                        emit(LyricsUiState.Success(chosen, allCandidates))
                    } else {
                        emit(LyricsUiState.NotAvailable)
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LyricsUiState.NotAvailable)

    private val playbackProgressFlow = combine(
        recordingPlaybackController.isPlaying,
        recordingPlaybackController.currentPositionMs,
        recordingPlaybackController.durationMs,
        recordingPlaybackController.playbackSpeed,
        recordingPlaybackController.isLooping
    ) { isPlaying, positionMs, durationMs, speed, isLooping ->
        PlaybackProgressTuple(isPlaying, positionMs, durationMs, speed, isLooping)
    }

    private val extrasFlow = combine(
        recordingPlaybackController.amplitude,
        recordingPlaybackController.sleepTimerEndTimeMs,
        recordingPlaybackController.sleepTimerTotalDurationMs,
        recordingPlaybackController.volume
    ) { amplitude, sleepEnd, sleepTotal, volume ->
        PlaybackExtrasTuple(amplitude, sleepEnd, sleepTotal, volume)
    }

    private val lyricsMetaFlow = combine(
        activeTrackFlow,
        lyricsUiState,
        lyricsSyncOffsetMs,
        customLyricsQuery,
        selectedLyricsProviderId
    ) { track, lyrics, offset, customQuery, providerId ->
        LyricsMetaTuple(track, lyrics, offset, customQuery, providerId)
    }

    val uiState: StateFlow<RecordingPlayerUiState> = combine(
        recordingPlaybackController.currentRecording,
        recordingPlaybackController.playlist,
        recordingPlaybackController.currentIndex,
        _folders,
        playbackProgressFlow,
        extrasFlow,
        lyricsMetaFlow
    ) { values ->
        val rec = values[0] as RecordingFile?
        @Suppress("UNCHECKED_CAST")
        val playlist = values[1] as List<RecordingFile>
        val index = values[2] as Int
        @Suppress("UNCHECKED_CAST")
        val folders = values[3] as List<RecordingFolder>
        val progress = values[4] as PlaybackProgressTuple
        val extras = values[5] as PlaybackExtrasTuple
        val lyricsMeta = values[6] as LyricsMetaTuple

        val matchingFolder = rec?.let { r -> folders.firstOrNull { it.stationName == r.stationName } }
        RecordingPlayerUiState(
            currentRecording = rec,
            playlist = playlist,
            currentIndex = index,
            folderArtworkUri = matchingFolder?.artworkUri,
            folderRecordings = matchingFolder?.recordings.orEmpty(),
            isPlaying = progress.isPlaying,
            currentPositionMs = progress.positionMs,
            durationMs = progress.durationMs,
            playbackSpeed = progress.speed,
            isLooping = progress.isLooping,
            amplitude = extras.amplitude,
            sleepTimerEndTimeMs = extras.sleepEnd,
            sleepTimerTotalDurationMs = extras.sleepTotal,
            volume = extras.volume,
            activeTrack = lyricsMeta.track,
            lyricsUiState = lyricsMeta.lyrics,
            lyricsSyncOffsetMs = lyricsMeta.offset,
            customLyricsQueryTitle = lyricsMeta.customQuery?.trackTitle,
            customLyricsQueryArtist = lyricsMeta.customQuery?.artistName,
            selectedLyricsProviderId = lyricsMeta.providerId
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecordingPlayerUiState())

    fun setActiveMode(mode: ActivePlaybackMode) {
        activePlaybackCoordinator.setMode(mode)
    }

    fun getCurrentPositionRealtime(): Long = recordingPlaybackController.getCurrentPositionRealtime()

    fun playRecording(recording: RecordingFile, playlist: List<RecordingFile> = emptyList()) {
        recordingPlaybackController.play(recording, playlist)
    }

    fun togglePlayPause() {
        recordingPlaybackController.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        recordingPlaybackController.seekTo(positionMs)
    }

    fun next() {
        recordingPlaybackController.next()
    }

    fun previous() {
        recordingPlaybackController.previous()
    }

    fun setPlaybackSpeed(speed: Float) {
        recordingPlaybackController.setPlaybackSpeed(speed)
    }

    fun toggleLoop() {
        recordingPlaybackController.toggleLoop()
    }

    fun setSleepTimer(durationMillis: Long) {
        recordingPlaybackController.setSleepTimer(durationMillis)
    }

    fun cancelSleepTimer() {
        recordingPlaybackController.cancelSleepTimer()
    }

    fun setVolume(volume: Float) {
        recordingPlaybackController.setVolume(volume)
    }

    fun stop() {
        recordingPlaybackController.stop()
    }

    fun setLyricsSyncOffset(offsetMs: Long) {
        lyricsSyncOffsetMs.value = offsetMs
    }

    fun searchLyrics(trackTitle: String, artistName: String?) {
        val trimmedTitle = trackTitle.trim()
        val trimmedArtist = artistName?.trim()?.takeIf { it.isNotBlank() }
        if (trimmedTitle.isNotBlank()) {
            customLyricsQuery.value = CustomLyricsQuery(trimmedTitle, trimmedArtist)
        }
    }

    fun selectLyricsProvider(providerId: String?) {
        selectedLyricsProviderId.value = if (providerId.isNullOrBlank() || providerId.equals("auto", ignoreCase = true)) {
            null
        } else {
            providerId
        }
    }

    fun resetLyricsQuery() {
        customLyricsQuery.value = null
        selectedLyricsProviderId.value = null
    }

    fun getAllLyricsProviders(): List<LyricsProviderInfo> = lyricsRepository.getAllProviders()

    fun updateNote(recording: RecordingFile, note: String?) {
        val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }
        val updatedFolders = _folders.value.map { folder ->
            if (folder.stationName == recording.stationName) {
                folder.copy(
                    recordings = folder.recordings.map { item ->
                        if (item.uri == recording.uri) item.copy(note = cleanNote) else item
                    }
                )
            } else {
                folder
            }
        }
        _folders.value = updatedFolders
        recordingPlaybackController.syncCurrentRecording(updatedFolders.flatMap { it.recordings })
        viewModelScope.launch {
            recordingRepository.updateRecordingNote(recording, cleanNote)
        }
    }

    fun deleteRecording(recording: RecordingFile) {
        viewModelScope.launch {
            if (recordingPlaybackController.currentRecording.value?.uri == recording.uri) {
                recordingPlaybackController.stop()
            }
            deleteRecordingUseCase.deleteFile(recording)
        }
    }

    fun routeToRecording(recording: RecordingFile) {
        recordingPlaybackController.routeToRecording(recording.stationName, recording.uri.toString())
    }

    private fun resolveActiveTrack(recording: RecordingFile?, positionMs: Long): RecordingTrack? {
        val tracks = recording?.tracks ?: return null
        if (tracks.isEmpty()) return null
        val sorted = tracks.sortedBy { it.offsetMs }
        return sorted.lastOrNull { it.offsetMs <= positionMs } ?: sorted.first()
    }

    private data class PlaybackProgressTuple(
        val isPlaying: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val speed: Float,
        val isLooping: Boolean
    )

    private data class PlaybackExtrasTuple(
        val amplitude: Float,
        val sleepEnd: Long?,
        val sleepTotal: Long,
        val volume: Float
    )

    private data class LyricsMetaTuple(
        val track: RecordingTrack?,
        val lyrics: LyricsUiState,
        val offset: Long,
        val customQuery: CustomLyricsQuery?,
        val providerId: String?
    )
}
