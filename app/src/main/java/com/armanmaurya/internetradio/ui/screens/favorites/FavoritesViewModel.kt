package com.armanmaurya.internetradio.ui.screens.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.data.repository.FavoriteRepository
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
class FavoritesViewModel @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val useFilter: StateFlow<Boolean> = settingsRepository.appPreferencesFlow
        .map { it.useFilterOnFavorites }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val favorites: StateFlow<List<RadioStation>> = combine(
        favoriteRepository.getAllFavorites(),
        settingsRepository.appPreferencesFlow
    ) { stations, preferences ->
        if (preferences.useFilterOnFavorites) {
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
            settingsRepository.setUseFilterOnFavorites(!useFilter.value)
        }
    }
}
