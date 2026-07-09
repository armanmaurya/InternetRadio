package com.armanmaurya.internetradio.ui.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.data.model.GithubRelease
import com.armanmaurya.internetradio.data.repository.SettingsRepository
import com.armanmaurya.internetradio.data.repository.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val updateRepository: UpdateRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _updateAvailable = MutableStateFlow<GithubRelease?>(null)
    val updateAvailable: StateFlow<GithubRelease?> = _updateAvailable.asStateFlow()

    fun checkForUpdates(currentVersion: String, force: Boolean = false) {
        viewModelScope.launch {
            val appPreferences = settingsRepository.appPreferencesFlow.first()
            val lastCheckTime = appPreferences.lastUpdateCheckTime
            val currentTime = System.currentTimeMillis()
            val twentyFourHours = 24 * 60 * 60 * 1000L

            if (force || currentTime - lastCheckTime > twentyFourHours) {
                val release = updateRepository.getLatestRelease()
                if (release != null) {
                    val latestVersion = release.tag_name
                    if (isNewerVersion(currentVersion, latestVersion)) {
                        _updateAvailable.value = release
                    }
                }
                settingsRepository.setLastUpdateCheckTime(currentTime)
            }
        }
    }

    fun dismissUpdate() {
        _updateAvailable.value = null
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        val currentParts = current.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val latestParts = latest.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }

        val maxLen = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until maxLen) {
            val c = currentParts.getOrElse(i) { 0 }
            val l = latestParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
