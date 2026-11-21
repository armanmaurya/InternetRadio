package com.armanmaurya.internetradio.data.repository

import android.util.Log
import com.armanmaurya.internetradio.core.utils.TrackSanitizer
import com.armanmaurya.internetradio.domain.lyrics.LyricsProvider
import com.armanmaurya.internetradio.domain.lyrics.LyricsQuery
import com.armanmaurya.internetradio.domain.lyrics.LyricsProviderInfo
import com.armanmaurya.internetradio.domain.model.Lyrics
import com.armanmaurya.internetradio.domain.repository.LyricsFetchException
import com.armanmaurya.internetradio.domain.repository.LyricsRepository
import com.armanmaurya.internetradio.domain.repository.LyricsSearchResult
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsRepositoryImpl @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards LyricsProvider>,
    private val settingsRepository: SettingsRepository? = null
) : LyricsRepository {

    private sealed interface CacheEntry {
        data class Found(val lyrics: Lyrics, val allCandidates: List<Lyrics> = emptyList()) : CacheEntry
        data class NotFound(val atMs: Long) : CacheEntry
    }

    private data class CandidateResult(
        val index: Int,
        val provider: LyricsProvider,
        val lyrics: Lyrics
    )

    // Songs repeat on radio stations; remember recent answers so they aren't fetched again.
    private val cache = object : LinkedHashMap<String, CacheEntry>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?) = size > CACHE_SIZE
    }

    override suspend fun getLyricsForTrack(trackName: String, artistName: String?): Lyrics? {
        return getLyricsSearchResult(trackName, artistName).selectedLyrics
    }

    override suspend fun getLyricsSearchResult(trackName: String, artistName: String?): LyricsSearchResult {
        val cleanedTrack = TrackSanitizer.sanitizeTrackInfo(trackName)
        if (cleanedTrack.isBlank()) return LyricsSearchResult(null, emptyList())
        val cleanedArtist = artistName?.let { TrackSanitizer.sanitizeTrackInfo(it) }
        val key = "${cleanedTrack.lowercase()}|${cleanedArtist?.lowercase().orEmpty()}"

        cachedLookup(key)?.let { entry ->
            val cached = (entry as? CacheEntry.Found)
            if (cached != null) {
                Log.d(TAG, "Cache HIT for '$key' -> [${cached.lyrics.providerInfo.displayName}] (${cached.allCandidates.size} candidates)")
                return LyricsSearchResult(cached.lyrics, cached.allCandidates)
            }
        }

        Log.d(TAG, "Searching lyrics for '$cleanedTrack' (artist='${cleanedArtist.orEmpty()}')")

        val query = LyricsQuery(trackTitle = cleanedTrack, artistName = cleanedArtist)

        val appPrefs = settingsRepository?.appPreferencesFlow?.firstOrNull()
        val enabledSet = appPrefs?.enabledLyricsProviders
        val customOrder = appPrefs?.lyricsProviderOrder

        val sortedProviders = providers
            .filter { provider -> enabledSet == null || provider.info.id in enabledSet }
            .sortedWith(compareBy(
                { provider ->
                    val idx = customOrder?.indexOf(provider.info.id) ?: -1
                    if (idx >= 0) idx else Int.MAX_VALUE
                },
                { it.priority }
            ))

        Log.d(TAG, "Active providers (parallel query): ${sortedProviders.map { it.info.displayName }}")

        val candidates = coroutineScope {
            sortedProviders.mapIndexed { index, provider ->
                async {
                    try {
                        Log.d(TAG, "Querying provider [${provider.info.displayName}]...")
                        val result = withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                            provider.searchLyrics(query)
                        }
                        if (result != null) {
                            Log.d(TAG, "Provider [${provider.info.displayName}] returned (hasWordSync=${result.hasWordSync}, syncedLines=${result.syncedLyrics?.size ?: 0}, isPlain=${!result.plainLyrics.isNullOrBlank()})")
                            CandidateResult(index, provider, result)
                        } else {
                            Log.d(TAG, "Provider [${provider.info.displayName}] had no lyrics or timed out.")
                            null
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: LyricsFetchException) {
                        Log.w(TAG, "Provider [${provider.info.displayName}] failed: ${e.message}")
                        null
                    } catch (e: Exception) {
                        Log.w(TAG, "Provider [${provider.info.displayName}] error: ${e.message}")
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }

        // Compare all responses:
        // Tier 0: Word-Synced lyrics (highest quality)
        // Tier 1: Line-Synced lyrics
        // Tier 2: Plain lyrics
        // Tie-breaker within each tier: User priority order (lowest index)
        val bestCandidate = candidates
            .filter { it.lyrics.hasWordSync || !it.lyrics.syncedLyrics.isNullOrEmpty() || !it.lyrics.plainLyrics.isNullOrBlank() }
            .minWithOrNull(
                compareBy(
                    { candidate ->
                        when {
                            candidate.lyrics.hasWordSync -> 0
                            !candidate.lyrics.syncedLyrics.isNullOrEmpty() -> 1
                            else -> 2
                        }
                    },
                    { candidate -> candidate.index }
                )
            )

        val allCandidatesList = candidates
            .map { it.lyrics }
            .filter { it.hasWordSync || !it.syncedLyrics.isNullOrEmpty() || !it.plainLyrics.isNullOrBlank() }

        val finalLyrics = bestCandidate?.lyrics
        if (finalLyrics != null) {
            val syncType = when {
                finalLyrics.hasWordSync -> "word-synced"
                !finalLyrics.syncedLyrics.isNullOrEmpty() -> "line-synced"
                else -> "plain"
            }
            Log.i(TAG, "SUCCESS: Selected best $syncType lyrics from [${finalLyrics.providerInfo.displayName}]. Locator: ${finalLyrics.locator}")
        } else {
            Log.w(TAG, "No lyrics found from any provider for '$cleanedTrack'")
        }

        synchronized(cache) {
            cache[key] = if (finalLyrics != null) CacheEntry.Found(finalLyrics, allCandidatesList) else CacheEntry.NotFound(System.currentTimeMillis())
        }
        return LyricsSearchResult(finalLyrics, allCandidatesList)
    }

    override suspend fun getLyricsFromProvider(providerId: String, trackName: String, artistName: String?): Lyrics? {
        val cleanedTrack = TrackSanitizer.sanitizeTrackInfo(trackName)
        if (cleanedTrack.isBlank()) return null
        val cleanedArtist = artistName?.let { TrackSanitizer.sanitizeTrackInfo(it) }
        val provider = providers.firstOrNull { it.info.id.equals(providerId, ignoreCase = true) } ?: return null

        val query = LyricsQuery(
            trackTitle = cleanedTrack,
            artistName = cleanedArtist?.takeIf { it.isNotBlank() }
        )
        return try {
            withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                provider.searchLyrics(query)
            }
        } catch (e: Exception) {
            Log.w(TAG, "getLyricsFromProvider error for '$providerId': ${e.message}")
            null
        }
    }

    override fun getAllProviders(): List<LyricsProviderInfo> {
        return providers.map { it.info }
    }

    override suspend fun getLyricsByLocator(locator: String): Lyrics? {
        Log.d(TAG, "Fetching lyrics by locator: '$locator'")
        val colonIndex = locator.indexOf(':')
        if (colonIndex <= 0) {
            Log.w(TAG, "Invalid locator format: '$locator'")
            return null
        }
        val providerId = locator.substring(0, colonIndex)
        val token = locator.substring(colonIndex + 1)

        val provider = providers.firstOrNull { it.info.id.equals(providerId, ignoreCase = true) }
        if (provider == null) {
            Log.w(TAG, "Provider '$providerId' not found for locator '$locator'")
            return null
        }

        return try {
            val result = provider.fetchByLocator(token)
            if (result != null) {
                Log.i(TAG, "Successfully resolved locator '$locator' via [${provider.info.displayName}]")
            } else {
                Log.w(TAG, "Provider [${provider.info.displayName}] could not resolve token '$token'")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving locator '$locator': ${e.message}", e)
            null
        }
    }

    private fun cachedLookup(key: String): CacheEntry? = synchronized(cache) {
        when (val entry = cache[key]) {
            is CacheEntry.NotFound ->
                if (System.currentTimeMillis() - entry.atMs > NOT_FOUND_TTL_MS) {
                    cache.remove(key)
                    null
                } else {
                    entry
                }
            else -> entry
        }
    }

    companion object {
        private const val TAG = "LyricsEngine"
        private const val CACHE_SIZE = 100
        private const val NOT_FOUND_TTL_MS = 30 * 60 * 1000L
        private const val SEARCH_TIMEOUT_MS = 10_000L
    }
}
