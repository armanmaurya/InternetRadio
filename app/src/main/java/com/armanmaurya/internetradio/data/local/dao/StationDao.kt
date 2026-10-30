package com.armanmaurya.internetradio.data.local.dao

import androidx.room.*
import com.armanmaurya.internetradio.data.local.entity.StationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(station: StationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStation(station: StationEntity)

    @Query("SELECT * FROM stations WHERE stationUuid = :stationUuid LIMIT 1")
    suspend fun getStationById(stationUuid: String): StationEntity?

    @Query("SELECT * FROM stations WHERE isCustom = 1")
    suspend fun getCustomStations(): List<StationEntity>

    @Query("UPDATE stations SET stationUuid = :newUuid, isCustom = 0 WHERE stationUuid = :oldUuid")
    suspend fun updateStationUuid(oldUuid: String, newUuid: String)

    // --- Library / Favorites Queries ---

    @Query("SELECT * FROM stations WHERE isFavorite = 1 ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<StationEntity>>

    @Query("SELECT * FROM stations WHERE isFavorite = 1 ORDER BY addedAt ASC")
    fun getFavoritesByOldestAdded(): Flow<List<StationEntity>>

    @Query("SELECT * FROM stations WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavoritesByName(): Flow<List<StationEntity>>

    @Query("SELECT * FROM stations WHERE isFavorite = 1 ORDER BY name DESC")
    fun getFavoritesByNameDescending(): Flow<List<StationEntity>>

    @Query("SELECT * FROM stations WHERE isFavorite = 1 ORDER BY coalesce(lastPlayedAt, 0) DESC, addedAt DESC")
    fun getFavoritesByRecentlyPlayed(): Flow<List<StationEntity>>

    @Query("SELECT * FROM stations WHERE isFavorite = 1 ORDER BY coalesce(lastPlayedAt, 0) ASC, addedAt ASC")
    fun getFavoritesByLeastRecentlyPlayed(): Flow<List<StationEntity>>

    @Query("SELECT * FROM stations WHERE isFavorite = 1 ORDER BY orderIndex ASC")
    fun getFavoritesByCustomOrder(): Flow<List<StationEntity>>

    @Query("SELECT * FROM stations WHERE isFavorite = 1 ORDER BY addedAt DESC")
    suspend fun getAllFavoriteEntities(): List<StationEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM stations WHERE stationUuid = :stationUuid AND isFavorite = 1)")
    fun isStationInLibrary(stationUuid: String): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM stations WHERE stationUuid = :stationUuid AND isFavorite = 1)")
    suspend fun isStationInLibraryDirect(stationUuid: String): Boolean

    @Update
    suspend fun updateStations(stations: List<StationEntity>)


    @Query("DELETE FROM stations WHERE stationUuid = :stationUuid")
    suspend fun deleteStationById(stationUuid: String)

    // Play time used by the library "recently played" sort; recents live in RecentStationDao
    @Query("UPDATE stations SET lastPlayedAt = :timestamp WHERE stationUuid = :stationUuid")
    suspend fun updateLastPlayed(stationUuid: String, timestamp: Long)
}
