package com.armanmaurya.internetradio.ui.shared.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.repository.LibraryRepository
import com.armanmaurya.internetradio.domain.repository.RecentRepository
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import com.armanmaurya.internetradio.domain.controller.PlayerController
import com.armanmaurya.internetradio.service.PlaybackService
import com.armanmaurya.internetradio.domain.controller.WidgetController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecentViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recentRepository: RecentRepository,
    private val settingsRepository: SettingsRepository,
    private val libraryRepository: LibraryRepository,
    private val playerController: PlayerController,
    private val widgetController: WidgetController,
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
            val hasStateFilter = !preferences.selectedStateCode.isNullOrBlank()
            val hasLanguageFilter = !preferences.selectedLanguage.isNullOrBlank()
            val hasTagFilter = preferences.selectedTags.isNotEmpty()

            // If no filter criteria are set at all, show everything
            if (!hasQuery && !hasCountryFilter && !hasStateFilter && !hasLanguageFilter && !hasTagFilter) {
                stations
            } else {
                stations.filter { station ->
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
            if (!playerController.playbackState.value.isPlaying) {
                val nextStation = recentRepository.getAllRecent().first().firstOrNull()
                widgetController.cleanStaleWidgetState(nextStation?.name, nextStation?.favicon)
            }
        }
    }

    fun clearAllRecent() {
        viewModelScope.launch {
            recentRepository.clearAllRecent()
            if (!playerController.playbackState.value.isPlaying) {
                widgetController.cleanStaleWidgetState()
            }
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
}