package com.armanmaurya.internetradio.ui.shared.viewmodels

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.core.system.FileSystemFacade
import com.armanmaurya.internetradio.core.system.SystemFacade
import com.armanmaurya.internetradio.data.backup.LibraryBackup
import com.armanmaurya.internetradio.data.backup.toBackupStarredTrack
import com.armanmaurya.internetradio.data.backup.toBackupStation
import com.armanmaurya.internetradio.data.backup.toBackupTrack
import com.armanmaurya.internetradio.data.local.entity.toDomain
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.repository.LibraryRepository
import com.armanmaurya.internetradio.domain.repository.TrackHistoryRepository
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

private const val TAG = "ExportViewModel"

/**
 * One library station on the export screen. History and stars go into the file only along with
 * the station; leaving the station out keeps their choice for when it is picked again.
 */
data class ExportStationItem(
    override val station: RadioStation,
    override val playCount: Int,
    override val starredCount: Int,
    override val selected: Set<ImportPart>
) : StationParts {
    override val availableParts: List<ImportPart> = availableParts(playCount, starredCount)

    val isPicked: Boolean get() = ImportPart.STATION in selected

    override fun canChoose(part: ImportPart): Boolean =
        part in availableParts && (part == ImportPart.STATION || isPicked)

    /** Whether [part] goes into the file for this station. */
    fun exports(part: ImportPart): Boolean = isPicked && part in selected

    override val state: ToggleableState
        get() = when {
            !isPicked -> ToggleableState.Off
            selected.containsAll(availableParts) -> ToggleableState.On
            else -> ToggleableState.Indeterminate
        }
}

sealed interface ExportUiState {
    /** Not open, or the export finished or was cancelled. */
    data object Idle : ExportUiState

    data object Loading : ExportUiState

    data class Choosing(
        val stations: List<ExportStationItem>,
        val isExporting: Boolean = false
    ) : ExportUiState {
        val selectedCount: Int get() = stations.count { it.isPicked }
        val hasHistory: Boolean get() = stations.any { it.playCount > 0 }
        val hasStarred: Boolean get() = stations.any { it.starredCount > 0 }

        /** Whether [part] is on for every, some or none of the stations that have it. */
        fun partState(part: ImportPart): ToggleableState {
            val withPart = stations.filter { part in it.availableParts }
            val chosen = withPart.count { part in it.selected }
            return when {
                withPart.isEmpty() || chosen == 0 -> ToggleableState.Off
                chosen == withPart.size -> ToggleableState.On
                else -> ToggleableState.Indeterminate
            }
        }

        val allState: ToggleableState
            get() = when (selectedCount) {
                0 -> ToggleableState.Off
                stations.size -> ToggleableState.On
                else -> ToggleableState.Indeterminate
            }
    }
}

sealed interface ExportResult {
    data class Exported(val count: Int) : ExportResult
    /** [reason] is null when the file couldn't be opened for writing. */
    data class Failed(val reason: String?) : ExportResult
}

/** The message shown once the export screen has closed after [this] result. */
fun ExportResult.message(context: Context): String = when (this) {
    is ExportResult.Exported -> context.resources.getQuantityString(R.plurals.settings_exported_success, count, count)
    is ExportResult.Failed ->
        if (reason == null) context.getString(R.string.settings_exported_failed)
        else context.getString(R.string.settings_exported_failed_exception, reason)
}

/**
 * Drives the export screen: lists the library, lets the user pick stations and whether their
 * history and starred tracks go along, then writes the chosen ones to a backup file.
 */
