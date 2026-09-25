package com.armanmaurya.internetradio.ui.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.core.media.prober.StreamProbeResult
import com.armanmaurya.internetradio.core.media.prober.StreamProber
import com.armanmaurya.internetradio.domain.model.AppPreferences
import com.armanmaurya.internetradio.domain.model.LibrarySortOption
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.model.Tag
import com.armanmaurya.internetradio.domain.repository.LibraryRepository
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import com.armanmaurya.internetradio.domain.repository.StationRepository
import com.armanmaurya.internetradio.domain.controller.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import javax.inject.Inject
import okhttp3.OkHttpClient

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val stationRepository: StationRepository,
    private val playerController: PlayerController,
    private val okHttpClient: OkHttpClient,
    private val fileSystemFacade: com.armanmaurya.internetradio.core.system.FileSystemFacade,
    private val systemFacade: com.armanmaurya.internetradio.core.system.SystemFacade,
    private val streamProber: StreamProber
) : ViewModel() {

    // Using useFilterOnFavorites and isGridViewFavorites for now, maybe we can rename these in Settings later
    val useFilter: StateFlow<Boolean> = settingsRepository.appPreferencesFlow
        .map { it.useFilterOnFavorites }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isGridView: StateFlow<Boolean> = settingsRepository.appPreferencesFlow
        .map { it.isGridViewFavorites }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val sortOption: StateFlow<LibrarySortOption> = settingsRepository.appPreferencesFlow
        .map { it.librarySortOption }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibrarySortOption.RECENTLY_ADDED)

    fun setSortOption(option: LibrarySortOption) {
        viewModelScope.launch {
            settingsRepository.setLibrarySortOption(option)
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    private val _tagSearchQuery = MutableStateFlow("")
    
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    val fetchedTags: StateFlow<List<Tag>> = _tagSearchQuery
        .debounce(500)
        .flatMapLatest { query ->
            flow {
                if (query.isNotBlank()) {
                    stationRepository.getTags(query)
                        .onSuccess { emit(it.take(10)) }
                        .onFailure { emit(emptyList()) }
                } else {
                    emit(emptyList())
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onTagSearchQueryChange(query: String) {
        _tagSearchQuery.value = query
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val sortedStationsFlow = settingsRepository.appPreferencesFlow
        .map { it.librarySortOption }
        .distinctUntilChanged()
        .flatMapLatest { sortOpt ->
            when (sortOpt) {
                LibrarySortOption.NAME_A_Z -> libraryRepository.getStationsByName()
                LibrarySortOption.NAME_Z_A -> libraryRepository.getStationsByNameDescending()
                LibrarySortOption.RECENTLY_PLAYED -> libraryRepository.getStationsByRecentlyPlayed()
                LibrarySortOption.LEAST_RECENTLY_PLAYED -> libraryRepository.getStationsByLeastRecentlyPlayed()
                LibrarySortOption.CUSTOM -> libraryRepository.getStationsByCustomOrder()
                LibrarySortOption.RECENTLY_ADDED -> libraryRepository.getAllStations()
                LibrarySortOption.OLDEST_ADDED -> libraryRepository.getStationsByOldestAdded()
            }
        }

    private fun filterStations(
        stationsList: List<RadioStation>,
        preferences: AppPreferences,
        query: String
    ): List<RadioStation> {
        val hasQuery = query.isNotBlank()
        val hasCountryFilter = !preferences.selectedCountryCode.isNullOrBlank()
        val hasStateFilter = !preferences.selectedStateCode.isNullOrBlank()
        val hasLanguageFilter = !preferences.selectedLanguage.isNullOrBlank()
        val hasTagFilter = preferences.selectedTags.isNotEmpty()

        if (!hasQuery && !hasCountryFilter && !hasStateFilter && !hasLanguageFilter && !hasTagFilter) {
            return stationsList
        }

        return stationsList.filter { station ->
            val queryMatch = !hasQuery ||
                    station.name.contains(query, ignoreCase = true) ||
                    station.tags.any { tag -> tag.contains(query, ignoreCase = true) }
            val countryMatch = !hasCountryFilter ||
                    station.countryCode == preferences.selectedCountryCode
            val stateMatch = !hasStateFilter ||
                    station.iso3166_2 == preferences.selectedStateCode
            val languageMatch = if (!hasLanguageFilter) true else {
                val selectedCode = preferences.selectedLanguage!!
                station.languageCodes.contains(selectedCode)
            }
            val tagsMatch = !hasTagFilter ||
                    preferences.selectedTags.any { it in station.tags }

            queryMatch && countryMatch && stateMatch && languageMatch && tagsMatch
        }
    }

    val stations: StateFlow<List<RadioStation>?> = combine(
        sortedStationsFlow,
        settingsRepository.appPreferencesFlow,
        _searchQuery
    ) { stationsList, preferences, query ->
        if (preferences.useFilterOnFavorites) {
            filterStations(stationsList, preferences, query)
        } else {
            stationsList
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val searchStations: StateFlow<List<RadioStation>?> = combine(
        sortedStationsFlow,
        settingsRepository.appPreferencesFlow,
        _searchQuery
    ) { stationsList, preferences, query ->
        filterStations(stationsList, preferences, query)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // Set of all bookmarked UUIDs — used by Browse/Recent to show the bookmark badge
    val stationUuids: StateFlow<Set<String>> = libraryRepository.getAllStations()
        .map { list -> list.map { it.stationUuid }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun toggleFilter() {
        viewModelScope.launch {
            settingsRepository.setUseFilterOnFavorites(!useFilter.value)
        }
    }

    fun setFilterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setUseFilterOnFavorites(enabled)
        }
    }

    fun onGridViewChange(isGrid: Boolean) {
        viewModelScope.launch { settingsRepository.setGridViewFavorites(isGrid) }
    }

    fun isStationInLibrary(stationUuid: String) =
        libraryRepository.isStationInLibrary(stationUuid)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun addStationToLibrary(station: RadioStation) {
        viewModelScope.launch {
            libraryRepository.addStationToLibrary(station)
        }
    }

    fun removeStation(stationUuid: String) {
        viewModelScope.launch {
            libraryRepository.removeStationFromLibrary(stationUuid)
        }
    }

    fun updateStation(
        stationUuid: String,
        name: String,
        url: String,
        favicon: String,
        tags: List<String>,
        countryCode: String,
        languageCodes: List<String>,
        homepage: String,
        iso31662: String? = null,
        codec: String,
        bitrate: Int
    ) {
        viewModelScope.launch {
            libraryRepository.updateStation(
                stationUuid = stationUuid,
                name = name,
                url = url,
                favicon = favicon,
                tags = tags,
                countryCode = countryCode,
                languageCodes = languageCodes,
                homepage = homepage,
                iso31662 = iso31662,
                codec = codec,
                bitrate = bitrate
            )
            val updatedStation = libraryRepository.getStationById(stationUuid)
            if (updatedStation != null && playerController.playbackState.value.currentStation?.stationUuid == stationUuid) {
                playerController.updateCurrentStation(updatedStation)
            }
        }
    }

    fun fetchOriginalStation(stationUuid: String, onResult: (RadioStation?) -> Unit) {
        viewModelScope.launch {
            stationRepository.getStationsByUuid(listOf(stationUuid))
                .onSuccess { stations ->
                    onResult(stations.firstOrNull())
                }
                .onFailure {
                    onResult(null)
                }
        }
    }

    suspend fun probeStream(url: String): StreamProbeResult? {
        return streamProber.probe(url)
    }

    fun addStation(
        name: String,
        url: String,
        favicon: String,
        tags: String,
        countryCode: String,
        languageCodes: String,
        homepage: String,
        iso31662: String? = null,
        codec: String = "unknown",
        bitrate: Int = 0
    ) {
        val tagList = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val langList = languageCodes.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        
        viewModelScope.launch {
            libraryRepository.addCustomStation(
                name = name,
                url = url,
                favicon = favicon,
                tags = tagList,
                countryCode = countryCode,
                languageCodes = langList,
                homepage = homepage,
                iso31662 = iso31662,
                codec = codec,
                bitrate = bitrate
            )
        }
    }

    fun uploadStationToRadioBrowser(
        stationUuid: String,
        name: String,
        url: String,
        homepage: String,
        favicon: String,
        countryCode: String,
        iso31662: String? = null,
        languageCodes: List<String>,
        tags: List<String>,
        codec: String,
        bitrate: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = if (stationUuid.isEmpty()) {
                libraryRepository.uploadAndSaveNewStation(
                    name = name,
                    url = url,
                    homepage = homepage,
                    favicon = favicon,
                    countryCode = countryCode,
                    iso31662 = iso31662,
                    languageCodes = languageCodes,
                    tags = tags,
                    codec = codec,
                    bitrate = bitrate
                )
            } else {
                libraryRepository.uploadExistingCustomStation(
                    stationUuid = stationUuid,
                    name = name,
                    url = url,
                    homepage = homepage,
                    favicon = favicon,
                    countryCode = countryCode,
                    iso31662 = iso31662,
                    languageCodes = languageCodes,
                    tags = tags,
                    codec = codec,
                    bitrate = bitrate
                )
            }
            result.onSuccess { newUuid ->
                // Update player state if the uploaded station is currently playing
                val currentPlayingId = playerController.playbackState.value.currentStation?.stationUuid
                if (currentPlayingId == stationUuid || currentPlayingId == newUuid) {
                    val updatedStation = libraryRepository.getStationById(newUuid)
                    if (updatedStation != null) {
                        playerController.updateCurrentStation(updatedStation, oldUuid = stationUuid)
                    }
                }
                onSuccess()
            }.onFailure { e ->
                onError(e.message ?: "Failed to upload station")
            }
        }
    }

    fun updateStationsOrder(orderedStations: List<RadioStation>) {
        viewModelScope.launch {
            val entities = libraryRepository.getAllStationEntities()
            val updatedEntities = orderedStations.mapIndexedNotNull { index, station ->
                entities.find { it.stationUuid == station.stationUuid }?.copy(orderIndex = index)
            }
            libraryRepository.updateStations(updatedEntities)
        }
    }

    // --- Similar Stations by URL ---
    private val _duplicateStations = MutableStateFlow<List<RadioStation>>(emptyList())
    val duplicateStations = _duplicateStations.asStateFlow()

    private val _isCheckingUrl = MutableStateFlow(false)
    val isCheckingUrl = _isCheckingUrl.asStateFlow()

    private var urlCheckJob: Job? = null

    fun checkDuplicateUrl(url: String) {
        urlCheckJob?.cancel()
        if (url.isBlank()) {
            _duplicateStations.value = emptyList()
            _isCheckingUrl.value = false
            return
        }
        urlCheckJob = viewModelScope.launch {
            delay(500)
            _isCheckingUrl.value = true
            stationRepository.getStationsByUrl(url)
                .onSuccess { _duplicateStations.value = it }
                .onFailure { _duplicateStations.value = emptyList() }
            _isCheckingUrl.value = false
        }
    }

    fun exportStation(context: android.content.Context, uri: android.net.Uri, station: RadioStation, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = com.armanmaurya.internetradio.core.utils.ExportUtils.exportStation(context, uri, station, fileSystemFacade, systemFacade)
            withContext(Dispatchers.Main) {
                onResult(result)
            }
        }
    }
}