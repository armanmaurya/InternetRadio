package com.armanmaurya.internetradio.ui.mobile.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.data.repository.SettingsRepository
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
    val selectedLanguage: String? = null,
    val selectedTags: Set<String> = emptySet(),
    val isPreferencesLoaded: Boolean = false,
    val autoRouteToBrowseOnSearch: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

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
                        selectedLanguage = preferences.selectedLanguage,
                        selectedTags = preferences.selectedTags,
                        selectedTab = if (isFirstLoad) preferences.defaultTab else it.selectedTab,
                        isPreferencesLoaded = true,
                        autoRouteToBrowseOnSearch = preferences.autoRouteToBrowseOnSearch
                    )
                }
                isFirstLoad = false
            }
            .launchIn(viewModelScope)
    }

    fun updateCountry(countryCode: String) {
        viewModelScope.launch {
            settingsRepository.setSelectedCountryCode(countryCode)
        }
    }

    fun updateLanguage(language: String) {
        val normalizedLanguage = if (language == "All Languages") null else language
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

    fun onTabSelected(index: Int) {
        _uiState.update {
            it.copy(selectedTab = index)
        }
    }
}
