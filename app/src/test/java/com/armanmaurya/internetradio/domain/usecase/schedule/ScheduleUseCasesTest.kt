package com.armanmaurya.internetradio.domain.usecase.schedule

import com.armanmaurya.internetradio.data.local.entity.ScheduleEntity
import com.armanmaurya.internetradio.data.local.entity.toDomain
import com.armanmaurya.internetradio.data.local.entity.toEntity
import com.armanmaurya.internetradio.domain.controller.ScheduleController
import com.armanmaurya.internetradio.domain.model.Schedule
import com.armanmaurya.internetradio.domain.model.ScheduleType
import com.armanmaurya.internetradio.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleUseCasesTest {

    private class FakeScheduleRepository : ScheduleRepository {
        val schedules = mutableListOf<Schedule>()
        val updatedStatuses = mutableMapOf<Int, Boolean>()

        override fun getAllSchedules(): Flow<List<Schedule>> = flowOf(schedules)

        override suspend fun getScheduleById(id: Int): Schedule? =
            schedules.find { it.id == id }

        override suspend fun insertSchedule(schedule: Schedule): Long {
            val generatedId = if (schedule.id == 0) (schedules.maxOfOrNull { it.id } ?: 0) + 1 else schedule.id
            schedules.add(schedule.copy(id = generatedId))
            return generatedId.toLong()
        }

        override suspend fun updateSchedule(schedule: Schedule) {
            val index = schedules.indexOfFirst { it.id == schedule.id }
            if (index != -1) {
                schedules[index] = schedule
            } else {
                schedules.add(schedule)
            }
        }

        override suspend fun deleteSchedule(schedule: Schedule) {
            schedules.removeAll { it.id == schedule.id }
        }

        override suspend fun updateScheduleStatus(id: Int, isEnabled: Boolean) {
            updatedStatuses[id] = isEnabled
            val index = schedules.indexOfFirst { it.id == id }
            if (index != -1) {
                schedules[index] = schedules[index].copy(isEnabled = isEnabled)
            }
        }
    }

    private class FakeScheduleController : ScheduleController {
        val scheduledList = mutableListOf<Schedule>()
        val cancelledIds = mutableListOf<Int>()
        val recordingStops = mutableListOf<Triple<String, Int, Boolean>>()
        val recordingStopCancels = mutableListOf<String>()
        val snoozedAlarms = mutableListOf<Pair<Int, Int>>()

        override fun schedule(schedule: Schedule) {
            scheduledList.add(schedule)
        }

        override fun cancel(scheduleId: Int) {
            cancelledIds.add(scheduleId)
        }

        override fun snooze(scheduleId: Int, minutes: Int) {
            snoozedAlarms.add(scheduleId to minutes)
        }

        override fun scheduleRecordingStop(stationUuid: String, durationMinutes: Int, keepPlayback: Boolean) {
            recordingStops.add(Triple(stationUuid, durationMinutes, keepPlayback))
        }

        override fun cancelRecordingStop(stationUuid: String) {
            recordingStopCancels.add(stationUuid)
        }
    }

    @Test
    fun testEntityToDomainAndDomainToEntityMapping() {
        val entity = ScheduleEntity(
            id = 42,
            stationUuid = "station-123",
            stationName = "Jazz FM",
            type = ScheduleType.RECORD,
            triggerTimeInMillis = 123456789L,
            durationMinutes = 60,
            isRecurring = true,
            daysOfWeek = "1,2,3",
            timeHour = 14,
            timeMinute = 30,
            isEnabled = true,
            volumeLevel = 0.8f,
            keepPlayback = true,
            playOnRecording = false,
            scheduleName = "My Jazz Recording"
        )

        val domain = entity.toDomain()
        assertEquals(entity.id, domain.id)
        assertEquals(entity.stationUuid, domain.stationUuid)
        assertEquals(entity.stationName, domain.stationName)
        assertEquals(entity.type, domain.type)
        assertEquals(entity.triggerTimeInMillis, domain.triggerTimeInMillis)
        assertEquals(entity.durationMinutes, domain.durationMinutes)
        assertEquals(entity.isRecurring, domain.isRecurring)
        assertEquals(entity.daysOfWeek, domain.daysOfWeek)
        assertEquals(entity.timeHour, domain.timeHour)
        assertEquals(entity.timeMinute, domain.timeMinute)
        assertEquals(entity.isEnabled, domain.isEnabled)
        assertEquals(entity.volumeLevel, domain.volumeLevel, 0.001f)
        assertEquals(entity.keepPlayback, domain.keepPlayback)
        assertEquals(entity.playOnRecording, domain.playOnRecording)
        assertEquals(entity.scheduleName, domain.scheduleName)

        val convertedEntity = domain.toEntity()
        assertEquals(entity, convertedEntity)
    }

    @Test
    fun testSaveScheduleUseCase_newScheduleEnabled() = runBlocking {
        val repo = FakeScheduleRepository()
        val controller = FakeScheduleController()
        val saveUseCase = SaveScheduleUseCase(repo, controller)

        val newSchedule = Schedule(
            id = 0,
            stationUuid = "uuid-1",
            stationName = "Station 1",
            type = ScheduleType.PLAYBACK,
            isEnabled = true
        )

        val resultId = saveUseCase(newSchedule)
        assertEquals(1L, resultId)
        assertEquals(1, repo.schedules.size)
        assertEquals(1, controller.scheduledList.size)
        assertEquals(1, controller.scheduledList.first().id)
    }

    @Test
    fun testSaveScheduleUseCase_newScheduleDisabled() = runBlocking {
        val repo = FakeScheduleRepository()
        val controller = FakeScheduleController()
        val saveUseCase = SaveScheduleUseCase(repo, controller)

        val newSchedule = Schedule(
            id = 0,
            stationUuid = "uuid-2",
            stationName = "Station 2",
            type = ScheduleType.PLAYBACK,
            isEnabled = false
        )

        val resultId = saveUseCase(newSchedule)
        assertEquals(1L, resultId)
        assertEquals(1, repo.schedules.size)
        assertTrue(controller.scheduledList.isEmpty())
    }

    @Test
    fun testSaveScheduleUseCase_existingScheduleUpdated() = runBlocking {
        val repo = FakeScheduleRepository()
        val controller = FakeScheduleController()
        val saveUseCase = SaveScheduleUseCase(repo, controller)

        val existing = Schedule(
            id = 10,
            stationUuid = "uuid-10",
            stationName = "Station 10",
            type = ScheduleType.RECORD,
            isEnabled = true
        )
        repo.schedules.add(existing)

        val updated = existing.copy(stationName = "Station 10 Renamed")
        saveUseCase(updated)

        assertEquals("Station 10 Renamed", repo.schedules.first { it.id == 10 }.stationName)
        assertEquals(1, controller.scheduledList.size)
        assertEquals(10, controller.scheduledList.first().id)
    }

    @Test
    fun testSaveScheduleUseCase_existingScheduleDisabledCancelsAlarm() = runBlocking {
        val repo = FakeScheduleRepository()
        val controller = FakeScheduleController()
        val saveUseCase = SaveScheduleUseCase(repo, controller)

        val existing = Schedule(
            id = 10,
            stationUuid = "uuid-10",
            stationName = "Station 10",
            type = ScheduleType.RECORD,
            isEnabled = false
        )
        repo.schedules.add(existing)

        saveUseCase(existing)

        assertTrue(controller.cancelledIds.contains(10))
    }

    @Test
    fun testToggleScheduleUseCase() = runBlocking {
        val repo = FakeScheduleRepository()
        val controller = FakeScheduleController()
        val toggleUseCase = ToggleScheduleUseCase(repo, controller)

        val schedule = Schedule(
            id = 5,
            stationUuid = "uuid-5",
            stationName = "Station 5",
            type = ScheduleType.PLAYBACK,
            isEnabled = true
        )
        repo.schedules.add(schedule)

        // Disable
        toggleUseCase(schedule, false)
        assertEquals(false, repo.updatedStatuses[5])
        assertTrue(controller.cancelledIds.contains(5))

        // Enable
        toggleUseCase(schedule.copy(isEnabled = false), true)
        assertEquals(true, repo.updatedStatuses[5])
        assertEquals(5, controller.scheduledList.last().id)
    }

    @Test
    fun testDeleteScheduleUseCase() = runBlocking {
        val repo = FakeScheduleRepository()
        val controller = FakeScheduleController()
        val deleteUseCase = DeleteScheduleUseCase(repo, controller)

        val schedule = Schedule(
            id = 7,
            stationUuid = "uuid-7",
            stationName = "Station 7",
            type = ScheduleType.PLAYBACK
        )
        repo.schedules.add(schedule)

        deleteUseCase(schedule)

        assertTrue(controller.cancelledIds.contains(7))
        assertTrue(repo.schedules.none { it.id == 7 })
    }

    @Test
    fun testRescheduleAllSchedulesUseCase() = runBlocking {
        val repo = FakeScheduleRepository()
        val controller = FakeScheduleController()
        val rescheduleUseCase = RescheduleAllSchedulesUseCase(repo, controller)

        repo.schedules.add(Schedule(id = 1, stationUuid = "s1", stationName = "S1", type = ScheduleType.PLAYBACK, isEnabled = true))
        repo.schedules.add(Schedule(id = 2, stationUuid = "s2", stationName = "S2", type = ScheduleType.PLAYBACK, isEnabled = false))
        repo.schedules.add(Schedule(id = 3, stationUuid = "s3", stationName = "S3", type = ScheduleType.RECORD, isEnabled = true))

        rescheduleUseCase()

        // Only id 1 and id 3 should be scheduled
        assertEquals(2, controller.scheduledList.size)
        assertEquals(listOf(1, 3), controller.scheduledList.map { it.id })
    }

    @Test
    fun testSnoozeScheduleUseCase() {
        val controller = FakeScheduleController()
        val snoozeUseCase = SnoozeScheduleUseCase(controller)

        snoozeUseCase(scheduleId = 15, minutes = 10)

        assertEquals(1, controller.snoozedAlarms.size)
        assertEquals(15 to 10, controller.snoozedAlarms.first())
    }
}
