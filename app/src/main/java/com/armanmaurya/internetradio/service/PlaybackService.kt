package com.armanmaurya.internetradio.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.ui.widget.WidgetUpdater
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.repository.TrackHistoryRepository
import com.armanmaurya.internetradio.service.playback.AudioDeviceObserver
import com.armanmaurya.internetradio.service.playback.AudioEffectsManager
import com.armanmaurya.internetradio.service.playback.PlaybackQueueManager
import com.armanmaurya.internetradio.service.playback.PlaybackSessionCallback
import com.armanmaurya.internetradio.service.playback.TrackObserver
import com.armanmaurya.internetradio.service.playback.VolumeFadeController
import com.armanmaurya.internetradio.service.playback.createStationMediaItem
import com.armanmaurya.internetradio.service.playback.engine.AmplitudeAudioProcessor
import com.armanmaurya.internetradio.service.playback.engine.AudioAmplitudeManager
import com.armanmaurya.internetradio.service.playback.engine.CoilBitmapLoader
import com.armanmaurya.internetradio.service.playback.engine.ExponentialBackoffLoadErrorHandlingPolicy
import com.armanmaurya.internetradio.service.playback.engine.RetryStateTracker
import com.armanmaurya.internetradio.ui.mobile.MobileActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.armanmaurya.internetradio.core.media.player.RadioPlayer
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import kotlinx.coroutines.Job
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.net.toUri

