package com.armanmaurya.internetradio.domain.repository

import com.armanmaurya.internetradio.data.local.entity.StationEntity
import com.armanmaurya.internetradio.domain.model.RadioStation
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun getAllStations(): Flow<List<RadioStation>>
    fun getStationsByOldestAdded(): Flow<List<RadioStation>>
    fun getStationsByName(): Flow<List<RadioStation>>
    fun getStationsByNameDescending(): Flow<List<RadioStation>>
    fun getStationsByRecentlyPlayed(): Flow<List<RadioStation>>
    fun getStationsByLeastRecentlyPlayed(): Flow<List<RadioStation>>
    fun getStationsByCustomOrder(): Flow<List<RadioStation>>
    suspend fun updateStations(stations: List<StationEntity>)
    fun isStationInLibrary(stationUuid: String): Flow<Boolean>
    suspend fun isStationInLibraryDirect(stationUuid: String): Boolean
    suspend fun getStationById(stationUuid: String): RadioStation?
    suspend fun addStationToLibrary(station: RadioStation)
    suspend fun addCustomStation(name: String, url: String, favicon: String = "", tags: List<String> = emptyList(), countryCode: String = "", languageCodes: List<String> = emptyList(), homepage: String = "", iso31662: String? = null, codec: String = "unknown", bitrate: Int = 0)
    suspend fun updateStation(stationUuid: String, name: String, url: String, favicon: String, tags: List<String>, countryCode: String, languageCodes: List<String>, homepage: String, iso31662: String?, codec: String, bitrate: Int)
    suspend fun removeStationFromLibrary(stationUuid: String)
    suspend fun uploadAndSaveNewStation(name: String, url: String, homepage: String, favicon: String, countryCode: String, iso31662: String?, languageCodes: List<String>, tags: List<String>, codec: String, bitrate: Int): Result<String>
    suspend fun uploadExistingCustomStation(stationUuid: String, name: String, url: String, homepage: String, favicon: String, countryCode: String, iso31662: String?, languageCodes: List<String>, tags: List<String>, codec: String, bitrate: Int): Result<String>
    suspend fun getAllStationEntities(): List<StationEntity>
    suspend fun getEntityById(stationUuid: String): StationEntity?
    suspend fun insertEntity(entity: StationEntity)
}
