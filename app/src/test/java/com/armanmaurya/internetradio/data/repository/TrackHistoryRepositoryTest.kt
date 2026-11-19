package com.armanmaurya.internetradio.data.repository

import com.armanmaurya.internetradio.data.local.dao.TrackHistoryDao
import com.armanmaurya.internetradio.data.local.dao.StarredTrackDao
import com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity
import com.armanmaurya.internetradio.data.local.entity.StarredTrackEntity
import com.armanmaurya.internetradio.domain.model.AppPreferences
import com.armanmaurya.internetradio.domain.model.ConflictStrategy
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
    private lateinit var fakeStarredDao: FakeStarredTrackDao
    private lateinit var fakeSettingsRepository: FakeSettingsRepository
    private lateinit var repository: TrackHistoryRepositoryImpl

    @Before
    fun setup() {
        fakeDao = FakeTrackHistoryDao()
        fakeStarredDao = FakeStarredTrackDao()
        fakeSettingsRepository = FakeSettingsRepository()
        repository = TrackHistoryRepositoryImpl(fakeDao, fakeStarredDao, fakeSettingsRepository)
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

    private suspend fun history(stationUuid: String) = repository.getTrackHistory(stationUuid, 100).first()

    @Test
    fun getTrackHistory_marksStarredRows_onlyOnSameStation() = runBlocking {
        repository.logTrack("station-1", "Song A", "Raw Song A")
        repository.toggleTrackStarred("station-1", "Song A", true)
        assertTrue(history("station-1").single().isStarred)

        // Same song on another station is not starred (stars are per station)
        repository.logTrack("station-2", "Song A", "Raw Song A")
        assertFalse(history("station-2").single().isStarred)

        // Song A plays again on station-1 -> its new row shows as starred too
        repository.logTrack("station-1", "Song B", "Raw Song B")
        val id3 = repository.logTrack("station-1", "Song A", "Raw Song A")
        val rows = history("station-1")
        assertTrue(rows.first { it.id == id3 }.isStarred)
        assertFalse(rows.first { it.trackTitle == "Song B" }.isStarred)
    }

    @Test
    fun getTrackHistory_marksRowStarred_whenOnlyRawTitleMatches() = runBlocking {
        fakeStarredDao.insert(StarredTrackEntity(stationUuid = "s1", trackTitle = "Clean", rawTrackTitle = "ARTIST - CLEAN [live]"))
        repository.logTrack("s1", "Artist - Clean (Live)", "artist - clean [live]")

        assertTrue(history("s1").single().isStarred)
    }

    @Test
    fun loggedTracks_doNotStoreStarredState() = runBlocking {
        fakeStarredDao.insert(StarredTrackEntity(stationUuid = "s1", trackTitle = "Song A"))
        repository.logTrack("s1", "Song A", "Song A")

        assertFalse(fakeDao.tracks.single().isStarred)
        assertTrue(history("s1").single().isStarred)
    }

    @Test
    fun toggleTrackStarred_writesOnlyStarredTable_forThatStation() = runBlocking {
        fakeDao.insert(TrackHistoryEntity(id = 1, stationUuid = "s1", trackTitle = "Song A", timestamp = 100))
        fakeDao.insert(TrackHistoryEntity(id = 2, stationUuid = "s2", trackTitle = "Song A", timestamp = 200))

        repository.toggleTrackStarred("s1", "Song A", true)

        assertEquals(1, fakeStarredDao.starred.size)
        assertTrue(history("s1").single().isStarred)
        assertFalse(history("s2").single().isStarred)

        repository.toggleTrackStarred("s1", "Song A", false)
        assertTrue(fakeStarredDao.starred.isEmpty())
        assertFalse(history("s1").single().isStarred)
    }

    @Test
    fun toggleTrackStarred_doesNotDuplicate_whenTitleDiffersOnlyByCase() = runBlocking {
        repository.toggleTrackStarred("s1", "Song A", true)
        repository.toggleTrackStarred("s1", "song a ", true)

        assertEquals(1, fakeStarredDao.starred.size)
        assertEquals("Song A", fakeStarredDao.starred.single().trackTitle)
    }

    @Test
    fun toggleTrackStarred_unstarsRow_starredThroughRawTitle() = runBlocking {
        fakeStarredDao.insert(StarredTrackEntity(stationUuid = "s1", trackTitle = "Clean", rawTrackTitle = "Raw Title"))
        repository.logTrack("s1", "Raw Title", "Raw Title")
        assertTrue(history("s1").single().isStarred)

        repository.toggleTrackStarred("s1", "Raw Title", false)

        assertTrue(fakeStarredDao.starred.isEmpty())
        assertFalse(history("s1").single().isStarred)
    }

    @Test
    fun setTrackStarredById_starsByTitleOfThatRow() = runBlocking {
        val id = repository.logTrack("s1", "Song A", "Song A")!!

        repository.setTrackStarredById(id, true)
        assertTrue(history("s1").single().isStarred)

        repository.setTrackStarredById(id, false)
        assertFalse(history("s1").single().isStarred)
    }

    @Test
    fun updateTrackMetadata_showsStarred_ifCleanedTitleWasAlreadyStarred() = runBlocking {
        fakeStarredDao.insert(StarredTrackEntity(stationUuid = "s1", trackTitle = "Clean Title"))

        val id = repository.logTrack("s1", "Raw Title", "Raw Title")!!
        assertFalse(history("s1").single().isStarred)

        repository.updateTrackMetadata(id, "Clean Title", "http://cover.jpg")
        assertTrue(history("s1").single().isStarred)
        assertEquals(1, fakeStarredDao.starred.size)
    }

    @Test
    fun updateTrackMetadata_movesStar_fromOldTitleToNewTitle() = runBlocking {
        val id = repository.logTrack("s1", "Raw Title", "Raw Title")!!
        repository.toggleTrackStarred("s1", "Raw Title", true)

        repository.updateTrackMetadata(id, "Clean Title", "http://cover.jpg")

        val star = fakeStarredDao.starred.single()
        assertEquals("Clean Title", star.trackTitle)
        assertEquals("Raw Title", star.rawTrackTitle)
        assertEquals("http://cover.jpg", star.coverArtUrl)
        assertTrue(history("s1").single().isStarred)
    }

    @Test
    fun updateTrackMetadata_dropsOldStar_whenBothTitlesAreStarred() = runBlocking {
        fakeStarredDao.insert(StarredTrackEntity(stationUuid = "s1", trackTitle = "Clean Title"))
        val id = repository.logTrack("s1", "Raw Title", "Raw Title")!!
        repository.toggleTrackStarred("s1", "Raw Title", true)
        assertEquals(2, fakeStarredDao.starred.size)

        repository.updateTrackMetadata(id, "Clean Title", null)

        assertEquals("Clean Title", fakeStarredDao.starred.single().trackTitle)
        assertTrue(history("s1").single().isStarred)
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

    @Test
    fun importTracks_insertsNewTracks_andSkipsDuplicateTimestamps() = runBlocking {
        fakeDao.insert(TrackHistoryEntity(id = 1, stationUuid = "s1", trackTitle = "Song 1", timestamp = 1000L))

        val toImport = listOf(
            TrackHistoryEntity(id = 0, stationUuid = "s1", trackTitle = "Song 1 (Duplicate)", timestamp = 1000L),
            TrackHistoryEntity(id = 0, stationUuid = "s1", trackTitle = "Song 2", timestamp = 2000L)
        )

        repository.importTracks("s1", toImport, ConflictStrategy.SKIP)

        assertEquals(2, fakeDao.tracks.size)
        assertEquals("Song 1", fakeDao.tracks.first { it.timestamp == 1000L }.trackTitle)
        assertEquals("Song 2", fakeDao.tracks.first { it.timestamp == 2000L }.trackTitle)
    }

    @Test
    fun getAllTracksForStation_returnsHistoryOnly() = runBlocking {
        fakeDao.insert(TrackHistoryEntity(id = 1, stationUuid = "s1", trackTitle = "Song 1", timestamp = 1000L))
        fakeStarredDao.insert(StarredTrackEntity(stationUuid = "s1", trackTitle = "Song 1", starredAt = 5000L))
        fakeStarredDao.insert(StarredTrackEntity(stationUuid = "s1", trackTitle = "Old Song", starredAt = 6000L))

        val tracks = repository.getAllTracksForStation("s1")

        assertEquals(listOf("Song 1"), tracks.map { it.trackTitle })
        assertEquals(2, repository.getAllStarredTracksForStation("s1").size)
    }

    @Test
    fun importStarredTracks_addsNewStars_andKeepsExistingOnes() = runBlocking {
        fakeStarredDao.insert(StarredTrackEntity(stationUuid = "s1", trackTitle = "Song 1", coverArtUrl = null, starredAt = 10L))

        repository.importStarredTracks(
            "s1",
            listOf(
                StarredTrackEntity(stationUuid = "s1", trackTitle = "song 1", coverArtUrl = "http://art.jpg", starredAt = 99L),
                StarredTrackEntity(stationUuid = "s1", trackTitle = "Song 2", starredAt = 20L)
            )
        )

        assertEquals(2, fakeStarredDao.starred.size)
        val song1 = fakeStarredDao.starred.first { it.trackTitle == "Song 1" }
        assertEquals("http://art.jpg", song1.coverArtUrl)
        assertEquals(10L, song1.starredAt)
        assertNotNull(fakeStarredDao.starred.firstOrNull { it.trackTitle == "Song 2" })
    }

    @Test
    fun importTracks_doesNotStoreStarredState() = runBlocking {
        repository.importTracks(
            "s1",
            listOf(TrackHistoryEntity(id = 0, stationUuid = "s1", trackTitle = "Song 1", timestamp = 1000L, isStarred = true)),
            ConflictStrategy.SKIP
        )

        assertFalse(fakeDao.tracks.single().isStarred)
        assertTrue(fakeStarredDao.starred.isEmpty())
    }

    @Test
    fun importTracks_enrichesMissingMetadata_whenStrategyIsSkip() = runBlocking {
        fakeDao.insert(TrackHistoryEntity(id = 1, stationUuid = "s1", trackTitle = "Song 1", timestamp = 1000L, coverArtUrl = null, artist = null))

        val toImport = listOf(
            TrackHistoryEntity(id = 0, stationUuid = "s1", trackTitle = "Song 1", timestamp = 1000L, coverArtUrl = "http://art.jpg", artist = "Artist 1")
        )

        repository.importTracks("s1", toImport, ConflictStrategy.SKIP)

        val updated = fakeDao.tracks.first()
        assertEquals("http://art.jpg", updated.coverArtUrl)
        assertEquals("Artist 1", updated.artist)
    }

    @Test
    fun importTracks_overwritesMetadata_whenStrategyIsOverwrite() = runBlocking {
        fakeDao.insert(TrackHistoryEntity(id = 1, stationUuid = "s1", trackTitle = "Old Title", timestamp = 1000L, coverArtUrl = "old.jpg"))

        val toImport = listOf(
            TrackHistoryEntity(id = 0, stationUuid = "s1", trackTitle = "New Title", timestamp = 1000L, coverArtUrl = "new.jpg")
        )

        repository.importTracks("s1", toImport, ConflictStrategy.OVERWRITE)

        val updated = fakeDao.tracks.first()
        assertEquals("New Title", updated.trackTitle)
        assertEquals("new.jpg", updated.coverArtUrl)
    }

    // --- Test Doubles ---

    private class FakeTrackHistoryDao : TrackHistoryDao {
        val tracks = mutableListOf<TrackHistoryEntity>()
        private var idCounter = 1L

        override suspend fun insert(trackHistory: TrackHistoryEntity): Long {
            val assignedId = if (trackHistory.id != 0L) trackHistory.id else idCounter++
            idCounter = maxOf(idCounter, assignedId + 1)
            val stored = trackHistory.copy(id = assignedId, isStarred = false)
            tracks.add(stored)
            return assignedId
        }

        override fun getTrackHistoryForStation(stationUuid: String, limit: Int): Flow<List<TrackHistoryEntity>> {
            return flowOf(tracks.filter { it.stationUuid == stationUuid }.takeLast(limit))
        }

        override suspend fun cleanupOldTracksForStation(stationUuid: String, keepCount: Int) {
            val stationTracks = tracks.filter { it.stationUuid == stationUuid }.sortedBy { it.timestamp }
            if (stationTracks.size > keepCount) {
                tracks.removeAll(stationTracks.take(stationTracks.size - keepCount))
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

        override suspend fun insertAll(trackHistories: List<TrackHistoryEntity>): List<Long> {
            val ids = mutableListOf<Long>()
            for (track in trackHistories) {
                val assignedId = if (track.id == 0L) (tracks.maxOfOrNull { it.id } ?: 0L) + 1L else track.id
                val toAdd = track.copy(id = assignedId, isStarred = false)
                tracks.add(toAdd)
                ids.add(assignedId)
            }
            return ids
        }

        override suspend fun getAllTracksForStationSync(stationUuid: String): List<TrackHistoryEntity> {
            return tracks.filter { it.stationUuid == stationUuid }.sortedBy { it.timestamp }
        }

        override suspend fun getTrackById(id: Long): TrackHistoryEntity? {
            return tracks.firstOrNull { it.id == id }
        }

        override suspend fun updateTrack(trackHistory: TrackHistoryEntity) {
            val index = tracks.indexOfFirst { it.id == trackHistory.id }
            if (index != -1) {
                tracks[index] = trackHistory.copy(isStarred = false)
            }
        }
    }

    private class FakeStarredTrackDao : StarredTrackDao {
        val starred = mutableListOf<StarredTrackEntity>()
        private var idCounter = 1L

        override suspend fun insert(starredTrack: StarredTrackEntity): Long {
            val assignedId = if (starredTrack.id != 0L) starredTrack.id else idCounter++
            idCounter = maxOf(idCounter, assignedId + 1)
            // Same as the real unique index: exact (stationUuid, trackTitle), case-sensitive
            val existingIndex = starred.indexOfFirst {
                it.stationUuid == starredTrack.stationUuid && it.trackTitle == starredTrack.trackTitle
            }
            val stored = starredTrack.copy(id = assignedId)
            if (existingIndex != -1) {
                starred[existingIndex] = stored
            } else {
                starred.add(stored)
            }
            return assignedId
        }

        override suspend fun insertAll(starredTracks: List<StarredTrackEntity>): List<Long> {
            return starredTracks.map { insert(it) }
        }

        override fun getStarredTracksForStation(stationUuid: String): Flow<List<StarredTrackEntity>> {
            return flowOf(starred.filter { it.stationUuid == stationUuid }.sortedByDescending { it.starredAt })
        }

        override suspend fun getStarredTracksForStationSync(stationUuid: String): List<StarredTrackEntity> {
            return starred.filter { it.stationUuid == stationUuid }.sortedByDescending { it.starredAt }
        }

        private fun StarredTrackEntity.matches(stationUuid: String, trackTitle: String, rawTrackTitle: String) =
            this.stationUuid == stationUuid && (
                this.trackTitle.trim().equals(trackTitle.trim(), ignoreCase = true) ||
                (this.rawTrackTitle.isNotEmpty() && this.rawTrackTitle.trim().equals(rawTrackTitle.trim(), ignoreCase = true))
            )

        override suspend fun isTrackStarred(stationUuid: String, trackTitle: String, rawTrackTitle: String): Boolean {
            return starred.any { it.matches(stationUuid, trackTitle, rawTrackTitle) }
        }

        override suspend fun findStarredTrack(stationUuid: String, trackTitle: String, rawTrackTitle: String): StarredTrackEntity? {
            return starred.firstOrNull { it.matches(stationUuid, trackTitle, rawTrackTitle) }
        }

        override suspend fun update(starredTrack: StarredTrackEntity) {
            val index = starred.indexOfFirst { it.id == starredTrack.id }
            if (index != -1) starred[index] = starredTrack
        }

        override suspend fun deleteStarredTrack(stationUuid: String, trackTitle: String, rawTrackTitle: String) {
            starred.removeAll { it.matches(stationUuid, trackTitle, rawTrackTitle) }
        }

        override suspend fun deleteStarredTrackById(id: Long) {
            starred.removeAll { it.id == id }
        }

        override suspend fun updateCoverArt(stationUuid: String, trackTitle: String, coverArtUrl: String) {
            val index = starred.indexOfFirst {
                it.stationUuid == stationUuid && it.trackTitle.trim().equals(trackTitle.trim(), ignoreCase = true)
            }
            if (index != -1) {
                starred[index] = starred[index].copy(coverArtUrl = coverArtUrl)
            }
        }

    }
}
