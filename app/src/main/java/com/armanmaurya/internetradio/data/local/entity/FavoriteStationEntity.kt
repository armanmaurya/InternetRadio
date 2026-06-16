package com.armanmaurya.internetradio.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_stations")
data class FavoriteStationEntity(
    @PrimaryKey val stationUuid: String,
    val name: String,
    val url: String,
    val urlResolved: String,
    val favicon: String,
    val tags: List<String>,
    val country: String,
    val countryCode: String,
    val language: String,
    val codec: String,
    val bitrate: Int,
    val addedAt: Long = System.currentTimeMillis()
)
