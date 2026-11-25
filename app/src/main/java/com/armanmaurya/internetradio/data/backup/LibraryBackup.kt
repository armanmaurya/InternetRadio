package com.armanmaurya.internetradio.data.backup

import com.armanmaurya.internetradio.data.local.entity.StarredTrackEntity
import com.armanmaurya.internetradio.data.local.entity.StationEntity
import com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity

data class LibraryBackup(
    val schemaVersion: Int = 2,
    val exportedAt: String = "",
    val appVersion: String = "",
    val stations: List<BackupStation>? = null
)

/** One play from a station's track history. */
data class BackupTrack(
    val trackTitle: String,
    val timestamp: Long,
    val coverArtUrl: String? = null,
    val rawTrackTitle: String? = null,
    /** Only read from older backups, which marked stars on history rows. New backups use [BackupStation.starredTracks]. */
    val isStarred: Boolean? = null,
    val artist: String? = null,
    val endTimestamp: Long? = null,
    val durationMs: Long? = null
)

/** One starred track of a station. */
data class BackupStarredTrack(
    val trackTitle: String,
    val rawTrackTitle: String? = null,
    val artist: String? = null,
    val coverArtUrl: String? = null,
    val starredAt: Long? = null
)

data class BackupStation(
    val stationUuid: String,
    val name: String,
    val url: String,
    val urlResolved: String? = null,
    val favicon: String? = null,
    val tags: List<String>? = null,
    val country: String? = null, // Old field
    val countryCode: String? = null,
    val language: String? = null, // Old field
    val languageCodes: List<String>? = null,
    val codec: String? = null,
    val bitrate: Int? = null,
    val isCustom: Boolean? = null,
    val addedAt: Long? = null,
    val homepage: String? = null,
    val iso3166_2: String? = null,
    val geoLat: Double? = null,
    val geoLong: Double? = null,
    val volumeLevel: Float? = null,
    val tracks: List<BackupTrack>? = null,
    val starredTracks: List<BackupStarredTrack>? = null
) {
    /** Stars to restore: [starredTracks], plus stars that older backups marked on [tracks]. */
    fun allStarredTracks(): List<BackupStarredTrack> {
        val legacy = tracks.orEmpty()
            .filter { it.isStarred == true }
            .map {
                BackupStarredTrack(
                    trackTitle = it.trackTitle,
                    rawTrackTitle = it.rawTrackTitle,
                    artist = it.artist,
                    coverArtUrl = it.coverArtUrl,
                    starredAt = it.timestamp
                )
            }
        return starredTracks.orEmpty() + legacy
    }

    fun toStationEntity(): StationEntity {
        val mappedLanguageCodes = languageCodes ?: language?.takeIf { it.isNotBlank() }?.let { langString ->
            com.armanmaurya.internetradio.core.utils.LanguageMapper.getCodesFromNameString(langString).mapNotNull { code ->
                com.neovisionaries.i18n.LanguageAlpha3Code.getByCodeIgnoreCase(code)?.alpha3B?.name
                    ?: com.neovisionaries.i18n.LanguageCode.getByCodeIgnoreCase(code)?.alpha3?.alpha3B?.name
                    ?: code
            }.distinct()
        } ?: emptyList()
        
        return StationEntity(
            stationUuid = stationUuid,
            name = name,
            url = url,
            urlResolved = urlResolved ?: "",
            favicon = favicon ?: "",
            tags = tags?.map { it.lowercase() } ?: emptyList(),
            countryCode = countryCode ?: "",
            languageCodes = mappedLanguageCodes,
            codec = codec ?: "unknown",
            bitrate = bitrate ?: 0,
            isCustom = isCustom ?: false,
            isFavorite = true,
            addedAt = addedAt ?: System.currentTimeMillis(),
            homepage = homepage ?: "",
            iso3166_2 = iso3166_2,
            geoLat = geoLat,
            geoLong = geoLong,
            orderIndex = 0,
            lastPlayedAt = null,
            volumeLevel = volumeLevel?.takeIf { !it.isNaN() }?.coerceIn(0f, 2f) ?: 1.0f
        )
    }
}

fun StationEntity.toBackupStation(
    tracks: List<BackupTrack>? = null,
    starredTracks: List<BackupStarredTrack>? = null
): BackupStation {
    return BackupStation(
        stationUuid = stationUuid,
        name = name,
        url = url,
        urlResolved = urlResolved,
        favicon = favicon,
        tags = tags,
        countryCode = countryCode,
        languageCodes = languageCodes,
        codec = codec,
        bitrate = bitrate,
        isCustom = isCustom,
        addedAt = addedAt,
        homepage = homepage,
        iso3166_2 = iso3166_2,
        geoLat = geoLat,
        geoLong = geoLong,
        volumeLevel = volumeLevel,
        tracks = tracks,
        starredTracks = starredTracks
    )
}

fun TrackHistoryEntity.toBackupTrack(): BackupTrack {
    return BackupTrack(
        trackTitle = trackTitle,
        timestamp = timestamp,
        coverArtUrl = coverArtUrl,
        rawTrackTitle = rawTrackTitle.takeIf { it.isNotBlank() },
        artist = artist,
        endTimestamp = endTimestamp.takeIf { it > 0 },
        durationMs = durationMs.takeIf { it > 0 }
    )
}

fun BackupTrack.toTrackHistoryEntity(stationUuid: String): TrackHistoryEntity {
    return TrackHistoryEntity(
        id = 0,
        stationUuid = stationUuid,
        trackTitle = trackTitle,
        timestamp = timestamp,
        coverArtUrl = coverArtUrl,
        rawTrackTitle = rawTrackTitle ?: "",
        artist = artist,
        endTimestamp = endTimestamp ?: timestamp,
        durationMs = durationMs ?: 0L
    )
}

fun StarredTrackEntity.toBackupStarredTrack(): BackupStarredTrack {
    return BackupStarredTrack(
        trackTitle = trackTitle,
        rawTrackTitle = rawTrackTitle.takeIf { it.isNotBlank() },
        artist = artist,
        coverArtUrl = coverArtUrl,
        starredAt = starredAt.takeIf { it > 0 }
    )
}

fun BackupStarredTrack.toStarredTrackEntity(stationUuid: String): StarredTrackEntity {
    return StarredTrackEntity(
        id = 0,
        stationUuid = stationUuid,
        trackTitle = trackTitle,
        rawTrackTitle = rawTrackTitle ?: "",
        artist = artist,
        coverArtUrl = coverArtUrl,
        starredAt = starredAt ?: System.currentTimeMillis()
    )
}
