package com.armanmaurya.internetradio.ui.shared.viewmodels

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.data.backup.BackupStarredTrack
import com.armanmaurya.internetradio.data.backup.BackupStation
import com.armanmaurya.internetradio.data.backup.BackupTrack
import com.armanmaurya.internetradio.data.backup.LibraryBackup
import com.armanmaurya.internetradio.data.backup.toStarredTrackEntity
import com.armanmaurya.internetradio.data.backup.toTrackHistoryEntity
import com.armanmaurya.internetradio.data.backup.parser.CompositeStationFileParser
import com.armanmaurya.internetradio.data.local.entity.toDomain
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.repository.LibraryRepository
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import com.armanmaurya.internetradio.domain.repository.TrackHistoryRepository
import com.armanmaurya.internetradio.domain.repository.TransactionRunner
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

private const val TAG = "ImportViewModel"

/** The parts of a backed-up station that can be imported separately. */
enum class ImportPart { STATION, HISTORY, STARRED }

/** Whether an existing station in the library should be smart-merged or strictly replaced. */
enum class StationUpdateMode { UPDATE, REPLACE }

/** The fields that can be toggled individually when updating or replacing an existing station. */
enum class StationDiffField {
    NAME,
    URL,
    TAGS,
    FAVICON,
    HOMEPAGE,
    COUNTRY,
    LANGUAGE,
    AUDIO
}

/** Differences between an existing library station and the incoming file version. */
data class StationFieldDiff(
    val nameDiffers: Boolean = false,
    val oldName: String = "",
    val newName: String = "",
    val updateName: Boolean = true,

    val urlDiffers: Boolean = false,
    val oldUrl: String = "",
    val newUrl: String = "",
    val updateUrl: Boolean = true,

    val tagsDiffer: Boolean = false,
    val oldTags: List<String> = emptyList(),
    val newTags: List<String> = emptyList(),
    val updateTags: Boolean = true,

    val faviconDiffers: Boolean = false,
    val oldFavicon: String = "",
    val newFavicon: String = "",
    val updateFavicon: Boolean = true,

    val homepageDiffers: Boolean = false,
    val oldHomepage: String = "",
    val newHomepage: String = "",
    val updateHomepage: Boolean = true,

    val countryDiffers: Boolean = false,
    val oldCountryCode: String = "",
    val newCountryCode: String = "",
    val updateCountry: Boolean = true,

    val languageDiffers: Boolean = false,
    val oldLanguageCodes: List<String> = emptyList(),
    val newLanguageCodes: List<String> = emptyList(),
    val updateLanguage: Boolean = true,

    val audioDiffers: Boolean = false,
    val oldCodec: String = "",
    val newCodec: String = "",
    val oldBitrate: Int = 0,
    val newBitrate: Int = 0,
    val updateAudio: Boolean = true
) {
    val hasAnyDiff: Boolean
        get() = nameDiffers || urlDiffers || tagsDiffer || faviconDiffers ||
                homepageDiffers || countryDiffers || languageDiffers || audioDiffers
}

/** Import and export lists with more stations than this get a search field, on phone and TV. */
const val STATION_SEARCH_THRESHOLD = 20

/** A station whose parts can be chosen separately, on the import and export screens. */
interface StationParts {
    val station: RadioStation
    val playCount: Int
    val starredCount: Int
    val selected: Set<ImportPart>

    /** Parts there is something to bring along for: the station, plus history and stars if it has any. */
    val availableParts: List<ImportPart>

    fun canChoose(part: ImportPart): Boolean

    val stationUuid: String get() = station.stationUuid
    val name: String get() = station.name

    val state: ToggleableState
        get() = when {
            selected.isEmpty() -> ToggleableState.Off
            selected.containsAll(availableParts) -> ToggleableState.On
            else -> ToggleableState.Indeterminate
        }
}

internal fun availableParts(playCount: Int, starredCount: Int): List<ImportPart> = buildList {
    add(ImportPart.STATION)
    if (playCount > 0) add(ImportPart.HISTORY)
    if (starredCount > 0) add(ImportPart.STARRED)
}

