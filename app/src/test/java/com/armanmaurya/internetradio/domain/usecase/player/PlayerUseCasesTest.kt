package com.armanmaurya.internetradio.domain.usecase.player

import com.armanmaurya.internetradio.domain.controller.PlayerController
import com.armanmaurya.internetradio.domain.model.PlaybackSource
import com.armanmaurya.internetradio.domain.model.PlaybackState
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.repository.RecentRepository
import com.armanmaurya.internetradio.domain.repository.StationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlayerUseCasesTest {

    private lateinit var fakePlayerController: FakePlayerController
    private lateinit var fakeRecentRepository: FakeRecentRepository
    private lateinit var fakeStationRepository: FakeStationRepository

    private lateinit var playStationUseCase: PlayStationUseCase
    private lateinit var togglePlayPauseUseCase: TogglePlayPauseUseCase
    private lateinit var playNextStationUseCase: PlayNextStationUseCase
    private lateinit var playPreviousStationUseCase: PlayPreviousStationUseCase
    private lateinit var setSleepTimerUseCase: SetSleepTimerUseCase
    private lateinit var setPlayerVolumeUseCase: SetPlayerVolumeUseCase
    private lateinit var stopPlaybackUseCase: StopPlaybackUseCase

    private fun createTestStation(uuid: String = "test-uuid-1"): RadioStation {
        return RadioStation(
            changeUuid = "change-1",
            stationUuid = uuid,
            name = "Jazz FM",
            url = "http://stream.jazz.fm",
            urlResolved = "http://stream.jazz.fm",
            homepage = "http://jazz.fm",
            favicon = "",
            tags = emptyList(),
            country = "US",
            countryCode = "US",
            state = "",
            iso3166_2 = null,
            language = "en",
            languageCodes = emptyList(),
            votes = 100,
            lastChangeTime = "",
            codec = "MP3",
            bitrate = 128,
            lastCheckOk = true,
            lastCheckTime = "",
            lastCheckOkTime = "",
            lastLocalCheckTime = "",
            clickTimestamp = "",
            clickCount = 10,
            clickTrend = 1,
            sslError = false,
            geoLat = null,
            geoLong = null,
            geoDistance = null,
            hasExtendedInfo = false,
            isCustom = false
        )
    }

    @Before
    fun setUp() {
        fakePlayerController = FakePlayerController()
        fakeRecentRepository = FakeRecentRepository()
        fakeStationRepository = FakeStationRepository()

        playStationUseCase = PlayStationUseCase(fakePlayerController, fakeRecentRepository, fakeStationRepository)
        togglePlayPauseUseCase = TogglePlayPauseUseCase(fakePlayerController)
        playNextStationUseCase = PlayNextStationUseCase(fakePlayerController)
        playPreviousStationUseCase = PlayPreviousStationUseCase(fakePlayerController)
        setSleepTimerUseCase = SetSleepTimerUseCase(fakePlayerController)
        setPlayerVolumeUseCase = SetPlayerVolumeUseCase(fakePlayerController)
        stopPlaybackUseCase = StopPlaybackUseCase(fakePlayerController)
    }

    @Test
    fun playStationUseCase_playsStationAndUpdatesRecentAndClick() = runBlocking {
        val testStation = createTestStation()
        val stations = listOf(testStation)
        playStationUseCase(stations, 0, PlaybackSource.Library, playWhenReady = true)

        assertTrue(fakePlayerController.playCalled)
        assertEquals(stations, fakePlayerController.playedStations)
        assertEquals(0, fakePlayerController.playedStartIndex)
        assertEquals(PlaybackSource.Library, fakePlayerController.playedSource)

        assertEquals(listOf(testStation), fakeRecentRepository.recentStations)
        assertEquals(listOf("test-uuid-1"), fakeStationRepository.clickedUuids)
    }

    @Test
    fun togglePlayPauseUseCase_delegatesToController() {
        togglePlayPauseUseCase()
        assertTrue(fakePlayerController.togglePlayPauseCalled)
    }

    @Test
    fun playNextStationUseCase_delegatesToController() {
        playNextStationUseCase()
        assertTrue(fakePlayerController.nextCalled)
    }

    @Test
    fun playPreviousStationUseCase_delegatesToController() {
        playPreviousStationUseCase()
        assertTrue(fakePlayerController.previousCalled)
    }

    @Test
    fun setSleepTimerUseCase_setsAndCancelsTimer() {
        setSleepTimerUseCase(30_000L)
        assertEquals(30_000L, fakePlayerController.sleepTimerDuration)

        setSleepTimerUseCase.cancel()
        assertTrue(fakePlayerController.cancelSleepTimerCalled)
    }

    @Test
    fun setPlayerVolumeUseCase_updatesVolume() {
        setPlayerVolumeUseCase(0.75f)
        assertEquals(0.75f, fakePlayerController.currentVolume, 0.001f)
    }

    @Test
    fun stopPlaybackUseCase_stopsController() {
        stopPlaybackUseCase()
        assertTrue(fakePlayerController.stopCalled)
    }

    private class FakePlayerController : PlayerController {
        private val _playbackState = MutableStateFlow(PlaybackState())
        override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
        override val currentPlaylistSnapshot: List<RadioStation> = emptyList()
        override val currentPosition: Long = 0L
        private val _amplitude = MutableStateFlow(0f)
        override val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

        var playCalled = false
        var playedStations: List<RadioStation> = emptyList()
        var playedStartIndex: Int = -1
        var playedSource: PlaybackSource = PlaybackSource.None
        var togglePlayPauseCalled = false
        var nextCalled = false
        var previousCalled = false
        var stopCalled = false
        var sleepTimerDuration: Long? = null
        var cancelSleepTimerCalled = false
        var currentVolume: Float = 1f

        override fun play(
            stations: List<RadioStation>,
            startIndex: Int,
            source: PlaybackSource,
            playWhenReady: Boolean
        ) {
            playCalled = true
            playedStations = stations
            playedStartIndex = startIndex
            playedSource = source
        }

        override fun playIndex(index: Int) {}
        override fun pause() {}
        override fun togglePlayPause() {
            togglePlayPauseCalled = true
        }
        override fun next() {
            nextCalled = true
        }
        override fun previous() {
            previousCalled = true
        }
        override fun stop() {
            stopCalled = true
        }
        override fun setSleepTimer(durationMillis: Long) {
            sleepTimerDuration = durationMillis
        }
        override fun cancelSleepTimer() {
            cancelSleepTimerCalled = true
        }
        override fun setVolume(volume: Float) {
            this.currentVolume = volume
        }
        override fun setLyricsSyncOffset(offsetMs: Long) {}
        override fun syncAndroidAutoContext(
            stations: List<RadioStation>,
            startIndex: Int,
            source: PlaybackSource
        ) {}
        override fun updateCurrentStation(updatedStation: RadioStation, oldUuid: String?) {}
        override fun updateAmplitude(rms: Float) {
            _amplitude.value = rms
        }
    }

    private class FakeRecentRepository : RecentRepository {
        val recentStations = mutableListOf<RadioStation>()

        override fun getAllRecent(): Flow<List<RadioStation>> = flowOf(recentStations)
        override suspend fun addRecentStation(station: RadioStation) {
            recentStations.add(station)
        }
        override suspend fun removeRecent(stationUuid: String) {}
        override suspend fun clearAllRecent() {}
        override suspend fun getStationById(stationUuid: String): RadioStation? =
            recentStations.find { it.stationUuid == stationUuid }
    }

    private class FakeStationRepository : StationRepository {
        val clickedUuids = mutableListOf<String>()

        override suspend fun registerClick(stationUuid: String) {
            clickedUuids.add(stationUuid)
        }

        override suspend fun filterStations(
            name: String?,
            nameExact: Boolean?,
            country: String?,
            countryExact: Boolean?,
            countryCode: String?,
            state: String?,
            stateExact: Boolean?,
            language: String?,
            languageExact: Boolean?,
            tag: String?,
            tagExact: Boolean?,
            tagList: String?,
            codec: String?,
            bitrateMin: Int?,
            bitrateMax: Int?,
            hasExtendedInfo: Boolean?,
            isHttps: Boolean?,
            order: String,
            reverse: Boolean,
            limit: Int,
            offset: Int,
            hideBroken: Boolean
        ): Result<List<RadioStation>> = Result.success(emptyList())

        override suspend fun getCountries(): Result<List<com.armanmaurya.internetradio.domain.model.Country>> = Result.success(emptyList())
        override suspend fun getLanguages(filter: String?): Result<List<com.armanmaurya.internetradio.domain.model.Language>> = Result.success(emptyList())
        override suspend fun getTags(filter: String?): Result<List<com.armanmaurya.internetradio.domain.model.Tag>> = Result.success(emptyList())
        override suspend fun getCurrentCountryCode(): Result<String> = Result.success("US")
        override suspend fun getStationsByUuid(uuids: List<String>): Result<List<RadioStation>> = Result.success(emptyList())
        override suspend fun getStationsByUrl(url: String): Result<List<RadioStation>> = Result.success(emptyList())
        override suspend fun addStation(
            name: String,
            url: String,
            homepage: String?,
            favicon: String?,
            countryCode: String?,
            iso31662: String?,
            languageCodes: String?,
            tags: String?,
            geoLat: Double?,
            geoLong: Double?
        ): Result<com.armanmaurya.internetradio.domain.model.StationAddResult> =
            Result.success(com.armanmaurya.internetradio.domain.model.StationAddResult(true, "OK", "uuid"))
    }
}
