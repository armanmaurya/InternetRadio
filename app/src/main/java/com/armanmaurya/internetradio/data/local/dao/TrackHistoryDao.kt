package com.armanmaurya.internetradio.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trackHistory: TrackHistoryEntity): Long

    @Query("SELECT * FROM track_history WHERE stationUuid = :stationUuid ORDER BY timestamp DESC LIMIT :limit")
    fun getTrackHistoryForStation(stationUuid: String, limit: Int = 50): Flow<List<TrackHistoryEntity>>

    @Query("DELETE FROM track_history WHERE stationUuid = :stationUuid AND isStarred = 0 AND id NOT IN (SELECT id FROM track_history WHERE stationUuid = :stationUuid AND isStarred = 0 ORDER BY timestamp DESC LIMIT :keepCount)")
    suspend fun cleanupOldTracksForStation(stationUuid: String, keepCount: Int = 50)
    
    @Query("SELECT * FROM track_history WHERE stationUuid = :stationUuid ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestTrackForStation(stationUuid: String): TrackHistoryEntity?

    @Query("UPDATE track_history SET coverArtUrl = :coverArtUrl WHERE stationUuid = :stationUuid AND trackTitle = :trackTitle")
    suspend fun updateCoverArt(stationUuid: String, trackTitle: String, coverArtUrl: String)

    @Query("UPDATE track_history SET trackTitle = :newTrackTitle, coverArtUrl = :coverArtUrl WHERE id = :id")
    suspend fun updateTrackMetadata(id: Long, newTrackTitle: String, coverArtUrl: String?)

    @Query("SELECT * FROM track_history WHERE isStarred = 1 GROUP BY LOWER(TRIM(trackTitle)) ORDER BY timestamp DESC")
    fun getAllStarredTracks(): Flow<List<TrackHistoryEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM track_history WHERE isStarred = 1 AND (LOWER(TRIM(trackTitle)) = LOWER(TRIM(:trackTitle)) OR (rawTrackTitle != '' AND LOWER(TRIM(rawTrackTitle)) = LOWER(TRIM(:rawTrackTitle)))))")
    suspend fun isTrackStarred(trackTitle: String, rawTrackTitle: String = ""): Boolean

    @Query("UPDATE track_history SET isStarred = :isStarred WHERE LOWER(TRIM(trackTitle)) = LOWER(TRIM(:trackTitle)) OR (rawTrackTitle != '' AND LOWER(TRIM(rawTrackTitle)) = LOWER(TRIM(:trackTitle)))")
    suspend fun setTrackStarred(trackTitle: String, isStarred: Boolean)

    @Query("UPDATE track_history SET isStarred = :isStarred WHERE id = :id")
    suspend fun setTrackStarredById(id: Long, isStarred: Boolean)
}
