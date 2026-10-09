package com.armanmaurya.internetradio.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.armanmaurya.internetradio.domain.model.RadioStation

@Entity(tableName = "stations")
data class StationEntity(
    @PrimaryKey val stationUuid: String,
    val name: String,
    val url: String,
    val urlResolved: String = "",
    val favicon: String = "",
    val tags: List<String> = emptyList(),
    val countryCode: String = "",
    @ColumnInfo(defaultValue = "")
    val languageCodes: List<String> = emptyList(),
    val codec: String = "unknown",
    val bitrate: Int = 0,
    @ColumnInfo(defaultValue = "")
    val homepage: String = "",
    val iso3166_2: String? = null,
    val geoLat: Double? = null,
    val geoLong: Double? = null,
    @ColumnInfo(defaultValue = "0")
    val isCustom: Boolean = false,

    // State & Sorting flags
    @ColumnInfo(defaultValue = "0")
    val isFavorite: Boolean = false,
    val addedAt: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val orderIndex: Int = 0,
    val lastPlayedAt: Long? = null
)

fun StationEntity.toDomain() = RadioStation(
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

fun RadioStation.toLibraryEntity(isCustom: Boolean = this.isCustom) = toEntity(isFavorite = true)

fun RadioStation.toEntity(
    isFavorite: Boolean = this.isCustom,
    addedAt: Long? = null,
    orderIndex: Int = 0,
    lastPlayedAt: Long? = null
) = StationEntity(
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
    isCustom = isCustom,
    homepage = homepage,
    iso3166_2 = iso3166_2,
    geoLat = geoLat,
    geoLong = geoLong,
    isFavorite = isFavorite,
    addedAt = addedAt,
    orderIndex = orderIndex,
    lastPlayedAt = lastPlayedAt
)
