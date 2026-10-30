package com.armanmaurya.internetradio.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.preferencesDataStore
import com.armanmaurya.internetradio.domain.model.AppPreferences
import com.armanmaurya.internetradio.domain.model.ConflictStrategy
import com.armanmaurya.internetradio.domain.model.LibrarySortOption
import com.armanmaurya.internetradio.domain.model.StartOfWeek
import com.armanmaurya.internetradio.ui.shared.theme.AppColor
import com.armanmaurya.internetradio.ui.shared.theme.AppTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import com.armanmaurya.internetradio.domain.repository.SettingsRepository

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler(
        produceNewData = { emptyPreferences() }
    )
)

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {
    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val APP_COLOR = stringPreferencesKey("app_color")
        val CUSTOM_COLOR_ARGB = androidx.datastore.preferences.core.intPreferencesKey("custom_color_argb")
        val PURE_BLACK = booleanPreferencesKey("pure_black")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val SELECTED_COUNTRY_CODE = stringPreferencesKey("selected_country_code")
        val SELECTED_STATE_CODE = stringPreferencesKey("selected_state_code")
        val SELECTED_LANGUAGE = stringPreferencesKey("selected_language")
        val SELECTED_TAGS = androidx.datastore.preferences.core.stringSetPreferencesKey("selected_tags")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val SORT_REVERSE = booleanPreferencesKey("sort_reverse")
        val USE_FILTER_ON_RECENT = booleanPreferencesKey("use_filter_on_recent")
        val USE_FILTER_ON_FAVORITES = booleanPreferencesKey("use_filter_on_favorites")
        val USE_FILTER_ON_ADDED = booleanPreferencesKey("use_filter_on_added")
        val AUTO_ROUTE_TO_BROWSE_ON_SEARCH = booleanPreferencesKey("auto_route_to_browse_on_search")
        val IS_GRID_VIEW_BROWSE = booleanPreferencesKey("is_grid_view_browse")
        val IS_GRID_VIEW_RECENT = booleanPreferencesKey("is_grid_view_recent")
        val IS_GRID_VIEW_FAVORITES = booleanPreferencesKey("is_grid_view_favorites")
        val IS_GRID_VIEW_ADDED = booleanPreferencesKey("is_grid_view_added")
        val TRACK_HISTORY_LIMIT = androidx.datastore.preferences.core.intPreferencesKey("track_history_limit")
        val RECENT_LIMIT = androidx.datastore.preferences.core.intPreferencesKey("recent_limit")
        val DEFAULT_TAB = androidx.datastore.preferences.core.intPreferencesKey("default_tab")
        val AUTO_PLAY_ON_START = booleanPreferencesKey("auto_play_on_start")
        val APP_LAUNCH_COUNT = androidx.datastore.preferences.core.intPreferencesKey("app_launch_count")
        val HAS_RATED_APP = booleanPreferencesKey("has_rated_app")
        val DISABLE_UPDATE_CHECK = booleanPreferencesKey("disable_update_check")
        val LAST_UPDATE_CHECK_TIME = androidx.datastore.preferences.core.longPreferencesKey("last_update_check_time")
        val MAX_RETRY_DURATION = androidx.datastore.preferences.core.longPreferencesKey("max_retry_duration")
        val CONFLICT_STRATEGY = stringPreferencesKey("conflict_strategy")
        val STOP_ON_AUDIO_BECOMING_NOISY = booleanPreferencesKey("stop_on_audio_becoming_noisy")
        val LIBRARY_SORT_OPTION = stringPreferencesKey("library_sort_option")
        val START_OF_WEEK = stringPreferencesKey("start_of_week")
        val SHOW_COVER_ART_IN_NOTIFICATION = booleanPreferencesKey("show_cover_art_in_notification")
        val IS_ALARM_VOLUME_TRANSITION_ENABLED = booleanPreferencesKey("is_alarm_volume_transition_enabled")
        val ALARM_VOLUME_TRANSITION_SECONDS = androidx.datastore.preferences.core.intPreferencesKey("alarm_volume_transition_seconds")
        val SELECT_ALL_TEXT_ON_FOCUS = booleanPreferencesKey("select_all_text_on_focus")
        val PAUSE_ON_VOLUME_ZERO = booleanPreferencesKey("pause_on_volume_zero")
        val ENABLE_VOLUME_BOOST = booleanPreferencesKey("enable_volume_boost")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val WIDGET_BACKGROUND_ALPHA = androidx.datastore.preferences.core.floatPreferencesKey("widget_background_alpha")
        val SHOW_STATION_THUMBNAILS = booleanPreferencesKey("show_station_thumbnails")
        val IS_AUTO_BACKUP_ENABLED = booleanPreferencesKey("is_auto_backup_enabled")
        val AUTO_BACKUP_TREE_URI = stringPreferencesKey("auto_backup_tree_uri")
        val AUTO_BACKUP_FOLDER_NAME = stringPreferencesKey("auto_backup_folder_name")
        val LAST_AUTO_BACKUP_TIME = androidx.datastore.preferences.core.longPreferencesKey("last_auto_backup_time")
        val LAST_AUTO_BACKUP_STATUS = stringPreferencesKey("last_auto_backup_status")
        val LAST_AUTO_BACKUP_ERROR = stringPreferencesKey("last_auto_backup_error")
    }

    override val appPreferencesFlow: Flow<AppPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val themeModeName = preferences[PreferencesKeys.THEME_MODE]
            val themeMode = AppTheme.entries.find { it.name == themeModeName } ?: AppTheme.SYSTEM
            
            val useDynamicColor = preferences[PreferencesKeys.DYNAMIC_COLOR] ?: true
            val appColorName = preferences[PreferencesKeys.APP_COLOR]
            val appColor = when (appColorName) {
                "ORANGE" -> AppColor.PINK
                else -> AppColor.entries.find { it.name == appColorName } ?: AppColor.PURPLE
            }
            val customColorArgb = preferences[PreferencesKeys.CUSTOM_COLOR_ARGB] ?: 0xFF00BCD4.toInt()
            val pureBlack = preferences[PreferencesKeys.PURE_BLACK] ?: false
            val appLanguage = preferences[PreferencesKeys.APP_LANGUAGE] ?: "System"
            val selectedCountryCode = preferences[PreferencesKeys.SELECTED_COUNTRY_CODE]
            val selectedStateCode = preferences[PreferencesKeys.SELECTED_STATE_CODE]
            val selectedLanguage = preferences[PreferencesKeys.SELECTED_LANGUAGE]
            val selectedTags = preferences[PreferencesKeys.SELECTED_TAGS] ?: emptySet()
            val stopOnAudioBecomingNoisy = preferences[PreferencesKeys.STOP_ON_AUDIO_BECOMING_NOISY] ?: true
            val pauseOnVolumeZero = preferences[PreferencesKeys.PAUSE_ON_VOLUME_ZERO] ?: false
            val enableVolumeBoost = preferences[PreferencesKeys.ENABLE_VOLUME_BOOST] ?: false
            val keepScreenOn = preferences[PreferencesKeys.KEEP_SCREEN_ON] ?: false
            val order = preferences[PreferencesKeys.SORT_ORDER] ?: "votes"
            val reverse = preferences[PreferencesKeys.SORT_REVERSE] ?: true
            val useFilterOnRecent = preferences[PreferencesKeys.USE_FILTER_ON_RECENT] ?: false
            val useFilterOnFavorites = preferences[PreferencesKeys.USE_FILTER_ON_FAVORITES] ?: false
            val useFilterOnAdded = preferences[PreferencesKeys.USE_FILTER_ON_ADDED] ?: false
            val autoRouteToBrowseOnSearch = preferences[PreferencesKeys.AUTO_ROUTE_TO_BROWSE_ON_SEARCH] ?: true
            val isGridViewBrowse = preferences[PreferencesKeys.IS_GRID_VIEW_BROWSE] ?: true
            val isGridViewRecent = preferences[PreferencesKeys.IS_GRID_VIEW_RECENT] ?: true
            val isGridViewFavorites = preferences[PreferencesKeys.IS_GRID_VIEW_FAVORITES] ?: true
            val isGridViewAdded = preferences[PreferencesKeys.IS_GRID_VIEW_ADDED] ?: true
            val trackHistoryLimit = preferences[PreferencesKeys.TRACK_HISTORY_LIMIT] ?: 50
            val recentLimit = preferences[PreferencesKeys.RECENT_LIMIT] ?: 50
            val defaultTab = preferences[PreferencesKeys.DEFAULT_TAB] ?: 0
            val autoPlayOnStart = preferences[PreferencesKeys.AUTO_PLAY_ON_START] ?: false
            val appLaunchCount = preferences[PreferencesKeys.APP_LAUNCH_COUNT] ?: 0
            val hasRatedApp = preferences[PreferencesKeys.HAS_RATED_APP] ?: false
            val disableUpdateCheck = preferences[PreferencesKeys.DISABLE_UPDATE_CHECK] ?: false
            val lastUpdateCheckTime = preferences[PreferencesKeys.LAST_UPDATE_CHECK_TIME] ?: 0L
            val maxRetryDuration = preferences[PreferencesKeys.MAX_RETRY_DURATION] ?: 300_000L
            val conflictStrategyName = preferences[PreferencesKeys.CONFLICT_STRATEGY]
            val conflictStrategy = ConflictStrategy.entries.find { it.name == conflictStrategyName } ?: ConflictStrategy.SKIP
            val librarySortOptionName = preferences[PreferencesKeys.LIBRARY_SORT_OPTION]
            val librarySortOption = LibrarySortOption.entries.find { it.name == librarySortOptionName } ?: LibrarySortOption.RECENTLY_ADDED
            val startOfWeekName = preferences[PreferencesKeys.START_OF_WEEK]
            val startOfWeek = StartOfWeek.entries.find { it.name == startOfWeekName } ?: StartOfWeek.SUNDAY
            val showCoverArtInNotification = preferences[PreferencesKeys.SHOW_COVER_ART_IN_NOTIFICATION] ?: true
            val isAlarmVolumeTransitionEnabled = preferences[PreferencesKeys.IS_ALARM_VOLUME_TRANSITION_ENABLED] ?: false
            val alarmVolumeTransitionSeconds = preferences[PreferencesKeys.ALARM_VOLUME_TRANSITION_SECONDS] ?: 15
            val selectAllTextOnFocus = preferences[PreferencesKeys.SELECT_ALL_TEXT_ON_FOCUS] ?: true
            val widgetBackgroundAlpha = preferences[PreferencesKeys.WIDGET_BACKGROUND_ALPHA] ?: 1.0f
            val showStationThumbnails = preferences[PreferencesKeys.SHOW_STATION_THUMBNAILS] ?: true
            val isAutoBackupEnabled = preferences[PreferencesKeys.IS_AUTO_BACKUP_ENABLED] ?: false
            val autoBackupTreeUri = preferences[PreferencesKeys.AUTO_BACKUP_TREE_URI]
            val autoBackupFolderName = preferences[PreferencesKeys.AUTO_BACKUP_FOLDER_NAME]
            val lastAutoBackupTime = preferences[PreferencesKeys.LAST_AUTO_BACKUP_TIME] ?: 0L
            val lastAutoBackupStatusName = preferences[PreferencesKeys.LAST_AUTO_BACKUP_STATUS]
            val lastAutoBackupStatus = com.armanmaurya.internetradio.domain.model.AutoBackupStatus.entries.find { it.name == lastAutoBackupStatusName } ?: com.armanmaurya.internetradio.domain.model.AutoBackupStatus.IDLE
            val lastAutoBackupErrorMessage = preferences[PreferencesKeys.LAST_AUTO_BACKUP_ERROR]

            AppPreferences(
                themeMode = themeMode, 
                useDynamicColor = useDynamicColor, 
                appColor = appColor,
                customColorArgb = customColorArgb,
                pureBlack = pureBlack, 
                appLanguage = appLanguage,
                selectedCountryCode = selectedCountryCode,
                selectedStateCode = selectedStateCode,
                selectedLanguage = selectedLanguage,
                selectedTags = selectedTags,
                stopOnAudioBecomingNoisy = stopOnAudioBecomingNoisy,
                pauseOnVolumeZero = pauseOnVolumeZero,
                enableVolumeBoost = enableVolumeBoost,
                keepScreenOn = keepScreenOn,
                order = order,
                reverse = reverse,
                useFilterOnRecent = useFilterOnRecent,
                useFilterOnFavorites = useFilterOnFavorites,
                useFilterOnAdded = useFilterOnAdded,
                autoRouteToBrowseOnSearch = autoRouteToBrowseOnSearch,
                isGridViewBrowse = isGridViewBrowse,
                isGridViewRecent = isGridViewRecent,
                isGridViewFavorites = isGridViewFavorites,
                isGridViewAdded = isGridViewAdded,
                selectAllTextOnFocus = selectAllTextOnFocus,
                trackHistoryLimit = trackHistoryLimit,
                recentLimit = recentLimit,
                defaultTab = defaultTab,
                autoPlayOnStart = autoPlayOnStart,
                appLaunchCount = appLaunchCount,
                hasRatedApp = hasRatedApp,
                disableUpdateCheck = disableUpdateCheck,
                lastUpdateCheckTime = lastUpdateCheckTime,
                maxRetryDuration = maxRetryDuration,
                conflictStrategy = conflictStrategy,
                librarySortOption = librarySortOption,
                startOfWeek = startOfWeek,
                showCoverArtInNotification = showCoverArtInNotification,
                isAlarmVolumeTransitionEnabled = isAlarmVolumeTransitionEnabled,
                alarmVolumeTransitionSeconds = alarmVolumeTransitionSeconds,
                widgetBackgroundAlpha = widgetBackgroundAlpha,
                showStationThumbnails = showStationThumbnails,
                isAutoBackupEnabled = isAutoBackupEnabled,
                autoBackupTreeUri = autoBackupTreeUri,
                autoBackupFolderName = autoBackupFolderName,
                lastAutoBackupTime = lastAutoBackupTime,
                lastAutoBackupStatus = lastAutoBackupStatus,
                lastAutoBackupErrorMessage = lastAutoBackupErrorMessage
            )
        }

    override suspend fun setUseFilterOnRecent(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.USE_FILTER_ON_RECENT] = enabled }
    }

    override suspend fun setAutoRouteToBrowseOnSearch(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.AUTO_ROUTE_TO_BROWSE_ON_SEARCH] = enabled }
    }

    override suspend fun setSelectAllTextOnFocus(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SELECT_ALL_TEXT_ON_FOCUS] = enabled }
    }

    override suspend fun setAutoPlayOnStart(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.AUTO_PLAY_ON_START] = enabled }
    }

    override suspend fun setAppLaunchCount(count: Int) {
        context.dataStore.edit { it[PreferencesKeys.APP_LAUNCH_COUNT] = count }
    }

    override suspend fun setHasRatedApp(rated: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.HAS_RATED_APP] = rated }
    }

    override suspend fun setDisableUpdateCheck(disabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.DISABLE_UPDATE_CHECK] = disabled }
    }

    override suspend fun setStopOnAudioBecomingNoisy(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.STOP_ON_AUDIO_BECOMING_NOISY] = enabled }
    }

    override suspend fun setPauseOnVolumeZero(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.PAUSE_ON_VOLUME_ZERO] = enabled }
    }

    override suspend fun setEnableVolumeBoost(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.ENABLE_VOLUME_BOOST] = enabled }
    }

    override suspend fun setUseFilterOnFavorites(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.USE_FILTER_ON_FAVORITES] = enabled }
    }

    override suspend fun setUseFilterOnAdded(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.USE_FILTER_ON_ADDED] = enabled }
    }

    override suspend fun setThemeMode(themeMode: AppTheme) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = themeMode.name
        }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DYNAMIC_COLOR] = enabled
        }
    }

    override suspend fun setAppColor(appColor: AppColor) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_COLOR] = appColor.name
        }
    }

    override suspend fun setCustomColor(colorArgb: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CUSTOM_COLOR_ARGB] = colorArgb
        }
    }

    override suspend fun setPureBlack(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PURE_BLACK] = enabled
        }
    }

    override suspend fun setAppLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_LANGUAGE] = language
        }
    }

    override suspend fun getSavedAppLanguage(): String? {
        return context.dataStore.data.first()[PreferencesKeys.APP_LANGUAGE]
    }

    override suspend fun setSelectedCountryCode(countryCode: String?) {
        context.dataStore.edit { preferences ->
            if (countryCode == null) {
                preferences.remove(PreferencesKeys.SELECTED_COUNTRY_CODE)
            } else {
                preferences[PreferencesKeys.SELECTED_COUNTRY_CODE] = countryCode
            }
        }
    }

    override suspend fun setSelectedStateCode(stateCode: String?) {
        context.dataStore.edit { preferences ->
            if (stateCode == null) {
                preferences.remove(PreferencesKeys.SELECTED_STATE_CODE)
            } else {
                preferences[PreferencesKeys.SELECTED_STATE_CODE] = stateCode
            }
        }
    }

    override suspend fun setSelectedLanguage(language: String?) {
        context.dataStore.edit { preferences ->
            if (language == null) {
                preferences.remove(PreferencesKeys.SELECTED_LANGUAGE)
            } else {
                preferences[PreferencesKeys.SELECTED_LANGUAGE] = language
            }
        }
    }

    override suspend fun setSelectedTags(tags: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_TAGS] = tags
        }
    }

    override suspend fun setSortOrder(order: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SORT_ORDER] = order
        }
    }

    override suspend fun setSortReverse(reverse: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SORT_REVERSE] = reverse
        }
    }

    override suspend fun setGridViewBrowse(isGrid: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_GRID_VIEW_BROWSE] = isGrid
        }
    }

    override suspend fun setGridViewRecent(isGrid: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_GRID_VIEW_RECENT] = isGrid
        }
    }

    override suspend fun setGridViewFavorites(isGrid: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_GRID_VIEW_FAVORITES] = isGrid
        }
    }

    override suspend fun setGridViewAdded(isGrid: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_GRID_VIEW_ADDED] = isGrid
        }
    }

    override suspend fun setTrackHistoryLimit(limit: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TRACK_HISTORY_LIMIT] = limit
        }
    }

    override suspend fun setRecentLimit(limit: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.RECENT_LIMIT] = limit
        }
    }

    override suspend fun setDefaultTab(tabIndex: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_TAB] = tabIndex
        }
    }

    override suspend fun setLastUpdateCheckTime(timeInMillis: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_UPDATE_CHECK_TIME] = timeInMillis
        }
    }

    override suspend fun setMaxRetryDuration(durationInMillis: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MAX_RETRY_DURATION] = durationInMillis
        }
    }

    override suspend fun setConflictStrategy(strategy: ConflictStrategy) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CONFLICT_STRATEGY] = strategy.name
        }
    }

    override suspend fun setLibrarySortOption(option: LibrarySortOption) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LIBRARY_SORT_OPTION] = option.name
        }
    }

    override suspend fun setStartOfWeek(startOfWeek: StartOfWeek) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.START_OF_WEEK] = startOfWeek.name
        }
    }

    override suspend fun setShowCoverArtInNotification(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_COVER_ART_IN_NOTIFICATION] = enabled
        }
    }

    override suspend fun setAlarmVolumeTransitionSeconds(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ALARM_VOLUME_TRANSITION_SECONDS] = seconds
        }
    }

    override suspend fun setAlarmVolumeTransitionEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_ALARM_VOLUME_TRANSITION_ENABLED] = enabled
        }
    }

    override suspend fun setKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEEP_SCREEN_ON] = enabled
        }
    }

    override suspend fun setWidgetBackgroundAlpha(alpha: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WIDGET_BACKGROUND_ALPHA] = alpha.coerceIn(0f, 1f)
        }
    }

    override suspend fun setShowStationThumbnails(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_STATION_THUMBNAILS] = enabled
        }
    }

    override suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_AUTO_BACKUP_ENABLED] = enabled
        }
    }

    override suspend fun setAutoBackupFolder(uri: String?, folderName: String?) {
        context.dataStore.edit { preferences ->
            if (uri != null) {
                preferences[PreferencesKeys.AUTO_BACKUP_TREE_URI] = uri
            } else {
                preferences.remove(PreferencesKeys.AUTO_BACKUP_TREE_URI)
            }
            if (folderName != null) {
                preferences[PreferencesKeys.AUTO_BACKUP_FOLDER_NAME] = folderName
            } else {
                preferences.remove(PreferencesKeys.AUTO_BACKUP_FOLDER_NAME)
            }
        }
    }

    override suspend fun setLastAutoBackupResult(
        time: Long,
        status: com.armanmaurya.internetradio.domain.model.AutoBackupStatus,
        errorMessage: String?
    ) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_AUTO_BACKUP_TIME] = time
            preferences[PreferencesKeys.LAST_AUTO_BACKUP_STATUS] = status.name
            if (errorMessage != null) {
                preferences[PreferencesKeys.LAST_AUTO_BACKUP_ERROR] = errorMessage
            } else {
                preferences.remove(PreferencesKeys.LAST_AUTO_BACKUP_ERROR)
            }
        }
    }
}