/** One station from the picked file(s), as shown on the import screen. */
data class ImportStationItem(
    /** The station as it is in the file, for showing it with the usual station card. */
    override val station: RadioStation,
    val isInLibrary: Boolean,
    override val playCount: Int,
    override val starredCount: Int,
    /** Plays this station would have after merging the file's history with the local one. */
    val playsAfterImport: Int,
    override val selected: Set<ImportPart>,
    val updateMode: StationUpdateMode = StationUpdateMode.UPDATE,
    val fieldDiff: StationFieldDiff = StationFieldDiff(),
    val isDetailsExpanded: Boolean = false
) : StationParts {
    /** Parts the file actually has for this station. */
    override val availableParts: List<ImportPart> = availableParts(playCount, starredCount)

    /**
     * History and stars of a station that isn't in the library would be saved where nothing shows
     * them, so for a new station they can only be chosen together with the station itself.
     */
    override fun canChoose(part: ImportPart): Boolean =
        part in availableParts && (isInLibrary || part == ImportPart.STATION || ImportPart.STATION in selected)
}

sealed interface ImportUiState {
    /** Nothing picked, or the import finished or was cancelled. */
    data object Idle : ImportUiState

    data object Loading : ImportUiState

    data class Review(
        val newStations: List<ImportStationItem>,
        val existingStations: List<ImportStationItem>,
        val failedFiles: Int,
        val historyLimit: Int,
        val keepAllPlays: Boolean = false,
        val isImporting: Boolean = false
    ) : ImportUiState {
        val stations: List<ImportStationItem> get() = newStations + existingStations
        val selectedCount: Int get() = stations.count { it.selected.isNotEmpty() }
        val hasHistory: Boolean get() = stations.any { it.playCount > 0 }
        val hasStarred: Boolean get() = stations.any { it.starredCount > 0 }

        /** Highest play count a station would reach with the chosen history; 0 if none chosen. */
        val playsNeeded: Int
            get() = stations.filter { ImportPart.HISTORY in it.selected }.maxOfOrNull { it.playsAfterImport } ?: 0

        /** True when the chosen history doesn't fit the user's history limit. */
        val exceedsHistoryLimit: Boolean get() = playsNeeded > historyLimit

        /** Whether [part] is chosen for every, some or none of the stations it can be chosen for. */
        fun partState(part: ImportPart): ToggleableState {
            val withPart = stations.filter { it.canChoose(part) }
            if (withPart.isEmpty()) return ToggleableState.Off
            val chosen = withPart.count { part in it.selected }
            return when (chosen) {
                0 -> ToggleableState.Off
                withPart.size -> ToggleableState.On
                else -> ToggleableState.Indeterminate
            }
        }

        fun sectionState(existing: Boolean): ToggleableState {
            val section = if (existing) existingStations else newStations
            return when {
                section.all { it.state == ToggleableState.On } -> ToggleableState.On
                section.all { it.state == ToggleableState.Off } -> ToggleableState.Off
                else -> ToggleableState.Indeterminate
            }
        }
    }
}

sealed interface ImportEvent {
    /** The import was saved: [added] new stations, [updated] stations already in the library. */
    data class Finished(val added: Int, val updated: Int, val failedFiles: Int) : ImportEvent

    /** None of the picked files had a usable station; nothing was saved. */
    data class NothingToImport(val failedFiles: Int) : ImportEvent

    /** Saving failed; nothing was changed. */
    data object Failed : ImportEvent
}

/** The message shown once the import screen has closed after [this] result. */
fun ImportEvent.message(context: Context): String = when (this) {
    is ImportEvent.NothingToImport ->
        if (failedFiles > 0) context.resources.getQuantityString(R.plurals.settings_imported_failed, failedFiles, failedFiles)
        else context.getString(R.string.import_no_stations)
    ImportEvent.Failed -> context.getString(R.string.import_failed)
    is ImportEvent.Finished -> buildList {
        if (added > 0) add(context.getString(R.string.settings_imported, added))
        if (updated > 0) add(context.getString(R.string.settings_imported_updated, updated))
        if (failedFiles > 0) add(context.resources.getQuantityString(R.plurals.settings_imported_failed, failedFiles, failedFiles))
        if (isEmpty()) add(context.getString(R.string.settings_imported_empty))
    }.joinToString(", ")
}

