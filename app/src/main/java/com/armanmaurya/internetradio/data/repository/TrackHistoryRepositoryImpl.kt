package com.armanmaurya.internetradio.data.repository

import com.armanmaurya.internetradio.data.local.dao.TrackHistoryDao
import com.armanmaurya.internetradio.data.local.dao.StarredTrackDao
import com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity
import com.armanmaurya.internetradio.data.local.entity.StarredTrackEntity
import com.armanmaurya.internetradio.data.local.entity.toTrackHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import com.armanmaurya.internetradio.domain.repository.TrackHistoryRepository
import com.armanmaurya.internetradio.domain.repository.SettingsRepository

@Singleton
class TrackHistoryRepositoryImpl @Inject constructor(
    private val trackHistoryDao: TrackHistoryDao,
    private val starredTrackDao: StarredTrackDao,
    private val settingsRepository: SettingsRepository
) : TrackHistoryRepository {
    override suspend fun logTrack(stationUuid: String, trackTitle: String, rawTrackTitle: String): Long? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val latestTrack = trackHistoryDao.getLatestTrackForStation(stationUuid)

        // Use rawTrackTitle for duplicate detection to avoid inserting duplicates
        // when the stream emits the raw title but the DB was updated to the cleaned title.
        val latestRawTitle = if (latestTrack?.rawTrackTitle?.isNotBlank() == true) {
            latestTrack.rawTrackTitle
        } else {
            latestTrack?.trackTitle // Fallback for old rows before migration
        }

        val latestEndTime = if (latestTrack != null) {
            maxOf(latestTrack.timestamp, latestTrack.endTimestamp)
        } else {
            0L
        }
        val idleGapMs = if (latestTrack != null) (now - latestEndTime).coerceAtLeast(0L) else Long.MAX_VALUE

        if (latestRawTitle != rawTrackTitle || idleGapMs >= com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.history.HistorySessionGrouping.DEFAULT_GAP_MS) {
            val newTrack = TrackHistoryEntity(
                stationUuid = stationUuid,
                trackTitle = trackTitle,
                rawTrackTitle = rawTrackTitle,
                timestamp = now,
                endTimestamp = now,
                durationMs = 0L
            )
            val id = trackHistoryDao.insert(newTrack)

            // Cleanup old tracks based on the user's limit setting
            val limit = settingsRepository.appPreferencesFlow.first().trackHistoryLimit
            trackHistoryDao.cleanupOldTracksForStation(stationUuid, keepCount = limit)
            id
        } else {
            latestTrack?.id
        }
    }

    override suspend fun updateTrackMetadata(id: Long, newTrackTitle: String, coverArtUrl: String?) = withContext(Dispatchers.IO) {
        val track = trackHistoryDao.getTrackById(id)
        if (track != null) {
            // A star saved under the old title follows the track to its new title
            val oldStar = starredTrackDao.findStarredTrack(track.stationUuid, track.trackTitle)
            if (oldStar != null) {
                if (normalize(oldStar.trackTitle) == normalize(newTrackTitle)) {
                    if (coverArtUrl != null) starredTrackDao.update(oldStar.copy(coverArtUrl = coverArtUrl))
                } else if (starredTrackDao.findStarredTrack(track.stationUuid, newTrackTitle) != null) {
                    // The new title is already starred; drop the old row instead of duplicating it
                    starredTrackDao.deleteStarredTrackById(oldStar.id)
                } else {
                    starredTrackDao.update(
                        oldStar.copy(
                            trackTitle = newTrackTitle,
                            coverArtUrl = coverArtUrl ?: oldStar.coverArtUrl
                        )
                    )
                }
            }
        }
        trackHistoryDao.updateTrackMetadata(id, newTrackTitle, coverArtUrl)
    }

    override suspend fun updateLyricsLocator(id: Long, lyricsLocator: String?) = withContext(Dispatchers.IO) {
        trackHistoryDao.updateLyricsLocator(id, lyricsLocator)
        val track = trackHistoryDao.getTrackById(id)
        if (track != null) {
            starredTrackDao.updateLyricsLocator(track.stationUuid, track.trackTitle, lyricsLocator)
        }
    }

    override suspend fun updateLyricsLocatorByTitle(stationUuid: String, trackTitle: String, lyricsLocator: String?) = withContext(Dispatchers.IO) {
        trackHistoryDao.updateLyricsLocatorByTitle(stationUuid, trackTitle, lyricsLocator)
        starredTrackDao.updateLyricsLocator(stationUuid, trackTitle, lyricsLocator)
    }

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    override fun getTrackHistory(stationUuid: String, limit: Int): Flow<List<TrackHistoryEntity>> {
        return settingsRepository.appPreferencesFlow
            .map { minOf(limit, it.trackHistoryLimit) }
            .distinctUntilChanged()
            .flatMapLatest { effectiveLimit ->
                combine(
                    trackHistoryDao.getTrackHistoryForStation(stationUuid, limit = effectiveLimit),
                    starredTrackDao.getStarredTracksForStation(stationUuid)
                ) { history, starred -> markStarred(history, starred) }
            }
    }

    override suspend fun updateCoverArt(stationUuid: String, trackTitle: String, coverArtUrl: String) = withContext(Dispatchers.IO) {
        trackHistoryDao.updateCoverArt(stationUuid, trackTitle, coverArtUrl)
        starredTrackDao.updateCoverArt(stationUuid, trackTitle, coverArtUrl)
    }

    override fun getStarredTracks(stationUuid: String): Flow<List<TrackHistoryEntity>> {
        return starredTrackDao.getStarredTracksForStation(stationUuid)
            .map { list -> list.map { it.toTrackHistoryEntity() } }
    }

    override suspend fun toggleTrackStarred(stationUuid: String, trackTitle: String, isStarred: Boolean) = withContext(Dispatchers.IO) {
        val latest = trackHistoryDao.getAllTracksForStationSync(stationUuid)
            .lastOrNull { normalize(it.trackTitle) == normalize(trackTitle) }
        val rawTrackTitle = latest?.rawTrackTitle.orEmpty()
        if (isStarred) {
            if (starredTrackDao.findStarredTrack(stationUuid, trackTitle, rawTrackTitle) == null) {
                starredTrackDao.insert(
                    StarredTrackEntity(
                        stationUuid = stationUuid,
                        trackTitle = trackTitle,
                        rawTrackTitle = rawTrackTitle.ifBlank { trackTitle },
                        artist = latest?.artist,
                        coverArtUrl = latest?.coverArtUrl,
                        starredAt = System.currentTimeMillis()
                    )
                )
            }
        } else {
            // Raw title too, so a row shown as starred through its raw title can be unstarred
            starredTrackDao.deleteStarredTrack(stationUuid, trackTitle, rawTrackTitle)
        }
    }

    override suspend fun setTrackStarredById(id: Long, isStarred: Boolean) = withContext(Dispatchers.IO) {
        val track = trackHistoryDao.getTrackById(id) ?: return@withContext
        toggleTrackStarred(track.stationUuid, track.trackTitle, isStarred)
    }

    override suspend fun isTrackStarred(stationUuid: String, trackTitle: String): Boolean = withContext(Dispatchers.IO) {
        starredTrackDao.isTrackStarred(stationUuid, trackTitle)
    }

    override suspend fun recordTrackListeningProgress(id: Long, endTimestamp: Long, addedDurationMs: Long) = withContext(Dispatchers.IO) {
        trackHistoryDao.recordTrackListeningProgress(id, endTimestamp, addedDurationMs)
    }

    override suspend fun getAllTracksForStation(stationUuid: String): List<TrackHistoryEntity> = withContext(Dispatchers.IO) {
        trackHistoryDao.getAllTracksForStationSync(stationUuid)
    }

    override suspend fun getAllStarredTracksForStation(stationUuid: String): List<StarredTrackEntity> = withContext(Dispatchers.IO) {
        starredTrackDao.getStarredTracksForStationSync(stationUuid)
    }

    override suspend fun importTracks(stationUuid: String, tracks: List<TrackHistoryEntity>) = withContext(Dispatchers.IO) {
        if (tracks.isEmpty()) return@withContext

        val existingByTimestamp = trackHistoryDao.getAllTracksForStationSync(stationUuid).associateBy { it.timestamp }
        val newTracksToInsert = mutableListOf<TrackHistoryEntity>()

        for (imported in tracks) {
            val existing = existingByTimestamp[imported.timestamp]
            if (existing == null) {
                newTracksToInsert.add(imported.copy(id = 0, stationUuid = stationUuid))
            } else if ((existing.coverArtUrl.isNullOrBlank() && !imported.coverArtUrl.isNullOrBlank()) ||
                (existing.artist.isNullOrBlank() && !imported.artist.isNullOrBlank())) {
                // A play that is already here stays as it is; only fill in what it is missing
                trackHistoryDao.updateTrack(
                    existing.copy(
                        coverArtUrl = existing.coverArtUrl ?: imported.coverArtUrl,
                        artist = existing.artist ?: imported.artist
                    )
                )
            }
        }

        if (newTracksToInsert.isNotEmpty()) {
            trackHistoryDao.insertAll(newTracksToInsert)
        }

        // Keep only the newest plays within the user's history limit, as logTrack does
        val limit = settingsRepository.appPreferencesFlow.first().trackHistoryLimit
        trackHistoryDao.cleanupOldTracksForStation(stationUuid, keepCount = limit)
    }

    override suspend fun importStarredTracks(stationUuid: String, tracks: List<StarredTrackEntity>) = withContext(Dispatchers.IO) {
        // Stars from a backup are always added to the local ones, whatever the conflict strategy
        for (imported in tracks) {
            val existing = starredTrackDao.findStarredTrack(stationUuid, imported.trackTitle, imported.rawTrackTitle)
            if (existing == null) {
                starredTrackDao.insert(imported.copy(id = 0, stationUuid = stationUuid))
            } else if ((existing.coverArtUrl == null && imported.coverArtUrl != null) ||
                (existing.artist == null && imported.artist != null)) {
                starredTrackDao.update(
                    existing.copy(
                        coverArtUrl = existing.coverArtUrl ?: imported.coverArtUrl,
                        artist = existing.artist ?: imported.artist
                    )
                )
            }
        }
    }

    private companion object {
        fun normalize(title: String): String = title.trim().lowercase(Locale.ROOT)

        /**
         * Sets [TrackHistoryEntity.isStarred] on each history row, using the same rule as
         * StarredTrackDao.isTrackStarred: the clean titles match, or the raw stream titles match.
         */
        fun markStarred(history: List<TrackHistoryEntity>, starred: List<StarredTrackEntity>): List<TrackHistoryEntity> {
            if (starred.isEmpty()) return history
            val titles = starred.mapTo(HashSet()) { normalize(it.trackTitle) }
            val rawTitles = starred.mapNotNullTo(HashSet()) { s -> normalize(s.rawTrackTitle).takeIf { it.isNotEmpty() } }
            return history.map { track ->
                val raw = normalize(track.rawTrackTitle)
                val isStarred = normalize(track.trackTitle) in titles || (raw.isNotEmpty() && raw in rawTitles)
                if (track.isStarred == isStarred) track else track.copy(isStarred = isStarred)
            }
        }
    }

    override suspend fun getTrackTimestamps(stationUuid: String): List<Long> = withContext(Dispatchers.IO) {
        trackHistoryDao.getTimestampsForStation(stationUuid)
    }

    override suspend fun countTracksPerStation(): Map<String, Int> = withContext(Dispatchers.IO) {
        trackHistoryDao.countPerStation().associate { it.stationUuid to it.count }
    }

    override suspend fun countStarredTracksPerStation(): Map<String, Int> = withContext(Dispatchers.IO) {
        starredTrackDao.countPerStation().associate { it.stationUuid to it.count }
    }
}
