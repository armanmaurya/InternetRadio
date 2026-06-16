package com.armanmaurya.internetradio.data.repository

import com.armanmaurya.internetradio.data.local.dao.FavoriteStationDao
import com.armanmaurya.internetradio.data.local.entity.FavoriteStationEntity
import com.armanmaurya.internetradio.data.model.RadioStation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteRepository @Inject constructor(
    private val favoriteStationDao: FavoriteStationDao
) {
    fun getAllFavorites(): Flow<List<RadioStation>> {
        return favoriteStationDao.getAllFavorites().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun isFavorite(stationUuid: String): Flow<Boolean> {
        return favoriteStationDao.isFavorite(stationUuid).map { it != 0 }
    }

    suspend fun isFavoriteDirect(stationUuid: String): Boolean {
        return favoriteStationDao.isFavoriteDirect(stationUuid)
    }

    suspend fun addFavorite(station: RadioStation) {
        favoriteStationDao.insertFavorite(station.toEntity())
    }

    suspend fun removeFavorite(stationUuid: String) {
        favoriteStationDao.deleteFavoriteById(stationUuid)
    }

    private fun FavoriteStationEntity.toDomain(): RadioStation {
        return RadioStation(
            changeUuid = "", // Not stored
            stationUuid = stationUuid,
            name = name,
            url = url,
            urlResolved = urlResolved,
            homepage = "",
            favicon = favicon,
            tags = tags,
            country = country,
            countryCode = countryCode,
            state = "",
            iso3166_2 = null,
            language = language,
            languageCodes = emptyList(),
            votes = 0,
            lastChangeTime = "",
            codec = codec,
            bitrate = bitrate,
            hls = false,
            lastCheckOk = true,
            lastCheckTime = "",
            lastCheckOkTime = "",
            lastLocalCheckTime = "",
            clickTimestamp = "",
            clickCount = 0,
            clickTrend = 0,
            sslError = false,
            geoLat = null,
            geoLong = null,
            geoDistance = null,
            hasExtendedInfo = false
        )
    }

    private fun RadioStation.toEntity(): FavoriteStationEntity {
        return FavoriteStationEntity(
            stationUuid = stationUuid,
            name = name,
            url = url,
            urlResolved = urlResolved,
            favicon = favicon,
            tags = tags,
            country = country,
            countryCode = countryCode,
            language = language,
            codec = codec,
            bitrate = bitrate
        )
    }
}