/**
 * Drives the import review: reads the picked backup files, lets the user choose per station what
 * to bring in (station details, track history, starred tracks), then saves only that.
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val trackHistoryRepository: TrackHistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val transactionRunner: TransactionRunner,
    @ApplicationContext private val context: Context,
    private val stationFileParser: CompositeStationFileParser = CompositeStationFileParser()
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    /**
     * How the last load or import ended, for Settings to show once the import screen has closed
     * (the import screen itself closes as soon as the state is back to [ImportUiState.Idle]).
     */
    private val _result = MutableStateFlow<ImportEvent?>(null)
    val result: StateFlow<ImportEvent?> = _result.asStateFlow()

    /** What each station brings, by uuid, kept while the review is open. */
    private var parsed: Map<String, ParsedStation> = emptyMap()
    private var loadJob: Job? = null

    private class ParsedStation(
        val station: BackupStation,
        val tracks: List<BackupTrack>,
        val starred: List<BackupStarredTrack>
    )

    fun load(uris: List<Uri>) {
        loadJob?.cancel()
        _uiState.value = ImportUiState.Loading
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            var failedFiles = 0
            val stations = mutableListOf<BackupStation>()
            for (uri in uris) {
                val fileStations = try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stationFileParser.parse(stream)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Could not read import file $uri", e)
                    null
                }
                if (fileStations == null) {
                    failedFiles++
                } else {
                    stations += fileStations.filter { it.isUsable() }
                }
            }

            if (stations.isEmpty()) {
                _result.value = ImportEvent.NothingToImport(failedFiles)
                _uiState.value = ImportUiState.Idle
                return@launch
            }

            try {
                showReview(stations, failedFiles)
            } catch (e: Exception) {
                Log.e(TAG, "Could not prepare import", e)
                _result.value = ImportEvent.NothingToImport(uris.size)
                _uiState.value = ImportUiState.Idle
            }
        }
    }

    /** Ticks or unticks every part of a station. */
    fun toggleStation(stationUuid: String) = updateStations { item ->
        if (item.stationUuid != stationUuid) item
        else item.copy(selected = if (item.state == ToggleableState.On) emptySet() else item.availableParts.toSet())
    }

    fun setPart(stationUuid: String, part: ImportPart, checked: Boolean) = updateStations { item ->
        when {
            item.stationUuid != stationUuid || !item.canChoose(part) -> item
            checked -> item.copy(selected = item.selected + part)
            // Leaving out a new station leaves out its history and stars too.
            part == ImportPart.STATION && !item.isInLibrary -> item.copy(selected = emptySet())
            else -> item.copy(selected = item.selected - part)
        }
    }

    /** Selects or deselects Update vs Replace for an existing station. */
    fun setStationUpdateMode(stationUuid: String, mode: StationUpdateMode) = updateStations { item ->
        if (item.stationUuid != stationUuid) item
        else {
            val isCurrentlyInMode = ImportPart.STATION in item.selected && item.updateMode == mode
            if (isCurrentlyInMode) {
                // Tapping the currently selected option deselects it
                item.copy(selected = item.selected - ImportPart.STATION)
            } else {
                item.copy(
                    selected = item.selected + ImportPart.STATION,
                    updateMode = mode
                )
            }
        }
    }

    /** Toggles the expanded state of the field diff checklist for an existing station. */
    fun toggleStationDetails(stationUuid: String) = updateStations { item ->
        if (item.stationUuid != stationUuid) item
        else item.copy(isDetailsExpanded = !item.isDetailsExpanded)
    }

    /** Toggles whether a specific field should be updated for an existing station. */
    fun toggleStationField(stationUuid: String, field: StationDiffField) = updateStations { item ->
        if (item.stationUuid != stationUuid) item
        else {
            val diff = item.fieldDiff
            val newDiff = when (field) {
                StationDiffField.NAME -> diff.copy(updateName = !diff.updateName)
                StationDiffField.URL -> diff.copy(updateUrl = !diff.updateUrl)
                StationDiffField.TAGS -> diff.copy(updateTags = !diff.updateTags)
                StationDiffField.FAVICON -> diff.copy(updateFavicon = !diff.updateFavicon)
                StationDiffField.HOMEPAGE -> diff.copy(updateHomepage = !diff.updateHomepage)
                StationDiffField.COUNTRY -> diff.copy(updateCountry = !diff.updateCountry)
                StationDiffField.LANGUAGE -> diff.copy(updateLanguage = !diff.updateLanguage)
                StationDiffField.AUDIO -> diff.copy(updateAudio = !diff.updateAudio)
            }
            item.copy(fieldDiff = newDiff)
        }
    }

    /** Ticks or unticks every station in the "new" or "already in library" section. */
    fun toggleSection(existing: Boolean) {
        val review = _uiState.value as? ImportUiState.Review ?: return
        val selectAll = review.sectionState(existing) != ToggleableState.On
        updateStations { item ->
            if (item.isInLibrary != existing) item
            else item.copy(selected = if (selectAll) item.availableParts.toSet() else emptySet())
        }
    }

    /** Ticks or unticks [part] for every station that has it. */
    fun togglePartForAll(part: ImportPart) {
        val review = _uiState.value as? ImportUiState.Review ?: return
        val selectAll = review.partState(part) != ToggleableState.On
        updateStations { item ->
            when {
                !item.canChoose(part) -> item
                selectAll -> item.copy(selected = item.selected + part)
                else -> item.copy(selected = item.selected - part)
            }
        }
    }

    fun setKeepAllPlays(keep: Boolean) = _uiState.update { state ->
        if (state is ImportUiState.Review) state.copy(keepAllPlays = keep) else state
    }

    fun cancel() {
        loadJob?.cancel()
        parsed = emptyMap()
        _uiState.value = ImportUiState.Idle
    }

    fun import() {
        val review = _uiState.value as? ImportUiState.Review ?: return
        if (review.isImporting || review.selectedCount == 0) return
        _uiState.value = review.copy(isImporting = true)

        viewModelScope.launch(Dispatchers.IO) {
            val chosen = review.stations.filter { it.selected.isNotEmpty() && it.stationUuid in parsed }
            val oldLimit = settingsRepository.appPreferencesFlow.first().trackHistoryLimit
            val raiseLimit = review.keepAllPlays && review.exceedsHistoryLimit

            try {
                // The import trims history to the limit, so raise it first; put back below on failure.
                if (raiseLimit) settingsRepository.setTrackHistoryLimit(review.playsNeeded)

                // One flag per chosen station: true if it was added, false if it was already there.
                val wasAdded = transactionRunner.run {
                    // New stations go after the user's own, in the order they were listed, the same
                    // as stations added from Browse.
                    var nextOrder = libraryRepository.nextOrderIndex()
                    chosen.map { item ->
                        val uuid = item.stationUuid
                        val source = parsed.getValue(uuid)
                        val existing = libraryRepository.getEntityById(uuid)
                        if (ImportPart.STATION in item.selected) {
                            val fromFile = source.station.toStationEntity()
                            val entityToSave = if (existing != null) {
                                val diff = item.fieldDiff
                                if (item.updateMode == StationUpdateMode.UPDATE) {
                                    // Smart Merge with field overrides:
                                    existing.copy(
                                        name = if (diff.updateName && fromFile.name.isNotBlank()) fromFile.name else existing.name,
                                        url = if (diff.updateUrl && fromFile.url.isNotBlank()) fromFile.url else existing.url,
                                        urlResolved = if (diff.updateUrl && fromFile.urlResolved.isNotBlank()) fromFile.urlResolved else existing.urlResolved,
                                        favicon = if (diff.updateFavicon && fromFile.favicon.isNotBlank()) fromFile.favicon else existing.favicon,
                                        tags = if (diff.updateTags) (existing.tags + fromFile.tags).map { it.lowercase() }.distinct() else existing.tags,
                                        homepage = if (diff.updateHomepage && fromFile.homepage.isNotBlank()) fromFile.homepage else existing.homepage,
                                        countryCode = if (diff.updateCountry && fromFile.countryCode.isNotBlank()) fromFile.countryCode else existing.countryCode,
                                        languageCodes = if (diff.updateLanguage) (existing.languageCodes + fromFile.languageCodes).distinct() else existing.languageCodes,
                                        codec = if (diff.updateAudio && fromFile.codec.isNotBlank() && fromFile.codec != "unknown") fromFile.codec else existing.codec,
                                        bitrate = if (diff.updateAudio && fromFile.bitrate > 0) fromFile.bitrate else existing.bitrate,
                                        isCustom = existing.isCustom
                                    )
                                } else {
                                    // Strict Replace (overwrites from file, honoring diff overrides if any):
                                    fromFile.copy(
                                        orderIndex = existing.orderIndex,
                                        addedAt = existing.addedAt,
                                        lastPlayedAt = existing.lastPlayedAt,
                                        name = if (diff.nameDiffers && !diff.updateName) existing.name else fromFile.name,
                                        url = if (diff.urlDiffers && !diff.updateUrl) existing.url else fromFile.url,
                                        tags = if (diff.tagsDiffer && !diff.updateTags) existing.tags else fromFile.tags,
                                        favicon = if (diff.faviconDiffers && !diff.updateFavicon) existing.favicon else fromFile.favicon,
                                        homepage = if (diff.homepageDiffers && !diff.updateHomepage) existing.homepage else fromFile.homepage,
                                        countryCode = if (diff.countryDiffers && !diff.updateCountry) existing.countryCode else fromFile.countryCode,
                                        languageCodes = if (diff.languageDiffers && !diff.updateLanguage) existing.languageCodes else fromFile.languageCodes,
                                        codec = if (diff.audioDiffers && !diff.updateAudio) existing.codec else fromFile.codec,
                                        bitrate = if (diff.audioDiffers && !diff.updateAudio) existing.bitrate else fromFile.bitrate
                                    )
                                }
                            } else {
                                fromFile.copy(orderIndex = nextOrder++)
                            }
                            libraryRepository.insertEntity(entityToSave)
                        }
                        if (ImportPart.HISTORY in item.selected) {
                            trackHistoryRepository.importTracks(uuid, source.tracks.map { it.toTrackHistoryEntity(uuid) })
                        }
                        if (ImportPart.STARRED in item.selected) {
                            trackHistoryRepository.importStarredTracks(uuid, source.starred.map { it.toStarredTrackEntity(uuid) })
                        }
                        existing == null
                    }
                }

                val added = wasAdded.count { it }
                _result.value = ImportEvent.Finished(added, wasAdded.size - added, review.failedFiles)
            } catch (e: Exception) {
                Log.e(TAG, "Import failed, nothing was saved", e)
                if (raiseLimit) settingsRepository.setTrackHistoryLimit(oldLimit)
                _result.value = ImportEvent.Failed
            }

            parsed = emptyMap()
            _uiState.value = ImportUiState.Idle
        }
    }

    /** Called once Settings has shown the result message. */
    fun resultShown() {
        _result.value = null
    }

    /** Shows the review for stations already read from the picked files. */
    internal suspend fun showReview(stations: List<BackupStation>, failedFiles: Int = 0) {
        _uiState.value = buildReview(stations, failedFiles)
    }

    private suspend fun buildReview(stations: List<BackupStation>, failedFiles: Int): ImportUiState.Review {
        // The same station can appear in several files: newest copy wins, history and stars are combined.
        val byUuid = stations.groupBy { it.stationUuid }.mapValues { (_, copies) ->
            ParsedStation(
                station = copies.last(),
                tracks = copies.flatMap { it.tracks.orEmpty() }.distinctBy { it.timestamp },
                starred = copies.flatMap { it.allStarredTracks() }
                    .distinctBy { it.trackTitle.trim().lowercase(Locale.ROOT) }
            )
        }
        parsed = byUuid

        val historyLimit = settingsRepository.appPreferencesFlow.first().trackHistoryLimit
        val items = byUuid.values.map { source ->
            val uuid = source.station.stationUuid
            val existing = libraryRepository.getEntityById(uuid)
            val isInLibrary = existing != null
            val playsAfterImport = if (source.tracks.isEmpty()) 0 else {
                val local = trackHistoryRepository.getTrackTimestamps(uuid)
                (local + source.tracks.map { it.timestamp }).toSet().size
            }
            val fromEntity = source.station.toStationEntity()
            val fieldDiff = if (existing != null) {
                val nameDiffers = fromEntity.name.isNotBlank() && fromEntity.name != existing.name
                val urlDiffers = fromEntity.url.isNotBlank() && fromEntity.url != existing.url
                val newTags = fromEntity.tags.filter { it.isNotBlank() && it !in existing.tags }
                val tagsDiffer = fromEntity.tags != existing.tags
                val faviconDiffers = fromEntity.favicon.isNotBlank() && fromEntity.favicon != existing.favicon
                val homepageDiffers = fromEntity.homepage.isNotBlank() && fromEntity.homepage != existing.homepage
                val countryDiffers = fromEntity.countryCode.isNotBlank() && fromEntity.countryCode != existing.countryCode
                val languageDiffers = fromEntity.languageCodes.isNotEmpty() && fromEntity.languageCodes != existing.languageCodes
                val newCodec = if (fromEntity.codec.isNotBlank() && fromEntity.codec != "unknown") fromEntity.codec else ""
                val audioDiffers = (newCodec.isNotBlank() && newCodec != existing.codec) || (fromEntity.bitrate > 0 && fromEntity.bitrate != existing.bitrate)
                StationFieldDiff(
                    nameDiffers = nameDiffers,
                    oldName = existing.name,
                    newName = fromEntity.name,
                    updateName = true,

                    urlDiffers = urlDiffers,
                    oldUrl = existing.url,
                    newUrl = fromEntity.url,
                    updateUrl = true,

                    tagsDiffer = tagsDiffer,
                    oldTags = existing.tags,
                    newTags = if (fromEntity.tags.isNotEmpty()) fromEntity.tags else emptyList(),
                    updateTags = true,

                    faviconDiffers = faviconDiffers,
                    oldFavicon = existing.favicon,
                    newFavicon = fromEntity.favicon,
                    updateFavicon = true,

                    homepageDiffers = homepageDiffers,
                    oldHomepage = existing.homepage,
                    newHomepage = fromEntity.homepage,
                    updateHomepage = true,

                    countryDiffers = countryDiffers,
                    oldCountryCode = existing.countryCode,
                    newCountryCode = fromEntity.countryCode,
                    updateCountry = true,

                    languageDiffers = languageDiffers,
                    oldLanguageCodes = existing.languageCodes,
                    newLanguageCodes = fromEntity.languageCodes,
                    updateLanguage = true,

                    audioDiffers = audioDiffers,
                    oldCodec = existing.codec,
                    newCodec = fromEntity.codec,
                    oldBitrate = existing.bitrate,
                    newBitrate = fromEntity.bitrate,
                    updateAudio = true
                )
            } else StationFieldDiff()

            val item = ImportStationItem(
                station = fromEntity.toDomain(),
                isInLibrary = isInLibrary,
                playCount = source.tracks.size,
                starredCount = source.starred.size,
                playsAfterImport = playsAfterImport,
                selected = emptySet(),
                fieldDiff = fieldDiff
            )
            // New stations come in whole. For ones already in the library, history and stars are
            // merged by default, but the user's own station details are kept.
            item.copy(
                selected = if (isInLibrary) item.availableParts.toSet() - ImportPart.STATION
                else item.availableParts.toSet()
            )
        }.sortedBy { it.name.lowercase(Locale.ROOT) }

        return ImportUiState.Review(
            newStations = items.filterNot { it.isInLibrary },
            existingStations = items.filter { it.isInLibrary },
            failedFiles = failedFiles,
            historyLimit = historyLimit
        )
    }

    private fun updateStations(transform: (ImportStationItem) -> ImportStationItem) = _uiState.update { state ->
        if (state !is ImportUiState.Review || state.isImporting) state
        else state.copy(
            newStations = state.newStations.map(transform),
            existingStations = state.existingStations.map(transform)
        )
    }

    /** Gson can leave required fields null for hand-edited or damaged files. */
    @Suppress("SENSELESS_COMPARISON", "USELESS_CAST")
    private fun BackupStation.isUsable(): Boolean =
        !(stationUuid as String?).isNullOrBlank() && !(name as String?).isNullOrBlank() && !(url as String?).isNullOrBlank()
}
