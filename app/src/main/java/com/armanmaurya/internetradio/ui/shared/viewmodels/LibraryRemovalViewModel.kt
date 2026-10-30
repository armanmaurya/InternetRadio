package com.armanmaurya.internetradio.ui.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.domain.controller.ShortcutController
import com.armanmaurya.internetradio.domain.repository.LibraryRepository
import com.armanmaurya.internetradio.domain.repository.ScheduleRepository
import com.armanmaurya.internetradio.domain.usecase.library.RemoveStationFromLibraryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class PendingStationRemoval(
    val stationUuid: String,
    val stationName: String,
    val scheduleCount: Int,
    val hasShortcut: Boolean
)

/**
 * Every "remove from library" button goes through here. A station with schedules or a home screen
 * shortcut is held as [pendingRemoval] until the user confirms in the app-wide sheet, since removing
 * it deletes the schedules and disables the shortcut.
 */
@Singleton
class LibraryRemovalCoordinator @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val scheduleRepository: ScheduleRepository,
    private val shortcutController: ShortcutController,
    private val removeStationFromLibrary: RemoveStationFromLibraryUseCase
) {
    private val _pendingRemoval = MutableStateFlow<PendingStationRemoval?>(null)
    val pendingRemoval: StateFlow<PendingStationRemoval?> = _pendingRemoval.asStateFlow()

    suspend fun requestRemoval(stationUuid: String) {
        val schedules = scheduleRepository.getSchedulesForStation(stationUuid)
        val hasShortcut = shortcutController.hasShortcut(stationUuid)
        if (schedules.isEmpty() && !hasShortcut) {
            removeStationFromLibrary(stationUuid)
            return
        }
        val stationName = libraryRepository.getLibraryStationById(stationUuid)?.name
            ?: schedules.firstOrNull()?.stationName
            ?: return
        _pendingRemoval.value = PendingStationRemoval(stationUuid, stationName, schedules.size, hasShortcut)
    }

    suspend fun confirmRemoval(stationUuid: String) {
        _pendingRemoval.value = null
        removeStationFromLibrary(stationUuid)
    }

    fun dismissRemoval() {
        _pendingRemoval.value = null
    }
}

@HiltViewModel
class LibraryRemovalViewModel @Inject constructor(
    private val coordinator: LibraryRemovalCoordinator
) : ViewModel() {
    val pendingRemoval: StateFlow<PendingStationRemoval?> = coordinator.pendingRemoval

    fun confirmRemoval(stationUuid: String) {
        viewModelScope.launch { coordinator.confirmRemoval(stationUuid) }
    }

    fun dismissRemoval() = coordinator.dismissRemoval()
}
