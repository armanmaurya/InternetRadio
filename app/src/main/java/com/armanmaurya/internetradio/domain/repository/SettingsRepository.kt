package com.armanmaurya.internetradio.domain.repository

import com.armanmaurya.internetradio.domain.model.AppPreferences
import com.armanmaurya.internetradio.domain.model.ConflictStrategy
import com.armanmaurya.internetradio.domain.model.LibrarySortOption
import com.armanmaurya.internetradio.domain.model.StartOfWeek
import com.armanmaurya.internetradio.ui.shared.theme.AppColor
import com.armanmaurya.internetradio.ui.shared.theme.AppTheme
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val appPreferencesFlow: Flow<AppPreferences>
    suspend fun setUseFilterOnRecent(enabled: Boolean)
    suspend fun setAutoRouteToBrowseOnSearch(enabled: Boolean)
    suspend fun setSelectAllTextOnFocus(enabled: Boolean)
    suspend fun setAutoPlayOnStart(enabled: Boolean)
    suspend fun setAppLaunchCount(count: Int)
    suspend fun setHasRatedApp(rated: Boolean)
    suspend fun setDisableUpdateCheck(disabled: Boolean)
    suspend fun setStopOnAudioBecomingNoisy(enabled: Boolean)
    suspend fun setPauseOnVolumeZero(enabled: Boolean)
    suspend fun setEnableVolumeBoost(enabled: Boolean)
    suspend fun setUseFilterOnFavorites(enabled: Boolean)
    suspend fun setUseFilterOnAdded(enabled: Boolean)
    suspend fun setThemeMode(themeMode: AppTheme)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setAppColor(appColor: AppColor)
    suspend fun setCustomColor(colorArgb: Int)
    suspend fun setPureBlack(enabled: Boolean)
    suspend fun setAppLanguage(language: String)
    suspend fun getSavedAppLanguage(): String?
    suspend fun setSelectedCountryCode(countryCode: String?)
    suspend fun setSelectedStateCode(stateCode: String?)
    suspend fun setSelectedLanguage(language: String?)
    suspend fun setSelectedTags(tags: Set<String>)
    suspend fun setSortOrder(order: String)
    suspend fun setSortReverse(reverse: Boolean)
    suspend fun setGridViewBrowse(isGrid: Boolean)
    suspend fun setGridViewRecent(isGrid: Boolean)
    suspend fun setGridViewFavorites(isGrid: Boolean)
    suspend fun setGridViewAdded(isGrid: Boolean)
    suspend fun setTrackHistoryLimit(limit: Int)
    suspend fun setRecentLimit(limit: Int)
    suspend fun setDefaultTab(tabIndex: Int)
    suspend fun setLastUpdateCheckTime(timeInMillis: Long)
    suspend fun setMaxRetryDuration(durationInMillis: Long)
    suspend fun setConflictStrategy(strategy: ConflictStrategy)
    suspend fun setLibrarySortOption(option: LibrarySortOption)
    suspend fun setStartOfWeek(startOfWeek: StartOfWeek)
    suspend fun setShowCoverArtInNotification(enabled: Boolean)
    suspend fun setAlarmVolumeTransitionSeconds(seconds: Int)
    suspend fun setAlarmVolumeTransitionEnabled(enabled: Boolean)
    suspend fun setKeepScreenOn(enabled: Boolean)
    suspend fun setWidgetBackgroundAlpha(alpha: Float)
    suspend fun setShowStationThumbnails(enabled: Boolean)
    suspend fun setAutoBackupEnabled(enabled: Boolean)
    suspend fun setAutoBackupFolder(uri: String?, folderName: String?)
    suspend fun setLastAutoBackupResult(time: Long, status: com.armanmaurya.internetradio.domain.model.AutoBackupStatus, errorMessage: String?)
}

