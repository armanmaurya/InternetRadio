package com.armanmaurya.internetradio.data.repository

import com.armanmaurya.internetradio.data.local.dao.TrackHistoryDao
import com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity
import com.armanmaurya.internetradio.domain.model.AppPreferences
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TrackHistoryRepositoryTest {

    private lateinit var fakeDao: FakeTrackHistoryDao
    private lateinit var fakeSettingsRepository: FakeSettingsRepository
    private lateinit var repository: TrackHistoryRepositoryImpl

    @Before
    fun setup() {
        fakeDao = FakeTrackHistoryDao()
        fakeSettingsRepository = FakeSettingsRepository()
        repository = TrackHistoryRepositoryImpl(fakeDao, fakeSettingsRepository)
    }

    @Test
    fun logTrack_insertsNewTrack_whenRawTitleChanges() = runBlocking {
        val id = repository.logTrack("station-1", "Daft Punk - One More Time", "Daft Punk - One More Time")
        assertNotNull(id)
        assertEquals(1, fakeDao.tracks.size)
        assertEquals("Daft Punk - One More Time", fakeDao.tracks.first().trackTitle)
    }

    @Test
    fun logTrack_ignoresDuplicate_whenConsecutiveRawTitleIsSame() = runBlocking {
        val id1 = repository.logTrack("station-1", "Song A", "Raw Song A")
        val id2 = repository.logTrack("station-1", "Song A", "Raw Song A")
        assertEquals(id1, id2)
        assertEquals(1, fakeDao.tracks.size)
    }

    @Test
    fun logTrack_inheritsStarredState_onlyOnSameStation() = runBlocking {
        // First track is played and starred on station-1
        val id1 = repository.logTrack("station-1", "Song A", "Raw Song A")
        repository.toggleTrackStarred("station-1", "Song A", true)
        assertTrue(fakeDao.tracks.first().isStarred)

        // Same song plays on station-2 -> should NOT be starred (station-specific)
        val id2 = repository.logTrack("station-2", "Song A", "Raw Song A")
        assertNotNull(id2)
        val station2Track = fakeDao.tracks.first { it.stationUuid == "station-2" }
        assertFalse(station2Track.isStarred)

        // Song B plays on station-1, then Song A plays again on station-1 -> inherits starred state
        repository.logTrack("station-1", "Song B", "Raw Song B")
        val id3 = repository.logTrack("station-1", "Song A", "Raw Song A")
        val latestStation1Track = fakeDao.tracks.first { it.id == id3 }
        assertTrue(latestStation1Track.isStarred)
    }

    @Test
    fun toggleTrackStarred_updatesOnlyMatchingStationTracks() = runBlocking {
        fakeDao.insert(TrackHistoryEntity(id = 1, stationUuid = "s1", trackTitle = "Song A", timestamp = 100))
        fakeDao.insert(TrackHistoryEntity(id = 2, stationUuid = "s2", trackTitle = "Song A", timestamp = 200))

        repository.toggleTrackStarred("s1", "Song A", true)

        assertTrue(fakeDao.tracks.first { it.stationUuid == "s1" }.isStarred)
        assertFalse(fakeDao.tracks.first { it.stationUuid == "s2" }.isStarred)

        repository.toggleTrackStarred("s1", "Song A", false)
        assertTrue(fakeDao.tracks.none { it.isStarred })
    }

    @Test
    fun updateTrackMetadata_marksStarred_ifCleanedTitleWasAlreadyStarredOnSameStation() = runBlocking {
        // Pre-star cleaned title on s1
        fakeDao.insert(TrackHistoryEntity(id = 1, stationUuid = "s1", trackTitle = "Clean Title", timestamp = 100, isStarred = true))

        // New raw track is logged on s1
        val id = repository.logTrack("s1", "Raw Title", "Raw Title")!!
        assertFalse(fakeDao.tracks.first { it.id == id }.isStarred)

        // Metadata arrives resolving to the starred title on s1
        repository.updateTrackMetadata(id, "Clean Title", "http://cover.jpg")
        assertTrue(fakeDao.tracks.first { it.id == id }.isStarred)
    }

    @Test
    fun logTrack_insertsNewRow_whenSameRawTitleAppearsAfterIdleGapThreshold() = runBlocking {
        val twentyMinsAgo = System.currentTimeMillis() - 20 * 60 * 1000L
        fakeDao.insert(
            TrackHistoryEntity(
                id = 1,
                stationUuid = "station-1",
                trackTitle = "Static Show",
                rawTrackTitle = "Static Show",
                timestamp = twentyMinsAgo,
                endTimestamp = twentyMinsAgo
            )
        )

        val id2 = repository.logTrack("station-1", "Static Show", "Static Show")
        assertNotNull(id2)
        assertEquals(2, fakeDao.tracks.size)
    }

    @Test
    fun recordTrackListeningProgress_updatesEndTimestampAndAccumulatesDuration() = runBlocking {
        val id = repository.logTrack("station-1", "Song A", "Song A")!!
        val newEnd = System.currentTimeMillis() + 15_000L

        repository.recordTrackListeningProgress(id, newEnd, 15_000L)
        repository.recordTrackListeningProgress(id, newEnd + 15_000L, 15_000L)

        val updated = fakeDao.tracks.first { it.id == id }
        assertEquals(newEnd + 15_000L, updated.endTimestamp)
        assertEquals(30_000L, updated.durationMs)
    }

    // --- Test Doubles ---

    private class FakeTrackHistoryDao : TrackHistoryDao {
        val tracks = mutableListOf<TrackHistoryEntity>()
        private var idCounter = 1L

        override suspend fun insert(trackHistory: TrackHistoryEntity): Long {
            val assignedId = if (trackHistory.id != 0L) trackHistory.id else idCounter++
            idCounter = maxOf(idCounter, assignedId + 1)
            val stored = trackHistory.copy(id = assignedId)
            tracks.add(stored)
            return assignedId
        }

        override fun getTrackHistoryForStation(stationUuid: String, limit: Int): Flow<List<TrackHistoryEntity>> {
            return flowOf(tracks.filter { it.stationUuid == stationUuid }.takeLast(limit))
        }

        override suspend fun cleanupOldTracksForStation(stationUuid: String, keepCount: Int) {
            // Keep all starred tracks and recent non-starred tracks up to keepCount
            val stationTracks = tracks.filter { it.stationUuid == stationUuid }
            val nonStarred = stationTracks.filter { !it.isStarred }
            if (nonStarred.size > keepCount) {
                val toRemove = nonStarred.take(nonStarred.size - keepCount)
                tracks.removeAll(toRemove)
            }
        }

        override suspend fun getLatestTrackForStation(stationUuid: String): TrackHistoryEntity? {
            return tracks.filter { it.stationUuid == stationUuid }.maxByOrNull { it.timestamp }
        }

        override suspend fun updateCoverArt(stationUuid: String, trackTitle: String, coverArtUrl: String) {
            val index = tracks.indexOfFirst { it.stationUuid == stationUuid && it.trackTitle == trackTitle }
            if (index != -1) {
                tracks[index] = tracks[index].copy(coverArtUrl = coverArtUrl)
            }
        }

        override suspend fun updateTrackMetadata(id: Long, newTrackTitle: String, coverArtUrl: String?) {
            val index = tracks.indexOfFirst { it.id == id }
            if (index != -1) {
                tracks[index] = tracks[index].copy(trackTitle = newTrackTitle, coverArtUrl = coverArtUrl)
            }
        }

        override fun getStarredTracksForStation(stationUuid: String): Flow<List<TrackHistoryEntity>> {
            return flowOf(tracks.filter { it.stationUuid == stationUuid && it.isStarred })
        }

        override suspend fun isTrackStarred(stationUuid: String, trackTitle: String, rawTrackTitle: String): Boolean {
            return tracks.any {
                it.stationUuid == stationUuid && it.isStarred && (
                    it.trackTitle.trim().equals(trackTitle.trim(), ignoreCase = true) ||
                    (rawTrackTitle.isNotBlank() && it.rawTrackTitle.isNotBlank() && it.rawTrackTitle.trim().equals(rawTrackTitle.trim(), ignoreCase = true))
                )
            }
        }

        override suspend fun isTrackStarredForTrackIdStation(id: Long, trackTitle: String): Boolean {
            val targetStationUuid = tracks.firstOrNull { it.id == id }?.stationUuid ?: return false
            return isTrackStarred(targetStationUuid, trackTitle, trackTitle)
        }

        override suspend fun setTrackStarred(stationUuid: String, trackTitle: String, isStarred: Boolean) {
            for (i in tracks.indices) {
                if (tracks[i].stationUuid == stationUuid && tracks[i].trackTitle.trim().equals(trackTitle.trim(), ignoreCase = true)) {
                    tracks[i] = tracks[i].copy(isStarred = isStarred)
                }
            }
        }

        override suspend fun setTrackStarredById(id: Long, isStarred: Boolean) {
            val index = tracks.indexOfFirst { it.id == id }
            if (index != -1) {
                tracks[index] = tracks[index].copy(isStarred = isStarred)
            }
        }

        override suspend fun recordTrackListeningProgress(id: Long, endTimestamp: Long, addedDurationMs: Long) {
            val index = tracks.indexOfFirst { it.id == id }
            if (index != -1) {
                val cur = tracks[index]
                tracks[index] = cur.copy(
                    endTimestamp = endTimestamp,
                    durationMs = cur.durationMs + addedDurationMs
                )
            }
        }
    }
}
