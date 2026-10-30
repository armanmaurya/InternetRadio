package com.armanmaurya.internetradio.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.armanmaurya.internetradio.data.local.entity.ScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY timeHour ASC, timeMinute ASC")
    fun getAllSchedules(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun getScheduleById(id: Int): ScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: ScheduleEntity): Long

    @Update
    suspend fun updateSchedule(schedule: ScheduleEntity)

    @Delete
    suspend fun deleteSchedule(schedule: ScheduleEntity)

    @Query("SELECT * FROM schedules WHERE stationUuid = :stationUuid ORDER BY timeHour ASC, timeMinute ASC")
    suspend fun getSchedulesForStation(stationUuid: String): List<ScheduleEntity>

    @Query("DELETE FROM schedules WHERE stationUuid = :stationUuid")
    suspend fun deleteSchedulesForStation(stationUuid: String)

    @Query("UPDATE schedules SET stationUuid = :newUuid WHERE stationUuid = :oldUuid")
    suspend fun updateStationUuid(oldUuid: String, newUuid: String)

    @Query("UPDATE schedules SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun updateScheduleStatus(id: Int, isEnabled: Boolean)
}
