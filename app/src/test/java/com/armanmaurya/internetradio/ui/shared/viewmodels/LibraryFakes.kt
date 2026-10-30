package com.armanmaurya.internetradio.ui.shared.viewmodels

import com.armanmaurya.internetradio.data.local.entity.StationEntity
import com.armanmaurya.internetradio.domain.controller.ScheduleController
import com.armanmaurya.internetradio.domain.controller.ShortcutController
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.model.Schedule
import com.armanmaurya.internetradio.domain.repository.LibraryRepository
import com.armanmaurya.internetradio.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

internal class FakeLibraryRepository : LibraryRepository {
    val stationsFlow = MutableStateFlow<List<RadioStation>>(emptyList())
    val removedUuids = mutableListOf<String>()
    override fun getAllStations(): Flow<List<RadioStation>> = stationsFlow
    override fun getAllStationEntitiesFlow(): Flow<List<StationEntity>> = flowOf(emptyList())
    override suspend fun getAllStationEntities(): List<StationEntity> = emptyList()
    override suspend fun getLibraryStationById(stationUuid: String): RadioStation? =
        stationsFlow.value.find { it.stationUuid == stationUuid }
    override suspend fun getEntityById(stationUuid: String): StationEntity? = null
    override fun isStationInLibrary(stationUuid: String): Flow<Boolean> = flowOf(false)
    override suspend fun isStationInLibraryDirect(stationUuid: String): Boolean = false
    override suspend fun addStationToLibrary(station: RadioStation) {}
    override suspend fun insertEntity(entity: StationEntity) {}
    override suspend fun removeStationFromLibrary(stationUuid: String) {
        removedUuids += stationUuid
        stationsFlow.value = stationsFlow.value.filterNot { it.stationUuid == stationUuid }
    }
    override suspend fun updateStations(stations: List<StationEntity>) {}
    override suspend fun addCustomStation(name: String, url: String, favicon: String, tags: List<String>, countryCode: String, languageCodes: List<String>, homepage: String, iso31662: String?, codec: String, bitrate: Int) {}
    override suspend fun updateStation(stationUuid: String, name: String, url: String, favicon: String, tags: List<String>, countryCode: String, languageCodes: List<String>, homepage: String, iso31662: String?, codec: String, bitrate: Int) {}
    override suspend fun uploadAndSaveNewStation(name: String, url: String, homepage: String, favicon: String, countryCode: String, iso31662: String?, languageCodes: List<String>, tags: List<String>, codec: String, bitrate: Int): Result<String> = Result.success("")
    override suspend fun uploadExistingCustomStation(stationUuid: String, name: String, url: String, homepage: String, favicon: String, countryCode: String, iso31662: String?, languageCodes: List<String>, tags: List<String>, codec: String, bitrate: Int): Result<String> = Result.success("")
    override fun getStationsByName(): Flow<List<RadioStation>> = flowOf(emptyList())
    override fun getStationsByNameDescending(): Flow<List<RadioStation>> = flowOf(emptyList())
    override fun getStationsByRecentlyPlayed(): Flow<List<RadioStation>> = flowOf(emptyList())
    override fun getStationsByLeastRecentlyPlayed(): Flow<List<RadioStation>> = flowOf(emptyList())
    override fun getStationsByCustomOrder(): Flow<List<RadioStation>> = flowOf(emptyList())
    override fun getStationsByOldestAdded(): Flow<List<RadioStation>> = flowOf(emptyList())
}

internal class FakeScheduleRepository : ScheduleRepository {
    val schedules = mutableListOf<Schedule>()
    override fun getAllSchedules(): Flow<List<Schedule>> = flowOf(schedules)
    override suspend fun getScheduleById(id: Int): Schedule? = schedules.find { it.id == id }
    override suspend fun getSchedulesForStation(stationUuid: String): List<Schedule> =
        schedules.filter { it.stationUuid == stationUuid }
    override suspend fun deleteSchedulesForStation(stationUuid: String) {
        schedules.removeAll { it.stationUuid == stationUuid }
    }
    override suspend fun insertSchedule(schedule: Schedule): Long {
        schedules += schedule
        return schedule.id.toLong()
    }
    override suspend fun updateSchedule(schedule: Schedule) {}
    override suspend fun deleteSchedule(schedule: Schedule) {
        schedules.removeAll { it.id == schedule.id }
    }
    override suspend fun updateScheduleStatus(id: Int, isEnabled: Boolean) {}
}

internal class FakeScheduleController : ScheduleController {
    val cancelledIds = mutableListOf<Int>()
    override fun schedule(schedule: Schedule) {}
    override fun cancel(scheduleId: Int) {
        cancelledIds += scheduleId
    }
    override fun snooze(scheduleId: Int, minutes: Int) {}
    override fun scheduleRecordingStop(stationUuid: String, durationMinutes: Int, keepPlayback: Boolean) {}
    override fun cancelRecordingStop(stationUuid: String) {}
    override fun schedulePlaybackStop(scheduleId: Int, durationMinutes: Int) {}
    override fun cancelPlaybackStop(scheduleId: Int) {}
}

internal class FakeShortcutController : ShortcutController {
    val shortcutUuids = mutableSetOf<String>()
    override fun initialize() {}
    override suspend fun hasShortcut(stationUuid: String): Boolean = stationUuid in shortcutUuids
    override fun pinStationShortcut(station: RadioStation): Boolean {
        shortcutUuids += station.stationUuid
        return true
    }
    override suspend fun moveStationShortcut(oldUuid: String, newUuid: String) {
        if (shortcutUuids.remove(oldUuid)) shortcutUuids += newUuid
    }
}
