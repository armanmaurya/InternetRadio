package com.armanmaurya.internetradio.domain.controller

import com.armanmaurya.internetradio.domain.model.Schedule

interface ScheduleController {
    fun schedule(schedule: Schedule)
    fun cancel(scheduleId: Int)
    fun snooze(scheduleId: Int, minutes: Int = 10)
    fun scheduleRecordingStop(stationUuid: String, durationMinutes: Int, keepPlayback: Boolean)
    fun cancelRecordingStop(stationUuid: String)
    fun schedulePlaybackStop(scheduleId: Int, durationMinutes: Int)
    fun cancelPlaybackStop(scheduleId: Int)
}