@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject
    lateinit var audioAttributes: AudioAttributes

    @Inject
    lateinit var sessionCallback: PlaybackSessionCallback

    @Inject
    lateinit var trackObserver: TrackObserver

    @Inject
    lateinit var audioEffectsManager: AudioEffectsManager

    @Inject
    lateinit var audioDeviceObserver: AudioDeviceObserver

    @Inject
    lateinit var volumeFadeController: VolumeFadeController

    @Inject
    lateinit var queueManager: PlaybackQueueManager

    @Inject
    lateinit var audioAmplitudeManager: AudioAmplitudeManager

    @Inject
    lateinit var retryStateTracker: RetryStateTracker

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var widgetUpdater: WidgetUpdater

    @Inject
    lateinit var okHttpClient: okhttp3.OkHttpClient

    private var player: Player? = null
    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var loadErrorHandlingPolicy: ExponentialBackoffLoadErrorHandlingPolicy

    private var showStationThumbnails: Boolean = true
    private var alarmFadeInSeconds: Int = 0

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        var isRunning = false
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        isRunning = true

        loadErrorHandlingPolicy = ExponentialBackoffLoadErrorHandlingPolicy(retryStateTracker)

        serviceScope.launch {
            settingsRepository.appPreferencesFlow.collect { prefs ->
                loadErrorHandlingPolicy.maxRetryDurationMs = prefs.maxRetryDuration
                showStationThumbnails = prefs.showStationThumbnails
                alarmFadeInSeconds =
                    if (prefs.isAlarmVolumeTransitionEnabled) prefs.alarmVolumeTransitionSeconds else 0
            }
        }

        var retryToast: android.widget.Toast? = null
        serviceScope.launch(Dispatchers.Main) {
            retryStateTracker.retryToastEvent.collect {
                retryToast?.cancel()
                retryToast = android.widget.Toast.makeText(
                    this@PlaybackService,
                    getString(R.string.player_retrying_connection),
                    android.widget.Toast.LENGTH_SHORT
                )
                retryToast?.show()
            }
        }

        val streamingOkHttpClient = okHttpClient.newBuilder().addNetworkInterceptor { chain ->
            val request = chain.request().newBuilder().header("Icy-MetaData", "1").build()
            chain.proceed(request)
        }.build()

        val dataSourceFactory =
            androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(streamingOkHttpClient)

        val mediaSourceFactory =
            DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory)
                .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(AmplitudeAudioProcessor(audioAmplitudeManager::updateAmplitude)))
                    .build()
            }
        }

        val exoPlayer = ExoPlayer.Builder(this).setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory).setAudioAttributes(audioAttributes, true)
            .setDeviceVolumeControlEnabled(true).build()

        audioEffectsManager.attachToPlayer(exoPlayer)

        exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
        exoPlayer.setWakeMode(C.WAKE_MODE_NETWORK)

        val radioPlayer = RadioPlayer(exoPlayer, retryStateTracker)
        player = radioPlayer

        audioDeviceObserver.attachPlayer(radioPlayer)
        widgetUpdater.attachPlayer(radioPlayer)
        trackObserver.attachPlayer(radioPlayer)
        queueManager.attachPlayer(radioPlayer, sessionCallback)

        val intent = Intent(this, MobileActivity::class.java).apply {
            action = "com.armanmaurya.internetradio.ACTION_OPEN_PLAYER"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_player_sheet", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        mediaLibrarySession = MediaLibrarySession.Builder(this, radioPlayer, sessionCallback)
            .setSessionActivity(pendingIntent).setBitmapLoader(CoilBitmapLoader(this)).build()

        sessionCallback.activeSession = mediaLibrarySession
        sessionCallback.onVolumeBoostChanged = audioEffectsManager::setVolumeBoost
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        isRunning = false
        trackObserver.detachPlayer()
        widgetUpdater.detachPlayer()
        widgetUpdater.pushStoppedWidgetUpdate()
        queueManager.detachPlayer()
        audioDeviceObserver.detachPlayer()
        volumeFadeController.release()
        audioEffectsManager.release()
        serviceScope.cancel()

        sessionCallback.activeSession = null
        sessionCallback.onVolumeBoostChanged = null

        mediaLibrarySession?.run {
            player.release()
            release()
        }
        mediaLibrarySession = null
        player = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = player
        val isActivelyPlaying =
            player != null && player.mediaItemCount > 0 && (player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING))

        if (isActivelyPlaying) {
            widgetUpdater.updateWidget()
        } else {
            widgetUpdater.pushStoppedWidgetUpdate()
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "com.armanmaurya.internetradio.ACTION_PLAY_STATION") {
            val stationUuid = intent.getStringExtra("STATION_UUID")
            val stationUrl = intent.getStringExtra("STATION_URL")
            val stationName = intent.getStringExtra("STATION_NAME") ?: ""
            val stationFavicon = intent.getStringExtra("STATION_FAVICON") ?: ""
            val volumeLevel = intent.getFloatExtra("VOLUME_LEVEL", -1f)
            val transitionSeconds =
                intent.getIntExtra("ALARM_TRANSITION_SECONDS", alarmFadeInSeconds)

            if (stationUuid != null && !stationUrl.isNullOrBlank()) {
                startStationPlayback(
                    stationUuid,
                    stationUrl,
                    stationName,
                    stationFavicon,
                    volumeLevel,
                    transitionSeconds
                )
            }
        } else if (action == "com.armanmaurya.internetradio.ACTION_STOP_PLAYBACK") {
            volumeFadeController.cancelFade(player)
            player?.stop()
        } else if (action != null && widgetUpdater.handleWidgetAction(action) { restoreLastStationOrStop() }) {
            // Handled by WidgetUpdater
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startStationPlayback(
        stationUuid: String,
        stationUrl: String,
        stationName: String,
        stationFavicon: String,
        volumeLevel: Float,
        transitionSeconds: Int
    ) {
        val mediaItem = createStationMediaItem(
            context = this,
            stationUuid = stationUuid,
            stationUrl = stationUrl,
            stationName = stationName,
            stationFavicon = stationFavicon,
            showThumbnails = showStationThumbnails
        )

        val isSameStation = player?.currentMediaItem?.mediaId == stationUuid

        player?.let { p ->
            volumeFadeController.startAlarmVolumeTransition(
                player = p,
                volumeLevel = volumeLevel,
                transitionSeconds = transitionSeconds,
                isSameStation = isSameStation,
                onSetVolumeZero = audioDeviceObserver::ignoreNextVolumeZero
            )

            p.playWhenReady = true
            if (!isSameStation) {
                p.setMediaItem(mediaItem)
                p.prepare()
            } else if (p.playbackState == Player.STATE_IDLE || p.playbackState == Player.STATE_ENDED) {
                p.prepare()
            }
        }
    }

    private fun restoreLastStationOrStop() {
        val p = player ?: return
        serviceScope.launch {
            val restored = queueManager.restoreAndPlayLastStation(p)
            if (!restored) {
                widgetUpdater.pushStoppedWidgetUpdate()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    stopForeground(true)
                }
                stopSelf()
            }
        }
    }
}
