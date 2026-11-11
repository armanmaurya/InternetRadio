package com.armanmaurya.internetradio.ui.mobile.navigation

sealed class AppDestination(val route: String) {
    data object Discover : AppDestination("discover")
    data object Settings : AppDestination("settings")
    data object AppLanguage : AppDestination("app_language")
    data object CountrySelect : AppDestination("country_select?selectedCode={selectedCode}") {
        fun createRoute(selectedCode: String?) = "country_select?selectedCode=$selectedCode"
    }
    data object LanguageSelect : AppDestination("language_select?selectedLanguage={selectedLanguage}") {
        fun createRoute(selectedLanguage: String?) = "language_select?selectedLanguage=$selectedLanguage"
    }
    data object TagSelect : AppDestination("tag_select?selectedTags={selectedTags}") {
        fun createRoute(selectedTags: Set<String>) = "tag_select?selectedTags=${selectedTags.joinToString(",")}"
    }
    data object EditSchedule : AppDestination("edit_schedule?scheduleId={scheduleId}") {
        fun createRoute(scheduleId: Int? = null) = if (scheduleId != null) "edit_schedule?scheduleId=$scheduleId" else "edit_schedule"
    }
    data object EditStation : AppDestination("edit_station?stationUuid={stationUuid}") {
        fun createRoute(stationUuid: String?) = if (stationUuid != null) "edit_station?stationUuid=$stationUuid" else "edit_station"
    }
}