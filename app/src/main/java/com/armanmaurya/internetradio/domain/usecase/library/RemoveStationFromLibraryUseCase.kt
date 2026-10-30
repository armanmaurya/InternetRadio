package com.armanmaurya.internetradio.domain.usecase.library

import com.armanmaurya.internetradio.domain.controller.ScheduleController
import com.armanmaurya.internetradio.domain.repository.LibraryRepository
import com.armanmaurya.internetradio.domain.repository.ScheduleRepository
import javax.inject.Inject

/**
 * Schedules only use library stations, so removing a station also cancels and deletes its schedules.
 * Its home screen shortcut is disabled by [com.armanmaurya.internetradio.domain.controller.ShortcutController]
 * once the library changes.
 */
class RemoveStationFromLibraryUseCase @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val scheduleRepository: ScheduleRepository,
    private val scheduleController: ScheduleController
) {
    suspend operator fun invoke(stationUuid: String) {
        scheduleRepository.getSchedulesForStation(stationUuid).forEach { schedule ->
            scheduleController.cancel(schedule.id)
        }
        scheduleRepository.deleteSchedulesForStation(stationUuid)
        libraryRepository.removeStationFromLibrary(stationUuid)
    }
}
