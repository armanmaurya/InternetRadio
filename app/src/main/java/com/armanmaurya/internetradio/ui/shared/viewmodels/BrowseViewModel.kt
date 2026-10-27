package com.armanmaurya.internetradio.ui.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.repository.LibraryRepository
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import com.armanmaurya.internetradio.domain.repository.StationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrowseUiState(
    val searchQuery: String = "",
    val stations: List<RadioStation> = emptyList(),
    val isLoading: Boolean = true,
    val isNextPageLoading: Boolean = false,
    val canLoadMore: Boolean = true,
    val isSearchActive: Boolean = false,
    val error: String? = null,
    val selectedCountryCode: String? = null,
    val selectedStateCode: String? = null,
    val selectedLanguage: String? = null,
    val selectedTags: Set<String> = emptySet(),
    val order: String = "votes",
    val reverse: Boolean = true,
    val isGridView: Boolean = true,
    val isVerified: Boolean = false
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repository: StationRepository,
    private val settingsRepository: SettingsRepository,
    private val libraryRepository: LibraryRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    val libraryStationUuids: StateFlow<Set<String>> = libraryRepository.getAllStations()
        .map { stations -> stations.map { it.stationUuid }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private var currentOffset = 0
    private val pageSize = 60
    private var isInitialSettingsLoad = true
    private var isDetectingCountry = false

    init {
        observeSettings()
        observeSearchQuery()
    }

    private fun observeSettings() {
        settingsRepository.appPreferencesFlow
            .map { preferences ->
                BrowseFilterParams(
                    selectedCountryCode = preferences.selectedCountryCode,
                    selectedStateCode = preferences.selectedStateCode,
                    selectedLanguage = preferences.selectedLanguage,
                    selectedTags = preferences.selectedTags,
                    order = preferences.order,
                    reverse = preferences.reverse,
                    isGridView = preferences.isGridViewBrowse
                )
            }
            .distinctUntilChanged()
            .onEach { params ->
                val oldState = _uiState.value
                val isFirst = isInitialSettingsLoad
                isInitialSettingsLoad = false

                _uiState.update {
                    it.copy(
                        selectedCountryCode = params.selectedCountryCode,
                        selectedStateCode = params.selectedStateCode,
                        selectedLanguage = params.selectedLanguage,
                        selectedTags = params.selectedTags,
                        order = params.order,
                        reverse = params.reverse,
                        isGridView = params.isGridView
                    )
                }

                val filtersChanged = oldState.selectedCountryCode != params.selectedCountryCode ||
                        oldState.selectedStateCode != params.selectedStateCode ||
                        oldState.selectedLanguage != params.selectedLanguage ||
                        oldState.selectedTags != params.selectedTags ||
                        oldState.order != params.order ||
                        oldState.reverse != params.reverse

                if (!filtersChanged && !isFirst) return@onEach

                if (_uiState.value.isSearchActive) {
                    searchStations(_uiState.value.searchQuery)
                } else if (params.selectedCountryCode == null) {
                    detectCountryIfNeeded()
                } else {
                    loadStations(
                        countryCode = params.selectedCountryCode,
                        stateCode = params.selectedStateCode,
                        language = params.selectedLanguage,
                        tags = params.selectedTags
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun detectCountryIfNeeded() {
        if (isDetectingCountry) return
        isDetectingCountry = true
        viewModelScope.launch {
            try {
                if (_uiState.value.selectedCountryCode == null) {
                    _uiState.update { it.copy(isLoading = true) }
                    repository.getCurrentCountryCode()
                        .onSuccess { countryCode ->
                            settingsRepository.setSelectedCountryCode(countryCode)
                        }
                        .onFailure {
                            _uiState.update { it.copy(isLoading = false, selectedCountryCode = null) }
                            loadStations(null)
                        }
                }
            } finally {
                isDetectingCountry = false
            }
        }
    }

    private fun loadStations(
        countryCode: String?,
        stateCode: String? = _uiState.value.selectedStateCode,
        language: String? = _uiState.value.selectedLanguage,
        tags: Set<String> = _uiState.value.selectedTags
    ) {
        viewModelScope.launch {
            currentOffset = 0
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    selectedCountryCode = countryCode,
                    selectedStateCode = stateCode,
                    selectedLanguage = language,
                    selectedTags = tags,
                    canLoadMore = true
                )
            }
            val state = _uiState.value
            
            val apiLanguageQuery = state.selectedLanguage?.let { code ->
                com.neovisionaries.i18n.LanguageAlpha3Code.getByCodeIgnoreCase(code)?.getName()?.lowercase() ?: code.lowercase()
            }?.takeIf { it.isNotBlank() }

            val apiStateQuery = if (countryCode != null && stateCode != null) {
                com.armanmaurya.internetradio.core.utils.StateUtils.getStateNameByCode(
                    context,
                    countryCode, stateCode
                )
            } else null

            repository.filterStations(
                countryCode = countryCode?.takeIf { it.isNotBlank() },
                state = apiStateQuery?.takeIf { it.isNotBlank() },
                language = apiLanguageQuery,
                tagList = tags.joinToString(",").takeIf { it.isNotBlank() },
                hasExtendedInfo = state.isVerified.takeIf { it },
                order = state.order,
                reverse = state.reverse,
                limit = pageSize,
                offset = currentOffset
            )
                .onSuccess { stations ->
                    _uiState.update {
                        it.copy(
                            stations = stations.distinctBy { it.stationUuid },
                            isLoading = false,
                            canLoadMore = stations.size >= pageSize
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message, isLoading = false) }
                }
        }
    }

    fun loadMoreStations() {
        var shouldProceed = false
        _uiState.update {
            if (!it.isLoading && !it.isNextPageLoading && it.canLoadMore) {
                shouldProceed = true
                it.copy(isNextPageLoading = true)
            } else {
                it
            }
        }
        if (!shouldProceed) return

        viewModelScope.launch {
            val state = _uiState.value
            currentOffset += pageSize

            val isUrl = android.util.Patterns.WEB_URL.matcher(state.searchQuery).matches()
            
            val apiLanguageQuery = state.selectedLanguage?.let { code ->
                com.neovisionaries.i18n.LanguageAlpha3Code.getByCodeIgnoreCase(code)?.getName()?.lowercase() ?: code.lowercase()
            }?.takeIf { it.isNotBlank() }

            val result = if (state.searchQuery.isBlank()) {
                repository.filterStations(
                    countryCode = state.selectedCountryCode?.takeIf { it.isNotBlank() },
                    language = apiLanguageQuery,
                    tagList = state.selectedTags.joinToString(",").takeIf { it.isNotBlank() },
                    hasExtendedInfo = state.isVerified.takeIf { it },
                    order = state.order,
                    reverse = state.reverse,
                    limit = pageSize,
                    offset = currentOffset
                )
            } else if (isUrl) {
                Result.success(emptyList<RadioStation>())
            } else {
                repository.filterStations(
                    name = state.searchQuery,
                    countryCode = state.selectedCountryCode?.takeIf { it.isNotBlank() },
                    language = apiLanguageQuery,
                    tagList = state.selectedTags.joinToString(",").takeIf { it.isNotBlank() },
                    hasExtendedInfo = state.isVerified.takeIf { it },
                    order = state.order,
                    reverse = state.reverse,
                    limit = pageSize,
                    offset = currentOffset
                )
            }

            result.onSuccess { newStations ->
                _uiState.update {
                    it.copy(
                        stations = (it.stations + newStations).distinctBy { station -> station.stationUuid },
                        isNextPageLoading = false,
                        canLoadMore = newStations.size >= pageSize
                    )
                }
            }
            .onFailure {
                _uiState.update { it.copy(isNextPageLoading = false) }
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeSearchQuery() {
        _uiState
            .map { it.searchQuery }
            .distinctUntilChanged()
            .drop(1)
            .debounce(400)
            .onEach { query ->
                if (query.isBlank()) {
                    loadStations(_uiState.value.selectedCountryCode)
                } else {
                    searchStations(query)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun searchStations(query: String) {
        viewModelScope.launch {
            currentOffset = 0
            _uiState.update { it.copy(isLoading = true, error = null, canLoadMore = true) }
            val state = _uiState.value
            
            val isUrl = android.util.Patterns.WEB_URL.matcher(query).matches()
            
            val apiLanguageQuery = state.selectedLanguage?.let { code ->
                com.neovisionaries.i18n.LanguageAlpha3Code.getByCodeIgnoreCase(code)?.getName()?.lowercase() ?: code.lowercase()
            }?.takeIf { it.isNotBlank() }
            
            val result = if (isUrl) {
                repository.getStationsByUrl(query)
            } else {
                repository.filterStations(
                    name = query,
                    countryCode = state.selectedCountryCode?.takeIf { it.isNotBlank() },
                    language = apiLanguageQuery,
                    tagList = state.selectedTags.joinToString(",").takeIf { it.isNotBlank() },
                    hasExtendedInfo = state.isVerified.takeIf { it },
                    order = state.order,
                    reverse = state.reverse,
                    limit = pageSize,
                    offset = currentOffset
                )
            }

            result.onSuccess { stations ->
                    _uiState.update {
                        it.copy(
                            stations = stations.distinctBy { it.stationUuid },
                            isLoading = false,
                            canLoadMore = if (isUrl) false else stations.size >= pageSize
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message, isLoading = false) }
                }
        }
    }

    fun onOrderChange(order: String) {
        if (_uiState.value.order == order) return
        viewModelScope.launch { settingsRepository.setSortOrder(order) }
    }

    fun onReverseChange(reverse: Boolean) {
        if (_uiState.value.reverse == reverse) return
        viewModelScope.launch { settingsRepository.setSortReverse(reverse) }
    }

    fun onGridViewChange(isGrid: Boolean) {
        viewModelScope.launch { settingsRepository.setGridViewBrowse(isGrid) }
    }

    fun onVerifiedChange(isVerified: Boolean) {
        if (_uiState.value.isVerified == isVerified) return
        _uiState.update { it.copy(isVerified = isVerified) }
        retry()
    }

    fun retry() {
        val state = _uiState.value
        if (state.isSearchActive) {
            searchStations(state.searchQuery)
        } else if (state.selectedCountryCode == null) {
            detectCountryIfNeeded()
        } else {
            loadStations(state.selectedCountryCode, state.selectedStateCode, state.selectedLanguage, state.selectedTags)
        }
    }

    /** Called by HomeScreen to forward the search query from HomeViewModel */
    fun onSearchQueryChange(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                isSearchActive = query.isNotBlank()
            )
        }
    }

    fun toggleLibrary(station: RadioStation) {
        viewModelScope.launch {
            if (libraryStationUuids.value.contains(station.stationUuid)) {
                libraryRepository.removeStationFromLibrary(station.stationUuid)
            } else {
                libraryRepository.addStationToLibrary(station)
            }
        }
    }

    private data class BrowseFilterParams(
        val selectedCountryCode: String?,
        val selectedStateCode: String?,
        val selectedLanguage: String?,
        val selectedTags: Set<String>,
        val order: String,
        val reverse: Boolean,
        val isGridView: Boolean
    )
}
