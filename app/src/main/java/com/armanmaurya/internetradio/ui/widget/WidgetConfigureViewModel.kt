package com.armanmaurya.internetradio.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.core.utils.extractPaletteFromBitmap
import com.armanmaurya.internetradio.core.utils.resolveArtwork
import com.armanmaurya.internetradio.domain.controller.WidgetController
import com.armanmaurya.internetradio.domain.model.AppPreferences
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.repository.RecentRepository
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import com.armanmaurya.internetradio.service.PlaybackService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WidgetConfigureUiState(
    val isLoading: Boolean = true,
    val initialAlpha: Float = 1.0f,
    val savedBgColor: Color? = null,
    val savedTitleColor: Color? = null,
    val savedArtistColor: Color? = null,
    val savedTitle: String? = null,
    val savedArtist: String? = null,
    val savedArtworkUrl: String? = null,
    val savedStationThumbUrl: String? = null,
    val savedIsCoverArtFetched: Boolean = false,
    val savedIsPlaying: Boolean = false,
    val lastStation: RadioStation? = null,
)

@HiltViewModel
class WidgetConfigureViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val recentRepository: RecentRepository,
    private val widgetController: WidgetController,
) : ViewModel() {

    val appPreferences: StateFlow<AppPreferences> = settingsRepository.appPreferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences())

    private val _uiState = MutableStateFlow(WidgetConfigureUiState())
    val uiState: StateFlow<WidgetConfigureUiState> = _uiState.asStateFlow()

    /**
     * Loads existing widget preferences and recent station for a given [appWidgetId].
     * Must be called once from the Activity with the widget ID from the Intent.
     */
    fun loadWidgetState(appWidgetId: Int, isDark: Boolean) {
        viewModelScope.launch {
            val lastStation = try {
                recentRepository.getAllRecent().first().firstOrNull()
            } catch (_: Exception) { null }

            val manager = GlanceAppWidgetManager(context)
            var loadedPrefs: androidx.datastore.preferences.core.Preferences? = null

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                try {
                    val glanceId = manager.getGlanceIdBy(appWidgetId)
                    loadedPrefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
                } catch (_: Exception) {}
            } else {
                try {
                    val firstGlanceId = manager.getGlanceIds(NowPlayingWidget::class.java).firstOrNull()
                    if (firstGlanceId != null) {
                        loadedPrefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, firstGlanceId)
                    }
                } catch (_: Exception) {}
            }

            val isServiceRunning = PlaybackService.isRunning
            val latestPayload = if (isServiceRunning) widgetController.latestPayload else null

            val loadedAlpha = loadedPrefs?.get(WidgetPreferenceKeys.BG_ALPHA)
                ?: latestPayload?.bgAlpha
                ?: try { settingsRepository.appPreferencesFlow.first().widgetBackgroundAlpha } catch (_: Exception) { 1.0f }

            var bgColorInt = if (isDark) {
                latestPayload?.bgColor ?: loadedPrefs?.get(WidgetPreferenceKeys.BG_COLOR)
            } else {
                latestPayload?.dayBgColor ?: loadedPrefs?.get(WidgetPreferenceKeys.DAY_BG_COLOR)
                    ?: latestPayload?.bgColor ?: loadedPrefs?.get(WidgetPreferenceKeys.BG_COLOR)
            }
            var titleColorInt = if (isDark) {
                latestPayload?.titleColor ?: loadedPrefs?.get(WidgetPreferenceKeys.TITLE_COLOR)
            } else {
                latestPayload?.dayTitleColor ?: loadedPrefs?.get(WidgetPreferenceKeys.DAY_TITLE_COLOR)
                    ?: latestPayload?.titleColor ?: loadedPrefs?.get(WidgetPreferenceKeys.TITLE_COLOR)
            }
            var artistColorInt = if (isDark) {
                latestPayload?.artistColor ?: loadedPrefs?.get(WidgetPreferenceKeys.ARTIST_COLOR)
            } else {
                latestPayload?.dayArtistColor ?: loadedPrefs?.get(WidgetPreferenceKeys.DAY_ARTIST_COLOR)
                    ?: latestPayload?.artistColor ?: loadedPrefs?.get(WidgetPreferenceKeys.ARTIST_COLOR)
            }

            val candidateArtUrl = latestPayload?.artworkUrl?.takeIf { it.isNotBlank() }
                ?: loadedPrefs?.get(WidgetPreferenceKeys.ARTWORK_URL)?.takeIf { it.isNotBlank() }
                ?: lastStation?.favicon
            val candidateThumbUrl = latestPayload?.stationThumbnailUrl?.takeIf { it.isNotBlank() }
                ?: loadedPrefs?.get(WidgetPreferenceKeys.STATION_THUMBNAIL_URL)?.takeIf { it.isNotBlank() }

            if (bgColorInt == null && !candidateArtUrl.isNullOrBlank()) {
                val bmp = resolveArtwork(context, candidateArtUrl)
                var palette = extractPaletteFromBitmap(bmp)
                if (palette == null && !candidateThumbUrl.isNullOrBlank() && candidateThumbUrl != candidateArtUrl) {
                    palette = extractPaletteFromBitmap(resolveArtwork(context, candidateThumbUrl, maxDimension = 96))
                }
                if (palette != null) {
                    bgColorInt = if (isDark) palette.backgroundColor else palette.dayBackgroundColor
                    titleColorInt = if (isDark) palette.titleTextColor else palette.dayTitleTextColor
                    artistColorInt = if (isDark) palette.artistTextColor else palette.dayArtistTextColor
                }
            }

            _uiState.update {
                WidgetConfigureUiState(
                    isLoading = false,
                    initialAlpha = loadedAlpha,
                    savedBgColor = bgColorInt?.let { Color(it) },
                    savedTitleColor = titleColorInt?.let { Color(it) },
                    savedArtistColor = artistColorInt?.let { Color(it) },
                    savedTitle = latestPayload?.title ?: loadedPrefs?.get(WidgetPreferenceKeys.TITLE),
                    savedArtist = latestPayload?.artist ?: loadedPrefs?.get(WidgetPreferenceKeys.ARTIST),
                    savedArtworkUrl = latestPayload?.artworkUrl ?: loadedPrefs?.get(WidgetPreferenceKeys.ARTWORK_URL),
                    savedStationThumbUrl = latestPayload?.stationThumbnailUrl ?: loadedPrefs?.get(WidgetPreferenceKeys.STATION_THUMBNAIL_URL),
                    savedIsCoverArtFetched = latestPayload?.isCoverArtFetched ?: loadedPrefs?.get(WidgetPreferenceKeys.IS_COVER_ART_FETCHED) ?: false,
                    savedIsPlaying = latestPayload?.isPlaying ?: loadedPrefs?.get(WidgetPreferenceKeys.IS_PLAYING) ?: false,
                    lastStation = lastStation,
                )
            }
        }
    }

    /**
     * Persists the chosen alpha value for the specific widget instance and globally.
     */
    suspend fun saveConfiguration(appWidgetId: Int, alpha: Float) {
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            try {
                val manager = GlanceAppWidgetManager(context)
                val glanceId = manager.getGlanceIdBy(appWidgetId)
                if (!PlaybackService.isRunning) {
                    val lastStation = recentRepository.getAllRecent().first().firstOrNull()
                    widgetController.cleanStaleWidgetState(
                        stationName = lastStation?.name,
                        favicon = lastStation?.favicon,
                    )
                }
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[WidgetPreferenceKeys.BG_ALPHA] = alpha
                    }
                }
                NowPlayingWidget().update(context, glanceId)
            } catch (_: Exception) {}
        }

        settingsRepository.setWidgetBackgroundAlpha(alpha)
        widgetController.updateWidgetAlpha(alpha)
    }
}
