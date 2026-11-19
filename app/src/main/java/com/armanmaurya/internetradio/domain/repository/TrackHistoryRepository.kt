package com.armanmaurya.internetradio.domain.repository

import com.armanmaurya.internetradio.data.local.entity.StarredTrackEntity
import com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity
import kotlinx.coroutines.flow.Flow

interface TrackHistoryRepository {
    suspend fun logTrack(stationUuid: String, trackTitle: String, rawTrackTitle: String = trackTitle): Long?
    suspend fun updateTrackMetadata(id: Long, newTrackTitle: String, coverArtUrl: String?)
    /**
     * Newest-first history for [stationUuid], at most [limit] rows (and never more than the
     * user's track history limit setting). Callers grow [limit] to load more as the user scrolls.
     * Each row's isStarred comes from starred_tracks and updates when a track is starred or unstarred.
     */
    fun getTrackHistory(stationUuid: String, limit: Int): Flow<List<TrackHistoryEntity>>
    suspend fun updateCoverArt(stationUuid: String, trackTitle: String, coverArtUrl: String)
    fun getStarredTracks(stationUuid: String): Flow<List<TrackHistoryEntity>>
    suspend fun toggleTrackStarred(stationUuid: String, trackTitle: String, isStarred: Boolean)
    suspend fun setTrackStarredById(id: Long, isStarred: Boolean)
    suspend fun isTrackStarred(stationUuid: String, trackTitle: String): Boolean
    suspend fun recordTrackListeningProgress(id: Long, endTimestamp: Long, addedDurationMs: Long)
    /** Every history row for [stationUuid], oldest first. Starred tracks are not included. */
    suspend fun getAllTracksForStation(stationUuid: String): List<TrackHistoryEntity>
    suspend fun getAllStarredTracksForStation(stationUuid: String): List<StarredTrackEntity>
    /** Adds backup history rows, matching existing rows by timestamp and resolving clashes with [strategy]. */
    suspend fun importTracks(stationUuid: String, tracks: List<TrackHistoryEntity>, strategy: com.armanmaurya.internetradio.domain.model.ConflictStrategy)
    /** Adds backup stars to the local ones; a track already starred is kept, filling in missing artwork or artist. */
    suspend fun importStarredTracks(stationUuid: String, tracks: List<StarredTrackEntity>)
}
