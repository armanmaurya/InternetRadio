package com.armanmaurya.internetradio.data.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.armanmaurya.internetradio.domain.controller.RecordingController
import com.armanmaurya.internetradio.domain.controller.ScheduleController
import com.armanmaurya.internetradio.domain.model.ScheduleType
import com.armanmaurya.internetradio.domain.repository.LibraryRepository
import com.armanmaurya.internetradio.domain.repository.ScheduleRepository
import com.armanmaurya.internetradio.domain.controller.PlayerController
import com.armanmaurya.internetradio.service.AlarmService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ScheduleReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduleRepository: ScheduleRepository

    @Inject
    lateinit var libraryRepository: LibraryRepository

    @Inject
    lateinit var scheduleController: ScheduleController

    @Inject
    lateinit var playerController: PlayerController

    @Inject
    lateinit var recordingController: RecordingController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        if (action == ACTION_STOP_RECORDING) {
            val uuid = intent.getStringExtra("UUID")
            if (uuid != null) {
                recordingController.stopRecording(uuid)
            }
            val keepPlayback = intent.getBooleanExtra("KEEP_PLAYBACK", false)
            if (!keepPlayback) {
                playerController.stop()
            }
            return
        }

        val scheduleId = intent.getIntExtra(EXTRA_SCHEDULE_ID, -1)
        if (scheduleId == -1) return

        val type = intent.getStringExtra("EXTRA_TYPE")
        val playOnRecording = intent.getBooleanExtra("EXTRA_PLAY_ON_RECORDING", true)

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "InternetRadio:ScheduleWakeLock")
        wakeLock.acquire(60_000L)

        val isPlayback = type == ScheduleType.PLAYBACK.name || (type == ScheduleType.RECORD.name && playOnRecording)
        val isRecord = type == ScheduleType.RECORD.name

        if (isPlayback) {
            AlarmWakeLockBridge.acquire(context)
            val alarmIntent = Intent(context, AlarmService::class.java).apply {
                this.action = AlarmService.ACTION_START_ALARM
                putExtra(AlarmService.EXTRA_SCHEDULE_ID, scheduleId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(alarmIntent)
            } else {
                context.startService(alarmIntent)
            }
        }

        // Handle recording and rescheduling in background using goAsync
        val pendingResult = goAsync()
        scope.launch {
            try {
                val schedule = scheduleRepository.getScheduleById(scheduleId)
                if (schedule != null) {
                    if (isRecord) {
                        val station = libraryRepository.getStationById(schedule.stationUuid)
                        if (station != null) {
                            recordingController.startRecording(station)

                            if (schedule.durationMinutes > 0) {
                                scheduleController.scheduleRecordingStop(
                                    stationUuid = station.stationUuid,
                                    durationMinutes = schedule.durationMinutes,
                                    keepPlayback = schedule.keepPlayback
                                )
                            }
                        }
                    }

                    if (schedule.isRecurring) {
                        scheduleController.schedule(schedule)
                    } else {
                        scheduleRepository.updateScheduleStatus(schedule.id, false)
                    }
                }
            } finally {
                try {
                    if (wakeLock.isHeld) {
                        wakeLock.release()
                    }
                } catch (_: Exception) {}
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
        const val ACTION_STOP_RECORDING = "com.armanmaurya.internetradio.ACTION_STOP_RECORDING"
    }
}
