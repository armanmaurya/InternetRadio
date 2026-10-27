package com.armanmaurya.internetradio.ui.shared.viewmodels

import android.content.ContextWrapper
import com.armanmaurya.internetradio.data.local.entity.StationEntity
import com.armanmaurya.internetradio.domain.model.AppPreferences
import com.armanmaurya.internetradio.domain.model.ConflictStrategy
import com.armanmaurya.internetradio.domain.model.Country
import com.armanmaurya.internetradio.domain.model.Language
import com.armanmaurya.internetradio.domain.model.LibrarySortOption
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.model.StartOfWeek
import com.armanmaurya.internetradio.domain.model.StationAddResult
import com.armanmaurya.internetradio.domain.model.Tag
import com.armanmaurya.internetradio.domain.repository.LibraryRepository
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import com.armanmaurya.internetradio.domain.repository.StationRepository
import com.armanmaurya.internetradio.ui.shared.theme.AppColor
import com.armanmaurya.internetradio.ui.shared.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakeStationRepository: FakeStationRepository
    private lateinit var fakeSettingsRepository: FakeSettingsRepository
    private lateinit var fakeLibraryRepository: FakeLibraryRepository
    private val fakeContext = ContextWrapper(null)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeStationRepository = FakeStationRepository()
        fakeSettingsRepository = FakeSettingsRepository()
        fakeLibraryRepository = FakeLibraryRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onFirstOpen_whenCountryIsNull_automaticallyDetectsAndSavesCountry() = testScope.runTest {
        fakeStationRepository.detectedCountryCode = "IN"
        fakeSettingsRepository.prefsFlow.value = AppPreferences(selectedCountryCode = null)

        val viewModel = BrowseViewModel(
            repository = fakeStationRepository,
            settingsRepository = fakeSettingsRepository,
            libraryRepository = fakeLibraryRepository,
            context = fakeContext
        )

        advanceUntilIdle()

        // Verify country was detected and saved to settings
        assertEquals("IN", fakeSettingsRepository.prefsFlow.value.selectedCountryCode)
        assertEquals("IN", viewModel.uiState.value.selectedCountryCode)
        assertEquals("IN", fakeStationRepository.lastFilteredCountryCode)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun onLaunch_whenCountryAlreadyConfigured_doesNotRedetect() = testScope.runTest {
        fakeStationRepository.detectedCountryCode = "IN"
        fakeSettingsRepository.prefsFlow.value = AppPreferences(selectedCountryCode = "US")

        val viewModel = BrowseViewModel(
            repository = fakeStationRepository,
            settingsRepository = fakeSettingsRepository,
            libraryRepository = fakeLibraryRepository,
            context = fakeContext
        )

        advanceUntilIdle()

        // Should load US directly without triggering detection
        assertEquals("US", fakeSettingsRepository.prefsFlow.value.selectedCountryCode)
        assertEquals("US", viewModel.uiState.value.selectedCountryCode)
        assertEquals("US", fakeStationRepository.lastFilteredCountryCode)
        assertEquals(0, fakeStationRepository.detectionCallCount)
    }

    @Test
    fun onLaunch_whenAllCountriesExplicitlySelected_loadsAllWithoutDetection() = testScope.runTest {
        fakeStationRepository.detectedCountryCode = "IN"
        // "" represents "All Countries" in the app
        fakeSettingsRepository.prefsFlow.value = AppPreferences(selectedCountryCode = "")

        val viewModel = BrowseViewModel(
            repository = fakeStationRepository,
            settingsRepository = fakeSettingsRepository,
            libraryRepository = fakeLibraryRepository,
            context = fakeContext
        )

        advanceUntilIdle()

        assertEquals("", fakeSettingsRepository.prefsFlow.value.selectedCountryCode)
        assertEquals("", viewModel.uiState.value.selectedCountryCode)
        assertEquals(null, fakeStationRepository.lastFilteredCountryCode) // empty string mapped to null for API
        assertEquals(0, fakeStationRepository.detectionCallCount)
    }

    // --- Fakes ---

    private class FakeStationRepository : StationRepository {
        var detectedCountryCode: String = "US"
        var detectionCallCount: Int = 0
        var lastFilteredCountryCode: String? = "UNSET"

        override suspend fun getCurrentCountryCode(): Result<String> {
            detectionCallCount++
            return Result.success(detectedCountryCode)
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
        ): Result<List<RadioStation>> {
            lastFilteredCountryCode = countryCode
            return Result.success(emptyList())
        }

        override suspend fun getCountries(): Result<List<Country>> = Result.success(emptyList())
        override suspend fun getLanguages(filter: String?): Result<List<Language>> = Result.success(emptyList())
        override suspend fun getTags(filter: String?): Result<List<Tag>> = Result.success(emptyList())
        override suspend fun registerClick(stationUuid: String) {}
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
        ): Result<StationAddResult> = Result.failure(NotImplementedError())
    }

    private class FakeSettingsRepository : SettingsRepository {
        val prefsFlow = MutableStateFlow(AppPreferences())
        override val appPreferencesFlow: Flow<AppPreferences> = prefsFlow

        override suspend fun setSelectedCountryCode(countryCode: String?) {
            prefsFlow.value = prefsFlow.value.copy(selectedCountryCode = countryCode)
        }

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
        override suspend fun setThemeMode(themeMode: AppTheme) {}
        override suspend fun setDynamicColor(enabled: Boolean) {}
        override suspend fun setAppColor(appColor: AppColor) {}
        override suspend fun setCustomColor(colorArgb: Int) {}
        override suspend fun setPureBlack(enabled: Boolean) {}
        override suspend fun setAppLanguage(language: String) {}
        override suspend fun getSavedAppLanguage(): String? = null
        override suspend fun setSelectedStateCode(stateCode: String?) {
            prefsFlow.value = prefsFlow.value.copy(selectedStateCode = stateCode)
        }
        override suspend fun setSelectedLanguage(language: String?) {
            prefsFlow.value = prefsFlow.value.copy(selectedLanguage = language)
        }
        override suspend fun setSelectedTags(tags: Set<String>) {
            prefsFlow.value = prefsFlow.value.copy(selectedTags = tags)
        }
        override suspend fun setSortOrder(order: String) {
            prefsFlow.value = prefsFlow.value.copy(order = order)
        }
        override suspend fun setSortReverse(reverse: Boolean) {
            prefsFlow.value = prefsFlow.value.copy(reverse = reverse)
        }
        override suspend fun setGridViewBrowse(isGrid: Boolean) {
            prefsFlow.value = prefsFlow.value.copy(isGridViewBrowse = isGrid)
        }
        override suspend fun setGridViewRecent(isGrid: Boolean) {}
        override suspend fun setGridViewFavorites(isGrid: Boolean) {}
        override suspend fun setGridViewAdded(isGrid: Boolean) {}
        override suspend fun setTrackHistoryLimit(limit: Int) {}
        override suspend fun setDefaultTab(tabIndex: Int) {}
        override suspend fun setLastUpdateCheckTime(timeInMillis: Long) {}
        override suspend fun setMaxRetryDuration(durationInMillis: Long) {}
        override suspend fun setConflictStrategy(strategy: ConflictStrategy) {}
        override suspend fun setLibrarySortOption(option: LibrarySortOption) {}
        override suspend fun setStartOfWeek(startOfWeek: StartOfWeek) {}
        override suspend fun setShowCoverArtInNotification(enabled: Boolean) {}
        override suspend fun setAlarmVolumeTransitionSeconds(seconds: Int) {}
        override suspend fun setAlarmVolumeTransitionEnabled(enabled: Boolean) {}
        override suspend fun setKeepScreenOn(enabled: Boolean) {}
        override suspend fun setWidgetBackgroundAlpha(alpha: Float) {}
        override suspend fun setShowStationThumbnails(enabled: Boolean) {}
        override suspend fun setAutoBackupEnabled(enabled: Boolean) {}
        override suspend fun setAutoBackupFolder(uri: String?, folderName: String?) {}
        override suspend fun setLastAutoBackupResult(
            time: Long,
            status: com.armanmaurya.internetradio.domain.model.AutoBackupStatus,
            errorMessage: String?
        ) {}
    }

    private class FakeLibraryRepository : LibraryRepository {
        val stationsFlow = MutableStateFlow<List<RadioStation>>(emptyList())
        override fun getAllStations(): Flow<List<RadioStation>> = stationsFlow
        override fun getAllStationEntitiesFlow(): Flow<List<StationEntity>> = flowOf(emptyList())
        override suspend fun getAllStationEntities(): List<StationEntity> = emptyList()
        override suspend fun getStationById(stationUuid: String): RadioStation? = null
        override suspend fun getEntityById(stationUuid: String): StationEntity? = null
        override fun isStationInLibrary(stationUuid: String): Flow<Boolean> = flowOf(false)
        override suspend fun isStationInLibraryDirect(stationUuid: String): Boolean = false
        override suspend fun addStationToLibrary(station: RadioStation) {}
        override suspend fun insertEntity(entity: StationEntity) {}
        override suspend fun removeStationFromLibrary(stationUuid: String) {}
        override suspend fun updateStations(stations: List<StationEntity>) {}
        override suspend fun addCustomStation(name: String, url: String, favicon: String, tags: List<String>, countryCode: String, languageCodes: List<String>, homepage: String, iso31662: String?, codec: String, bitrate: Int) {}
        override suspend fun updateStation(stationUuid: String, name: String, url: String, favicon: String, tags: List<String>, countryCode: String, languageCodes: List<String>, homepage: String, iso31662: String?, codec: String, bitrate: Int) {}
        override suspend fun uploadAndSaveNewStation(name: String, url: String, homepage: String, favicon: String, countryCode: String, iso31662: String?, languageCodes: List<String>, tags: List<String>, codec: String, bitrate: Int): Result<String> = Result.success("")
        override suspend fun uploadExistingCustomStation(stationUuid: String, name: String, url: String, homepage: String, favicon: String, countryCode: String, iso31662: String?, languageCodes: List<String>, tags: List<String>, codec: String, bitrate: Int): Result<String> = Result.success("")
        override fun getStationsByName(): Flow<List<RadioStation>> = flowOf(emptyList())
        override fun getStationsByNameDescending(): Flow<List<RadioStation>> = flowOf(emptyList())
        override fun getStationsByRecentlyPlayed(): Flow<List<RadioStation>> = flowOf(emptyList())
        override fun getStationsByLeastRecentlyPlayed(): Flow<List<RadioStation>> = flowOf(emptyList())
        override fun getStationsByCustomOrder(): Flow<List<RadioStation>> = flowOf(emptyList())
        override fun getStationsByOldestAdded(): Flow<List<RadioStation>> = flowOf(emptyList())
    }
}
