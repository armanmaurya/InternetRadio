package com.armanmaurya.internetradio.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.text.format.DateUtils
import androidx.core.app.NotificationCompat
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.controller.RecordingController
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.model.RecordingSession
import com.armanmaurya.internetradio.ui.mobile.MobileActivity
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class RecordingService : Service() {
    @Inject
    lateinit var recordingController: RecordingController

    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private val activeNotificationIds = mutableSetOf<Int>()

    private var notificationJob: Job? = null
    private var updateTickerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val stationJson = intent.getStringExtra("STATION_JSON")
                val station = if (stationJson != null) {
                    Gson().fromJson(stationJson, RadioStation::class.java)
                } else null
                val fileNamePrefix = intent.getStringExtra(EXTRA_FILE_NAME_PREFIX).orEmpty()
                station?.let { recordingController.startRecordingStream(it, fileNamePrefix) }
                startForeground(NOTIF_ID, buildGroupSummaryNotification(recordingController.activeSessions.value))
                observeSessions()
            }
            ACTION_STOP -> {
                val uuid = intent.getStringExtra("UUID") ?: return START_STICKY
                recordingController.stopRecording(uuid)
                if (recordingController.activeSessions.value.isEmpty()) stopSelf()
            }
        }
        return START_STICKY
    }

    private fun observeSessions() {
        if (notificationJob != null) return
        notificationJob = recordingController.activeSessions.onEach { sessions ->
            if (sessions.isEmpty()) {
                updateTickerJob?.cancel()
                cancelAllNotifications()
                stopSelf()
            } else {
                updateNotifications(sessions)

                if (updateTickerJob == null || updateTickerJob?.isActive != true) {
                    updateTickerJob = scope.launch {
                        while (isActive) {
                            delay(1000)
                            val currentSessions = recordingController.activeSessions.value
                            updateNotifications(currentSessions)
                        }
                    }
                }
            }
        }.launchIn(scope)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active Recording",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun buildGroupSummaryNotification(sessions: Map<String, RecordingSession>): Notification {
        val contentIntent = Intent(this, MobileActivity::class.java).apply {
            putExtra("open_tab", "recordings")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (sessions.isEmpty()) {
            "Preparing recording..."
        } else {
            "Recording ${sessions.size} station(s)"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.media3_notification_small_icon)
            .setOngoing(true)
            .setGroup(GROUP_RECORDINGS)
            .setGroupSummary(true)
            .setContentTitle(title)
            .setContentIntent(contentPendingIntent)
            .build()
    }

    private fun buildChildNotification(session: RecordingSession): Notification {
        val stopIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_STOP
            putExtra("UUID", session.station.stationUuid)
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            session.station.stationUuid.hashCode(),
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentIntent = Intent(this, MobileActivity::class.java).apply {
            putExtra("open_tab", "recordings")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val sizeMb = session.bytesWritten / (1024f * 1024f)
        val sizeText = String.format(Locale.US, "%.2f MB", sizeMb)
        val timeText = DateUtils.formatElapsedTime(session.durationSeconds.value)
        val statsText = "$timeText • $sizeText"

        val isReconnecting = session.isReconnecting.value
        val title = if (isReconnecting) {
            "Recording: ${session.station.name} (${getString(R.string.recording_reconnecting)})"
        } else {
            "Recording: ${session.station.name}"
        }
        val contentText = if (isReconnecting) {
            getString(R.string.recording_notification_reconnecting, statsText)
        } else {
            statsText
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.media3_notification_small_icon)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setGroup(GROUP_RECORDINGS)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(contentPendingIntent)
            .setUsesChronometer(false)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }

    private fun updateNotifications(sessions: Map<String, RecordingSession>) {
        val currentIds = sessions.keys.map { it.hashCode() }.toSet()

        val stoppedIds = activeNotificationIds - currentIds
        stoppedIds.forEach { notificationManager?.cancel(it) }

        activeNotificationIds.clear()
        activeNotificationIds.addAll(currentIds)

        notificationManager?.notify(NOTIF_ID, buildGroupSummaryNotification(sessions))

        sessions.forEach { (uuid, session) ->
            notificationManager?.notify(uuid.hashCode(), buildChildNotification(session))
        }
    }

    private fun cancelAllNotifications() {
        activeNotificationIds.forEach { notificationManager?.cancel(it) }
        activeNotificationIds.clear()
        notificationManager?.cancel(NOTIF_ID)
    }

    override fun onDestroy() {
        updateTickerJob?.cancel()
        notificationJob?.cancel()
        recordingController.stopAllRecordings()
        cancelAllNotifications()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.armanmaurya.internetradio.REC_START"
        const val EXTRA_FILE_NAME_PREFIX = "FILE_NAME_PREFIX"
        const val ACTION_STOP  = "com.armanmaurya.internetradio.REC_STOP"
        const val NOTIF_ID     = 3001
        private const val CHANNEL_ID = "recording_channel"
        private const val GROUP_RECORDINGS = "GROUP_RECORDINGS"
    }
}