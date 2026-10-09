package com.armanmaurya.internetradio.data.repository

import com.armanmaurya.internetradio.data.local.dao.ScheduleDao
import com.armanmaurya.internetradio.data.local.dao.StationDao
import com.armanmaurya.internetradio.data.local.entity.StationEntity
import com.armanmaurya.internetradio.data.local.entity.toDomain
import com.armanmaurya.internetradio.data.local.entity.toEntity
import com.armanmaurya.internetradio.data.remote.RadioBrowserApi
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val stationDao: StationDao,
    private val scheduleDao: ScheduleDao,
    private val radioBrowserApi: RadioBrowserApi
) : LibraryRepository {
    override fun getAllStations(): Flow<List<RadioStation>> {
        return stationDao.getAllFavorites().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getStationsByOldestAdded(): Flow<List<RadioStation>> {
        return stationDao.getFavoritesByOldestAdded().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getStationsByName(): Flow<List<RadioStation>> {
        return stationDao.getFavoritesByName().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getStationsByNameDescending(): Flow<List<RadioStation>> {
        return stationDao.getFavoritesByNameDescending().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getStationsByRecentlyPlayed(): Flow<List<RadioStation>> {
        return stationDao.getFavoritesByRecentlyPlayed().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getStationsByLeastRecentlyPlayed(): Flow<List<RadioStation>> {
        return stationDao.getFavoritesByLeastRecentlyPlayed().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getStationsByCustomOrder(): Flow<List<RadioStation>> {
        return stationDao.getFavoritesByCustomOrder().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun updateStations(stations: List<StationEntity>) {
        stationDao.updateStations(stations)
    }

    override fun isStationInLibrary(stationUuid: String): Flow<Boolean> {
        return stationDao.isStationInLibrary(stationUuid).map { it != 0 }
    }

    override suspend fun isStationInLibraryDirect(stationUuid: String): Boolean {
        return stationDao.isStationInLibraryDirect(stationUuid)
    }

    override suspend fun getStationById(stationUuid: String): RadioStation? {
        return stationDao.getStationById(stationUuid)?.toDomain()
    }

    override suspend fun addStationToLibrary(station: RadioStation) {
        val existing = stationDao.getStationById(station.stationUuid)
        val now = System.currentTimeMillis()
        if (existing != null) {
            stationDao.updateFavoriteStatus(station.stationUuid, true, now)
        } else {
            stationDao.insertStation(station.toEntity(isFavorite = true, addedAt = now))
        }
    }

    override suspend fun addCustomStation(
        name: String,
        url: String,
        favicon: String,
        tags: List<String>,
        countryCode: String,
        languageCodes: List<String>,
        homepage: String,
        iso31662: String?,
        codec: String,
        bitrate: Int
    ) {
        val station = StationEntity(
            stationUuid = UUID.randomUUID().toString(),
            name = name,
            url = url,
            urlResolved = url,
            favicon = favicon,
            tags = tags,
            countryCode = countryCode,
            languageCodes = languageCodes,
            homepage = homepage,
            iso3166_2 = iso31662,
            codec = codec,
            bitrate = bitrate,
            isCustom = true,
            isFavorite = true,
            addedAt = System.currentTimeMillis()
        )
        stationDao.insertStation(station)
    }
    
    override suspend fun updateStation(
        stationUuid: String,
        name: String,
        url: String,
        favicon: String,
        tags: List<String>,
        countryCode: String,
        languageCodes: List<String>,
        homepage: String,
        iso31662: String?,
        codec: String,
        bitrate: Int
    ) {
        val existing = stationDao.getStationById(stationUuid) ?: return
        val updated = existing.copy(
            name = name,
            url = url,
            urlResolved = url,
            favicon = favicon,
            tags = tags,
            countryCode = countryCode,
            languageCodes = languageCodes,
            homepage = homepage,
            iso3166_2 = iso31662,
            codec = codec,
            bitrate = bitrate
        )
        stationDao.insertOrUpdate(updated)
    }

    override suspend fun removeStationFromLibrary(stationUuid: String) {
        val existing = stationDao.getStationById(stationUuid) ?: return
        if (existing.lastPlayedAt != null) {
            stationDao.updateFavoriteStatus(stationUuid, false, null)
        } else {
            stationDao.deleteStationById(stationUuid)
        }
    }

    override suspend fun uploadAndSaveNewStation(
        name: String,
        url: String,
        homepage: String,
        favicon: String,
        countryCode: String,
        iso31662: String?,
        languageCodes: List<String>,
        tags: List<String>,
        codec: String,
        bitrate: Int
    ): Result<String> {
        return try {
            val response = radioBrowserApi.addStation(
                name = name,
                url = url,
                homepage = homepage,
                favicon = favicon,
                countryCode = countryCode,
                iso31662 = iso31662,
                languageCodes = languageCodes.joinToString(","),
                tags = tags.joinToString(","),
            )
            if (response.ok) {
                val newUuid = response.uuid?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
                val newStation = StationEntity(
                    stationUuid = newUuid,
                    name = name,
                    url = url,
                    urlResolved = url,
                    favicon = favicon,
                    tags = tags,
                    countryCode = countryCode,
                    languageCodes = languageCodes,
                    homepage = homepage,
                    iso3166_2 = iso31662,
                    codec = codec,
                    bitrate = bitrate,
                    isCustom = false,
                    isFavorite = true,
                    addedAt = System.currentTimeMillis()
                )
                stationDao.insertStation(newStation)
                Result.success(newUuid)
            } else {
                Result.failure(Exception(response.message ?: "Unknown API error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadExistingCustomStation(
        stationUuid: String,
        name: String,
        url: String,
        homepage: String,
        favicon: String,
        countryCode: String,
        iso31662: String?,
        languageCodes: List<String>,
        tags: List<String>,
        codec: String,
        bitrate: Int
    ): Result<String> {
        return try {
            val response = radioBrowserApi.addStation(
                name = name,
                url = url,
                homepage = homepage,
                favicon = favicon,
                countryCode = countryCode,
                iso31662 = iso31662,
                languageCodes = languageCodes.joinToString(","),
                tags = tags.joinToString(","),
            )
            if (response.ok) {
                val newUuid = response.uuid?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
                val oldUuid = stationUuid
                stationDao.updateStationUuid(oldUuid, newUuid)
                scheduleDao.updateStationUuid(oldUuid, newUuid)
                updateStation(
                    stationUuid = newUuid,
                    name = name,
                    url = url,
                    favicon = favicon,
                    tags = tags,
                    countryCode = countryCode,
                    languageCodes = languageCodes,
                    homepage = homepage,
                    iso31662 = iso31662,
                    codec = codec,
                    bitrate = bitrate
                )
                Result.success(newUuid)
            } else {
                Result.failure(Exception(response.message ?: "Unknown API error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Backup & Restore ---

    override suspend fun getAllStationEntities(): List<StationEntity> {
        return stationDao.getAllFavoriteEntities()
    }

    override suspend fun getEntityById(stationUuid: String): StationEntity? {
        return stationDao.getStationById(stationUuid)
    }

    override suspend fun insertEntity(entity: StationEntity) {
        stationDao.insertStation(entity)
    }
}
