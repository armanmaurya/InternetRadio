package com.armanmaurya.internetradio.ui.shared.viewmodels

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.AppPreferences
import com.armanmaurya.internetradio.domain.model.ConflictStrategy
import com.armanmaurya.internetradio.domain.model.RecordingTimeFormat
import com.armanmaurya.internetradio.domain.model.StartOfWeek
import com.armanmaurya.internetradio.data.backup.LibraryBackup
import com.armanmaurya.internetradio.data.backup.toBackupStation
import com.armanmaurya.internetradio.data.backup.toBackupStarredTrack
import com.armanmaurya.internetradio.data.backup.toBackupTrack
import com.armanmaurya.internetradio.data.backup.toStarredTrackEntity
import com.armanmaurya.internetradio.data.backup.toTrackHistoryEntity
import com.armanmaurya.internetradio.domain.repository.LibraryRepository
import com.armanmaurya.internetradio.domain.repository.RecentRepository
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import com.armanmaurya.internetradio.domain.repository.TrackHistoryRepository
import com.armanmaurya.internetradio.ui.shared.theme.AppColor
import com.armanmaurya.internetradio.ui.shared.theme.AppTheme
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import com.armanmaurya.internetradio.domain.controller.WidgetController
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

private const val TAG = "SettingsViewModel"

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val libraryRepository: LibraryRepository,
    private val recentRepository: RecentRepository,
    private val trackHistoryRepository: TrackHistoryRepository,
    private val fileSystemFacade: com.armanmaurya.internetradio.core.system.FileSystemFacade,
    private val systemFacade: com.armanmaurya.internetradio.core.system.SystemFacade,
    private val widgetController: WidgetController,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val uiState: StateFlow<AppPreferences> = settingsRepository.appPreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppPreferences()
        )

    private val _backupResult = Channel<String>(Channel.BUFFERED)
    val backupResult = _backupResult.receiveAsFlow()

    fun setAppTheme(theme: AppTheme) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(theme)
        }
    }

    fun setDynamicTheme(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDynamicColor(enabled)
        }
    }

    fun setAppColor(color: AppColor) {
        viewModelScope.launch {
            settingsRepository.setAppColor(color)
        }
    }

    fun setCustomColor(colorArgb: Int) {
        viewModelScope.launch {
            settingsRepository.setCustomColor(colorArgb)
        }
    }

    fun setAutoRouteToBrowseOnSearch(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoRouteToBrowseOnSearch(enabled)
        }
    }

    fun setAutoPlayOnStart(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoPlayOnStart(enabled)
        }
    }

    fun setTvScreensaverMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setTvScreensaverMinutes(minutes)
        }
    }

    /** The stored preferences, waiting for them to load (unlike [uiState], which starts with defaults). */
    suspend fun loadPreferences(): AppPreferences = settingsRepository.appPreferencesFlow.first()

    fun setTvLyricsOpen(open: Boolean) {
        viewModelScope.launch {
            settingsRepository.setTvLyricsOpen(open)
        }
    }

    fun setWidgetBackgroundAlpha(alpha: Float) {
        viewModelScope.launch {
            settingsRepository.setWidgetBackgroundAlpha(alpha)
            withContext(Dispatchers.IO) {
                widgetController.updateWidgetAlpha(alpha)
            }
        }
    }

    fun setDisableUpdateCheck(disabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDisableUpdateCheck(disabled)
        }
    }

    fun setStopOnAudioBecomingNoisy(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setStopOnAudioBecomingNoisy(enabled)
        }
    }

    fun setPauseOnVolumeZero(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPauseOnVolumeZero(enabled)
        }
    }

    fun setEnableVolumeBoost(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setEnableVolumeBoost(enabled)
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setKeepScreenOn(enabled)
        }
    }

    fun setShowStationThumbnails(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowStationThumbnails(enabled)
        }
    }

    fun setAppLanguage(language: String) {
        viewModelScope.launch {
            settingsRepository.setAppLanguage(language)
            val localeList = if (language == "System") {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(language)
            }
            AppCompatDelegate.setApplicationLocales(localeList)
        }
    }

    fun setTrackHistoryLimit(limit: Int) {
        viewModelScope.launch {
            settingsRepository.setTrackHistoryLimit(limit)
        }
    }

    fun setIncludeTracksInBackup(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setIncludeTracksInBackup(enabled)
        }
    }

    fun setRecentLimit(limit: Int) {
        viewModelScope.launch {
            settingsRepository.setRecentLimit(limit)
            // Lowering the limit removes the oldest recents right away, so raising it later doesn't bring them back
            recentRepository.trimToLimit()
        }
    }

    fun setDefaultTab(tabIndex: Int) {
        viewModelScope.launch {
            settingsRepository.setDefaultTab(tabIndex)
        }
    }

    fun setMaxRetryDuration(durationInMillis: Long) {
        viewModelScope.launch {
            settingsRepository.setMaxRetryDuration(durationInMillis)
        }
    }

    fun setConflictStrategy(strategy: ConflictStrategy) {
        viewModelScope.launch {
            settingsRepository.setConflictStrategy(strategy)
        }
    }

    fun setRecordingTimeFormat(format: RecordingTimeFormat) {
        viewModelScope.launch {
            settingsRepository.setRecordingTimeFormat(format)
        }
    }

    fun setStartOfWeek(startOfWeek: StartOfWeek) {
        viewModelScope.launch {
            settingsRepository.setStartOfWeek(startOfWeek)
        }
    }

    fun setShowCoverArtInNotification(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowCoverArtInNotification(enabled)
        }
    }

    fun setAlarmVolumeTransitionSeconds(seconds: Int) {
        viewModelScope.launch {
            settingsRepository.setAlarmVolumeTransitionSeconds(seconds)
        }
    }

    fun setAlarmVolumeTransitionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAlarmVolumeTransitionEnabled(enabled)
        }
    }

    fun setHasRatedApp(rated: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHasRatedApp(rated)
        }
    }

    fun exportLibrary(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entities = libraryRepository.getAllStationEntities()
                Log.d(TAG, "Exporting ${entities.size} stations to $uri")

                val versionName = systemFacade.getAppVersionName()

                // Use SimpleDateFormat for API 24 compatibility (Instant.now() requires API 26)
                val exportedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                    .format(Date())

                val includeTracks = uiState.value.includeTracksInBackup
                val backupStations = entities.map { station ->
                    val tracks = if (includeTracks) {
                        trackHistoryRepository.getAllTracksForStation(station.stationUuid).map { it.toBackupTrack() }
                    } else null
                    val starredTracks = if (includeTracks) {
                        trackHistoryRepository.getAllStarredTracksForStation(station.stationUuid).map { it.toBackupStarredTrack() }
                    } else null
                    station.toBackupStation(tracks = tracks, starredTracks = starredTracks)
                }

                val backup = LibraryBackup(
                    exportedAt = exportedAt,
                    appVersion = versionName,
                    stations = backupStations
                )
                val json = Gson().toJson(backup)
                fileSystemFacade.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray())
                } ?: throw Exception("Could not open output stream")

                Log.d(TAG, "Export successful: ${entities.size} stations written")
                _backupResult.send(context.resources.getQuantityString(R.plurals.settings_exported_success, entities.size, entities.size))
            } catch (e: Exception) {
                Log.e(TAG, "Export failed with exception", e)
                _backupResult.send(context.getString(R.string.settings_exported_failed_exception, e.localizedMessage))
            }
        }
    }

    fun importLibraries(context: Context, uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            var totalImported = 0
            var totalUpdated = 0
            var totalSkipped = 0
            var failedFiles = 0

            val strategy = uiState.value.conflictStrategy
            Log.d(TAG, "Using conflict strategy: $strategy")

            for (uri in uris) {
                try {
                    Log.d(TAG, "Starting import from $uri")

                    val json = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.readText()
                        ?: run {
                            Log.e(TAG, "Import failed: could not open input stream for uri=$uri")
                            failedFiles++
                            continue
                        }

                    val backup: LibraryBackup? = try {
                        Gson().fromJson(json, LibraryBackup::class.java)
                    } catch (e: JsonSyntaxException) {
                        Log.e(TAG, "Import failed: invalid JSON format", e)
                        failedFiles++
                        continue
                    }

                    if (backup == null || backup.stations == null) {
                        Log.e(TAG, "Import failed: File empty or stations missing")
                        failedFiles++
                        continue
                    }

                    backup.stations.forEach { backupStation ->
                        val entity = backupStation.toStationEntity()
                        try {
                            val existing = libraryRepository.getEntityById(entity.stationUuid)
                            when {
                                existing == null -> {
                                    libraryRepository.insertEntity(entity)
                                    totalImported++
                                }
                                strategy == ConflictStrategy.OVERWRITE -> {
                                    libraryRepository.insertEntity(entity)
                                    totalUpdated++
                                }
                                strategy == ConflictStrategy.KEEP_NEWER -> {
                                    if ((entity.addedAt ?: 0L) > (existing.addedAt ?: 0L)) {
                                        libraryRepository.insertEntity(entity)
                                        totalUpdated++
                                    } else {
                                        totalSkipped++
                                    }
                                }
                                else -> {
                                    totalSkipped++
                                }
                            }
                            if (!backupStation.tracks.isNullOrEmpty()) {
                                trackHistoryRepository.importTracks(
                                    entity.stationUuid,
                                    backupStation.tracks.map { it.toTrackHistoryEntity(entity.stationUuid) },
                                    strategy
                                )
                            }
                            val starredTracks = backupStation.allStarredTracks()
                            if (starredTracks.isNotEmpty()) {
                                trackHistoryRepository.importStarredTracks(
                                    entity.stationUuid,
                                    starredTracks.map { it.toStarredTrackEntity(entity.stationUuid) }
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to process station '${entity.name}' (${entity.stationUuid})", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Import failed with unexpected exception for uri=$uri", e)
                    failedFiles++
                }
            }

            val parts = buildList {
                if (totalImported > 0) add(context.getString(R.string.settings_imported, totalImported))
                if (totalUpdated > 0) add(context.getString(R.string.settings_imported_updated, totalUpdated))
                if (totalSkipped > 0) add(context.getString(R.string.settings_imported_skipped, totalSkipped))
                if (failedFiles > 0) add(context.resources.getQuantityString(R.plurals.settings_imported_failed, failedFiles, failedFiles))
                if (isEmpty()) add(context.getString(R.string.settings_imported_empty))
            }
            val resultMessage = parts.joinToString(", ")
            Log.d(TAG, "Import complete: $resultMessage")
            _backupResult.send(resultMessage)
        }
    }
}
