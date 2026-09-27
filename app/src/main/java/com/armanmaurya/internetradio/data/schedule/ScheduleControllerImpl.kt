package com.armanmaurya.internetradio.data.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.armanmaurya.internetradio.domain.controller.ScheduleController
import com.armanmaurya.internetradio.domain.model.Schedule
import com.armanmaurya.internetradio.domain.model.ScheduleType
import com.armanmaurya.internetradio.ui.mobile.MobileActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ScheduleController {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun schedule(schedule: Schedule) {
        if (!schedule.isEnabled) {
            cancel(schedule.id)
            return
        }

        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            putExtra(ScheduleReceiver.EXTRA_SCHEDULE_ID, schedule.id)
            putExtra("EXTRA_TYPE", schedule.type.name)
            putExtra("EXTRA_PLAY_ON_RECORDING", schedule.playOnRecording)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            schedule.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MobileActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            schedule.id,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = getNextTriggerTime(schedule)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            }
        } catch (e: SecurityException) {
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } catch (_: Exception) {}
        }
    }

    override fun cancel(scheduleId: Int) {
        val intent = Intent(context, ScheduleReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            scheduleId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        cancelPlaybackStop(scheduleId)
    }

    override fun snooze(scheduleId: Int, minutes: Int) {
        val snoozeTriggerTime = System.currentTimeMillis() + (minutes * 60 * 1000L)

        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            putExtra(ScheduleReceiver.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra("EXTRA_TYPE", ScheduleType.PLAYBACK.name)
            putExtra("EXTRA_PLAY_ON_RECORDING", false)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            scheduleId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MobileActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            scheduleId,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeTriggerTime, pendingIntent)
            } else {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(snoozeTriggerTime, showPendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            }
        } catch (e: SecurityException) {
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeTriggerTime, pendingIntent)
            } catch (_: Exception) {}
        }
    }

    override fun scheduleRecordingStop(stationUuid: String, durationMinutes: Int, keepPlayback: Boolean) {
        if (durationMinutes <= 0) return

        val stopIntent = Intent(context, ScheduleReceiver::class.java).apply {
            action = ScheduleReceiver.ACTION_STOP_RECORDING
            putExtra("KEEP_PLAYBACK", keepPlayback)
            putExtra("UUID", stationUuid)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            stationUuid.hashCode(),
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val showIntent = Intent(context, MobileActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            stationUuid.hashCode(),
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAt = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, stopAt, pendingIntent)
            } else {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(stopAt, showPendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            }
        } catch (e: SecurityException) {
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, stopAt, pendingIntent)
            } catch (_: Exception) {}
        }
    }

    override fun cancelRecordingStop(stationUuid: String) {
        val stopIntent = Intent(context, ScheduleReceiver::class.java).apply {
            action = ScheduleReceiver.ACTION_STOP_RECORDING
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            stationUuid.hashCode(),
            stopIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    override fun schedulePlaybackStop(scheduleId: Int, durationMinutes: Int) {
        if (durationMinutes <= 0) return

        val stopIntent = Intent(context, ScheduleReceiver::class.java).apply {
            action = ScheduleReceiver.ACTION_STOP_PLAYBACK
            putExtra(ScheduleReceiver.EXTRA_SCHEDULE_ID, scheduleId)
        }
        val requestCode = getPlaybackStopRequestCode(scheduleId)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val showIntent = Intent(context, MobileActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAt = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, stopAt, pendingIntent)
            } else {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(stopAt, showPendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            }
        } catch (e: SecurityException) {
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, stopAt, pendingIntent)
            } catch (_: Exception) {}
        }
    }

    override fun cancelPlaybackStop(scheduleId: Int) {
        val stopIntent = Intent(context, ScheduleReceiver::class.java).apply {
            action = ScheduleReceiver.ACTION_STOP_PLAYBACK
        }
        val requestCode = getPlaybackStopRequestCode(scheduleId)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            stopIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun getPlaybackStopRequestCode(scheduleId: Int): Int {
        return REQUEST_CODE_PLAYBACK_STOP_OFFSET + scheduleId
    }

    private fun getNextTriggerTime(schedule: Schedule): Long {
        if (!schedule.isRecurring && schedule.triggerTimeInMillis > System.currentTimeMillis()) {
            return schedule.triggerTimeInMillis
        }

        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        calendar.set(Calendar.HOUR_OF_DAY, schedule.timeHour)
        calendar.set(Calendar.MINUTE, schedule.timeMinute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // If time has already passed today, check if it's recurring or one-time
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        if (schedule.isRecurring && schedule.daysOfWeek.isNotBlank()) {
            val days = schedule.daysOfWeek.split(",").mapNotNull { it.toIntOrNull() }
            if (days.isNotEmpty()) {
                // Find next valid day
                var currentDay = calendar.get(Calendar.DAY_OF_WEEK)
                var daysToAdd = 0
                while (!days.contains(currentDay)) {
                    currentDay++
                    if (currentDay > 7) currentDay = 1
                    daysToAdd++
                }
                if (daysToAdd > 0) {
                    calendar.add(Calendar.DAY_OF_YEAR, daysToAdd)
                }
            }
        }

        return calendar.timeInMillis
    }

    companion object {
        private const val REQUEST_CODE_PLAYBACK_STOP_OFFSET = 1_000_000
    }
}
