package com.armanmaurya.internetradio.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "track_history")
data class TrackHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val stationUuid: String,
    val trackTitle: String,
    val timestamp: Long,
    @androidx.room.ColumnInfo(defaultValue = "NULL")
    val coverArtUrl: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "")
    val rawTrackTitle: String = "",
    @androidx.room.ColumnInfo(defaultValue = "0")
    val isStarred: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "NULL")
    val artist: String? = null
)
