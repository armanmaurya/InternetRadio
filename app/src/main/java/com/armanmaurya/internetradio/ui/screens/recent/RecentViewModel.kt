package com.armanmaurya.internetradio.ui.screens.recent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.data.repository.RecentRepository
import com.armanmaurya.internetradio.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecentViewModel @Inject constructor(
    private val recentRepository: RecentRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val useFilter: StateFlow<Boolean> = settingsRepository.appPreferencesFlow
        .map { it.useFilterOnRecent }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val recentStations: StateFlow<List<RadioStation>> = combine(
        recentRepository.getAllRecent(),
        settingsRepository.appPreferencesFlow
    ) { stations, preferences ->
        if (preferences.useFilterOnRecent) {
            stations.filter { station ->
                val countryMatch = preferences.selectedCountryCode == null || 
                                 station.countryCode == preferences.selectedCountryCode
                val languageMatch = preferences.selectedLanguage == null || 
                                  station.language == preferences.selectedLanguage
                val tagsMatch = preferences.selectedTags.isEmpty() || 
                              preferences.selectedTags.any { it in station.tags }
                
                countryMatch && languageMatch && tagsMatch
            }
        } else {
            stations
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun toggleFilter() {
        viewModelScope.launch {
            settingsRepository.setUseFilterOnRecent(!useFilter.value)
        }
    }
}
