package com.armanmaurya.internetradio.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.data.recording.RecordingPlaybackControllerImpl
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import com.armanmaurya.internetradio.ui.mobile.MobileActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RecordingPlaybackService : MediaSessionService() {

    @Inject
    lateinit var recordingPlaybackController: RecordingPlaybackControllerImpl

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    companion object {
        const val ACTION_PLAY = "com.armanmaurya.internetradio.REC_PLAY"
        const val ACTION_PAUSE = "com.armanmaurya.internetradio.REC_PAUSE"
        const val ACTION_TOGGLE = "com.armanmaurya.internetradio.REC_TOGGLE"
        const val ACTION_SEEK = "com.armanmaurya.internetradio.REC_SEEK"
        const val ACTION_STOP = "com.armanmaurya.internetradio.REC_STOP"
        const val ACTION_SET_SPEED = "com.armanmaurya.internetradio.REC_SET_SPEED"
        const val ACTION_SET_REPEAT_MODE = "com.armanmaurya.internetradio.REC_SET_REPEAT_MODE"

        const val EXTRA_RECORDING_URI = "EXTRA_RECORDING_URI"
        const val EXTRA_FILE_NAME = "EXTRA_FILE_NAME"
        const val EXTRA_STATION_NAME = "EXTRA_STATION_NAME"
        const val EXTRA_DURATION_MS = "EXTRA_DURATION_MS"
        const val EXTRA_SEEK_POSITION = "EXTRA_SEEK_POSITION"
        const val EXTRA_SPEED = "EXTRA_SPEED"
        const val EXTRA_REPEAT_MODE = "EXTRA_REPEAT_MODE"

        private const val CHANNEL_ID = "recording_playback_channel"
        private const val NOTIFICATION_ID = 3002
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingAlwaysEnabled(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(this, extractorsFactory)

        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = exoPlayer

        serviceScope.launch {
            settingsRepository.appPreferencesFlow
                .map { it.stopOnAudioBecomingNoisy }
                .distinctUntilChanged()
                .collect { enabled -> player?.setHandleAudioBecomingNoisy(enabled) }
        }

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.recording_playback_channel_name)
                .setNotificationId(NOTIFICATION_ID)
                .build()
        )

        val defaultSessionIntent = buildSessionPendingIntent("", "")
        val session = MediaSession.Builder(this, exoPlayer)
            .setId("RecordingPlaybackSession")
            .setSessionActivity(defaultSessionIntent)
            .build()
        mediaSession = session
        addSession(session)

        recordingPlaybackController.attachService(this, exoPlayer)
    }

    private fun buildSessionPendingIntent(stationName: String, uriString: String): PendingIntent {
        val intent = Intent(this, MobileActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", "recordings")
            if (stationName.isNotBlank()) {
                putExtra("route_recording_station", stationName)
            }
            if (uriString.isNotBlank()) {
                putExtra("route_recording_uri", uriString)
            }
        }
        val requestCode = if (uriString.isNotBlank()) uriString.hashCode() else 100
        return PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.recording_playback_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.recording_playback_channel_description)
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val uriString = intent.getStringExtra(EXTRA_RECORDING_URI)
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: ""
                val stationName = intent.getStringExtra(EXTRA_STATION_NAME) ?: ""

                if (!uriString.isNullOrBlank()) {
                    playUri(uriString, fileName, stationName)
                }
            }
            ACTION_PAUSE -> {
                player?.pause()
            }
            ACTION_TOGGLE -> {
                val p = player
                if (p != null) {
                    if (p.isPlaying) p.pause() else p.play()
                }
            }
            ACTION_SEEK -> {
                val pos = intent.getLongExtra(EXTRA_SEEK_POSITION, 0L)
                player?.seekTo(pos)
            }
            ACTION_SET_SPEED -> {
                val speed = intent.getFloatExtra(EXTRA_SPEED, 1.0f)
                player?.setPlaybackSpeed(speed)
            }
            ACTION_SET_REPEAT_MODE -> {
                val looping = intent.getBooleanExtra(EXTRA_REPEAT_MODE, false)
                player?.repeatMode = if (looping) androidx.media3.common.Player.REPEAT_MODE_ONE else androidx.media3.common.Player.REPEAT_MODE_OFF
            }
            ACTION_STOP -> {
                player?.stop()
                player?.clearMediaItems()
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun playUri(uriString: String, fileName: String, stationName: String) {
        val p = player ?: return
        mediaSession?.setSessionActivity(buildSessionPendingIntent(stationName, uriString))

        val currentMediaId = p.currentMediaItem?.mediaId
        if (currentMediaId == uriString) {
            p.setPlaybackSpeed(recordingPlaybackController.playbackSpeed.value)
            p.repeatMode = if (recordingPlaybackController.isLooping.value) androidx.media3.common.Player.REPEAT_MODE_ONE else androidx.media3.common.Player.REPEAT_MODE_OFF
            if (!p.isPlaying) {
                p.play()
            }
            return
        }

        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(fileName)
            .setDisplayTitle(fileName)
            .setArtist(stationName.ifBlank { "Recorded Audio" })
            .setIsPlayable(true)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(uriString))
            .setMediaId(uriString)
            .setMediaMetadata(mediaMetadata)
            .build()

        p.setMediaItem(mediaItem)
        p.setPlaybackSpeed(recordingPlaybackController.playbackSpeed.value)
        p.repeatMode = if (recordingPlaybackController.isLooping.value) androidx.media3.common.Player.REPEAT_MODE_ONE else androidx.media3.common.Player.REPEAT_MODE_OFF
        p.prepare()
        p.playWhenReady = true
        p.play()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = player
        if (p == null || !p.isPlaying) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        recordingPlaybackController.detachService(this)
        mediaSession?.run {
            removeSession(this)
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }
}
