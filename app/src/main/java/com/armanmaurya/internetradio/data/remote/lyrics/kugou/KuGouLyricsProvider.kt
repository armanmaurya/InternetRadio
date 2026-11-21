package com.armanmaurya.internetradio.data.remote.lyrics.kugou

import android.util.Log
import com.armanmaurya.internetradio.core.utils.TrackSanitizer
import com.armanmaurya.internetradio.data.remote.lyrics.LrcParser
import com.armanmaurya.internetradio.domain.lyrics.LyricsProvider
import com.armanmaurya.internetradio.domain.lyrics.LyricsProviderInfo
import com.armanmaurya.internetradio.domain.lyrics.LyricsQuery
import com.armanmaurya.internetradio.domain.model.Lyrics
import com.armanmaurya.internetradio.domain.repository.LyricsFetchException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KuGouLyricsProvider @Inject constructor(
    private val kuGouApi: KuGouApi
) : LyricsProvider {

    override val info: LyricsProviderInfo = LyricsProviderInfo(
        id = ID,
        displayName = "KuGou",
        websiteUrl = "https://kugou.com"
    )

    override val priority: Int = 400
    override val supportsSyncedLyrics: Boolean = true

    override suspend fun searchLyrics(query: LyricsQuery): Lyrics? {
        val cleanedTrack = TrackSanitizer.sanitizeTrackInfo(query.trackTitle)
        if (cleanedTrack.isBlank()) return null
        val cleanedArtist = query.artistName?.let { TrackSanitizer.sanitizeTrackInfo(it) }

        val keyword = if (!cleanedArtist.isNullOrBlank()) "$cleanedArtist - $cleanedTrack" else cleanedTrack
        Log.d("LyricsEngine", "[KuGou] Searching with keyword='$keyword'")

        return try {
            val searchResponse = kuGouApi.search(keyword = keyword)
            val candidate = searchResponse.candidates?.firstOrNull { !it.id.isNullOrBlank() && !it.accessKey.isNullOrBlank() }
            if (candidate == null) {
                Log.d("LyricsEngine", "[KuGou] No matching candidate found for '$keyword'")
                return null
            }

            Log.d("LyricsEngine", "[KuGou] Candidate found: id=${candidate.id}, song=${candidate.song}, singer=${candidate.singer}. Downloading lyrics...")
            downloadAndParse(
                id = candidate.id!!,
                accessKey = candidate.accessKey!!,
                trackTitle = candidate.song ?: cleanedTrack,
                artistName = candidate.singer ?: cleanedArtist
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw LyricsFetchException("Network error while fetching lyrics from KuGou", e)
        } catch (e: HttpException) {
            if (e.code() >= 500) throw LyricsFetchException("KuGou server error ${e.code()}", e)
            null
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun fetchByLocator(token: String): Lyrics? {
        val parts = token.split("|", limit = 2)
        if (parts.size < 2) return null
        val id = parts[0]
        val accessKey = parts[1]

        return try {
            downloadAndParse(id = id, accessKey = accessKey, trackTitle = null, artistName = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw LyricsFetchException("Network error while downloading lyrics from KuGou", e)
        } catch (e: HttpException) {
            if (e.code() >= 500) throw LyricsFetchException("KuGou server error ${e.code()}", e)
            null
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun downloadAndParse(
        id: String,
        accessKey: String,
        trackTitle: String?,
        artistName: String?
    ): Lyrics? {
        val downloadResponse = kuGouApi.download(id = id, accessKey = accessKey)
        val base64Content = downloadResponse.content?.trim() ?: return null
        if (base64Content.isBlank()) return null

        val decodedLrc = try {
            String(Base64.getDecoder().decode(base64Content), Charsets.UTF_8)
        } catch (_: Exception) {
            return null
        }

        val parsedLines = LrcParser.parseLrc(decodedLrc).takeIf { it.isNotEmpty() }
        val plain = parsedLines?.joinToString("\n") { it.text } ?: decodedLrc
        Log.i("LyricsEngine", "[KuGou] Successfully downloaded lyrics for id=$id (syncedLines=${parsedLines?.size ?: 0}, isPlain=${plain != null})")

        return Lyrics(
            plainLyrics = plain,
            syncedLyrics = parsedLines,
            rawLrc = if (parsedLines != null) decodedLrc else null,
            providerInfo = info,
            locatorToken = "$id|$accessKey",
            trackTitle = trackTitle,
            artistName = artistName
        )
    }

    companion object {
        const val ID = "kugou"
    }
}
