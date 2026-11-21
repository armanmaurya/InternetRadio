package com.armanmaurya.internetradio.data.remote.lyrics.lyricsplus

import android.util.Log
import com.armanmaurya.internetradio.core.utils.TrackSanitizer
import com.armanmaurya.internetradio.domain.lyrics.LyricsProvider
import com.armanmaurya.internetradio.domain.lyrics.LyricsProviderInfo
import com.armanmaurya.internetradio.domain.lyrics.LyricsQuery
import com.armanmaurya.internetradio.domain.model.LrcLine
import com.armanmaurya.internetradio.domain.model.LrcWord
import com.armanmaurya.internetradio.domain.model.Lyrics
import com.armanmaurya.internetradio.domain.repository.LyricsFetchException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsPlusProvider @Inject constructor(
    private val lyricsPlusApi: LyricsPlusApi
) : LyricsProvider {

    override val info: LyricsProviderInfo = LyricsProviderInfo(
        id = ID,
        displayName = "LyricsPlus",
        websiteUrl = "https://github.com/ibratabian17/LyricsPlus"
    )

    override val priority: Int = 300
    override val supportsSyncedLyrics: Boolean = true

    override suspend fun searchLyrics(query: LyricsQuery): Lyrics? {
        val cleanedTrack = TrackSanitizer.sanitizeTrackInfo(query.trackTitle)
        if (cleanedTrack.isBlank()) return null
        val cleanedArtist = query.artistName?.let { TrackSanitizer.sanitizeTrackInfo(it) }

        Log.d("LyricsEngine", "[LyricsPlus] Searching for title='$cleanedTrack', artist='$cleanedArtist'")

        return try {
            val response = lyricsPlusApi.getLyrics(title = cleanedTrack, artist = cleanedArtist)
            val result = response.toLyrics(cleanedTrack, cleanedArtist)
            if (result != null) {
                Log.i("LyricsEngine", "[LyricsPlus] Found lyrics (syncedLines=${result.syncedLyrics?.size ?: 0})")
            } else {
                Log.d("LyricsEngine", "[LyricsPlus] No lyrics in response: ${response.error}")
            }
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w("LyricsEngine", "[LyricsPlus] Network error: ${e.message}")
            throw LyricsFetchException("Network error while fetching lyrics from LyricsPlus", e)
        } catch (e: HttpException) {
            Log.w("LyricsEngine", "[LyricsPlus] HTTP error ${e.code()}: ${e.message()}")
            if (e.code() >= 500) throw LyricsFetchException("LyricsPlus error ${e.code()}", e)
            null
        } catch (e: Exception) {
            Log.w("LyricsEngine", "[LyricsPlus] Failed: ${e.message}")
            null
        }
    }

    override suspend fun fetchByLocator(token: String): Lyrics? {
        val parts = token.split("|", limit = 2)
        val track = parts.getOrNull(0) ?: return null
        val artist = parts.getOrNull(1)?.takeIf { it.isNotBlank() }

        return try {
            val response = lyricsPlusApi.getLyrics(title = track, artist = artist)
            response.toLyrics(track, artist)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw LyricsFetchException("Network error while fetching lyrics from LyricsPlus", e)
        } catch (e: HttpException) {
            if (e.code() >= 500) throw LyricsFetchException("LyricsPlus error ${e.code()}", e)
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun LyricsPlusResponse.toLyrics(queryTrack: String, queryArtist: String?): Lyrics? {
        val rawLines = lyrics ?: return null
        if (rawLines.isEmpty()) return null

        val parsedLines = rawLines.mapNotNull { line ->
            val time = line.time ?: return@mapNotNull null
            val text = line.text?.trim() ?: ""
            val words = line.syllabus?.mapNotNull { syl ->
                val sTime = syl.time ?: return@mapNotNull null
                val sText = syl.text ?: ""
                val sDuration = syl.duration ?: 0L
                LrcWord(
                    startMs = sTime,
                    endMs = if (sDuration > 0) sTime + sDuration else null,
                    text = sText
                )
            }?.takeIf { it.isNotEmpty() }

            LrcLine(
                timestampMs = time,
                text = text,
                endMs = line.duration?.let { if (it > 0) time + it else null },
                words = words
            )
        }.sortedBy { it.timestampMs }

        if (parsedLines.isEmpty()) return null

        val token = "$queryTrack|${queryArtist.orEmpty()}"

        return Lyrics(
            plainLyrics = parsedLines.joinToString("\n") { it.text },
            syncedLyrics = parsedLines,
            rawLrc = null,
            providerInfo = info,
            locatorToken = token,
            trackTitle = queryTrack,
            artistName = queryArtist
        )
    }

    companion object {
        const val ID = "lyricsplus"
    }
}
