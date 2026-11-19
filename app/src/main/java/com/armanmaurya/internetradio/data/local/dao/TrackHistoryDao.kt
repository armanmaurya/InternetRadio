package com.armanmaurya.internetradio.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trackHistory: TrackHistoryEntity): Long

    @Query("SELECT * FROM track_history WHERE stationUuid = :stationUuid ORDER BY timestamp DESC LIMIT :limit")
    fun getTrackHistoryForStation(stationUuid: String, limit: Int = 50): Flow<List<TrackHistoryEntity>>

    @Query("DELETE FROM track_history WHERE stationUuid = :stationUuid AND id NOT IN (SELECT id FROM track_history WHERE stationUuid = :stationUuid ORDER BY timestamp DESC LIMIT :keepCount)")
    suspend fun cleanupOldTracksForStation(stationUuid: String, keepCount: Int = 50)
    
    @Query("SELECT * FROM track_history WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: Long): TrackHistoryEntity?

    @Query("SELECT * FROM track_history WHERE stationUuid = :stationUuid ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestTrackForStation(stationUuid: String): TrackHistoryEntity?

    @Query("UPDATE track_history SET coverArtUrl = :coverArtUrl WHERE stationUuid = :stationUuid AND trackTitle = :trackTitle")
    suspend fun updateCoverArt(stationUuid: String, trackTitle: String, coverArtUrl: String)

    @Query("UPDATE track_history SET trackTitle = :newTrackTitle, coverArtUrl = :coverArtUrl WHERE id = :id")
    suspend fun updateTrackMetadata(id: Long, newTrackTitle: String, coverArtUrl: String?)

    @Query("UPDATE track_history SET endTimestamp = :endTimestamp, durationMs = durationMs + :addedDurationMs WHERE id = :id")
    suspend fun recordTrackListeningProgress(id: Long, endTimestamp: Long, addedDurationMs: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(trackHistories: List<TrackHistoryEntity>): List<Long>

    @Query("SELECT * FROM track_history WHERE stationUuid = :stationUuid ORDER BY timestamp ASC")
    suspend fun getAllTracksForStationSync(stationUuid: String): List<TrackHistoryEntity>

    @Update
    suspend fun updateTrack(trackHistory: TrackHistoryEntity)
}
