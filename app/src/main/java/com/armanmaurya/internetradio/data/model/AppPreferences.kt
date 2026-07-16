package com.armanmaurya.internetradio.data.model

import com.armanmaurya.internetradio.ui.shared.theme.AppTheme

data class AppPreferences(
    val themeMode: AppTheme = AppTheme.SYSTEM,
    val useDynamicColor: Boolean = true,
    val pureBlack: Boolean = false,
    val appLanguage: String = "System",
    val selectedCountryCode: String? = null,
    val selectedLanguage: String? = null,
    val selectedTags: Set<String> = emptySet(),
    val stopOnAudioBecomingNoisy: Boolean = true,
    val order: String = "votes",
    val reverse: Boolean = true,
    val useFilterOnRecent: Boolean = false,
    val useFilterOnFavorites: Boolean = false,
    val useFilterOnAdded: Boolean = false,
    val resumeStation: String? = null,
    val isGridViewBrowse: Boolean = true,
    val isGridViewRecent: Boolean = true,
    val autoPlayOnStart: Boolean = false,
    val isGridViewFavorites: Boolean = true,
    val isGridViewAdded: Boolean = true,
    val trackHistoryLimit: Int = 50,
    val defaultTab: Int = 0,
    val lastUpdateCheckTime: Long = 0L,
    val maxRetryDuration: Long = 300_000L,
    val conflictStrategy: ConflictStrategy = ConflictStrategy.SKIP,
    val librarySortOption: LibrarySortOption = LibrarySortOption.RECENTLY_ADDED
)
