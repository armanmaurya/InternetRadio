package com.armanmaurya.internetradio.ui.shared.viewmodels

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.data.model.AppPreferences
import com.armanmaurya.internetradio.data.model.ConflictStrategy
import com.armanmaurya.internetradio.data.model.LibraryBackup
import com.armanmaurya.internetradio.data.repository.LibraryRepository
import com.armanmaurya.internetradio.data.repository.SettingsRepository
import com.armanmaurya.internetradio.ui.shared.theme.AppTheme
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
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
    private val libraryRepository: LibraryRepository
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

    fun setPureBlack(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPureBlack(enabled)
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

    fun setStopOnAudioBecomingNoisy(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setStopOnAudioBecomingNoisy(enabled)
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

    fun exportLibrary(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entities = libraryRepository.getAllStationEntities()
                Log.d(TAG, "Exporting ${entities.size} stations to $uri")

                val versionName = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (e: Exception) {
                    Log.w(TAG, "Could not get version name", e)
                    "unknown"
                }

                // Use SimpleDateFormat for API 24 compatibility (Instant.now() requires API 26)
                val exportedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                    .format(Date())

                val backup = LibraryBackup(
                    exportedAt = exportedAt,
                    appVersion = versionName ?: "unknown",
                    stations = entities
                )
                val json = Gson().toJson(backup)
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray())
                } ?: run {
                    Log.e(TAG, "Export failed: output stream was null for uri=$uri")
                    _backupResult.send("Export failed: could not open file for writing")
                    return@launch
                }

                Log.d(TAG, "Export successful: ${entities.size} stations written")
                _backupResult.send("Exported ${entities.size} station(s) successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Export failed with exception", e)
                _backupResult.send("Export failed: ${e.localizedMessage}")
            }
        }
    }

    fun importLibrary(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting import from $uri")

                val json = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.readText()
                    ?: run {
                        Log.e(TAG, "Import failed: could not open input stream for uri=$uri")
                        _backupResult.send("Import failed: could not read file")
                        return@launch
                    }

                Log.d(TAG, "Read ${json.length} chars from file")

                val backup: LibraryBackup? = try {
                    Gson().fromJson(json, LibraryBackup::class.java)
                } catch (e: JsonSyntaxException) {
                    Log.e(TAG, "Import failed: invalid JSON format", e)
                    _backupResult.send("Import failed: invalid file format (not valid JSON)")
                    return@launch
                }

                if (backup == null) {
                    Log.e(TAG, "Import failed: Gson returned null — JSON was null or empty")
                    _backupResult.send("Import failed: file is empty or unreadable")
                    return@launch
                }

                if (backup.stations == null) {
                    Log.e(TAG, "Import failed: 'stations' field is missing from JSON. Schema version: ${backup.schemaVersion}")
                    _backupResult.send("Import failed: backup file has no stations field")
                    return@launch
                }

                Log.d(TAG, "Parsed backup: schemaVersion=${backup.schemaVersion}, stations=${backup.stations.size}")

                val strategy = uiState.value.conflictStrategy
                Log.d(TAG, "Using conflict strategy: $strategy")

                var imported = 0
                var updated = 0
                var skipped = 0

                backup.stations.forEach { entity ->
                    try {
                        val existing = libraryRepository.getEntityById(entity.stationUuid)
                        when {
                            existing == null -> {
                                libraryRepository.insertEntity(entity)
                                imported++
                                Log.d(TAG, "Inserted: ${entity.name}")
                            }
                            strategy == ConflictStrategy.OVERWRITE -> {
                                libraryRepository.insertEntity(entity)
                                updated++
                                Log.d(TAG, "Overwritten: ${entity.name}")
                            }
                            strategy == ConflictStrategy.KEEP_NEWER -> {
                                if (entity.addedAt > existing.addedAt) {
                                    libraryRepository.insertEntity(entity)
                                    updated++
                                    Log.d(TAG, "Kept newer (backup): ${entity.name}")
                                } else {
                                    skipped++
                                    Log.d(TAG, "Kept newer (local): ${entity.name}")
                                }
                            }
                            else -> {
                                skipped++
                                Log.d(TAG, "Skipped existing: ${entity.name}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to process station '${entity.name}' (${entity.stationUuid})", e)
                    }
                }

                val parts = buildList {
                    if (imported > 0) add("Imported $imported")
                    if (updated > 0) add("Updated $updated")
                    if (skipped > 0) add("Skipped $skipped already existing")
                    if (isEmpty()) add("No changes — all stations already exist")
                }
                val resultMessage = parts.joinToString(", ")
                Log.d(TAG, "Import complete: $resultMessage")
                _backupResult.send(resultMessage)

            } catch (e: Exception) {
                Log.e(TAG, "Import failed with unexpected exception", e)
                _backupResult.send("Import failed: ${e.localizedMessage}")
            }
        }
    }
}