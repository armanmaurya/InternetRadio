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
import com.armanmaurya.internetradio.data.model.AppPreferences
import com.armanmaurya.internetradio.data.model.ConflictStrategy
import com.armanmaurya.internetradio.ui.shared.theme.AppTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler(
        produceNewData = { emptyPreferences() }
    )
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val PURE_BLACK = booleanPreferencesKey("pure_black")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val SELECTED_COUNTRY_CODE = stringPreferencesKey("selected_country_code")
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
        val DEFAULT_TAB = androidx.datastore.preferences.core.intPreferencesKey("default_tab")
        val AUTO_PLAY_ON_START = booleanPreferencesKey("auto_play_on_start")
        val LAST_UPDATE_CHECK_TIME = androidx.datastore.preferences.core.longPreferencesKey("last_update_check_time")
        val MAX_RETRY_DURATION = androidx.datastore.preferences.core.longPreferencesKey("max_retry_duration")
        val CONFLICT_STRATEGY = stringPreferencesKey("conflict_strategy")
        val STOP_ON_AUDIO_BECOMING_NOISY = booleanPreferencesKey("stop_on_audio_becoming_noisy")
        val LIBRARY_SORT_OPTION = stringPreferencesKey("library_sort_option")
    }

    val appPreferencesFlow: Flow<AppPreferences> = context.dataStore.data
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
            val pureBlack = preferences[PreferencesKeys.PURE_BLACK] ?: false
            val appLanguage = preferences[PreferencesKeys.APP_LANGUAGE] ?: "System"
            val selectedCountryCode = preferences[PreferencesKeys.SELECTED_COUNTRY_CODE]
            val selectedLanguage = preferences[PreferencesKeys.SELECTED_LANGUAGE]
            val selectedTags = preferences[PreferencesKeys.SELECTED_TAGS] ?: emptySet()
            val stopOnAudioBecomingNoisy = preferences[PreferencesKeys.STOP_ON_AUDIO_BECOMING_NOISY] ?: true
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
            val defaultTab = preferences[PreferencesKeys.DEFAULT_TAB] ?: 0
            val autoPlayOnStart = preferences[PreferencesKeys.AUTO_PLAY_ON_START] ?: false
            val lastUpdateCheckTime = preferences[PreferencesKeys.LAST_UPDATE_CHECK_TIME] ?: 0L
            val maxRetryDuration = preferences[PreferencesKeys.MAX_RETRY_DURATION] ?: 300_000L
            val conflictStrategyName = preferences[PreferencesKeys.CONFLICT_STRATEGY]
            val conflictStrategy = ConflictStrategy.entries.find { it.name == conflictStrategyName } ?: ConflictStrategy.SKIP
            val librarySortOptionName = preferences[PreferencesKeys.LIBRARY_SORT_OPTION]
            val librarySortOption = com.armanmaurya.internetradio.data.model.LibrarySortOption.entries.find { it.name == librarySortOptionName } ?: com.armanmaurya.internetradio.data.model.LibrarySortOption.RECENTLY_ADDED

            AppPreferences(
                themeMode = themeMode, 
                useDynamicColor = useDynamicColor, 
                pureBlack = pureBlack, 
                appLanguage = appLanguage,
                selectedCountryCode = selectedCountryCode,
                selectedLanguage = selectedLanguage,
                selectedTags = selectedTags,
                stopOnAudioBecomingNoisy = stopOnAudioBecomingNoisy,
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
                trackHistoryLimit = trackHistoryLimit,
                defaultTab = defaultTab,
                autoPlayOnStart = autoPlayOnStart,
                lastUpdateCheckTime = lastUpdateCheckTime,
                maxRetryDuration = maxRetryDuration,
                conflictStrategy = conflictStrategy,
                librarySortOption = librarySortOption
            )
        }

    suspend fun setUseFilterOnRecent(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.USE_FILTER_ON_RECENT] = enabled }
    }

    suspend fun setAutoRouteToBrowseOnSearch(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.AUTO_ROUTE_TO_BROWSE_ON_SEARCH] = enabled }
    }

    suspend fun setAutoPlayOnStart(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.AUTO_PLAY_ON_START] = enabled }
    }

    suspend fun setStopOnAudioBecomingNoisy(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.STOP_ON_AUDIO_BECOMING_NOISY] = enabled }
    }

    suspend fun setUseFilterOnFavorites(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.USE_FILTER_ON_FAVORITES] = enabled }
    }

    suspend fun setUseFilterOnAdded(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.USE_FILTER_ON_ADDED] = enabled }
    }

    suspend fun setThemeMode(themeMode: AppTheme) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = themeMode.name
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun setPureBlack(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PURE_BLACK] = enabled
        }
    }

    suspend fun setAppLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_LANGUAGE] = language
        }
    }

    suspend fun getSavedAppLanguage(): String? {
        return context.dataStore.data.first()[PreferencesKeys.APP_LANGUAGE]
    }

    suspend fun setSelectedCountryCode(countryCode: String?) {
        context.dataStore.edit { preferences ->
            if (countryCode == null) {
                preferences.remove(PreferencesKeys.SELECTED_COUNTRY_CODE)
            } else {
                preferences[PreferencesKeys.SELECTED_COUNTRY_CODE] = countryCode
            }
        }
    }

    suspend fun setSelectedLanguage(language: String?) {
        context.dataStore.edit { preferences ->
            if (language == null) {
                preferences.remove(PreferencesKeys.SELECTED_LANGUAGE)
            } else {
                preferences[PreferencesKeys.SELECTED_LANGUAGE] = language
            }
        }
    }

    suspend fun setSelectedTags(tags: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_TAGS] = tags
        }
    }

    suspend fun setSortOrder(order: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SORT_ORDER] = order
        }
    }

    suspend fun setSortReverse(reverse: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SORT_REVERSE] = reverse
        }
    }

    suspend fun setGridViewBrowse(isGrid: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_GRID_VIEW_BROWSE] = isGrid
        }
    }

    suspend fun setGridViewRecent(isGrid: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_GRID_VIEW_RECENT] = isGrid
        }
    }

    suspend fun setGridViewFavorites(isGrid: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_GRID_VIEW_FAVORITES] = isGrid
        }
    }

    suspend fun setGridViewAdded(isGrid: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_GRID_VIEW_ADDED] = isGrid
        }
    }

    suspend fun setTrackHistoryLimit(limit: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TRACK_HISTORY_LIMIT] = limit
        }
    }

    suspend fun setDefaultTab(tabIndex: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_TAB] = tabIndex
        }
    }

    suspend fun setLastUpdateCheckTime(timeInMillis: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_UPDATE_CHECK_TIME] = timeInMillis
        }
    }

    suspend fun setMaxRetryDuration(durationInMillis: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MAX_RETRY_DURATION] = durationInMillis
        }
    }

    suspend fun setConflictStrategy(strategy: ConflictStrategy) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CONFLICT_STRATEGY] = strategy.name
        }
    }

    suspend fun setLibrarySortOption(option: com.armanmaurya.internetradio.data.model.LibrarySortOption) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LIBRARY_SORT_OPTION] = option.name
        }
    }
}
