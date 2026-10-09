package com.armanmaurya.internetradio.data.backup

import com.armanmaurya.internetradio.data.local.entity.StationEntity

data class LibraryBackup(
    val schemaVersion: Int = 2,
    val exportedAt: String = "",
    val appVersion: String = "",
    val stations: List<BackupStation>? = null
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
    val geoLong: Double? = null
) {
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
            lastPlayedAt = null
        )
    }
}

fun StationEntity.toBackupStation(): BackupStation {
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
        geoLong = geoLong
    )
}