@HiltViewModel
class ExportViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val trackHistoryRepository: TrackHistoryRepository,
    private val fileSystemFacade: FileSystemFacade,
    private val systemFacade: SystemFacade,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    /** How the last export ended, for Settings to show once the export screen has closed. */
    private val _result = MutableStateFlow<ExportResult?>(null)
    val result: StateFlow<ExportResult?> = _result.asStateFlow()

    private var loadJob: Job? = null

    /** Lists the library in the user's own order, everything picked. */
    fun load() {
        loadJob?.cancel()
        _uiState.value = ExportUiState.Loading
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            // Two count queries instead of loading every play just to count it.
            val plays = trackHistoryRepository.countTracksPerStation()
            val stars = trackHistoryRepository.countStarredTracksPerStation()
            val stations = libraryRepository.getAllStationEntities()
                .sortedBy { it.orderIndex }
                .map { entity ->
                    ExportStationItem(
                        station = entity.toDomain(),
                        playCount = plays[entity.stationUuid] ?: 0,
                        starredCount = stars[entity.stationUuid] ?: 0,
                        selected = setOf(ImportPart.STATION, ImportPart.HISTORY, ImportPart.STARRED)
                    )
                }
            _uiState.value = ExportUiState.Choosing(stations)
        }
    }

    fun toggleStation(stationUuid: String) = updateChoosing { state ->
        state.copy(stations = state.stations.map {
            if (it.stationUuid == stationUuid) it.withPart(ImportPart.STATION, !it.isPicked) else it
        })
    }

    /** Turns [part] on or off for one station; history and stars only while the station is picked. */
    fun setPart(stationUuid: String, part: ImportPart, checked: Boolean) = updateChoosing { state ->
        state.copy(stations = state.stations.map {
            if (it.stationUuid == stationUuid && it.canChoose(part)) it.withPart(part, checked) else it
        })
    }

    fun toggleAll() = updateChoosing { state ->
        val selectAll = state.allState != ToggleableState.On
        state.copy(stations = state.stations.map { it.withPart(ImportPart.STATION, selectAll) })
    }

    /** Turns [part] on for every station that has it, or off if it already is on for all. */
    fun togglePartForAll(part: ImportPart) = updateChoosing { state ->
        val selectAll = state.partState(part) != ToggleableState.On
        state.copy(stations = state.stations.map {
            if (part in it.availableParts) it.withPart(part, selectAll) else it
        })
    }

    private fun ExportStationItem.withPart(part: ImportPart, on: Boolean) =
        copy(selected = if (on) selected + part else selected - part)

    fun cancel() {
        loadJob?.cancel()
        _uiState.value = ExportUiState.Idle
    }

    /** Writes the picked stations to [uri], then closes the screen. */
    fun export(uri: Uri) {
        val state = _uiState.value as? ExportUiState.Choosing ?: return
        if (state.isExporting || state.selectedCount == 0) return
        _uiState.value = state.copy(isExporting = true)

        viewModelScope.launch(Dispatchers.IO) {
            _result.value = try {
                val picked = state.stations.filter { it.isPicked }.associateBy { it.stationUuid }
                val backupStations = libraryRepository.getAllStationEntities()
                    .filter { it.stationUuid in picked }
                    .sortedBy { it.orderIndex }
                    .map { station ->
                        val uuid = station.stationUuid
                        val item = picked.getValue(uuid)
                        station.toBackupStation(
                            tracks = if (!item.exports(ImportPart.HISTORY)) null else
                                trackHistoryRepository.getAllTracksForStation(uuid)
                                    .map { it.toBackupTrack() }.takeIf { it.isNotEmpty() },
                            starredTracks = if (!item.exports(ImportPart.STARRED)) null else
                                trackHistoryRepository.getAllStarredTracksForStation(uuid)
                                    .map { it.toBackupStarredTrack() }.takeIf { it.isNotEmpty() }
                        )
                    }

                val backup = LibraryBackup(
                    // SimpleDateFormat for API 24 (Instant needs API 26)
                    exportedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()),
                    appVersion = systemFacade.getAppVersionName(),
                    stations = backupStations
                )
                val stream = fileSystemFacade.openOutputStream(uri)
                if (stream == null) {
                    ExportResult.Failed(reason = null)
                } else {
                    stream.bufferedWriter().use { Gson().toJson(backup, it) }
                    ExportResult.Exported(backupStations.size)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                ExportResult.Failed(e.localizedMessage ?: e.javaClass.simpleName)
            }
            _uiState.value = ExportUiState.Idle
        }
    }

    fun resultShown() {
        _result.value = null
    }

    private fun updateChoosing(transform: (ExportUiState.Choosing) -> ExportUiState.Choosing) = _uiState.update { state ->
        if (state is ExportUiState.Choosing && !state.isExporting) transform(state) else state
    }
}
