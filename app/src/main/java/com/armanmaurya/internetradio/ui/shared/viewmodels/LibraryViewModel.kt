package com.armanmaurya.internetradio.ui.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.data.repository.LibraryRepository
import com.armanmaurya.internetradio.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    // Using useFilterOnFavorites and isGridViewFavorites for now, maybe we can rename these in Settings later
    val useFilter: StateFlow<Boolean> = settingsRepository.appPreferencesFlow
        .map { it.useFilterOnFavorites }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isGridView: StateFlow<Boolean> = settingsRepository.appPreferencesFlow
        .map { it.isGridViewFavorites }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val stations: StateFlow<List<RadioStation>> = combine(
        libraryRepository.getAllStations(),
        settingsRepository.appPreferencesFlow
    ) { stationsList, preferences ->
        if (preferences.useFilterOnFavorites) {
            val hasCountryFilter = !preferences.selectedCountryCode.isNullOrBlank()
            val hasLanguageFilter = !preferences.selectedLanguage.isNullOrBlank()
            val hasTagFilter = preferences.selectedTags.isNotEmpty()

            // If no filter criteria are set at all, show everything
            if (!hasCountryFilter && !hasLanguageFilter && !hasTagFilter) {
                stationsList
            } else {
                stationsList.filter { station ->
                    val countryMatch = !hasCountryFilter ||
                            station.countryCode == preferences.selectedCountryCode
                    val languageMatch = !hasLanguageFilter ||
                            station.language == preferences.selectedLanguage
                    val tagsMatch = !hasTagFilter ||
                            preferences.selectedTags.any { it in station.tags }

                    countryMatch && languageMatch && tagsMatch
                }
            }
        } else {
            stationsList
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
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

    fun updateStation(stationUuid: String, name: String, url: String, favicon: String, tags: List<String>) {
        viewModelScope.launch {
            libraryRepository.updateStation(
                stationUuid = stationUuid,
                name = name,
                url = url,
                favicon = favicon,
                tags = tags
            )
        }
    }

    fun addStation(
        name: String,
        url: String,
        favicon: String,
        tags: String,
        country: String,
        state: String,
        language: String
    ) {
        val tagList = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        viewModelScope.launch {
            libraryRepository.addCustomStation(
                name = name,
                url = url,
                favicon = favicon,
                tags = tagList,
                country = country,
                countryCode = state,
                language = language
            )
        }
    }
}