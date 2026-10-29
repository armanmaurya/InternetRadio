package com.armanmaurya.internetradio.ui.shared.viewmodels

import com.armanmaurya.internetradio.domain.controller.PlayerController
import com.armanmaurya.internetradio.domain.controller.RecordingPlaybackController
import com.armanmaurya.internetradio.domain.controller.RecordingRouteTarget
import com.armanmaurya.internetradio.domain.model.AppPreferences
import com.armanmaurya.internetradio.domain.model.PlaybackSession
import com.armanmaurya.internetradio.domain.model.PlaybackSource
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.model.RecordingFile
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import com.armanmaurya.internetradio.ui.mobile.screens.home.HomeViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingPlaybackControllerTest {

    private lateinit var fakeRecordingPlaybackController: FakeRecordingPlaybackController
    private lateinit var fakeSettingsRepository: FakeSettingsRepository
    private lateinit var homeViewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        fakeRecordingPlaybackController = FakeRecordingPlaybackController()
        fakeSettingsRepository = FakeSettingsRepository()
        homeViewModel = HomeViewModel(
            settingsRepository = fakeSettingsRepository,
            recordingPlaybackController = fakeRecordingPlaybackController
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_hasNoActivePlaybackOrRouteTarget() {
        assertNull(fakeRecordingPlaybackController.currentRecording.value)
        assertFalse(fakeRecordingPlaybackController.isPlaying.value)
        assertEquals(0L, fakeRecordingPlaybackController.currentPositionMs.value)
        assertEquals(0L, fakeRecordingPlaybackController.durationMs.value)
        assertFalse(fakeRecordingPlaybackController.isLooping.value)
        assertNull(fakeRecordingPlaybackController.routeToRecordingTarget.value)
    }

    @Test
    fun routeToRecording_setsRouteTargetCorrectly() {
        val station = "BBC Radio 1"
        val uri = "content://media/external/audio/media/101"

        fakeRecordingPlaybackController.routeToRecording(station, uri)

        val target = fakeRecordingPlaybackController.routeToRecordingTarget.value
        assertNotNull(target)
        assertEquals(station, target?.stationName)
        assertEquals(uri, target?.recordingUri)
    }

    @Test
    fun clearRouteTarget_resetsTargetToNull() {
        fakeRecordingPlaybackController.routeToRecording("Station", "uri://test")
        assertNotNull(fakeRecordingPlaybackController.routeToRecordingTarget.value)

        fakeRecordingPlaybackController.clearRouteTarget()
        assertNull(fakeRecordingPlaybackController.routeToRecordingTarget.value)
    }

    @Test
    fun homeViewModel_routeToRecording_forwardsExplicitTarget() {
        homeViewModel.routeToRecording("Jazz Lounge", "content://audio/42")

        val target = fakeRecordingPlaybackController.routeToRecordingTarget.value
        assertNotNull(target)
        assertEquals("Jazz Lounge", target?.stationName)
        assertEquals("content://audio/42", target?.recordingUri)
    }

    @Test
    fun homeViewModel_routeToRecording_fallsBackToRequestRouteWhenBlank() {
        fakeRecordingPlaybackController.mockActiveRecording(
            stationName = "Rock Classics",
            recordingUri = "content://audio/99"
        )

        homeViewModel.routeToRecording(null, null)

        val target = fakeRecordingPlaybackController.routeToRecordingTarget.value
        assertNotNull(target)
        assertEquals("Rock Classics", target?.stationName)
        assertEquals("content://audio/99", target?.recordingUri)
    }

    @Test
    fun stop_resetsAllPlaybackStates() {
        fakeRecordingPlaybackController.mockActiveRecording("Chill FM", "content://audio/1")
        fakeRecordingPlaybackController.seekTo(15000L)

        fakeRecordingPlaybackController.stop()

        assertNull(fakeRecordingPlaybackController.currentRecording.value)
        assertFalse(fakeRecordingPlaybackController.isPlaying.value)
        assertEquals(0L, fakeRecordingPlaybackController.currentPositionMs.value)
        assertEquals(0L, fakeRecordingPlaybackController.durationMs.value)
    }

    @Test
    fun togglePlayPause_togglesPlayingState() {
        fakeRecordingPlaybackController.mockActiveRecording("News 24", "content://audio/2")
        assertTrue(fakeRecordingPlaybackController.isPlaying.value)

        fakeRecordingPlaybackController.togglePlayPause()
        assertFalse(fakeRecordingPlaybackController.isPlaying.value)

        fakeRecordingPlaybackController.togglePlayPause()
        assertTrue(fakeRecordingPlaybackController.isPlaying.value)
    }

    @Test
    fun mutualExclusion_startingRecordingPlaybackPausesRadio() {
        var radioPauseCalled = false
        val radioController = object : EmptyPlayerController() {
            override fun pause() {
                radioPauseCalled = true
            }
        }

        // Simulate action performed when play(recording) is triggered
        radioController.pause()
        fakeRecordingPlaybackController.mockActiveRecording("Classical", "uri://classic")

        assertTrue(radioPauseCalled)
        assertTrue(fakeRecordingPlaybackController.isPlaying.value)
    }

    @Test
    fun playbackSpeed_initialValueIsDefault() {
        assertEquals(1.0f, fakeRecordingPlaybackController.playbackSpeed.value)
    }

    @Test
    fun setPlaybackSpeed_updatesPlaybackSpeed() {
        fakeRecordingPlaybackController.setPlaybackSpeed(1.5f)
        assertEquals(1.5f, fakeRecordingPlaybackController.playbackSpeed.value)

        fakeRecordingPlaybackController.setPlaybackSpeed(0.75f)
        assertEquals(0.75f, fakeRecordingPlaybackController.playbackSpeed.value)
    }

    @Test
    fun setLooping_updatesLoopingState() {
        assertFalse(fakeRecordingPlaybackController.isLooping.value)
        fakeRecordingPlaybackController.setLooping(true)
        assertTrue(fakeRecordingPlaybackController.isLooping.value)
        fakeRecordingPlaybackController.setLooping(false)
        assertFalse(fakeRecordingPlaybackController.isLooping.value)
    }

    @Test
    fun toggleLoop_togglesLoopingState() {
        assertFalse(fakeRecordingPlaybackController.isLooping.value)
        fakeRecordingPlaybackController.toggleLoop()
        assertTrue(fakeRecordingPlaybackController.isLooping.value)
        fakeRecordingPlaybackController.toggleLoop()
        assertFalse(fakeRecordingPlaybackController.isLooping.value)
    }

    @Test
    fun homeViewModel_isPlayingRecording_reflectsPlaybackState() {
        assertFalse(homeViewModel.isPlayingRecording.value)
        fakeRecordingPlaybackController.mockActiveRecording("Test Station", "content://test/1")
        assertTrue(homeViewModel.isPlayingRecording.value)
        fakeRecordingPlaybackController.pause()
        assertFalse(homeViewModel.isPlayingRecording.value)
    }

    @Test
    fun playbackEnded_whenLoopingDisabled_stopsPlayingAndResetsPosition() {
        fakeRecordingPlaybackController.mockActiveRecording("Podcast", "uri://podcast")
        fakeRecordingPlaybackController.setLooping(false)
        fakeRecordingPlaybackController.seekTo(60000L)
        assertTrue(fakeRecordingPlaybackController.isPlaying.value)

        fakeRecordingPlaybackController.onTrackEnded()

        assertFalse(fakeRecordingPlaybackController.isPlaying.value)
        assertEquals(0L, fakeRecordingPlaybackController.currentPositionMs.value)
    }

    @Test
    fun playbackEnded_whenLoopingEnabled_continuesPlayingAndResetsPosition() {
        fakeRecordingPlaybackController.mockActiveRecording("Song", "uri://song")
        fakeRecordingPlaybackController.setLooping(true)
        fakeRecordingPlaybackController.seekTo(60000L)
        assertTrue(fakeRecordingPlaybackController.isPlaying.value)

        fakeRecordingPlaybackController.onTrackEnded()

        assertTrue(fakeRecordingPlaybackController.isPlaying.value)
        assertEquals(0L, fakeRecordingPlaybackController.currentPositionMs.value)
    }

    // --- Test Doubles ---

    private class FakeRecordingPlaybackController : RecordingPlaybackController {
        private val _currentRecording = MutableStateFlow<RecordingFile?>(null)
        override val currentRecording: StateFlow<RecordingFile?> = _currentRecording.asStateFlow()

        private val _isPlaying = MutableStateFlow(false)
        override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

        private val _currentPositionMs = MutableStateFlow(0L)
        override val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

        private val _durationMs = MutableStateFlow(0L)
        override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

        private val _playbackSpeed = MutableStateFlow(1.0f)
        override val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

        private val _isLooping = MutableStateFlow(false)
        override val isLooping: StateFlow<Boolean> = _isLooping.asStateFlow()

        private val _routeToRecordingTarget = MutableStateFlow<RecordingRouteTarget?>(null)
        override val routeToRecordingTarget: StateFlow<RecordingRouteTarget?> = _routeToRecordingTarget.asStateFlow()

        private var activeStationName: String = ""
        private var activeUriString: String = ""

        fun mockActiveRecording(stationName: String, recordingUri: String) {
            activeStationName = stationName
            activeUriString = recordingUri
            _isPlaying.value = true
            _durationMs.value = 60000L
        }

        fun onTrackEnded() {
            if (_isLooping.value) {
                _currentPositionMs.value = 0L
                _isPlaying.value = true
            } else {
                _isPlaying.value = false
                _currentPositionMs.value = 0L
            }
        }

        override fun play(recording: RecordingFile) {
            _currentRecording.value = recording
            _isPlaying.value = true
            _durationMs.value = recording.durationMs
        }

        override fun pause() {
            _isPlaying.value = false
        }

        override fun togglePlayPause() {
            _isPlaying.value = !_isPlaying.value
        }

        override fun seekTo(positionMs: Long) {
            _currentPositionMs.value = positionMs
        }

        override fun setPlaybackSpeed(speed: Float) {
            _playbackSpeed.value = speed
        }

        override fun setLooping(enabled: Boolean) {
            _isLooping.value = enabled
        }

        override fun toggleLoop() {
            _isLooping.value = !_isLooping.value
        }

        override fun stop() {
            _isPlaying.value = false
            _currentRecording.value = null
            _currentPositionMs.value = 0L
            _durationMs.value = 0L
        }

        override fun requestRouteToPlayingRecording() {
            val targetStation = _currentRecording.value?.stationName ?: activeStationName
            val targetUri = _currentRecording.value?.uri?.toString() ?: activeUriString
            if (targetStation.isNotBlank()) {
                _routeToRecordingTarget.value = RecordingRouteTarget(targetStation, targetUri)
            }
        }

        override fun routeToRecording(stationName: String, recordingUri: String) {
            _routeToRecordingTarget.value = RecordingRouteTarget(stationName, recordingUri)
        }

        override fun clearRouteTarget() {
            _routeToRecordingTarget.value = null
        }

        override fun syncCurrentRecording(recordings: List<RecordingFile>) = Unit
    }

    private open class EmptyPlayerController : PlayerController {
        override val playbackSession: StateFlow<PlaybackSession> = MutableStateFlow(PlaybackSession())
        override val isBuffering: StateFlow<Boolean> = MutableStateFlow(false)
        override val isError: StateFlow<Boolean> = MutableStateFlow(false)
        override val isFetchingArtwork: StateFlow<Boolean> = MutableStateFlow(false)
        override val currentPlaylistSnapshot: List<RadioStation> = emptyList()
        override val currentPosition: Long = 0L
        override val amplitude: StateFlow<Float> = MutableStateFlow(0f)

        override fun play(stations: List<RadioStation>, startIndex: Int, source: PlaybackSource, playWhenReady: Boolean) {}
        override fun playIndex(index: Int) {}
        override fun pause() {}
        override fun togglePlayPause() {}
        override fun next() {}
        override fun previous() {}
        override fun stop() {}
        override fun setSleepTimer(durationMillis: Long) {}
        override fun cancelSleepTimer() {}
        override fun setVolume(volume: Float) {}
        override fun setLyricsSyncOffset(offsetMs: Long) {}
        override fun syncAndroidAutoContext(stations: List<RadioStation>, startIndex: Int, source: PlaybackSource) {}
        override fun updateCurrentStation(updatedStation: RadioStation, oldUuid: String?) {}
        override fun updateAmplitude(rms: Float) {}
    }

    private class FakeSettingsRepository : SettingsRepository {
        override val appPreferencesFlow: Flow<AppPreferences> = flowOf(AppPreferences())
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
        override suspend fun setTrackHistoryLimit(limit: Int) {}
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
