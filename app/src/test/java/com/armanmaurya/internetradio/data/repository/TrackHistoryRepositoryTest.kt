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
    fun logTrack_inheritsStarredState_whenTitleWasPreviouslyStarred() = runBlocking {
        // First track is played and starred
        val id1 = repository.logTrack("station-1", "Song A", "Raw Song A")
        repository.toggleTrackStarred("Song A", true)
        assertTrue(fakeDao.tracks.first().isStarred)

        // Same song plays on a different station
        val id2 = repository.logTrack("station-2", "Song A", "Raw Song A")
        assertNotNull(id2)
        val station2Track = fakeDao.tracks.first { it.stationUuid == "station-2" }
        assertTrue(station2Track.isStarred)
    }

    @Test
    fun toggleTrackStarred_updatesAllMatchingTracks() = runBlocking {
        fakeDao.insert(TrackHistoryEntity(id = 1, stationUuid = "s1", trackTitle = "Song A", timestamp = 100))
        fakeDao.insert(TrackHistoryEntity(id = 2, stationUuid = "s2", trackTitle = "Song A", timestamp = 200))

        repository.toggleTrackStarred("Song A", true)

        assertTrue(fakeDao.tracks.all { it.isStarred })

        repository.toggleTrackStarred("Song A", false)
        assertTrue(fakeDao.tracks.none { it.isStarred })
    }

    @Test
    fun updateTrackMetadata_marksStarred_ifCleanedTitleWasAlreadyStarred() = runBlocking {
        // Pre-star cleaned title
        fakeDao.insert(TrackHistoryEntity(id = 1, stationUuid = "s1", trackTitle = "Clean Title", timestamp = 100, isStarred = true))

        // New raw track is logged
        val id = repository.logTrack("s2", "Raw Title", "Raw Title")!!
        assertFalse(fakeDao.tracks.first { it.id == id }.isStarred)

        // Metadata arrives resolving to the starred title
        repository.updateTrackMetadata(id, "Clean Title", "http://cover.jpg")
        assertTrue(fakeDao.tracks.first { it.id == id }.isStarred)
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

        override fun getAllStarredTracks(): Flow<List<TrackHistoryEntity>> {
            return flowOf(tracks.filter { it.isStarred })
        }

        override suspend fun isTrackStarred(trackTitle: String, rawTrackTitle: String): Boolean {
            return tracks.any {
                it.isStarred && (
                    it.trackTitle.trim().equals(trackTitle.trim(), ignoreCase = true) ||
                    (rawTrackTitle.isNotBlank() && it.rawTrackTitle.isNotBlank() && it.rawTrackTitle.trim().equals(rawTrackTitle.trim(), ignoreCase = true))
                )
            }
        }

        override suspend fun setTrackStarred(trackTitle: String, isStarred: Boolean) {
            for (i in tracks.indices) {
                if (tracks[i].trackTitle.trim().equals(trackTitle.trim(), ignoreCase = true)) {
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
    }

    private class FakeSettingsRepository : SettingsRepository {
        val prefsFlow = MutableStateFlow(AppPreferences(trackHistoryLimit = 50))
        override val appPreferencesFlow: Flow<AppPreferences> = prefsFlow

        override suspend fun setUseFilterOnRecent(enabled: Boolean) {}
        override suspend fun setAutoRouteToBrowseOnSearch(enabled: Boolean) {}
        override suspend fun setSelectAllTextOnFocus(enabled: Boolean) {}
        override suspend fun setAutoPlayOnStart(enabled: Boolean) {}
        override suspend fun setAppLaunchCount(count: Int) {}
        override suspend fun setHasRatedApp(rated: Boolean) {}
        override suspend fun setDisableUpdateCheck(disabled: Boolean) {}
        override suspend fun setStopOnAudioBecomingNoisy(enabled: Boolean) {}
        override suspend fun setPauseOnVolumeZero(enabled: Boolean) {}
        override suspend fun setEnableVolumeBoost(enabled: Boolean) {}
        override suspend fun setUseFilterOnFavorites(enabled: Boolean) {}
        override suspend fun setUseFilterOnAdded(enabled: Boolean) {}
        override suspend fun setThemeMode(themeMode: com.armanmaurya.internetradio.ui.shared.theme.AppTheme) {}
        override suspend fun setDynamicColor(enabled: Boolean) {}
        override suspend fun setAppColor(appColor: com.armanmaurya.internetradio.ui.shared.theme.AppColor) {}
        override suspend fun setCustomColor(colorArgb: Int) {}
        override suspend fun setPureBlack(enabled: Boolean) {}
        override suspend fun setAppLanguage(language: String) {}
        override suspend fun getSavedAppLanguage(): String? = null
        override suspend fun setSelectedCountryCode(countryCode: String?) {}
        override suspend fun setSelectedStateCode(stateCode: String?) {}
        override suspend fun setSelectedLanguage(language: String?) {}
        override suspend fun setSelectedTags(tags: Set<String>) {}
        override suspend fun setSortOrder(order: String) {}
        override suspend fun setSortReverse(reverse: Boolean) {}
        override suspend fun setGridViewBrowse(isGrid: Boolean) {}
        override suspend fun setGridViewRecent(isGrid: Boolean) {}
        override suspend fun setGridViewFavorites(isGrid: Boolean) {}
        override suspend fun setGridViewAdded(isGrid: Boolean) {}
        override suspend fun setTrackHistoryLimit(limit: Int) {
            prefsFlow.value = prefsFlow.value.copy(trackHistoryLimit = limit)
        }
        override suspend fun setDefaultTab(tabIndex: Int) {}
        override suspend fun setLastUpdateCheckTime(timeInMillis: Long) {}
        override suspend fun setMaxRetryDuration(durationInMillis: Long) {}
        override suspend fun setConflictStrategy(strategy: com.armanmaurya.internetradio.domain.model.ConflictStrategy) {}
        override suspend fun setLibrarySortOption(option: com.armanmaurya.internetradio.domain.model.LibrarySortOption) {}
        override suspend fun setStartOfWeek(startOfWeek: com.armanmaurya.internetradio.domain.model.StartOfWeek) {}
        override suspend fun setShowCoverArtInNotification(enabled: Boolean) {}
        override suspend fun setAlarmVolumeTransitionSeconds(seconds: Int) {}
        override suspend fun setAlarmVolumeTransitionEnabled(enabled: Boolean) {}
        override suspend fun setKeepScreenOn(enabled: Boolean) {}
        override suspend fun setWidgetBackgroundAlpha(alpha: Float) {}
        override suspend fun setShowStationThumbnails(enabled: Boolean) {}
        override suspend fun setAutoBackupEnabled(enabled: Boolean) {}
        override suspend fun setAutoBackupFolder(uri: String?, folderName: String?) {}
        override suspend fun setLastAutoBackupResult(time: Long, status: com.armanmaurya.internetradio.domain.model.AutoBackupStatus, errorMessage: String?) {}
    }
}
