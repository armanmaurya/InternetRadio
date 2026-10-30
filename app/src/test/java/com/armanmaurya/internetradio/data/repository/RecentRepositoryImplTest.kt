package com.armanmaurya.internetradio.data.repository

import com.armanmaurya.internetradio.data.local.dao.RecentStationDao
import com.armanmaurya.internetradio.data.local.dao.StationDao
import com.armanmaurya.internetradio.data.local.entity.RecentStationEntity
import com.armanmaurya.internetradio.data.local.entity.StationEntity
import com.armanmaurya.internetradio.data.local.entity.toEntity
import com.armanmaurya.internetradio.domain.model.RadioStation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * Recents keep the version that was played: Radio Browser data from Browse, library data
 * (with the user's edits) from Library, and the saved copy when replayed from Recent.
 */
class RecentRepositoryImplTest {

    private lateinit var recentStationDao: FakeRecentStationDao
    private lateinit var stationDao: FakeStationDao
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var repository: RecentRepositoryImpl

    private val browseVersion = createStation(name = "Rock FM", url = "http://browse.example.com/stream")
    private val libraryVersion = createStation(name = "My Rock FM", url = "http://edited.example.com/stream")

    @Before
    fun setUp() {
        recentStationDao = FakeRecentStationDao()
        stationDao = FakeStationDao()
        settingsRepository = FakeSettingsRepository()
        repository = RecentRepositoryImpl(recentStationDao, stationDao, settingsRepository)
        stationDao.stations[STATION_UUID] = libraryVersion.toEntity(isFavorite = true, addedAt = 1L)
    }

    @Test
    fun playedFromBrowse_savesRadioBrowserVersion() = runTest {
        repository.addRecentStation(browseVersion)

        val recent = repository.getStationById(STATION_UUID)
        assertEquals("Rock FM", recent?.name)
        assertEquals("http://browse.example.com/stream", recent?.url)
    }

    @Test
    fun playedFromLibrary_afterBrowse_savesLibraryVersion() = runTest {
        repository.addRecentStation(browseVersion)
        repository.addRecentStation(libraryVersion)

        val recent = repository.getStationById(STATION_UUID)
        assertEquals("My Rock FM", recent?.name)
        assertEquals("http://edited.example.com/stream", recent?.url)
    }

    @Test
    fun playedFromBrowse_afterLibrary_savesRadioBrowserVersion() = runTest {
        repository.addRecentStation(libraryVersion)
        repository.addRecentStation(browseVersion)

        assertEquals("Rock FM", repository.getStationById(STATION_UUID)?.name)
    }

    @Test
    fun playedFromBrowse_leavesLibraryEditsUntouched() = runTest {
        repository.addRecentStation(browseVersion)

        val libraryRow = stationDao.stations[STATION_UUID]
        assertEquals("My Rock FM", libraryRow?.name)
        assertEquals("http://edited.example.com/stream", libraryRow?.url)
        // Only the play time reaches the library, for the "recently played" sort
        assertNotNull(libraryRow?.lastPlayedAt)
    }

    @Test
    fun playedFromRecent_keepsSavedCopyAndMovesItToTop() = runTest {
        val other = createStation(uuid = "station-b", name = "Jazz FM", url = "http://jazz.example.com/stream")
        repository.addRecentStation(browseVersion)
        repository.addRecentStation(other)

        // Recent tab replays the stored copy
        val savedCopy = repository.getStationById(STATION_UUID)!!
        repository.addRecentStation(savedCopy)

        val recents = repository.getAllRecent().first()
        assertEquals(listOf(STATION_UUID, "station-b"), recents.map { it.stationUuid })
        assertEquals("Rock FM", recents.first().name)
        assertEquals("http://browse.example.com/stream", recents.first().url)
    }

    @Test
    fun savingPastTheLimit_dropsOldestRecents() = runTest {
        settingsRepository.setRecentLimit(2)

        repository.addRecentStation(createStation(uuid = "station-1", name = "One", url = "http://one"))
        repository.addRecentStation(createStation(uuid = "station-2", name = "Two", url = "http://two"))
        repository.addRecentStation(createStation(uuid = "station-3", name = "Three", url = "http://three"))

        assertEquals(listOf("station-3", "station-2"), repository.getAllRecent().first().map { it.stationUuid })
        assertEquals(2, recentStationDao.storedCount)
    }

    @Test
    fun loweringTheLimit_trimsStoredRecents() = runTest {
        repository.addRecentStation(createStation(uuid = "station-1", name = "One", url = "http://one"))
        repository.addRecentStation(createStation(uuid = "station-2", name = "Two", url = "http://two"))
        repository.addRecentStation(createStation(uuid = "station-3", name = "Three", url = "http://three"))

        settingsRepository.setRecentLimit(1)
        repository.trimToLimit()

        assertEquals(1, recentStationDao.storedCount)
        // Raising the limit again doesn't bring trimmed stations back
        settingsRepository.setRecentLimit(50)
        assertEquals(listOf("station-3"), repository.getAllRecent().first().map { it.stationUuid })
    }

    private class FakeRecentStationDao : RecentStationDao {
        private val stations = MutableStateFlow<Map<String, RecentStationEntity>>(emptyMap())
        private var clock = 0L

        override suspend fun insertOrUpdate(station: RecentStationEntity) {
            // Real timestamps can collide within a test, so ordering uses a counter
            stations.value = stations.value + (station.stationUuid to station.copy(lastPlayedAt = ++clock))
        }

        override fun getAllRecent(limit: Int): Flow<List<RecentStationEntity>> =
            stations.map { it.values.sortedByDescending { station -> station.lastPlayedAt }.take(limit) }

        override suspend fun cleanupOldRecents(keepCount: Int) {
            val keep = stations.value.values.sortedByDescending { it.lastPlayedAt }.take(keepCount)
            stations.value = keep.associateBy { it.stationUuid }
        }

        val storedCount: Int get() = stations.value.size

        override suspend fun getStationById(stationUuid: String): RecentStationEntity? = stations.value[stationUuid]

        override suspend fun updateStationUuid(oldUuid: String, newUuid: String) {}

        override suspend fun deleteStationById(stationUuid: String) {
            stations.value = stations.value - stationUuid
        }

        override suspend fun deleteAll() {
            stations.value = emptyMap()
        }
    }

    private class FakeStationDao : StationDao {
        val stations = mutableMapOf<String, StationEntity>()

        override suspend fun insertOrUpdate(station: StationEntity) {
            stations[station.stationUuid] = station
        }
        override suspend fun insertStation(station: StationEntity) {
            stations[station.stationUuid] = station
        }
        override suspend fun getStationById(stationUuid: String): StationEntity? = stations[stationUuid]
        override suspend fun getCustomStations(): List<StationEntity> = emptyList()
        override suspend fun updateStationUuid(oldUuid: String, newUuid: String) {}
        override fun getAllFavorites(): Flow<List<StationEntity>> = flowOf(emptyList())
        override fun getFavoritesByOldestAdded(): Flow<List<StationEntity>> = flowOf(emptyList())
        override fun getFavoritesByName(): Flow<List<StationEntity>> = flowOf(emptyList())
        override fun getFavoritesByNameDescending(): Flow<List<StationEntity>> = flowOf(emptyList())
        override fun getFavoritesByRecentlyPlayed(): Flow<List<StationEntity>> = flowOf(emptyList())
        override fun getFavoritesByLeastRecentlyPlayed(): Flow<List<StationEntity>> = flowOf(emptyList())
        override fun getFavoritesByCustomOrder(): Flow<List<StationEntity>> = flowOf(emptyList())
        override suspend fun getAllFavoriteEntities(): List<StationEntity> = emptyList()
        override fun isStationInLibrary(stationUuid: String): Flow<Int> = flowOf(0)
        override suspend fun isStationInLibraryDirect(stationUuid: String): Boolean = stationUuid in stations
        override suspend fun updateStations(stations: List<StationEntity>) {}
        override suspend fun deleteStationById(stationUuid: String) {
            stations.remove(stationUuid)
        }
        override suspend fun updateLastPlayed(stationUuid: String, timestamp: Long) {
            stations[stationUuid]?.let { stations[stationUuid] = it.copy(lastPlayedAt = timestamp) }
        }
    }

    private fun createStation(
        uuid: String = STATION_UUID,
        name: String,
        url: String
    ) = RadioStation(
        changeUuid = "",
        stationUuid = uuid,
        name = name,
        url = url,
        urlResolved = url,
        homepage = "",
        favicon = "",
        tags = emptyList(),
        country = "",
        countryCode = "",
        state = "",
        iso3166_2 = null,
        language = "",
        languageCodes = emptyList(),
        votes = 0,
        lastChangeTime = "",
        codec = "MP3",
        bitrate = 128,
        lastCheckOk = true,
        lastCheckTime = "",
        lastCheckOkTime = "",
        lastLocalCheckTime = "",
        clickTimestamp = "",
        clickCount = 0,
        clickTrend = 0,
        sslError = false,
        geoLat = null,
        geoLong = null,
        geoDistance = null,
        hasExtendedInfo = false
    )

    private companion object {
        const val STATION_UUID = "station-a"
    }
}
