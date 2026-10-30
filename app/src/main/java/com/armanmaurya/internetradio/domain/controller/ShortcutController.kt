package com.armanmaurya.internetradio.domain.controller

import com.armanmaurya.internetradio.domain.model.RadioStation

/**
 * Home screen shortcuts, which only exist for library stations. A station leaving the library
 * disables its shortcut; adding it back re-enables the same shortcut.
 */
interface ShortcutController {
    /** Starts keeping pinned shortcuts in step with the library (enabled state, name and logo). */
    fun initialize()

    /** True when the station has a working shortcut on the home screen. */
    suspend fun hasShortcut(stationUuid: String): Boolean

    /** Asks the launcher to pin a shortcut. Returns false when the launcher can't pin shortcuts. */
    fun pinStationShortcut(station: RadioStation): Boolean

    /** Points the station's shortcut at its new id after a custom station is uploaded. */
    suspend fun moveStationShortcut(oldUuid: String, newUuid: String)

    companion object {
        const val ACTION_PLAY_STATION = "com.armanmaurya.internetradio.ACTION_PLAY_STATION"
        const val EXTRA_STATION_UUID = "STATION_UUID"
    }
}
