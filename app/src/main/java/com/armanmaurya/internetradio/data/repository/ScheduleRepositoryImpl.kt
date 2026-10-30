package com.armanmaurya.internetradio.data.repository

import com.armanmaurya.internetradio.data.local.dao.ScheduleDao
import com.armanmaurya.internetradio.data.local.entity.toDomain
import com.armanmaurya.internetradio.data.local.entity.toEntity
import com.armanmaurya.internetradio.domain.model.Schedule
import com.armanmaurya.internetradio.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepositoryImpl @Inject constructor(
    private val scheduleDao: ScheduleDao
) : ScheduleRepository {
    override fun getAllSchedules(): Flow<List<Schedule>> =
        scheduleDao.getAllSchedules().map { list -> list.map { it.toDomain() } }

    override suspend fun getScheduleById(id: Int): Schedule? =
        scheduleDao.getScheduleById(id)?.toDomain()

    override suspend fun getSchedulesForStation(stationUuid: String): List<Schedule> =
        scheduleDao.getSchedulesForStation(stationUuid).map { it.toDomain() }

    override suspend fun deleteSchedulesForStation(stationUuid: String) =
        scheduleDao.deleteSchedulesForStation(stationUuid)

    override suspend fun insertSchedule(schedule: Schedule): Long =
        scheduleDao.insertSchedule(schedule.toEntity())

    override suspend fun updateSchedule(schedule: Schedule) =
        scheduleDao.updateSchedule(schedule.toEntity())

    override suspend fun deleteSchedule(schedule: Schedule) =
        scheduleDao.deleteSchedule(schedule.toEntity())

    override suspend fun updateScheduleStatus(id: Int, isEnabled: Boolean) =
        scheduleDao.updateScheduleStatus(id, isEnabled)
}
