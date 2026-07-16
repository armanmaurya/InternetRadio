package com.armanmaurya.internetradio.data.local.dao

import androidx.room.*
import com.armanmaurya.internetradio.data.local.entity.LibraryStationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryStationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStation(station: LibraryStationEntity)

    @Query("DELETE FROM library_stations WHERE stationUuid = :stationUuid")
    suspend fun deleteStationById(stationUuid: String)

    @Query("SELECT * FROM library_stations ORDER BY addedAt DESC")
    fun getAllStations(): Flow<List<LibraryStationEntity>>

    @Query("SELECT * FROM library_stations ORDER BY addedAt ASC")
    fun getStationsByOldestAdded(): Flow<List<LibraryStationEntity>>
    
    @Query("SELECT * FROM library_stations ORDER BY name ASC")
    fun getStationsByName(): Flow<List<LibraryStationEntity>>

    @Query("SELECT * FROM library_stations ORDER BY name DESC")
    fun getStationsByNameDescending(): Flow<List<LibraryStationEntity>>

    @Query("""
        SELECT library_stations.* FROM library_stations 
        LEFT JOIN recent_stations ON library_stations.stationUuid = recent_stations.stationUuid 
        ORDER BY coalesce(recent_stations.lastPlayedAt, 0) DESC, library_stations.addedAt DESC
    """)
    fun getStationsByRecentlyPlayed(): Flow<List<LibraryStationEntity>>

    @Query("""
        SELECT library_stations.* FROM library_stations 
        LEFT JOIN recent_stations ON library_stations.stationUuid = recent_stations.stationUuid 
        ORDER BY coalesce(recent_stations.lastPlayedAt, 0) ASC, library_stations.addedAt ASC
    """)
    fun getStationsByLeastRecentlyPlayed(): Flow<List<LibraryStationEntity>>

    @Query("SELECT * FROM library_stations ORDER BY orderIndex ASC")
    fun getStationsByCustomOrder(): Flow<List<LibraryStationEntity>>

    @Update
    suspend fun updateStations(stations: List<LibraryStationEntity>)

    @Query("SELECT * FROM library_stations ORDER BY addedAt DESC")
    suspend fun getAllStationEntities(): List<LibraryStationEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM library_stations WHERE stationUuid = :stationUuid)")
    fun isStationInLibrary(stationUuid: String): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM library_stations WHERE stationUuid = :stationUuid)")
    suspend fun isStationInLibraryDirect(stationUuid: String): Boolean

    @Query("SELECT * FROM library_stations WHERE stationUuid = :stationUuid")
    suspend fun getStationById(stationUuid: String): LibraryStationEntity?
}
