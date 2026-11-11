package com.armanmaurya.internetradio.ui.mobile.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.domain.controller.RecordingPlaybackController
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val searchQuery: String = "",
    val selectedTab: Int = 0,
    val selectedCountryCode: String? = null,
    val selectedStateCode: String? = null,
    val selectedLanguage: String? = null,
    val selectedTags: Set<String> = emptySet(),
    val isPreferencesLoaded: Boolean = false,
    val autoRouteToBrowseOnSearch: Boolean = true,
    val isSearchBarHidden: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val recordingPlaybackController: RecordingPlaybackController
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val isPlayingRecording: StateFlow<Boolean> = recordingPlaybackController.isPlaying

    init {
        observeSettings()
    }

    private var isFirstLoad = true

    private fun observeSettings() {
        settingsRepository.appPreferencesFlow
            .onEach { preferences ->
                _uiState.update {
                    it.copy(
                        selectedCountryCode = preferences.selectedCountryCode,
                        selectedStateCode = preferences.selectedStateCode,
                        selectedLanguage = preferences.selectedLanguage,
                        selectedTags = preferences.selectedTags,
                        selectedTab = if (isFirstLoad) preferences.defaultTab else it.selectedTab,
                        isPreferencesLoaded = true,
                        autoRouteToBrowseOnSearch = preferences.autoRouteToBrowseOnSearch,
                        isSearchBarHidden = preferences.isSearchBarHidden
                    )
                }
                isFirstLoad = false
            }
            .launchIn(viewModelScope)
    }

    fun updateCountry(countryCode: String, stateCode: String? = null) {
        viewModelScope.launch {
            settingsRepository.setSelectedCountryCode(countryCode)
            settingsRepository.setSelectedStateCode(stateCode)
        }
    }

    fun updateLanguage(language: String) {
        val normalizedLanguage = if (language == "All Languages" || language.isBlank()) null else language
        viewModelScope.launch {
            settingsRepository.setSelectedLanguage(normalizedLanguage)
        }
    }

    fun updateTags(tags: Set<String>) {
        viewModelScope.launch {
            settingsRepository.setSelectedTags(tags)
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update {
            it.copy(searchQuery = query)
        }
    }

    fun onSearchCleared() {
        _uiState.update {
            it.copy(searchQuery = "")
        }
    }

    fun setSearchBarHidden(hidden: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSearchBarHidden(hidden)
        }
    }

    fun onTabSelected(index: Int) {
        _uiState.update {
            it.copy(selectedTab = index)
        }
    }

    fun routeToRecording(stationName: String?, uriString: String?) {
        if (!stationName.isNullOrBlank() && !uriString.isNullOrBlank()) {
            recordingPlaybackController.routeToRecording(stationName, uriString)
        } else {
            recordingPlaybackController.requestRouteToPlayingRecording()
        }
    }
}
