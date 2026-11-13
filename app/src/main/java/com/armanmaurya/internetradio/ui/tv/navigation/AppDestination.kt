package com.armanmaurya.internetradio.ui.tv.navigation

sealed class AppDestination(val route: String) {
    data object Browse : AppDestination("browse")
    data object Recent : AppDestination("recent")
    data object Library : AppDestination("library")
    data object Settings : AppDestination("settings")
    data object About : AppDestination("about")
    data object AppLanguage : AppDestination("app_language")
    data object Player : AppDestination("player")
    data object Tags : AppDestination("tags")
    data object Language : AppDestination("language")
    data object Country : AppDestination("country")
    data object AddEditStation : AppDestination("add_edit_station?stationUuid={stationUuid}") {
        fun createRoute(stationUuid: String? = null) =
            if (stationUuid != null) "add_edit_station?stationUuid=$stationUuid"
            else "add_edit_station"
    }
}
