package com.armanmaurya.internetradio.ui.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.data.repository.LibraryRepository
import com.armanmaurya.internetradio.data.repository.RecentRepository
import com.armanmaurya.internetradio.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecentViewModel @Inject constructor(
    private val recentRepository: RecentRepository,
    private val settingsRepository: SettingsRepository,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    val useFilter: StateFlow<Boolean> = settingsRepository.appPreferencesFlow
        .map { it.useFilterOnRecent }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isGridView: StateFlow<Boolean> = settingsRepository.appPreferencesFlow
        .map { it.isGridViewRecent }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val libraryStationUuids: StateFlow<Set<String>> = libraryRepository.getAllStations()
        .map { stations -> stations.map { it.stationUuid }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    val recentStations: StateFlow<List<RadioStation>?> = combine(
        recentRepository.getAllRecent(),
        settingsRepository.appPreferencesFlow,
        _searchQuery
    ) { stations, preferences, query ->
        if (preferences.useFilterOnRecent) {
            val hasQuery = query.isNotBlank()
            val hasCountryFilter = !preferences.selectedCountryCode.isNullOrBlank()
            val hasLanguageFilter = !preferences.selectedLanguage.isNullOrBlank()
            val hasTagFilter = preferences.selectedTags.isNotEmpty()

            // If no filter criteria are set at all, show everything
            if (!hasQuery && !hasCountryFilter && !hasLanguageFilter && !hasTagFilter) {
                stations
            } else {
                stations.filter { station ->
                    val queryMatch = !hasQuery ||
                            station.name.contains(query, ignoreCase = true) ||
                            station.tags.any { tag -> tag.contains(query, ignoreCase = true) }
                    val countryMatch = !hasCountryFilter ||
                            station.countryCode == preferences.selectedCountryCode
                    val languageMatch = !hasLanguageFilter ||
                            station.language == preferences.selectedLanguage
                    val tagsMatch = !hasTagFilter ||
                            preferences.selectedTags.any { it in station.tags }

                    queryMatch && countryMatch && languageMatch && tagsMatch
                }
            }
        } else {
            stations
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun toggleFilter() {
        viewModelScope.launch {
            settingsRepository.setUseFilterOnRecent(!useFilter.value)
        }
    }

    fun onGridViewChange(isGrid: Boolean) {
        viewModelScope.launch { settingsRepository.setGridViewRecent(isGrid) }
    }

    fun removeRecent(stationUuid: String) {
        viewModelScope.launch {
            recentRepository.removeRecent(stationUuid)
        }
    }

    fun clearAllRecent() {
        viewModelScope.launch {
            recentRepository.clearAllRecent()
        }
    }
}