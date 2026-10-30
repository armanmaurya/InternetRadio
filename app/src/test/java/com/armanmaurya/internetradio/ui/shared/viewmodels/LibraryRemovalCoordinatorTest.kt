package com.armanmaurya.internetradio.ui.shared.viewmodels

import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.model.Schedule
import com.armanmaurya.internetradio.domain.model.ScheduleType
import com.armanmaurya.internetradio.domain.usecase.library.RemoveStationFromLibraryUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LibraryRemovalCoordinatorTest {

    private lateinit var libraryRepository: FakeLibraryRepository
    private lateinit var scheduleRepository: FakeScheduleRepository
    private lateinit var scheduleController: FakeScheduleController
    private lateinit var shortcutController: FakeShortcutController
    private lateinit var coordinator: LibraryRemovalCoordinator

    @Before
    fun setUp() {
        libraryRepository = FakeLibraryRepository()
        scheduleRepository = FakeScheduleRepository()
        scheduleController = FakeScheduleController()
        shortcutController = FakeShortcutController()
        coordinator = LibraryRemovalCoordinator(
            libraryRepository = libraryRepository,
            scheduleRepository = scheduleRepository,
            shortcutController = shortcutController,
            removeStationFromLibrary = RemoveStationFromLibraryUseCase(
                libraryRepository,
                scheduleRepository,
                scheduleController
            )
        )
        libraryRepository.stationsFlow.value = listOf(createStation("station-a", "Rock FM"))
    }

    @Test
    fun requestRemoval_withoutSchedulesOrShortcut_removesImmediately() = runTest {
        coordinator.requestRemoval("station-a")

        assertNull(coordinator.pendingRemoval.value)
        assertEquals(listOf("station-a"), libraryRepository.removedUuids)
    }

    @Test
    fun requestRemoval_withSchedules_waitsForConfirmation() = runTest {
        scheduleRepository.schedules += createSchedule(id = 1, stationUuid = "station-a")

        coordinator.requestRemoval("station-a")

        val pending = coordinator.pendingRemoval.value
        assertEquals("Rock FM", pending?.stationName)
        assertEquals(1, pending?.schedules?.size)
        assertFalse(pending!!.hasShortcut)
        assertTrue(libraryRepository.removedUuids.isEmpty())
    }

    @Test
    fun requestRemoval_withShortcutOnly_waitsForConfirmation() = runTest {
        shortcutController.shortcutUuids += "station-a"

        coordinator.requestRemoval("station-a")

        val pending = coordinator.pendingRemoval.value
        assertTrue(pending!!.hasShortcut)
        assertTrue(pending.schedules.isEmpty())
        assertTrue(libraryRepository.removedUuids.isEmpty())
    }

    @Test
    fun confirmRemoval_removesStationAndCancelsItsSchedules() = runTest {
        scheduleRepository.schedules += createSchedule(id = 1, stationUuid = "station-a")
        scheduleRepository.schedules += createSchedule(id = 2, stationUuid = "station-b")
        coordinator.requestRemoval("station-a")

        coordinator.confirmRemoval("station-a")

        assertNull(coordinator.pendingRemoval.value)
        assertEquals(listOf("station-a"), libraryRepository.removedUuids)
        assertEquals(listOf(1), scheduleController.cancelledIds)
        assertEquals(listOf(2), scheduleRepository.schedules.map { it.id })
    }

    @Test
    fun dismissRemoval_keepsStationAndSchedules() = runTest {
        scheduleRepository.schedules += createSchedule(id = 1, stationUuid = "station-a")
        coordinator.requestRemoval("station-a")

        coordinator.dismissRemoval()

        assertNull(coordinator.pendingRemoval.value)
        assertTrue(libraryRepository.removedUuids.isEmpty())
        assertEquals(1, scheduleRepository.schedules.size)
    }

    private fun createSchedule(id: Int, stationUuid: String) = Schedule(
        id = id,
        stationUuid = stationUuid,
        stationName = "Rock FM",
        type = ScheduleType.PLAYBACK
    )

    private fun createStation(uuid: String, name: String) = RadioStation(
        changeUuid = "",
        stationUuid = uuid,
        name = name,
        url = "http://stream.example.com/$uuid",
        urlResolved = "http://stream.example.com/$uuid",
        homepage = "",
        favicon = "",
        tags = emptyList(),
        country = "",
        countryCode = "",
        state = "",
        iso3166_2 = null,
        language = "",
        languageCodes = emptyList(),
        votes = 0,
        lastChangeTime = "",
        codec = "MP3",
        bitrate = 128,
        lastCheckOk = true,
        lastCheckTime = "",
        lastCheckOkTime = "",
        lastLocalCheckTime = "",
        clickTimestamp = "",
        clickCount = 0,
        clickTrend = 0,
        sslError = false,
        geoLat = null,
        geoLong = null,
        geoDistance = null,
        hasExtendedInfo = false
    )
}
