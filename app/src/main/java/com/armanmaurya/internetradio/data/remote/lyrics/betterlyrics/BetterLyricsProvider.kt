package com.armanmaurya.internetradio.data.remote.lyrics.betterlyrics

import android.util.Log
import com.armanmaurya.internetradio.core.utils.TrackSanitizer
import com.armanmaurya.internetradio.data.remote.lyrics.LrcParser
import com.armanmaurya.internetradio.data.remote.lyrics.TtmlParser
import com.armanmaurya.internetradio.domain.lyrics.LyricsProvider
import com.armanmaurya.internetradio.domain.lyrics.LyricsProviderInfo
import com.armanmaurya.internetradio.domain.lyrics.LyricsQuery
import com.armanmaurya.internetradio.domain.model.Lyrics
import com.armanmaurya.internetradio.domain.repository.LyricsFetchException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BetterLyricsProvider @Inject constructor(
    private val betterLyricsApi: BetterLyricsApi
) : LyricsProvider {

    override val info: LyricsProviderInfo = LyricsProviderInfo(
        id = ID,
        displayName = "Better Lyrics",
        websiteUrl = "https://betterlyrics.org"
    )

    override val priority: Int = 100
    override val supportsSyncedLyrics: Boolean = true

    override suspend fun searchLyrics(query: LyricsQuery): Lyrics? {
        val cleanedTrack = TrackSanitizer.sanitizeTrackInfo(query.trackTitle)
        if (cleanedTrack.isBlank()) return null
        val cleanedArtist = query.artistName?.let { TrackSanitizer.sanitizeTrackInfo(it) }

        Log.d("LyricsEngine", "[Better Lyrics] Searching for song='$cleanedTrack', artist='$cleanedArtist'")

        return try {
            val response = betterLyricsApi.getLyrics(song = cleanedTrack, artist = cleanedArtist)
            val result = response.data?.toLyrics(cleanedTrack, cleanedArtist)
            if (result != null) {
                Log.i("LyricsEngine", "[Better Lyrics] Found lyrics (syncedLines=${result.syncedLyrics?.size ?: 0})")
            } else {
                Log.d("LyricsEngine", "[Better Lyrics] No lyrics in response data")
            }
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w("LyricsEngine", "[Better Lyrics] Network error: ${e.message}")
            throw LyricsFetchException("Network error while fetching lyrics from Better Lyrics", e)
        } catch (e: HttpException) {
            Log.w("LyricsEngine", "[Better Lyrics] HTTP error ${e.code()}: ${e.message()}")
            if (e.code() >= 500) throw LyricsFetchException("Better Lyrics error ${e.code()}", e)
            null
        } catch (e: Exception) {
            Log.w("LyricsEngine", "[Better Lyrics] Failed: ${e.message}")
            null
        }
    }

    override suspend fun fetchByLocator(token: String): Lyrics? {
        val parts = token.split("|", limit = 2)
        val track = parts.getOrNull(0) ?: return null
        val artist = parts.getOrNull(1)?.takeIf { it.isNotBlank() }

        return try {
            val response = betterLyricsApi.getLyrics(song = track, artist = artist)
            response.data?.toLyrics(track, artist)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw LyricsFetchException("Network error while fetching lyrics from Better Lyrics", e)
        } catch (e: HttpException) {
            if (e.code() >= 500) throw LyricsFetchException("Better Lyrics error ${e.code()}", e)
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun BetterLyricsData.toLyrics(queryTrack: String, queryArtist: String?): Lyrics? {
        val rawLyrics = lyrics?.trim() ?: return null
        if (rawLyrics.isBlank()) return null

        val isTtml = format.equals("ttml", ignoreCase = true) || rawLyrics.startsWith("<")
        val isLrc = format.equals("lrc", ignoreCase = true) || rawLyrics.startsWith("[")

        val parsedLines = when {
            isTtml -> TtmlParser.parse(rawLyrics).takeIf { it.isNotEmpty() }
            isLrc -> LrcParser.parseLrc(rawLyrics).takeIf { it.isNotEmpty() }
            else -> null
        }

        val plain = parsedLines?.joinToString("\n") { it.text } ?: rawLyrics
        val token = "$queryTrack|${queryArtist.orEmpty()}"

        return Lyrics(
            plainLyrics = plain,
            syncedLyrics = parsedLines,
            rawLrc = if (parsedLines != null && isLrc) rawLyrics else null,
            providerInfo = info,
            locatorToken = token,
            trackTitle = song ?: queryTrack,
            artistName = artist ?: queryArtist
        )
    }

    companion object {
        const val ID = "betterlyrics"
    }
}
