package com.armanmaurya.internetradio.data.repository

import com.armanmaurya.internetradio.core.utils.TrackSanitizer
import com.armanmaurya.internetradio.data.remote.LrcLibApi
import com.armanmaurya.internetradio.data.remote.LyricsfileParser
import com.armanmaurya.internetradio.data.remote.dto.LrcLibResponse
import com.armanmaurya.internetradio.domain.model.LrcLine
import com.armanmaurya.internetradio.domain.model.Lyrics
import com.armanmaurya.internetradio.domain.repository.LyricsFetchException
import com.armanmaurya.internetradio.domain.repository.LyricsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class LyricsRepositoryImpl @Inject constructor(
    private val lrcLibApi: LrcLibApi
) : LyricsRepository {

    private sealed interface CacheEntry {
        data class Found(val lyrics: Lyrics) : CacheEntry
        data class NotFound(val atMs: Long) : CacheEntry
    }

    // Songs repeat on radio stations; remember recent answers so they aren't fetched again.
    private val cache = object : LinkedHashMap<String, CacheEntry>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?) = size > CACHE_SIZE
    }

    override suspend fun getLyricsForTrack(trackName: String, artistName: String?): Lyrics? {
        val cleanedTrack = TrackSanitizer.sanitizeTrackInfo(trackName)
        if (cleanedTrack.isBlank()) return null
        val cleanedArtist = artistName?.let { TrackSanitizer.sanitizeTrackInfo(it) }
        val key = "${cleanedTrack.lowercase()}|${cleanedArtist?.lowercase().orEmpty()}"

        cachedLookup(key)?.let { entry ->
            return (entry as? CacheEntry.Found)?.lyrics
        }

        val lyrics = try {
            val responses = withRateLimitRetry {
                if (cleanedArtist != null) {
                    lrcLibApi.searchLyricsExplicit(cleanedTrack, cleanedArtist)
                } else {
                    lrcLibApi.searchLyrics(cleanedTrack)
                }
            }
            pickBestMatch(responses)?.toLyrics()
        } catch (e: CancellationException) {
            throw e
        } catch (e: LyricsFetchException) {
            throw e
        } catch (e: IOException) {
            throw LyricsFetchException("Network error while fetching lyrics", e)
        } catch (e: HttpException) {
            if (e.code() >= 500) throw LyricsFetchException("LRCLIB error ${e.code()}", e)
            null // 4xx other than rate limits: treat as no lyrics
        } catch (e: Exception) {
            null // Unexpected response shape
        }

        synchronized(cache) {
            cache[key] = if (lyrics != null) CacheEntry.Found(lyrics) else CacheEntry.NotFound(System.currentTimeMillis())
        }
        return lyrics
    }

    private fun cachedLookup(key: String): CacheEntry? = synchronized(cache) {
        when (val entry = cache[key]) {
            is CacheEntry.NotFound ->
                // LRCLIB may add missing lyrics later, so "not found" is only trusted for a while.
                if (System.currentTimeMillis() - entry.atMs > NOT_FOUND_TTL_MS) {
                    cache.remove(key)
                    null
                } else {
                    entry
                }
            else -> entry
        }
    }

    /**
     * Runs [block], waiting and retrying once when LRCLIB answers 429/503 with a short
     * Retry-After (plus jitter, as their docs ask). Longer waits fail fast instead.
     */
    private suspend fun <T> withRateLimitRetry(block: suspend () -> T): T {
        try {
            return block()
        } catch (e: HttpException) {
            if (e.code() != 429 && e.code() != 503) throw e
            val retryAfterSec = e.response()?.headers()?.get("Retry-After")?.trim()?.toLongOrNull() ?: 2L
            if (retryAfterSec > MAX_RETRY_WAIT_SEC) {
                throw LyricsFetchException("Rate limited for ${retryAfterSec}s", e)
            }
            val waitMs = retryAfterSec * 1000
            delay(waitMs + (waitMs * Random.nextDouble(0.0, 0.3)).toLong())
        }
        try {
            return block()
        } catch (e: HttpException) {
            if (e.code() == 429 || e.code() == 503) throw LyricsFetchException("Still rate limited", e)
            throw e
        }
    }

    private fun pickBestMatch(responses: List<LrcLibResponse>): LrcLibResponse? {
        // The first result is the one LRCLIB's search ranks most relevant.
        val topMatch = responses.firstOrNull() ?: return null

        // Other versions of the same track: prefer word-synced, then line-synced, then plain.
        val sameTrack = responses.filter {
            it.trackName.equals(topMatch.trackName, ignoreCase = true) &&
                it.artistName.equals(topMatch.artistName, ignoreCase = true)
        }
        val best = sameTrack.firstOrNull { it.hasWordSync == true && !it.lyricsfile.isNullOrBlank() }
            ?: sameTrack.firstOrNull { !it.syncedLyrics.isNullOrBlank() }
            ?: sameTrack.firstOrNull { !it.plainLyrics.isNullOrBlank() }
            ?: topMatch

        // Instrumental or empty: no lyrics to show.
        if (best.instrumental || (best.syncedLyrics.isNullOrBlank() && best.plainLyrics.isNullOrBlank() &&
                best.hasWordSync != true)
        ) {
            return null
        }
        return best
    }

    private fun LrcLibResponse.toLyrics(): Lyrics? {
        val wordSynced = if (hasWordSync == true) lyricsfile?.let { LyricsfileParser.parseLines(it) } else null
        val synced = wordSynced ?: syncedLyrics?.let { parseLrc(it) }?.takeIf { it.isNotEmpty() }
        if (synced == null && plainLyrics.isNullOrBlank()) return null
        return Lyrics(plainLyrics, synced)
    }

    companion object {
        private const val CACHE_SIZE = 100
        private const val NOT_FOUND_TTL_MS = 30 * 60 * 1000L
        private const val MAX_RETRY_WAIT_SEC = 10L

        private val LRC_LINE = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")

        /** Parses `[mm:ss.xx]` / `[mm:ss.xxx]` LRC lines. */
        internal fun parseLrc(lrcContent: String): List<LrcLine> =
            lrcContent.lines().mapNotNull { line ->
                val match = LRC_LINE.find(line) ?: return@mapNotNull null
                val (min, sec, fraction, text) = match.destructured
                val millis = if (fraction.length == 2) fraction.toLong() * 10 else fraction.toLong()
                LrcLine((min.toLong() * 60 + sec.toLong()) * 1000 + millis, text.trim())
            }
    }
}
