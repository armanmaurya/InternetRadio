package com.armanmaurya.internetradio.ui.shared.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.data.recording.RecordingFolderAccess
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.model.RecordingFile
import com.armanmaurya.internetradio.domain.model.RecordingFolder
import com.armanmaurya.internetradio.domain.model.RecordingSession
import com.armanmaurya.internetradio.domain.repository.LibraryRepository
import com.armanmaurya.internetradio.domain.repository.RecordingRepository
import com.armanmaurya.internetradio.domain.usecase.recording.DeleteRecordingUseCase
import com.armanmaurya.internetradio.domain.usecase.recording.GetActiveRecordingsUseCase
import com.armanmaurya.internetradio.domain.usecase.recording.GetRecordingFoldersUseCase
import com.armanmaurya.internetradio.domain.usecase.recording.StopRecordingUseCase
import com.armanmaurya.internetradio.domain.controller.RecordingPlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordingsViewModel @Inject constructor(
    private val getRecordingFoldersUseCase: GetRecordingFoldersUseCase,
    private val deleteRecordingUseCase: DeleteRecordingUseCase,
    private val getActiveRecordingsUseCase: GetActiveRecordingsUseCase,
    private val stopRecordingUseCase: StopRecordingUseCase,
    private val recordingRepository: RecordingRepository,
    private val libraryRepository: LibraryRepository,
    private val recordingPlaybackController: RecordingPlaybackController,
    private val recordingFolderAccess: RecordingFolderAccess
) : ViewModel() {

    private val _hasFolderAccess = MutableStateFlow(recordingFolderAccess.hasAccess())
    val hasFolderAccess: StateFlow<Boolean> = _hasFolderAccess.asStateFlow()

    val folderAccessInitialUri: Uri
        get() = recordingFolderAccess.initialPickerUri

    private val _folders = MutableStateFlow<List<RecordingFolder>>(emptyList())
    val folders: StateFlow<List<RecordingFolder>> = _folders.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    val activeSessions: StateFlow<Map<String, RecordingSession>> = getActiveRecordingsUseCase()

    val libraryStationUuids: StateFlow<Set<String>> = libraryRepository.getAllStations()
        .map { stations -> stations.map { it.stationUuid }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _selectedStationName = MutableStateFlow<String?>(null)
    val selectedStationName: StateFlow<String?> = _selectedStationName.asStateFlow()

    private val _expandedRecordingUri = MutableStateFlow<String?>(null)
    val expandedRecordingUri: StateFlow<String?> = _expandedRecordingUri.asStateFlow()

    private val _routeEventId = MutableStateFlow(0L)
    val routeEventId: StateFlow<Long> = _routeEventId.asStateFlow()

    val currentPlayingRecording: StateFlow<RecordingFile?> = recordingPlaybackController.currentRecording
    val isPlayingRecording: StateFlow<Boolean> = recordingPlaybackController.isPlaying
    val recordingPositionMs: StateFlow<Long> = recordingPlaybackController.currentPositionMs
    val recordingDurationMs: StateFlow<Long> = recordingPlaybackController.durationMs
    val recordingPlaybackSpeed: StateFlow<Float> = recordingPlaybackController.playbackSpeed
    val isRecordingLooping: StateFlow<Boolean> = recordingPlaybackController.isLooping

    init {
        loadFolders()
        viewModelScope.launch {
            recordingRepository.recordingsChangedEvent.collect {
                loadFolders()
            }
        }
        viewModelScope.launch {
            recordingPlaybackController.routeToRecordingTarget.collect { target ->
                if (target != null) {
                    routeToRecording(target.stationName, target.recordingUri)
                    recordingPlaybackController.clearRouteTarget()
                }
            }
        }
    }

    fun selectStation(stationName: String?) {
        _selectedStationName.value = stationName
        _expandedRecordingUri.value = null
    }

    fun setExpandedRecordingUri(uri: String?) {
        _expandedRecordingUri.value = uri
    }

    fun loadFolders() {
        viewModelScope.launch {
            val loadedFolders = getRecordingFoldersUseCase()
            _folders.value = loadedFolders
            recordingPlaybackController.syncCurrentRecording(loadedFolders.flatMap { it.recordings })
            _isLoaded.value = true
            val currentSelected = _selectedStationName.value
            if (currentSelected != null && loadedFolders.none { it.stationName == currentSelected }) {
                _selectedStationName.value = null
                _expandedRecordingUri.value = null
            }
        }
    }

    fun routeToRecording(stationName: String, uri: String) {
        _selectedStationName.value = stationName
        _expandedRecordingUri.value = uri
        _routeEventId.value = System.currentTimeMillis()
    }

    fun playRecording(recording: RecordingFile) {
        recordingPlaybackController.play(recording)
    }

    fun pauseRecordingPlayback() {
        recordingPlaybackController.pause()
    }

    fun togglePlayPauseRecording() {
        recordingPlaybackController.togglePlayPause()
    }

    fun seekRecordingPlayback(positionMs: Long) {
        recordingPlaybackController.seekTo(positionMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        recordingPlaybackController.setPlaybackSpeed(speed)
    }

    fun toggleLoopRecording() {
        recordingPlaybackController.toggleLoop()
    }

    fun stopRecordingPlayback() {
        recordingPlaybackController.stop()
    }

    fun requestRouteToPlayingRecording() {
        val playing = currentPlayingRecording.value
        if (playing != null) {
            routeToRecording(playing.stationName, playing.uri.toString())
        } else {
            recordingPlaybackController.requestRouteToPlayingRecording()
        }
    }

    fun stopRecording(stationUuid: String) {
        stopRecordingUseCase(stationUuid)
    }

    fun deleteRecording(recording: RecordingFile) {
        viewModelScope.launch {
            if (currentPlayingRecording.value?.uri == recording.uri) {
                recordingPlaybackController.stop()
            }
            deleteRecordingUseCase.deleteFile(recording)
            if (_expandedRecordingUri.value == recording.uri.toString()) {
                _expandedRecordingUri.value = null
            }
        }
    }

    fun deleteRecordings(recordings: List<RecordingFile>) {
        viewModelScope.launch {
            val deletedUris = recordings.map { it.uri.toString() }.toSet()
            if (currentPlayingRecording.value?.uri?.toString() in deletedUris) {
                recordingPlaybackController.stop()
            }
            deleteRecordingUseCase.deleteFiles(recordings)
            if (_expandedRecordingUri.value in deletedUris) {
                _expandedRecordingUri.value = null
            }
        }
    }

    fun deleteFolders(stationNames: List<String>) {
        viewModelScope.launch {
            deleteRecordingUseCase.deleteFolders(stationNames)
            if (_selectedStationName.value in stationNames) {
                _selectedStationName.value = null
                _expandedRecordingUri.value = null
            }
        }
    }

    fun updateRecordingNote(recording: RecordingFile, note: String?) {
        val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }
        _folders.value = _folders.value.map { folder ->
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
        viewModelScope.launch {
            recordingRepository.updateRecordingNote(recording, cleanNote)
        }
    }

    fun refreshFolderAccess() {
        val hasAccess = recordingFolderAccess.hasAccess()
        if (_hasFolderAccess.value != hasAccess) {
            _hasFolderAccess.value = hasAccess
            // Recordings are listed through the granted folder, so the visible set changes with access
            loadFolders()
        }
    }

    fun prepareFolderAccessRequest() {
        recordingFolderAccess.ensureFolderExists()
    }

    /** Returns false when the picked folder is not Music/InternetRadio. */
    fun onFolderAccessResult(treeUri: Uri): Boolean {
        val granted = recordingFolderAccess.grantAccess(treeUri)
        refreshFolderAccess()
        return granted
    }

    fun toggleLibrary(station: RadioStation) {
        viewModelScope.launch {
            if (libraryStationUuids.value.contains(station.stationUuid)) {
                libraryRepository.removeStationFromLibrary(station.stationUuid)
            } else {
                libraryRepository.addStationToLibrary(station)
            }
        }
    }
}
