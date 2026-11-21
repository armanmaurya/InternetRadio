package com.armanmaurya.internetradio.data.remote.lyrics.lrclib

import android.util.Log
import com.armanmaurya.internetradio.core.utils.TrackSanitizer
import com.armanmaurya.internetradio.data.remote.LrcLibApi
import com.armanmaurya.internetradio.data.remote.LyricsfileParser
import com.armanmaurya.internetradio.data.remote.dto.LrcLibResponse
import com.armanmaurya.internetradio.data.remote.lyrics.LrcParser
import com.armanmaurya.internetradio.domain.lyrics.LyricsProvider
import com.armanmaurya.internetradio.domain.lyrics.LyricsProviderInfo
import com.armanmaurya.internetradio.domain.lyrics.LyricsQuery
import com.armanmaurya.internetradio.domain.model.Lyrics
import com.armanmaurya.internetradio.domain.repository.LyricsFetchException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class LrcLibLyricsProvider @Inject constructor(
    private val lrcLibApi: LrcLibApi
) : LyricsProvider {

    override val info: LyricsProviderInfo = LyricsProviderInfo(
        id = ID,
        displayName = "LRCLIB",
        websiteUrl = "https://lrclib.net/"
    )

    override val priority: Int = 100
    override val supportsSyncedLyrics: Boolean = true

    override suspend fun searchLyrics(query: LyricsQuery): Lyrics? {
        val cleanedTrack = TrackSanitizer.sanitizeTrackInfo(query.trackTitle)
        if (cleanedTrack.isBlank()) return null
        val cleanedArtist = query.artistName?.let { TrackSanitizer.sanitizeTrackInfo(it) }

        Log.d("LyricsEngine", "[LRCLIB] Searching for '$cleanedTrack', artist='$cleanedArtist'")

        return try {
            val responses = withRateLimitRetry {
                var list = if (cleanedArtist != null) {
                    lrcLibApi.searchLyricsExplicit(cleanedTrack, cleanedArtist)
                } else {
                    lrcLibApi.searchLyrics(cleanedTrack)
                }
                if (list.isEmpty() && cleanedArtist != null) {
                    // Fallback 1: search combining artist and track
                    Log.d("LyricsEngine", "[LRCLIB] Explicit search empty, trying q='$cleanedArtist $cleanedTrack'")
                    list = lrcLibApi.searchLyrics("$cleanedArtist $cleanedTrack")
                }
                if (list.isEmpty()) {
                    // Fallback 2: search by track title alone
                    Log.d("LyricsEngine", "[LRCLIB] Combined search empty, trying q='$cleanedTrack'")
                    list = lrcLibApi.searchLyrics(cleanedTrack)
                }
                list
            }
            val best = pickBestMatch(responses)
            if (best == null) {
                Log.d("LyricsEngine", "[LRCLIB] No suitable match found in ${responses.size} results")
                return null
            }
            Log.i("LyricsEngine", "[LRCLIB] Picked best match: id=${best.id}, track='${best.trackName}', artist='${best.artistName}', hasWordSync=${best.hasWordSync}, synced=${!best.syncedLyrics.isNullOrBlank()}")
            best.toLyrics()
        } catch (e: CancellationException) {
            throw e
        } catch (e: LyricsFetchException) {
            throw e
        } catch (e: IOException) {
            throw LyricsFetchException("Network error while fetching lyrics from LRCLIB", e)
        } catch (e: HttpException) {
            if (e.code() >= 500) throw LyricsFetchException("LRCLIB error ${e.code()}", e)
            null
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun fetchByLocator(token: String): Lyrics? {
        val id = token.toLongOrNull() ?: return null
        Log.d("LyricsEngine", "[LRCLIB] Fetching by id=$id for locator")
        return try {
            val response = withRateLimitRetry {
                lrcLibApi.getLyricsById(id)
            }
            response.toLyrics()
        } catch (e: CancellationException) {
            throw e
        } catch (e: LyricsFetchException) {
            throw e
        } catch (e: IOException) {
            throw LyricsFetchException("Network error while fetching lyrics from LRCLIB", e)
        } catch (e: HttpException) {
            if (e.code() >= 500) throw LyricsFetchException("LRCLIB error ${e.code()}", e)
            null
        } catch (_: Exception) {
            null
        }
    }

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
        if (responses.isEmpty()) return null

        val topMatch = responses.first()
        val sameTrack = responses.filter {
            it.trackName.equals(topMatch.trackName, ignoreCase = true) &&
                it.artistName.equals(topMatch.artistName, ignoreCase = true)
        }

        val bestSame = sameTrack.firstOrNull { it.hasWordSync == true && !it.lyricsfile.isNullOrBlank() }
            ?: sameTrack.firstOrNull { !it.syncedLyrics.isNullOrBlank() }
            ?: sameTrack.firstOrNull { !it.plainLyrics.isNullOrBlank() }

        val best = bestSame
            ?: responses.firstOrNull { it.hasWordSync == true && !it.lyricsfile.isNullOrBlank() }
            ?: responses.firstOrNull { !it.syncedLyrics.isNullOrBlank() }
            ?: responses.firstOrNull { !it.plainLyrics.isNullOrBlank() }
            ?: topMatch

        if (best.instrumental || (best.syncedLyrics.isNullOrBlank() && best.plainLyrics.isNullOrBlank() &&
                best.hasWordSync != true)
        ) {
            return null
        }
        return best
    }

    private fun LrcLibResponse.toLyrics(): Lyrics? {
        val wordSynced = if (hasWordSync == true) lyricsfile?.let { LyricsfileParser.parseLines(it) } else null
        val synced = wordSynced ?: syncedLyrics?.let { LrcParser.parseLrc(it) }?.takeIf { it.isNotEmpty() }
        if (synced == null && plainLyrics.isNullOrBlank()) return null
        return Lyrics(
            plainLyrics = plainLyrics,
            syncedLyrics = synced,
            rawLrc = syncedLyrics,
            providerInfo = info,
            locatorToken = id.toString(),
            trackTitle = trackName,
            artistName = artistName,
            isInstrumental = instrumental
        )
    }

    companion object {
        const val ID = "lrclib"
        private const val MAX_RETRY_WAIT_SEC = 10L
    }
}
