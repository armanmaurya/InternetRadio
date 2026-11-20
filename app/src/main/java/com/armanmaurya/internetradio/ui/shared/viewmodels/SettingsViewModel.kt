package com.armanmaurya.internetradio.ui.shared.viewmodels

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.domain.model.AppPreferences
import com.armanmaurya.internetradio.domain.model.RecordingTimeFormat
import com.armanmaurya.internetradio.domain.model.StartOfWeek
import com.armanmaurya.internetradio.domain.repository.RecentRepository
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import com.armanmaurya.internetradio.ui.shared.theme.AppColor
import com.armanmaurya.internetradio.ui.shared.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import com.armanmaurya.internetradio.domain.controller.WidgetController
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject


@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val recentRepository: RecentRepository,
    private val widgetController: WidgetController,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val uiState: StateFlow<AppPreferences> = settingsRepository.appPreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppPreferences()
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
}
