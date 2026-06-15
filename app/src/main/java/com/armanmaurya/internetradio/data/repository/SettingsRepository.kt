package com.armanmaurya.internetradio.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.armanmaurya.internetradio.data.model.AppPreferences
import com.armanmaurya.internetradio.ui.theme.AppTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

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
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val SORT_REVERSE = booleanPreferencesKey("sort_reverse")
        val LAST_COUNTRY_FETCH_TIME = longPreferencesKey("last_country_fetch_time")
        val LAST_LANGUAGE_FETCH_TIME = longPreferencesKey("last_language_fetch_time")
        val LAST_TAG_FETCH_TIME = longPreferencesKey("last_tag_fetch_time")
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
            val order = preferences[PreferencesKeys.SORT_ORDER] ?: "votes"
            val reverse = preferences[PreferencesKeys.SORT_REVERSE] ?: true
            
            AppPreferences(
                themeMode = themeMode, 
                useDynamicColor = useDynamicColor, 
                pureBlack = pureBlack, 
                appLanguage = appLanguage,
                selectedCountryCode = selectedCountryCode,
                selectedLanguage = selectedLanguage,
                order = order,
                reverse = reverse
            )
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

    suspend fun getLastCountryFetchTime(): Long =
        context.dataStore.data.map { it[PreferencesKeys.LAST_COUNTRY_FETCH_TIME] ?: 0L }.first()

    suspend fun setLastCountryFetchTime(time: Long) {
        context.dataStore.edit { it[PreferencesKeys.LAST_COUNTRY_FETCH_TIME] = time }
    }

    suspend fun getLastLanguageFetchTime(): Long =
        context.dataStore.data.map { it[PreferencesKeys.LAST_LANGUAGE_FETCH_TIME] ?: 0L }.first()

    suspend fun setLastLanguageFetchTime(time: Long) {
        context.dataStore.edit { it[PreferencesKeys.LAST_LANGUAGE_FETCH_TIME] = time }
    }

    suspend fun getLastTagFetchTime(): Long =
        context.dataStore.data.map { it[PreferencesKeys.LAST_TAG_FETCH_TIME] ?: 0L }.first()

    suspend fun setLastTagFetchTime(time: Long) {
        context.dataStore.edit { it[PreferencesKeys.LAST_TAG_FETCH_TIME] = time }
    }
}
