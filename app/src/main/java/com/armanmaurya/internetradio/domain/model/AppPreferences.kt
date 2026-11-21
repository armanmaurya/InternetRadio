package com.armanmaurya.internetradio.domain.model

import com.armanmaurya.internetradio.ui.shared.theme.AppColor
import com.armanmaurya.internetradio.ui.shared.theme.AppTheme

data class AppPreferences(
    val themeMode: AppTheme = AppTheme.SYSTEM,
    val useDynamicColor: Boolean = true,
    val appColor: AppColor = AppColor.PURPLE,
    val customColorArgb: Int = 0xFF00BCD4.toInt(),
    val appLanguage: String = "System",
    val selectedCountryCode: String? = null,
    val selectedStateCode: String? = null,
    val selectedLanguage: String? = null,
    val selectedTags: Set<String> = emptySet(),
    val stopOnAudioBecomingNoisy: Boolean = true,
    val pauseOnVolumeZero: Boolean = false,
    val keepScreenOn: Boolean = false,
    val order: String = "votes",
    val reverse: Boolean = true,
    val useFilterOnRecent: Boolean = false,
    val useFilterOnFavorites: Boolean = false,
    val useFilterOnAdded: Boolean = false,
    val autoRouteToBrowseOnSearch: Boolean = true,
    val isSearchBarHidden: Boolean = false,
    val isGridViewBrowse: Boolean = true,
    val isGridViewRecent: Boolean = true,
    val autoPlayOnStart: Boolean = false,
    /** TV only: minutes of no input while playing before the in-app screensaver shows; 0 = off. */
    val tvScreensaverMinutes: Int = 5,
    /** TV only: whether the lyrics panel is open in the player. */
    val tvLyricsOpen: Boolean = false,
    val appLaunchCount: Int = 0,
    val hasRatedApp: Boolean = false,
    val isGridViewFavorites: Boolean = true,
    val isGridViewAdded: Boolean = true,
    val trackHistoryLimit: Int = 50,
    val recentLimit: Int = 50,
    val defaultTab: Int = 0,
    val disableUpdateCheck: Boolean = false,
    val lastUpdateCheckTime: Long = 0L,
    val maxRetryDuration: Long = 300_000L,
    val librarySortOption: LibrarySortOption = LibrarySortOption.RECENTLY_ADDED,
    val startOfWeek: StartOfWeek = StartOfWeek.SUNDAY,
    val recordingTimeFormat: RecordingTimeFormat = RecordingTimeFormat.SYSTEM,
    val showCoverArtInNotification: Boolean = true,
    val enableVolumeBoost: Boolean = false,
    val isAlarmVolumeTransitionEnabled: Boolean = false,
    val alarmVolumeTransitionSeconds: Int = 15,
    val widgetBackgroundAlpha: Float = 1.0f,
    val showStationThumbnails: Boolean = true,
    val lyricsProviderOrder: List<String> = listOf("betterlyrics", "unison", "lyricsplus", "kugou", "lrclib"),
    val enabledLyricsProviders: Set<String> = setOf("betterlyrics", "unison", "lyricsplus", "kugou", "lrclib")
)
