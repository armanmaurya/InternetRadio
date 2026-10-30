package com.armanmaurya.internetradio.domain.repository

import com.armanmaurya.internetradio.domain.model.Schedule
import kotlinx.coroutines.flow.Flow

interface ScheduleRepository {
    fun getAllSchedules(): Flow<List<Schedule>>
    suspend fun getScheduleById(id: Int): Schedule?
    suspend fun getSchedulesForStation(stationUuid: String): List<Schedule>
    suspend fun deleteSchedulesForStation(stationUuid: String)
    suspend fun insertSchedule(schedule: Schedule): Long
    suspend fun updateSchedule(schedule: Schedule)
    suspend fun deleteSchedule(schedule: Schedule)
    suspend fun updateScheduleStatus(id: Int, isEnabled: Boolean)
}
