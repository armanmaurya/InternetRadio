package com.armanmaurya.internetradio.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.data.schedule.AlarmWakeLockBridge
import com.armanmaurya.internetradio.domain.controller.ScheduleController
import com.armanmaurya.internetradio.domain.repository.LibraryRepository
import com.armanmaurya.internetradio.domain.repository.ScheduleRepository
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import com.armanmaurya.internetradio.domain.controller.PlayerController
import com.armanmaurya.internetradio.ui.mobile.MobileActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class AlarmService : Service() {

    @Inject
    lateinit var scheduleRepository: ScheduleRepository

    @Inject
    lateinit var libraryRepository: LibraryRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var scheduleController: ScheduleController

    @Inject
    lateinit var playerController: PlayerController

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var autoSilenceJob: Job? = null
    private var playbackObserverJob: Job? = null
    private var activeScheduleId: Int = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_START_ALARM -> {
                val scheduleId = intent.getIntExtra(EXTRA_SCHEDULE_ID, -1)
                if (scheduleId != -1) {
                    activeScheduleId = scheduleId
                    startAlarm(scheduleId)
                } else {
                    stopSelf()
                }
            }
            ACTION_DISMISS_ALARM -> {
                dismissAlarm()
            }
            ACTION_SNOOZE_ALARM -> {
                val scheduleId = intent.getIntExtra(EXTRA_SCHEDULE_ID, activeScheduleId)
                snoozeAlarm(scheduleId)
            }
            else -> {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startAlarm(scheduleId: Int) {
        createNotificationChannel()

        // 1. Immediately promote to foreground to satisfy Android background start requirements
        val initialNotification = buildAlarmNotification(scheduleId, getString(R.string.schedule_alarm_channel_name))
        startForeground(NOTIFICATION_ID_ALARM, initialNotification)

        // 2. Start 15-minute auto-silence timer
        autoSilenceJob?.cancel()
        autoSilenceJob = serviceScope.launch {
            delay(AUTO_SILENCE_DURATION_MS)
            dismissAlarm()
        }

        // 3. Resolve schedule and trigger playback
        serviceScope.launch(Dispatchers.IO) {
            val schedule = scheduleRepository.getScheduleById(scheduleId)
            if (schedule == null) {
                AlarmWakeLockBridge.release()
                withContext(Dispatchers.Main) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return@launch
            }

            val libraryStation = libraryRepository.getStationById(schedule.stationUuid)
            val prefs = settingsRepository.appPreferencesFlow.first()

            withContext(Dispatchers.Main) {
                // Update notification with resolved station name
                val updatedNotification = buildAlarmNotification(schedule.id, schedule.stationName)
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.notify(NOTIFICATION_ID_ALARM, updatedNotification)

                // Start audio stream in PlaybackService
                val playIntent = Intent(this@AlarmService, PlaybackService::class.java).apply {
                    action = "com.armanmaurya.internetradio.ACTION_PLAY_STATION"
                    putExtra("STATION_UUID", schedule.stationUuid)
                    putExtra("STATION_URL", libraryStation?.urlResolved ?: libraryStation?.url ?: "")
                    putExtra("STATION_NAME", schedule.stationName)
                    putExtra("STATION_FAVICON", libraryStation?.favicon ?: "")
                    putExtra("VOLUME_LEVEL", schedule.volumeLevel)
                    putExtra(
                        "ALARM_TRANSITION_SECONDS",
                        if (prefs.isAlarmVolumeTransitionEnabled) prefs.alarmVolumeTransitionSeconds else 0
                    )
                }
                try {
                    ContextCompat.startForegroundService(this@AlarmService, playIntent)
                } catch (e: Exception) {
                    Log.e("AlarmService", "Failed to start PlaybackService", e)
                }

                // Bridge wakelock can be released now that PlaybackService has been launched
                AlarmWakeLockBridge.release()

                // 4. Observe player state: if playback starts and user manually pauses/stops, dismiss alarm
                observePlaybackState()
            }
        }
    }

    private fun observePlaybackState() {
        playbackObserverJob?.cancel()
        playbackObserverJob = serviceScope.launch {
            var hasStartedPlaying = false
            playerController.playbackState.collect { state ->
                if (state.isPlaying) {
                    hasStartedPlaying = true
                } else if (hasStartedPlaying) {
                    // User manually stopped or paused playback inside the app
                    dismissAlarm()
                }
            }
        }
    }

    private fun dismissAlarm() {
        autoSilenceJob?.cancel()
        playbackObserverJob?.cancel()
        AlarmWakeLockBridge.release()

        // Stop playback in PlaybackService
        val stopIntent = Intent(this, PlaybackService::class.java).apply {
            action = "com.armanmaurya.internetradio.ACTION_STOP_PLAYBACK"
        }
        try {
            startService(stopIntent)
        } catch (_: Exception) {}

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun snoozeAlarm(scheduleId: Int) {
        autoSilenceJob?.cancel()
        playbackObserverJob?.cancel()
        AlarmWakeLockBridge.release()

        // Stop current playback
        val stopIntent = Intent(this, PlaybackService::class.java).apply {
            action = "com.armanmaurya.internetradio.ACTION_STOP_PLAYBACK"
        }
        try {
            startService(stopIntent)
        } catch (_: Exception) {}

        // Schedule snooze via ScheduleController
        if (scheduleId != -1) {
            scheduleController.snooze(scheduleId, DEFAULT_SNOOZE_MINUTES)
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.schedule_alarm_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.schedule_alarm_channel_description)
                setSound(null, null) // Media volume is handling the sound
                enableVibration(true)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildAlarmNotification(scheduleId: Int, title: String): Notification {
        val contentIntent = Intent(this, MobileActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_DISMISS_ALARM
        }
        val dismissPendingIntent = PendingIntent.getService(
            this,
            1,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_SNOOZE_ALARM
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
        }
        val snoozePendingIntent = PendingIntent.getService(
            this,
            2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.media3_notification_small_icon)
            .setContentTitle(title.ifBlank { getString(R.string.schedule_alarm_channel_name) })
            .setContentText(getString(R.string.schedule_alarm_playing))
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(
                0,
                getString(R.string.schedule_dismiss),
                dismissPendingIntent
            )
            .addAction(
                0,
                getString(R.string.schedule_snooze),
                snoozePendingIntent
            )
            .build()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        AlarmWakeLockBridge.release()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID_ALARM = 2001
        const val CHANNEL_ID = "alarm_schedule_channel"
        const val ACTION_START_ALARM = "com.armanmaurya.internetradio.ACTION_START_ALARM"
        const val ACTION_DISMISS_ALARM = "com.armanmaurya.internetradio.ACTION_DISMISS_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.armanmaurya.internetradio.ACTION_SNOOZE_ALARM"
        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
        const val DEFAULT_SNOOZE_MINUTES = 10
        const val AUTO_SILENCE_DURATION_MS = 15 * 60 * 1000L // 15 minutes
    }
}
