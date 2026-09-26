package com.armanmaurya.internetradio.domain.model

data class Schedule(
    val id: Int = 0,
    val stationUuid: String,
    val stationName: String,
    val type: ScheduleType,
    val triggerTimeInMillis: Long = 0L,
    val durationMinutes: Int = 0,
    val isRecurring: Boolean = false,
    val daysOfWeek: String = "", // E.g. "1,2,3,4,5"
    val timeHour: Int = 0,
    val timeMinute: Int = 0,
    val isEnabled: Boolean = true,
    val volumeLevel: Float = 1.0f,
    val keepPlayback: Boolean = false,
    val playOnRecording: Boolean = true,
    val scheduleName: String = ""
)

enum class ScheduleType {
    PLAYBACK,
    RECORD
}