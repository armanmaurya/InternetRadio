package com.armanmaurya.internetradio.data.repository

import com.armanmaurya.internetradio.domain.model.LrcLine
import com.armanmaurya.internetradio.domain.model.Lyrics
import com.armanmaurya.internetradio.data.remote.LrcLibApi
import com.armanmaurya.internetradio.core.utils.TrackSanitizer
import javax.inject.Inject
import javax.inject.Singleton
import com.armanmaurya.internetradio.domain.repository.LyricsRepository

@Singleton
class LyricsRepositoryImpl @Inject constructor(
    private val lrcLibApi: LrcLibApi
) : LyricsRepository {
    override suspend fun getLyricsForTrack(trackName: String, artistName: String?): Lyrics? {
        return try {
            suspend fun searchAndFindBestMatch(query: String, explicitArtist: String?): com.armanmaurya.internetradio.data.remote.dto.LrcLibResponse? {
                val responses = if (explicitArtist != null) {
                    lrcLibApi.searchLyricsExplicit(query, explicitArtist)
                } else {
                    lrcLibApi.searchLyrics(query)
                }
                if (responses.isEmpty()) return null
                
                // The first result is deemed the most relevant by LRCLIB's search engine.
                val topMatch = responses.first()
                
                // Find all responses that represent the exact same track (to prefer synced versions if available)
                val sameTrackGroup = responses.filter { 
                    it.trackName.equals(topMatch.trackName, ignoreCase = true) && 
                    it.artistName.equals(topMatch.artistName, ignoreCase = true)
                }
                
                // Prefer synced over plain within the same track group
                val bestVersion = sameTrackGroup.firstOrNull { !it.syncedLyrics.isNullOrBlank() } 
                    ?: sameTrackGroup.firstOrNull { !it.plainLyrics.isNullOrBlank() }
                    ?: topMatch // Fallback to the top match itself (even if it's instrumental)
                    
                // If the most relevant version is instrumental or has no text, we consider lyrics unavailable
                if (bestVersion.instrumental || (bestVersion.syncedLyrics.isNullOrBlank() && bestVersion.plainLyrics.isNullOrBlank())) {
                    return null
                }
                
                return bestVersion
            }

            // 1. Cleaned full query without hyphens or extra punctuation
            val cleanedFull = TrackSanitizer.sanitizeTrackInfo(trackName)
            val cleanedArtist = artistName?.let { TrackSanitizer.sanitizeTrackInfo(it) }
            val bestMatch = if (cleanedFull.isNotBlank()) searchAndFindBestMatch(cleanedFull, cleanedArtist) else null

            if (bestMatch != null) {
                val parsedSyncedLyrics = bestMatch.syncedLyrics?.let { parseLrc(it) }
                Lyrics(bestMatch.plainLyrics, parsedSyncedLyrics)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseLrc(lrcContent: String): List<LrcLine> {
        val lines = lrcContent.lines()
        val result = mutableListOf<LrcLine>()
        // Match [mm:ss.xx] or [mm:ss.xxx]
        val regex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")
        
        for (line in lines) {
            val match = regex.find(line)
            if (match != null) {
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val millisPart = match.groupValues[3]
                val millis = if (millisPart.length == 2) millisPart.toLong() * 10 else millisPart.toLong()
                
                val text = match.groupValues[4].trim()
                val totalMillis = (minutes * 60 + seconds) * 1000 + millis
                result.add(LrcLine(totalMillis, text))
            }
        }
        return result
    }
}
