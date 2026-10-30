package com.armanmaurya.internetradio.data.repository

import com.armanmaurya.internetradio.domain.model.AppPreferences
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
internal class FakeSettingsRepository : SettingsRepository {
    val prefsFlow = MutableStateFlow(AppPreferences(trackHistoryLimit = 50))
    override val appPreferencesFlow: Flow<AppPreferences> = prefsFlow

    override suspend fun setUseFilterOnRecent(enabled: Boolean) {}
    override suspend fun setAutoRouteToBrowseOnSearch(enabled: Boolean) {}
    override suspend fun setSelectAllTextOnFocus(enabled: Boolean) {}
    override suspend fun setAutoPlayOnStart(enabled: Boolean) {}
    override suspend fun setAppLaunchCount(count: Int) {}
    override suspend fun setHasRatedApp(rated: Boolean) {}
    override suspend fun setDisableUpdateCheck(disabled: Boolean) {}
    override suspend fun setStopOnAudioBecomingNoisy(enabled: Boolean) {}
    override suspend fun setPauseOnVolumeZero(enabled: Boolean) {}
    override suspend fun setEnableVolumeBoost(enabled: Boolean) {}
    override suspend fun setUseFilterOnFavorites(enabled: Boolean) {}
    override suspend fun setUseFilterOnAdded(enabled: Boolean) {}
    override suspend fun setThemeMode(themeMode: com.armanmaurya.internetradio.ui.shared.theme.AppTheme) {}
    override suspend fun setDynamicColor(enabled: Boolean) {}
    override suspend fun setAppColor(appColor: com.armanmaurya.internetradio.ui.shared.theme.AppColor) {}
    override suspend fun setCustomColor(colorArgb: Int) {}
    override suspend fun setPureBlack(enabled: Boolean) {}
    override suspend fun setAppLanguage(language: String) {}
    override suspend fun getSavedAppLanguage(): String? = null
    override suspend fun setSelectedCountryCode(countryCode: String?) {}
    override suspend fun setSelectedStateCode(stateCode: String?) {}
    override suspend fun setSelectedLanguage(language: String?) {}
    override suspend fun setSelectedTags(tags: Set<String>) {}
    override suspend fun setSortOrder(order: String) {}
    override suspend fun setSortReverse(reverse: Boolean) {}
    override suspend fun setGridViewBrowse(isGrid: Boolean) {}
    override suspend fun setGridViewRecent(isGrid: Boolean) {}
    override suspend fun setGridViewFavorites(isGrid: Boolean) {}
    override suspend fun setGridViewAdded(isGrid: Boolean) {}
    override suspend fun setTrackHistoryLimit(limit: Int) {
        prefsFlow.value = prefsFlow.value.copy(trackHistoryLimit = limit)
    }
    override suspend fun setRecentLimit(limit: Int) {
        prefsFlow.value = prefsFlow.value.copy(recentLimit = limit)
    }
    override suspend fun setDefaultTab(tabIndex: Int) {}
    override suspend fun setLastUpdateCheckTime(timeInMillis: Long) {}
    override suspend fun setMaxRetryDuration(durationInMillis: Long) {}
    override suspend fun setConflictStrategy(strategy: com.armanmaurya.internetradio.domain.model.ConflictStrategy) {}
    override suspend fun setLibrarySortOption(option: com.armanmaurya.internetradio.domain.model.LibrarySortOption) {}
    override suspend fun setStartOfWeek(startOfWeek: com.armanmaurya.internetradio.domain.model.StartOfWeek) {}
    override suspend fun setShowCoverArtInNotification(enabled: Boolean) {}
    override suspend fun setAlarmVolumeTransitionSeconds(seconds: Int) {}
    override suspend fun setAlarmVolumeTransitionEnabled(enabled: Boolean) {}
    override suspend fun setKeepScreenOn(enabled: Boolean) {}
    override suspend fun setWidgetBackgroundAlpha(alpha: Float) {}
    override suspend fun setShowStationThumbnails(enabled: Boolean) {}
    override suspend fun setAutoBackupEnabled(enabled: Boolean) {}
    override suspend fun setAutoBackupFolder(uri: String?, folderName: String?) {}
    override suspend fun setLastAutoBackupResult(time: Long, status: com.armanmaurya.internetradio.domain.model.AutoBackupStatus, errorMessage: String?) {}
}
