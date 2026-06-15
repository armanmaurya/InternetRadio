package com.armanmaurya.internetradio.data.repository

import android.content.Context
import com.armanmaurya.internetradio.data.local.dao.MetadataDao
import com.armanmaurya.internetradio.data.local.entity.toDomain
import com.armanmaurya.internetradio.data.local.entity.toEntity
import com.armanmaurya.internetradio.data.model.Country
import com.armanmaurya.internetradio.data.model.Language
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.data.model.Tag
import com.armanmaurya.internetradio.data.remote.RadioBrowserApi
import com.armanmaurya.internetradio.data.remote.dto.toDomain
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StationRepository @Inject constructor(
    private val api: RadioBrowserApi,
    @ApplicationContext private val context: Context,
    private val metadataDao: MetadataDao,
    private val settingsRepository: SettingsRepository
) {
    private companion object {
        const val METADATA_TTL_MILLIS = 7 * 24 * 60 * 60 * 1000L // 7 days
    }

    suspend fun filterStations(
        name: String? = null,
        nameExact: Boolean? = null,
        country: String? = null,
        countryExact: Boolean? = null,
        countryCode: String? = null,
        state: String? = null,
        stateExact: Boolean? = null,
        language: String? = null,
        languageExact: Boolean? = null,
        tag: String? = null,
        tagExact: Boolean? = null,
        tagList: String? = null,
        codec: String? = null,
        bitrateMin: Int? = null,
        bitrateMax: Int? = null,
        isHttps: Boolean? = null,
        order: String = "votes",
        reverse: Boolean = true,
        limit: Int = 40,
        offset: Int = 0,
        hideBroken: Boolean = true
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
                isHttps = isHttps,
                order = order,
                reverse = reverse,
                limit = limit,
                offset = offset,
                hideBroken = hideBroken
            ).map { it.toDomain() }
        }

    suspend fun getCountries(): Result<List<Country>> =
        runCatching {
            val lastFetch = settingsRepository.getLastCountryFetchTime()
            val cached = metadataDao.getCountries()
            val isExpired = System.currentTimeMillis() - lastFetch > METADATA_TTL_MILLIS

            if (cached.isNotEmpty() && !isExpired) {
                cached.map { it.toDomain() }
            } else {
                try {
                    val remote = api.getCountries()
                        .map { it.toDomain() }
                        .filter { it.isoCode.isNotBlank() }
                    
                    metadataDao.clearCountries()
                    metadataDao.insertCountries(remote.map { it.toEntity() })
                    settingsRepository.setLastCountryFetchTime(System.currentTimeMillis())
                    remote.sortedByDescending { it.stationCount }
                } catch (e: Exception) {
                    if (cached.isNotEmpty()) {
                        cached.map { it.toDomain() }
                    } else {
                        throw e
                    }
                }
            }
        }

    suspend fun getLanguages(filter: String? = null): Result<List<Language>> =
        runCatching {
            if (filter.isNullOrBlank()) {
                val lastFetch = settingsRepository.getLastLanguageFetchTime()
                val cached = metadataDao.getLanguages()
                val isExpired = System.currentTimeMillis() - lastFetch > METADATA_TTL_MILLIS

                if (cached.isNotEmpty() && !isExpired) {
                    cached.map { it.toDomain() }
                } else {
                    try {
                        val remote = api.getLanguages(order = "stationcount", reverse = true)
                            .map { it.toDomain() }
                        
                        metadataDao.clearLanguages()
                        metadataDao.insertLanguages(remote.map { it.toEntity() })
                        settingsRepository.setLastLanguageFetchTime(System.currentTimeMillis())
                        remote
                    } catch (e: Exception) {
                        if (cached.isNotEmpty()) {
                            cached.map { it.toDomain() }
                        } else {
                            throw e
                        }
                    }
                }
            } else {
                api.getLanguagesFiltered(filter = filter, order = "stationcount", reverse = true)
                    .map { it.toDomain() }
            }
        }

    suspend fun getTags(filter: String? = null): Result<List<Tag>> =
        runCatching {
            if (filter.isNullOrBlank()) {
                val lastFetch = settingsRepository.getLastTagFetchTime()
                val cached = metadataDao.getTags()
                val isExpired = System.currentTimeMillis() - lastFetch > METADATA_TTL_MILLIS

                if (cached.isNotEmpty() && !isExpired) {
                    cached.map { it.toDomain() }
                } else {
                    try {
                        val remote = api.getTags(order = "stationcount", reverse = true)
                            .map { it.toDomain() }
                        
                        metadataDao.clearTags()
                        metadataDao.insertTags(remote.map { it.toEntity() })
                        settingsRepository.setLastTagFetchTime(System.currentTimeMillis())
                        remote
                    } catch (e: Exception) {
                        if (cached.isNotEmpty()) {
                            cached.map { it.toDomain() }
                        } else {
                            throw e
                        }
                    }
                }
            } else {
                api.getTagsFiltered(filter = filter, order = "stationcount", reverse = true)
                    .map { it.toDomain() }
            }
        }

    suspend fun getCurrentCountryCode(): Result<String> =
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
    suspend fun registerClick(stationUuid: String) {
        runCatching { api.clickStation(stationUuid) }
    }
}