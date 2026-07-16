package com.armanmaurya.internetradio.data.repository

import com.armanmaurya.internetradio.data.local.dao.LibraryStationDao
import com.armanmaurya.internetradio.data.local.entity.LibraryStationEntity
import com.armanmaurya.internetradio.data.local.entity.toDomain
import com.armanmaurya.internetradio.data.local.entity.toLibraryEntity
import com.armanmaurya.internetradio.data.model.RadioStation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(
    private val libraryStationDao: LibraryStationDao
) {
    fun getAllStations(): Flow<List<RadioStation>> {
        return libraryStationDao.getAllStations().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getStationsByOldestAdded(): Flow<List<RadioStation>> {
        return libraryStationDao.getStationsByOldestAdded().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getStationsByName(): Flow<List<RadioStation>> {
        return libraryStationDao.getStationsByName().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getStationsByNameDescending(): Flow<List<RadioStation>> {
        return libraryStationDao.getStationsByNameDescending().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getStationsByRecentlyPlayed(): Flow<List<RadioStation>> {
        return libraryStationDao.getStationsByRecentlyPlayed().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getStationsByLeastRecentlyPlayed(): Flow<List<RadioStation>> {
        return libraryStationDao.getStationsByLeastRecentlyPlayed().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getStationsByCustomOrder(): Flow<List<RadioStation>> {
        return libraryStationDao.getStationsByCustomOrder().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun updateStations(stations: List<LibraryStationEntity>) {
        libraryStationDao.updateStations(stations)
    }

    fun isStationInLibrary(stationUuid: String): Flow<Boolean> {
        return libraryStationDao.isStationInLibrary(stationUuid).map { it != 0 }
    }

    suspend fun isStationInLibraryDirect(stationUuid: String): Boolean {
        return libraryStationDao.isStationInLibraryDirect(stationUuid)
    }

    suspend fun getStationById(stationUuid: String): RadioStation? {
        return libraryStationDao.getStationById(stationUuid)?.toDomain()
    }

    suspend fun addStationToLibrary(station: RadioStation) {
        libraryStationDao.insertStation(station.toLibraryEntity(isCustom = false))
    }

    suspend fun addCustomStation(
        name: String,
        url: String,
        favicon: String = "",
        tags: List<String> = emptyList(),
        country: String = "",
        countryCode: String = "",
        language: String = ""
    ) {
        val station = LibraryStationEntity(
            stationUuid = UUID.randomUUID().toString(),
            name = name,
            url = url,
            urlResolved = url,
            favicon = favicon,
            tags = tags,
            country = country,
            countryCode = countryCode,
            language = language,
            isCustom = true
        )
        libraryStationDao.insertStation(station)
    }
    
    suspend fun updateStation(
        stationUuid: String,
        name: String,
        url: String,
        favicon: String,
        tags: List<String>
    ) {
        val existing = libraryStationDao.getStationById(stationUuid) ?: return
        val updated = existing.copy(
            name = name,
            url = url,
            urlResolved = url,
            favicon = favicon,
            tags = tags
        )
        libraryStationDao.insertStation(updated)
    }

    suspend fun removeStationFromLibrary(stationUuid: String) {
        libraryStationDao.deleteStationById(stationUuid)
    }

    // --- Backup & Restore ---

    suspend fun getAllStationEntities(): List<LibraryStationEntity> {
        return libraryStationDao.getAllStationEntities()
    }

    suspend fun getEntityById(stationUuid: String): LibraryStationEntity? {
        return libraryStationDao.getStationById(stationUuid)
    }

    suspend fun insertEntity(entity: LibraryStationEntity) {
        libraryStationDao.insertStation(entity)
    }
}
