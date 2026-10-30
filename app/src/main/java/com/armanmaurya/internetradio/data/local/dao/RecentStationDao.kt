package com.armanmaurya.internetradio.data.local.dao

import androidx.room.*
import com.armanmaurya.internetradio.data.local.entity.RecentStationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentStationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(station: RecentStationEntity)

    @Query("SELECT * FROM recent_stations ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun getAllRecent(limit: Int): Flow<List<RecentStationEntity>>

    @Query("DELETE FROM recent_stations WHERE stationUuid NOT IN (SELECT stationUuid FROM recent_stations ORDER BY lastPlayedAt DESC LIMIT :keepCount)")
    suspend fun cleanupOldRecents(keepCount: Int)

    @Query("SELECT * FROM recent_stations WHERE stationUuid = :stationUuid LIMIT 1")
    suspend fun getStationById(stationUuid: String): RecentStationEntity?

    @Query("UPDATE recent_stations SET stationUuid = :newUuid, isCustom = 0 WHERE stationUuid = :oldUuid")
    suspend fun updateStationUuid(oldUuid: String, newUuid: String)

    @Query("DELETE FROM recent_stations WHERE stationUuid = :stationUuid")
    suspend fun deleteStationById(stationUuid: String)

    @Query("DELETE FROM recent_stations")
    suspend fun deleteAll()
}
