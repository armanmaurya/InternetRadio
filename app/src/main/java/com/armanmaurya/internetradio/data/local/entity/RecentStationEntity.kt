package com.armanmaurya.internetradio.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.armanmaurya.internetradio.data.model.RadioStation

@Entity(tableName = "recent_stations")
data class RecentStationEntity(
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
    val lastPlayedAt: Long = System.currentTimeMillis()
)

fun RecentStationEntity.toDomain() = RadioStation(
    changeUuid = "",
    stationUuid = stationUuid,
    name = name,
    url = url,
    urlResolved = urlResolved,
    homepage = "",
    favicon = favicon,
    tags = tags,
    country = country,
    countryCode = countryCode,
    state = "",
    iso3166_2 = null,
    language = language,
    languageCodes = emptyList(),
    votes = 0,
    lastChangeTime = "",
    codec = codec,
    bitrate = bitrate,
    hls = false,
    lastCheckOk = true,
    lastCheckTime = "",
    lastCheckOkTime = "",
    lastLocalCheckTime = "",
    clickTimestamp = "",
    clickCount = 0,
    clickTrend = 0,
    sslError = false,
    geoLat = null,
    geoLong = null,
    geoDistance = null,
    hasExtendedInfo = false
)

fun RadioStation.toRecentEntity(timestamp: Long = System.currentTimeMillis()) = RecentStationEntity(
    stationUuid = stationUuid,
    name = name,
    url = url,
    urlResolved = urlResolved,
    favicon = favicon,
    tags = tags,
    country = country,
    countryCode = countryCode,
    language = language,
    codec = codec,
    bitrate = bitrate,
    lastPlayedAt = timestamp
)
