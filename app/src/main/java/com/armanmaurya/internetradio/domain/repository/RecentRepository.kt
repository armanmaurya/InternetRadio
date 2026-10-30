package com.armanmaurya.internetradio.domain.repository

import com.armanmaurya.internetradio.domain.model.RadioStation
import kotlinx.coroutines.flow.Flow

interface RecentRepository {
    fun getAllRecent(): Flow<List<RadioStation>>
    suspend fun getStationById(stationUuid: String): RadioStation?
    /**
     * Saves [station] exactly as it was played: the Radio Browser version from Browse, the library
     * version from Library, the saved copy from Recent. Never merge library data in here.
     */
    suspend fun addRecentStation(station: RadioStation)

    /** Drops the oldest recents beyond the user's limit; called when the limit is lowered. */
    suspend fun trimToLimit()
    suspend fun removeRecent(stationUuid: String)
    suspend fun clearAllRecent()
}
