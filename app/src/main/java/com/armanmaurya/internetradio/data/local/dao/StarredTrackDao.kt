package com.armanmaurya.internetradio.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.armanmaurya.internetradio.data.local.entity.StarredTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StarredTrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(starredTrack: StarredTrackEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(starredTracks: List<StarredTrackEntity>): List<Long>

    @Query("SELECT * FROM starred_tracks WHERE stationUuid = :stationUuid ORDER BY starredAt DESC")
    fun getStarredTracksForStation(stationUuid: String): Flow<List<StarredTrackEntity>>

    @Query("SELECT * FROM starred_tracks WHERE stationUuid = :stationUuid ORDER BY starredAt DESC")
    suspend fun getStarredTracksForStationSync(stationUuid: String): List<StarredTrackEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM starred_tracks WHERE stationUuid = :stationUuid AND (LOWER(TRIM(trackTitle)) = LOWER(TRIM(:trackTitle)) OR (rawTrackTitle != '' AND LOWER(TRIM(rawTrackTitle)) = LOWER(TRIM(:rawTrackTitle)))))")
    suspend fun isTrackStarred(stationUuid: String, trackTitle: String, rawTrackTitle: String = ""): Boolean

    @Query("DELETE FROM starred_tracks WHERE stationUuid = :stationUuid AND (LOWER(TRIM(trackTitle)) = LOWER(TRIM(:trackTitle)) OR (rawTrackTitle != '' AND LOWER(TRIM(rawTrackTitle)) = LOWER(TRIM(:rawTrackTitle))))")
    suspend fun deleteStarredTrack(stationUuid: String, trackTitle: String, rawTrackTitle: String = "")

    @Query("DELETE FROM starred_tracks WHERE id = :id")
    suspend fun deleteStarredTrackById(id: Long)

    @Query("UPDATE starred_tracks SET coverArtUrl = :coverArtUrl WHERE stationUuid = :stationUuid AND trackTitle = :trackTitle")
    suspend fun updateCoverArt(stationUuid: String, trackTitle: String, coverArtUrl: String)

    /**
     * The starred row for this track, matched on the clean title or, when given, the raw stream
     * title (trimmed, case-insensitive). Leave [rawTrackTitle] blank to match on title only.
     */
    @Query("SELECT * FROM starred_tracks WHERE stationUuid = :stationUuid AND (LOWER(TRIM(trackTitle)) = LOWER(TRIM(:trackTitle)) OR (rawTrackTitle != '' AND LOWER(TRIM(rawTrackTitle)) = LOWER(TRIM(:rawTrackTitle)))) LIMIT 1")
    suspend fun findStarredTrack(stationUuid: String, trackTitle: String, rawTrackTitle: String = ""): StarredTrackEntity?

    @Update
    suspend fun update(starredTrack: StarredTrackEntity)
}
