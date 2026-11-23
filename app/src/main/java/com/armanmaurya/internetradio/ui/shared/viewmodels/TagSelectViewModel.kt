package com.armanmaurya.internetradio.ui.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.domain.model.Tag
import com.armanmaurya.internetradio.domain.repository.StationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TagSelectUiState(
    val tags: List<Tag> = emptyList(),
    val selectedTags: Set<String> = emptySet(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class TagSelectViewModel @Inject constructor(
    private val repository: StationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TagSelectUiState())
    val uiState: StateFlow<TagSelectUiState> = _uiState.asStateFlow()

    init {
        loadTags()
    }

    fun setInitialTags(tags: Set<String>) {
        _uiState.update { it.copy(selectedTags = tags) }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleSearch() {
        _uiState.update { 
            it.copy(
                isSearchActive = !it.isSearchActive,
                searchQuery = if (it.isSearchActive) "" else it.searchQuery
            ) 
        }
    }

    fun toggleTagSelection(tagName: String) {
        val lowerTag = tagName.lowercase()
        _uiState.update { state ->
            val newSelected = if (state.selectedTags.contains(lowerTag)) {
                state.selectedTags - lowerTag
            } else {
                state.selectedTags + lowerTag
            }
            state.copy(selectedTags = newSelected)
        }
    }

    fun addCustomTag(tagName: String) {
        if (tagName.isBlank()) return
        _uiState.update { state ->
            state.copy(
                selectedTags = state.selectedTags + tagName.trim().lowercase(),
                searchQuery = ""
            )
        }
    }

    fun clearSelectedTags() {
        _uiState.update { it.copy(selectedTags = emptySet()) }
    }

    private fun loadTags() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.getTags(prioritizeLocal = true)
                .onSuccess { tags ->
                    _uiState.update { it.copy(tags = tags, isLoading = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message, isLoading = false) }
                }
        }
    }
}
