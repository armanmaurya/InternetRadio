package com.armanmaurya.internetradio.data.repository

import android.content.Context
import com.armanmaurya.internetradio.domain.model.Country
import com.armanmaurya.internetradio.domain.model.Language
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.model.Tag
import com.armanmaurya.internetradio.domain.model.StationAddResult
import com.armanmaurya.internetradio.data.remote.RadioBrowserApi
import com.armanmaurya.internetradio.data.remote.toDomain
import com.armanmaurya.internetradio.data.remote.dto.toDomain
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.armanmaurya.internetradio.data.local.dao.StationDao
import com.armanmaurya.internetradio.domain.repository.StationRepository

@Singleton
class StationRepositoryImpl @Inject constructor(
    private val api: RadioBrowserApi,
    @ApplicationContext private val context: Context,
    private val stationDao: StationDao
) : StationRepository {

    override suspend fun filterStations(
        name: String?,
        nameExact: Boolean?,
        country: String?,
        countryExact: Boolean?,
        countryCode: String?,
        state: String?,
        stateExact: Boolean?,
        language: String?,
        languageExact: Boolean?,
        tag: String?,
        tagExact: Boolean?,
        tagList: String?,
        codec: String?,
        bitrateMin: Int?,
        bitrateMax: Int?,
        hasExtendedInfo: Boolean?,
        isHttps: Boolean?,
        order: String,
        reverse: Boolean,
        limit: Int,
        offset: Int,
        hideBroken: Boolean
    ): Result<List<RadioStation>> =
        runCatching {
            api.advancedSearch(
                name = name,
                nameExact = nameExact,
                country = country,
                countryExact = countryExact,
                countryCode = countryCode,
                state = state,
                stateExact = stateExact,
                language = language,
                languageExact = languageExact,
                tag = tag,
                tagExact = tagExact,
                tagList = tagList,
                codec = codec,
                bitrateMin = bitrateMin,
                bitrateMax = bitrateMax,
                hasExtendedInfo = hasExtendedInfo,
                isHttps = isHttps,
                order = order,
                reverse = reverse,
                limit = limit,
                offset = offset,
                hideBroken = hideBroken
            ).map { it.toDomain() }
        }

    override suspend fun getCountries(): Result<List<Country>> =
        runCatching {
            api.getCountries()
                .map { it.toDomain() }
                .filter { it.isoCode.isNotBlank() }
                .sortedByDescending { it.stationCount }
        }

    override suspend fun getLanguages(filter: String?): Result<List<Language>> =
        runCatching {
            if (filter.isNullOrBlank()) {
                api.getLanguages(order = "stationcount", reverse = true)
                    .map { it.toDomain() }
            } else {
                api.getLanguagesFiltered(filter = filter, order = "stationcount", reverse = true)
                    .map { it.toDomain() }
            }
        }

    override suspend fun getTags(filter: String?): Result<List<Tag>> =
        runCatching {
            val apiTags = if (filter.isNullOrBlank()) {
                api.getTags(order = "stationcount", reverse = true)
                    .map { it.toDomain() }
            } else {
                api.getTagsFiltered(filter = filter, order = "stationcount", reverse = true)
                    .map { it.toDomain() }
            }

            val customStations = stationDao.getCustomStations()
            val customTags = customStations.flatMap { it.tags }
                .filter { it.isNotBlank() }
                .filter { if (!filter.isNullOrBlank()) it.contains(filter, ignoreCase = true) else true }
                .distinct()
                .map { Tag(name = it, stationCount = 1) }

            val apiTagNames = apiTags.map { it.name.lowercase() }.toSet()
            val uniqueCustomTags = customTags.filter { it.name.lowercase() !in apiTagNames }

            apiTags + uniqueCustomTags
        }

    override suspend fun getCurrentCountryCode(): Result<String> =
        runCatching {
            val countryCode = context.resources.configuration.locales[0].country
            if (countryCode.isBlank()) {
                throw IllegalStateException("Country code not available in locale")
            }
            countryCode
        }

    /**
     * Should be called every time a user starts playing a station.
     * radio-browser.info uses this to rank station popularity.
     * Failures are silently ignored — this is a fire-and-forget call.
     */
    override suspend fun registerClick(stationUuid: String) {
        runCatching { api.clickStation(stationUuid) }
    }

    override suspend fun getStationsByUuid(uuids: List<String>): Result<List<RadioStation>> =
        runCatching {
            api.getStationsByUuid(uuids.joinToString(",")).map { it.toDomain() }
        }

    override suspend fun getStationsByUrl(url: String): Result<List<RadioStation>> =
        runCatching {
            api.searchByUrl(url).map { it.toDomain() }
        }

    override suspend fun addStation(
        name: String,
        url: String,
        homepage: String?,
        favicon: String?,
        countryCode: String?,
        iso31662: String?,
        languageCodes: String?,
        tags: String?,
        geoLat: Double?,
        geoLong: Double?,
    ): Result<StationAddResult> = runCatching {
        api.addStation(
            name = name,
            url = url,
            homepage = homepage,
            favicon = favicon,
            countryCode = countryCode,
            iso31662 = iso31662,
            languageCodes = languageCodes,
            tags = tags,
            geoLat = geoLat,
            geoLong = geoLong
        ).toDomain()
    }
}