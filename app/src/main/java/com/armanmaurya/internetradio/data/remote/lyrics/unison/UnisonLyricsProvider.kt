package com.armanmaurya.internetradio.data.remote.lyrics.unison

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
class UnisonLyricsProvider @Inject constructor(
    private val unisonApi: UnisonApi
) : LyricsProvider {

    override val info: LyricsProviderInfo = LyricsProviderInfo(
        id = ID,
        displayName = "Unison",
        websiteUrl = "https://unison.betterlyrics.org"
    )

    override val priority: Int = 200
    override val supportsSyncedLyrics: Boolean = true

    override suspend fun searchLyrics(query: LyricsQuery): Lyrics? {
        val cleanedTrack = TrackSanitizer.sanitizeTrackInfo(query.trackTitle)
        if (cleanedTrack.isBlank()) return null
        val cleanedArtist = query.artistName?.let { TrackSanitizer.sanitizeTrackInfo(it) }

        Log.d("LyricsEngine", "[Unison] Searching for song='$cleanedTrack', artist='$cleanedArtist'")

        return try {
            val response = unisonApi.getLyrics(song = cleanedTrack, artist = cleanedArtist)
            if (response.success == true && response.data != null) {
                val lyrics = response.data.toLyrics(cleanedTrack, cleanedArtist)
                Log.i("LyricsEngine", "[Unison] Found lyrics (syncedLines=${lyrics?.syncedLyrics?.size ?: 0})")
                lyrics
            } else {
                Log.d("LyricsEngine", "[Unison] No lyrics in response: ${response.error}")
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w("LyricsEngine", "[Unison] Network error: ${e.message}")
            throw LyricsFetchException("Network error while fetching lyrics from Unison", e)
        } catch (e: HttpException) {
            Log.w("LyricsEngine", "[Unison] HTTP error ${e.code()}: ${e.message()}")
            if (e.code() >= 500) throw LyricsFetchException("Unison error ${e.code()}", e)
            null
        } catch (e: Exception) {
            Log.w("LyricsEngine", "[Unison] Failed: ${e.message}")
            null
        }
    }

    override suspend fun fetchByLocator(token: String): Lyrics? {
        val id = token.toLongOrNull() ?: return null

        return try {
            val response = unisonApi.getLyricsById(id)
            if (response.success == true && response.data != null) {
                response.data.toLyrics(response.data.song.orEmpty(), response.data.artist)
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw LyricsFetchException("Network error while fetching lyrics from Unison", e)
        } catch (e: HttpException) {
            if (e.code() >= 500) throw LyricsFetchException("Unison error ${e.code()}", e)
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun UnisonData.toLyrics(queryTrack: String, queryArtist: String?): Lyrics? {
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
        val token = id?.toString() ?: "$queryTrack|${queryArtist.orEmpty()}"

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
        const val ID = "unison"
    }
}
