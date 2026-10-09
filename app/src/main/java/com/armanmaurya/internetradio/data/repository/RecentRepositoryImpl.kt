package com.armanmaurya.internetradio.data.repository

import com.armanmaurya.internetradio.data.local.dao.StationDao
import com.armanmaurya.internetradio.data.local.entity.toDomain
import com.armanmaurya.internetradio.data.local.entity.toEntity
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.repository.RecentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentRepositoryImpl @Inject constructor(
    private val stationDao: StationDao
) : RecentRepository {
    override fun getAllRecent(): Flow<List<RadioStation>> =
        stationDao.getAllRecent().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getStationById(stationUuid: String): RadioStation? {
        return stationDao.getStationById(stationUuid)?.toDomain()
    }

    override suspend fun addRecentStation(station: RadioStation) {
        val now = System.currentTimeMillis()
        val existing = stationDao.getStationById(station.stationUuid)
        if (existing != null) {
            stationDao.insertOrUpdate(
                station.toEntity(
                    isFavorite = existing.isFavorite,
                    addedAt = existing.addedAt,
                    orderIndex = existing.orderIndex,
                    lastPlayedAt = now
                )
            )
        } else {
            stationDao.insertOrUpdate(
                station.toEntity(
                    isFavorite = false,
                    lastPlayedAt = now
                )
            )
        }
    }

    override suspend fun removeRecent(stationUuid: String) {
        stationDao.removeRecentStation(stationUuid)
    }

    override suspend fun clearAllRecent() {
        stationDao.clearAllRecentStations()
    }
}
