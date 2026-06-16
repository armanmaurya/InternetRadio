package com.armanmaurya.internetradio.data.repository

import com.armanmaurya.internetradio.data.local.dao.UserStationDao
import com.armanmaurya.internetradio.data.local.entity.UserStationEntity
import com.armanmaurya.internetradio.data.local.entity.toDomain
import com.armanmaurya.internetradio.data.model.RadioStation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserStationRepository @Inject constructor(
    private val userStationDao: UserStationDao
) {
    fun getAllUserStations(): Flow<List<RadioStation>> =
        userStationDao.getAllUserStations().map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun addUserStation(
        name: String,
        url: String,
        favicon: String = "",
        tags: List<String> = emptyList(),
        country: String = "",
        countryCode: String = "",
        language: String = ""
    ) {
        val station = UserStationEntity(
            stationUuid = UUID.randomUUID().toString(),
            name = name,
            url = url,
            urlResolved = url,
            favicon = favicon,
            tags = tags,
            country = country,
            countryCode = countryCode,
            language = language
        )
        userStationDao.insertUserStation(station)
    }

    suspend fun deleteUserStation(stationUuid: String) {
        userStationDao.deleteUserStation(stationUuid)
    }
}
