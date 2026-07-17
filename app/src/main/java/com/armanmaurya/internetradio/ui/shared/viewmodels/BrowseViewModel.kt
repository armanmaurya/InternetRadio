package com.armanmaurya.internetradio.ui.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.data.repository.LibraryRepository
import com.armanmaurya.internetradio.data.repository.SettingsRepository
import com.armanmaurya.internetradio.data.repository.StationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrowseUiState(
    val searchQuery: String = "",
    val stations: List<RadioStation> = emptyList(),
    val isLoading: Boolean = false,
    val isNextPageLoading: Boolean = false,
    val canLoadMore: Boolean = true,
    val isSearchActive: Boolean = false,
    val error: String? = null,
    val selectedCountryCode: String? = null,
    val selectedLanguage: String? = null,
    val selectedTags: Set<String> = emptySet(),
    val order: String = "votes",
    val reverse: Boolean = true,
    val isGridView: Boolean = true,
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repository: StationRepository,
    private val settingsRepository: SettingsRepository,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    val libraryStationUuids: StateFlow<Set<String>> = libraryRepository.getAllStations()
        .map { stations -> stations.map { it.stationUuid }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private var currentOffset = 0
    private val pageSize = 60

    init {
        observeSettings()
        observeSearchQuery()
    }

    private fun observeSettings() {
        settingsRepository.appPreferencesFlow
            .map { preferences ->
                BrowseFilterParams(
                    selectedCountryCode = preferences.selectedCountryCode,
                    selectedLanguage = preferences.selectedLanguage,
                    selectedTags = preferences.selectedTags,
                    order = preferences.order,
                    reverse = preferences.reverse,
                    isGridView = preferences.isGridViewBrowse
                )
            }
            .distinctUntilChanged()
            .onEach { params ->
                _uiState.update {
                    it.copy(
                        selectedCountryCode = params.selectedCountryCode,
                        selectedLanguage = params.selectedLanguage,
                        selectedTags = params.selectedTags,
                        order = params.order,
                        reverse = params.reverse,
                        isGridView = params.isGridView
                    )
                }
                // If a search is active, don't reload stations — the search results
                // should be preserved (e.g. when toggling grid/list view).
                if (_uiState.value.isSearchActive) return@onEach
                if (params.selectedCountryCode == null) {
                    detectCountryIfNeeded()
                } else {
                    loadStations(
                        countryCode = params.selectedCountryCode,
                        language = params.selectedLanguage,
                        tags = params.selectedTags
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun detectCountryIfNeeded() {
        viewModelScope.launch {
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
        }
    }

    private fun loadStations(
        countryCode: String?,
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
                    selectedLanguage = language,
                    selectedTags = tags,
                    canLoadMore = true
                )
            }
            val state = _uiState.value
            repository.filterStations(
                countryCode = countryCode?.takeIf { it.isNotBlank() },
                language = language?.takeIf { it.isNotBlank() },
                tagList = tags.joinToString(",").takeIf { it.isNotBlank() },
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

            val result = if (state.searchQuery.isBlank()) {
                repository.filterStations(
                    countryCode = state.selectedCountryCode?.takeIf { it.isNotBlank() },
                    language = state.selectedLanguage?.takeIf { it.isNotBlank() },
                    tagList = state.selectedTags.joinToString(",").takeIf { it.isNotBlank() },
                    order = state.order,
                    reverse = state.reverse,
                    limit = pageSize,
                    offset = currentOffset
                )
            } else {
                repository.filterStations(
                    name = state.searchQuery,
                    language = state.selectedLanguage?.takeIf { it.isNotBlank() },
                    tagList = state.selectedTags.joinToString(",").takeIf { it.isNotBlank() },
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
            repository.filterStations(
                name = query,
                language = state.selectedLanguage?.takeIf { it.isNotBlank() },
                tagList = state.selectedTags.joinToString(",").takeIf { it.isNotBlank() },
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

    fun onOrderChange(order: String) {
        if (_uiState.value.order == order) return
        _uiState.update { it.copy(order = order) }
        viewModelScope.launch { settingsRepository.setSortOrder(order) }
    }

    fun onReverseChange(reverse: Boolean) {
        if (_uiState.value.reverse == reverse) return
        _uiState.update { it.copy(reverse = reverse) }
        viewModelScope.launch { settingsRepository.setSortReverse(reverse) }
    }

    fun onGridViewChange(isGrid: Boolean) {
        viewModelScope.launch { settingsRepository.setGridViewBrowse(isGrid) }
    }

    fun retry() {
        val state = _uiState.value
        if (state.isSearchActive) {
            searchStations(state.searchQuery)
        } else {
            loadStations(state.selectedCountryCode, state.selectedLanguage, state.selectedTags)
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

    private data class BrowseFilterParams(
        val selectedCountryCode: String?,
        val selectedLanguage: String?,
        val selectedTags: Set<String>,
        val order: String,
        val reverse: Boolean,
        val isGridView: Boolean
    )
}
