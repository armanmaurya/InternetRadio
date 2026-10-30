package com.armanmaurya.internetradio.data.repository

import com.armanmaurya.internetradio.data.local.dao.RecentStationDao
import com.armanmaurya.internetradio.data.local.dao.StationDao
import com.armanmaurya.internetradio.data.local.entity.toDomain
import com.armanmaurya.internetradio.data.local.entity.toRecentEntity
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.repository.RecentRepository
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentRepositoryImpl @Inject constructor(
    private val recentStationDao: RecentStationDao,
    private val stationDao: StationDao,
    private val settingsRepository: SettingsRepository
) : RecentRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getAllRecent(): Flow<List<RadioStation>> =
        settingsRepository.appPreferencesFlow
            .map { it.recentLimit }
            .distinctUntilChanged()
            .flatMapLatest { limit -> recentStationDao.getAllRecent(limit) }
            .map { entities -> entities.map { it.toDomain() } }

    override suspend fun getStationById(stationUuid: String): RadioStation? {
        return recentStationDao.getStationById(stationUuid)?.toDomain()
    }

    override suspend fun addRecentStation(station: RadioStation) {
        val now = System.currentTimeMillis()
        // Recents keep the version that was played; the library row only gets its play time
        recentStationDao.insertOrUpdate(station.toRecentEntity(lastPlayedAt = now))
        stationDao.updateLastPlayed(station.stationUuid, now)
        trimToLimit()
    }

    override suspend fun trimToLimit() {
        val limit = settingsRepository.appPreferencesFlow.first().recentLimit
        recentStationDao.cleanupOldRecents(keepCount = limit)
    }

    override suspend fun removeRecent(stationUuid: String) {
        recentStationDao.deleteStationById(stationUuid)
    }

    override suspend fun clearAllRecent() {
        recentStationDao.deleteAll()
    }
}
