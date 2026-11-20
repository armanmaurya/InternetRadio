package com.armanmaurya.internetradio.data.local.entity

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "track_history",
    // Every history read and the per-station trim go by station, newest first.
    indices = [Index(value = ["stationUuid", "timestamp"])]
)
data class TrackHistoryEntity @Ignore constructor(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val stationUuid: String,
    val trackTitle: String,
    val timestamp: Long,
    @androidx.room.ColumnInfo(defaultValue = "NULL")
    val coverArtUrl: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "")
    val rawTrackTitle: String = "",
    @androidx.room.ColumnInfo(defaultValue = "NULL")
    val artist: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val endTimestamp: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val durationMs: Long = 0,
    /**
     * Not stored in track_history. Filled in when history is read, from whether a matching row
     * exists in starred_tracks (see TrackHistoryRepository.getTrackHistory).
     */
    @Ignore
    val isStarred: Boolean = false
) {
    /** Constructor Room uses when reading rows: every column, without [isStarred]. */
    constructor(
        id: Long,
        stationUuid: String,
        trackTitle: String,
        timestamp: Long,
        coverArtUrl: String?,
        rawTrackTitle: String,
        artist: String?,
        endTimestamp: Long,
        durationMs: Long
    ) : this(id, stationUuid, trackTitle, timestamp, coverArtUrl, rawTrackTitle, artist, endTimestamp, durationMs, false)
}
