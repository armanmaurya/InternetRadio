package com.armanmaurya.internetradio.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "starred_tracks",
    indices = [
        Index(value = ["stationUuid", "trackTitle"], unique = true),
        Index(value = ["stationUuid", "starredAt"])
    ]
)
data class StarredTrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val stationUuid: String,
    val trackTitle: String,
    @ColumnInfo(defaultValue = "")
    val rawTrackTitle: String = "",
    @ColumnInfo(defaultValue = "NULL")
    val artist: String? = null,
    @ColumnInfo(defaultValue = "NULL")
    val coverArtUrl: String? = null,
    @ColumnInfo(defaultValue = "0")
    val starredAt: Long = System.currentTimeMillis()
)

fun StarredTrackEntity.toTrackHistoryEntity(): TrackHistoryEntity {
    return TrackHistoryEntity(
        id = id,
        stationUuid = stationUuid,
        trackTitle = trackTitle,
        timestamp = starredAt,
        coverArtUrl = coverArtUrl,
        rawTrackTitle = rawTrackTitle,
        isStarred = true,
        artist = artist,
        endTimestamp = starredAt,
        durationMs = 0L
    )
}
