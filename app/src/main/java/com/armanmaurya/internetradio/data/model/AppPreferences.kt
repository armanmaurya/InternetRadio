package com.armanmaurya.internetradio.data.model

import com.armanmaurya.internetradio.ui.theme.AppTheme

data class AppPreferences(
    val themeMode: AppTheme = AppTheme.SYSTEM,
    val useDynamicColor: Boolean = true,
    val pureBlack: Boolean = false,
    val appLanguage: String = "System",
    val selectedCountryCode: String? = null,
    val selectedLanguage: String? = null,
    val selectedTags: Set<String> = emptySet(),
    val order: String = "votes",
    val reverse: Boolean = true,
    val useFilterOnRecent: Boolean = false,
    val useFilterOnFavorites: Boolean = false,
    val useFilterOnAdded: Boolean = false,
    val resumeStation: String? = null
)
