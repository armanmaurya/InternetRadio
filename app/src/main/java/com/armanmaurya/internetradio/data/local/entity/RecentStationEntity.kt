package com.armanmaurya.internetradio.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.armanmaurya.internetradio.domain.model.RadioStation

/**
 * The station exactly as it was last played. Kept apart from [StationEntity] so playing a
 * station (for example the Radio Browser version from Browse) never overwrites a library edit.
 */
@Entity(tableName = "recent_stations")
data class RecentStationEntity(
    @PrimaryKey val stationUuid: String,
    val name: String,
    val url: String,
    val urlResolved: String,
    val favicon: String,
    val tags: List<String>,
    val countryCode: String,
    val languageCodes: List<String>,
    val codec: String,
    val bitrate: Int,
    val homepage: String,
    val iso3166_2: String?,
    val geoLat: Double?,
    val geoLong: Double?,
    val isCustom: Boolean,
    val lastPlayedAt: Long
)

fun RecentStationEntity.toDomain() = RadioStation(
    changeUuid = "",
    stationUuid = stationUuid,
    name = name,
    url = url,
    urlResolved = if (urlResolved.isBlank()) url else urlResolved,
    homepage = homepage,
    favicon = favicon,
    tags = tags,
    country = "",
    countryCode = countryCode,
    state = "",
    iso3166_2 = iso3166_2,
    language = "",
    languageCodes = languageCodes,
    votes = 0,
    lastChangeTime = "",
    codec = codec,
    bitrate = bitrate,
    lastCheckOk = true,
    lastCheckTime = "",
    lastCheckOkTime = "",
    lastLocalCheckTime = "",
    clickTimestamp = "",
    clickCount = 0,
    clickTrend = 0,
    sslError = false,
    geoLat = geoLat,
    geoLong = geoLong,
    geoDistance = null,
    hasExtendedInfo = false,
    isCustom = isCustom
)

fun RadioStation.toRecentEntity(lastPlayedAt: Long) = RecentStationEntity(
    stationUuid = stationUuid,
    name = name,
    url = url,
    urlResolved = if (urlResolved.isBlank()) url else urlResolved,
    favicon = favicon,
    tags = tags,
    countryCode = countryCode,
    languageCodes = languageCodes,
    codec = codec,
    bitrate = bitrate,
    homepage = homepage,
    iso3166_2 = iso3166_2,
    geoLat = geoLat,
    geoLong = geoLong,
    isCustom = isCustom,
    lastPlayedAt = lastPlayedAt
)
