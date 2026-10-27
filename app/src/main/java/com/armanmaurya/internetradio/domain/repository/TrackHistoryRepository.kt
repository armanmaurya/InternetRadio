package com.armanmaurya.internetradio.domain.repository

import com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity
import kotlinx.coroutines.flow.Flow

interface TrackHistoryRepository {
    suspend fun logTrack(stationUuid: String, trackTitle: String, rawTrackTitle: String = trackTitle): Long?
    suspend fun updateTrackMetadata(id: Long, newTrackTitle: String, coverArtUrl: String?)
    fun getTrackHistory(stationUuid: String): Flow<List<TrackHistoryEntity>>
    suspend fun updateCoverArt(stationUuid: String, trackTitle: String, coverArtUrl: String)
    fun getStarredTracks(stationUuid: String): Flow<List<TrackHistoryEntity>>
    suspend fun toggleTrackStarred(stationUuid: String, trackTitle: String, isStarred: Boolean)
    suspend fun setTrackStarredById(id: Long, isStarred: Boolean)
    suspend fun isTrackStarred(stationUuid: String, trackTitle: String): Boolean
    suspend fun recordTrackListeningProgress(id: Long, endTimestamp: Long, addedDurationMs: Long)
}
