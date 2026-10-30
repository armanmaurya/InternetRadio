package com.armanmaurya.internetradio.ui.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.domain.controller.ShortcutController
import com.armanmaurya.internetradio.domain.model.GithubRelease
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import com.armanmaurya.internetradio.domain.repository.UpdateRepository
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
    private val settingsRepository: SettingsRepository,
    val systemFacade: com.armanmaurya.internetradio.core.system.SystemFacade,
    val shortcutController: ShortcutController
) : ViewModel() {

    private val _updateAvailable = MutableStateFlow<GithubRelease?>(null)
    val updateAvailable: StateFlow<GithubRelease?> = _updateAvailable.asStateFlow()

    private val _showReviewPrompt = MutableStateFlow(false)
    val showReviewPrompt: StateFlow<Boolean> = _showReviewPrompt.asStateFlow()

    val appPreferences = settingsRepository.appPreferencesFlow

    init {
        viewModelScope.launch {
            val prefs = settingsRepository.appPreferencesFlow.first()
            if (!prefs.hasRatedApp) {
                val newCount = prefs.appLaunchCount + 1
                settingsRepository.setAppLaunchCount(newCount)
                if (newCount == 5 || newCount == 15) {
                    _showReviewPrompt.value = true
                }
            }
        }
    }

    fun dismissReviewPrompt(hasRated: Boolean) {
        _showReviewPrompt.value = false
        if (hasRated) {
            viewModelScope.launch {
                settingsRepository.setHasRatedApp(true)
            }
        }
    }

    fun checkForUpdates(force: Boolean = false, onResult: ((Boolean) -> Unit)? = null) {
        checkForUpdates(systemFacade.getAppVersionName(), force, onResult)
    }

    fun checkForUpdates(currentVersion: String, force: Boolean = false, onResult: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val appPreferences = settingsRepository.appPreferencesFlow.first()
            if (!force && appPreferences.disableUpdateCheck) {
                onResult?.invoke(false)
                return@launch
            }
            val lastCheckTime = appPreferences.lastUpdateCheckTime
            val currentTime = System.currentTimeMillis()
            val twentyFourHours = 24 * 60 * 60 * 1000L

            if (force || currentTime - lastCheckTime > twentyFourHours) {
                val isNightly = currentVersion.contains("nightly", ignoreCase = true)
                val release = updateRepository.getLatestRelease(isNightly)
                var hasUpdate = false
                if (release != null) {
                    if (isNewerVersion(currentVersion, release, isNightly)) {
                        _updateAvailable.value = release
                        hasUpdate = true
                    }
                    settingsRepository.setLastUpdateCheckTime(currentTime)
                }
                onResult?.invoke(hasUpdate)
            } else {
                onResult?.invoke(false)
            }
        }
    }

    fun dismissUpdate() {
        _updateAvailable.value = null
    }

    private fun isNewerVersion(current: String, release: GithubRelease, isNightly: Boolean): Boolean {
        if (isNightly) {
            val currentMatch = Regex("""(\d{4}-\d{2}-\d{2})""").find(current)
            val releaseMatch = Regex("""(\d{4}-\d{2}-\d{2})""").find(release.name ?: release.tag_name)
            if (currentMatch != null && releaseMatch != null) {
                val currentDate = currentMatch.groupValues[1].replace("-", "").toIntOrNull() ?: 0
                val releaseDate = releaseMatch.groupValues[1].replace("-", "").toIntOrNull() ?: 0
                return releaseDate > currentDate
            }
            return false
        }

        val latest = release.tag_name
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
