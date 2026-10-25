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
import com.armanmaurya.internetradio.domain.model.StartOfWeek
import com.armanmaurya.internetradio.data.backup.LibraryBackup
import com.armanmaurya.internetradio.data.backup.toBackupStation
import com.armanmaurya.internetradio.domain.repository.LibraryRepository
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import com.armanmaurya.internetradio.ui.shared.theme.AppColor
import com.armanmaurya.internetradio.ui.shared.theme.AppTheme
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.armanmaurya.internetradio.domain.model.MalformedBackupException
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
    private val fileSystemFacade: com.armanmaurya.internetradio.core.system.FileSystemFacade,
    private val systemFacade: com.armanmaurya.internetradio.core.system.SystemFacade,
    private val widgetController: WidgetController,
    private val autoBackupManager: com.armanmaurya.internetradio.domain.controller.AutoBackupManager,
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

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val _showMalformedBackupBottomSheet = MutableStateFlow(false)
    val showMalformedBackupBottomSheet = _showMalformedBackupBottomSheet.asStateFlow()

    val localStationsCount: StateFlow<Int> = libraryRepository.getAllStationEntitiesFlow()
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

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

    fun setSelectAllTextOnFocus(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSelectAllTextOnFocus(enabled)
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

                val backup = LibraryBackup(
                    exportedAt = exportedAt,
                    appVersion = versionName,
                    stations = entities.map { it.toBackupStation() }
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

    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoBackupEnabled(enabled)
            if (enabled && !uiState.value.autoBackupTreeUri.isNullOrBlank()) {
                autoBackupManager.performBackup()
            }
        }
    }

    fun setAutoBackupFolder(context: Context, treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)

                val folderPath = resolveFolderPath(context, treeUri)
                settingsRepository.setAutoBackupFolder(treeUri.toString(), folderPath)
                settingsRepository.setAutoBackupEnabled(true)
                _backupResult.send(context.getString(R.string.settings_folder_selected, folderPath))

                // Perform smart sync (auto-restores if local is empty, merges if both exist, creates if missing)
                _isSyncing.value = true
                try {
                    val result = autoBackupManager.performSmartSync()
                    result.onSuccess { message ->
                        _backupResult.send(message)
                    }.onFailure { e ->
                        if (e is MalformedBackupException || e.cause is MalformedBackupException) {
                            _showMalformedBackupBottomSheet.value = true
                        }
                        _backupResult.send(context.getString(R.string.settings_sync_status_failed, e.localizedMessage ?: context.getString(R.string.error_unknown)))
                    }
                } finally {
                    _isSyncing.value = false
                }
            } catch (e: Exception) {
                _isSyncing.value = false
                Log.e(TAG, "Failed to persist SAF permission", e)
                _backupResult.send(context.getString(R.string.settings_folder_error, e.localizedMessage ?: context.getString(R.string.error_unknown)))
            }
        }
    }

    fun syncNow(context: Context) {
        if (_isSyncing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncing.value = true
            try {
                val result = autoBackupManager.performSmartSync()
                result.onSuccess { message ->
                    _backupResult.send(message)
                }.onFailure { e ->
                    if (e is MalformedBackupException || e.cause is MalformedBackupException) {
                        _showMalformedBackupBottomSheet.value = true
                    }
                    _backupResult.send(context.getString(R.string.settings_sync_status_failed, e.localizedMessage ?: context.getString(R.string.error_unknown)))
                }
            } catch (e: Exception) {
                if (e is MalformedBackupException || e.cause is MalformedBackupException) {
                    _showMalformedBackupBottomSheet.value = true
                }
                _backupResult.send(context.getString(R.string.settings_sync_status_failed, e.localizedMessage ?: context.getString(R.string.error_unknown)))
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun dismissMalformedBackupBottomSheet() {
        _showMalformedBackupBottomSheet.value = false
    }

    fun overwriteMalformedBackup(context: Context) {
        _showMalformedBackupBottomSheet.value = false
        if (_isSyncing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncing.value = true
            try {
                val result = autoBackupManager.performBackup()
                result.onSuccess {
                    _backupResult.send(context.getString(R.string.settings_malformed_backup_overwritten))
                }.onFailure { e ->
                    _backupResult.send(context.getString(R.string.settings_sync_status_failed, e.localizedMessage ?: context.getString(R.string.error_unknown)))
                }
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun clearAutoBackupFolder(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            uiState.value.autoBackupTreeUri?.let { uriStr ->
                try {
                    val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.releasePersistableUriPermission(Uri.parse(uriStr), flags)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to release persistable URI permission", e)
                }
            }
            settingsRepository.setAutoBackupFolder(null, null)
            settingsRepository.setAutoBackupEnabled(false)
            _backupResult.send(context.getString(R.string.settings_folder_cleared))
        }
    }

    private fun resolveFolderPath(context: Context, treeUri: Uri): String {
        return try {
            val authority = treeUri.authority
            val docId = try {
                android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            } catch (_: Exception) {
                treeUri.lastPathSegment ?: ""
            }

            when (authority) {
                "com.android.externalstorage.documents" -> {
                    val split = docId.split(":", limit = 2)
                    val storageType = split[0]
                    val relativePath = if (split.size > 1) split[1].trim('/') else ""
                    val storageName = if (storageType.equals("primary", ignoreCase = true)) {
                        "Internal Storage"
                    } else {
                        "SD Card"
                    }
                    if (relativePath.isBlank()) storageName else "$storageName/$relativePath"
                }
                "com.google.android.apps.docs.storage" -> {
                    val name = queryDisplayName(context, treeUri)
                    if (!name.isNullOrBlank()) "Google Drive/$name" else "Google Drive"
                }
                "com.android.providers.downloads.documents" -> {
                    val name = queryDisplayName(context, treeUri)
                    if (!name.isNullOrBlank()) "Downloads/$name" else "Downloads"
                }
                else -> {
                    val name = queryDisplayName(context, treeUri)
                    val providerName = when {
                        authority?.contains("nextcloud", ignoreCase = true) == true -> "Nextcloud"
                        authority?.contains("owncloud", ignoreCase = true) == true -> "ownCloud"
                        authority?.contains("onedrive", ignoreCase = true) == true -> "OneDrive"
                        authority?.contains("dropbox", ignoreCase = true) == true -> "Dropbox"
                        else -> null
                    }
                    when {
                        providerName != null && !name.isNullOrBlank() -> "$providerName/$name"
                        !name.isNullOrBlank() -> name
                        docId.isNotBlank() -> docId
                        else -> treeUri.lastPathSegment ?: "Folder"
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve folder path for $treeUri", e)
            treeUri.lastPathSegment ?: "Folder"
        }
    }

    private fun queryDisplayName(context: Context, treeUri: Uri): String? {
        return try {
            val docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            val docUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            val cursor = context.contentResolver.query(
                docUri,
                arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (index != -1) it.getString(index) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}