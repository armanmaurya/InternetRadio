package com.armanmaurya.internetradio.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.media3.common.AudioAttributes
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
import com.armanmaurya.internetradio.domain.controller.PlayerController
import com.armanmaurya.internetradio.ui.widget.WidgetUpdater
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.repository.TrackHistoryRepository
import com.armanmaurya.internetradio.service.playback.AudioEffectsManager
import com.armanmaurya.internetradio.service.playback.PlaybackSessionCallback
import com.armanmaurya.internetradio.service.playback.TrackObserver
import com.armanmaurya.internetradio.service.playback.createStationMediaItem
import com.armanmaurya.internetradio.service.playback.engine.AmplitudeAudioProcessor
import com.armanmaurya.internetradio.service.playback.engine.CoilBitmapLoader
import com.armanmaurya.internetradio.service.playback.engine.ExponentialBackoffLoadErrorHandlingPolicy
import com.armanmaurya.internetradio.service.playback.engine.RetryStateTracker
import com.armanmaurya.internetradio.service.playback.toMediaItem
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
import com.armanmaurya.internetradio.domain.repository.LibraryRepository
import com.armanmaurya.internetradio.domain.repository.RecentRepository
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
    lateinit var playerController: PlayerController

    @Inject
    lateinit var retryStateTracker: RetryStateTracker

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var libraryRepository: LibraryRepository

    @Inject
    lateinit var recentRepository: RecentRepository

    @Inject
    lateinit var widgetUpdater: WidgetUpdater

    @Inject
    lateinit var okHttpClient: okhttp3.OkHttpClient

    private var player: Player? = null
    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var loadErrorHandlingPolicy: ExponentialBackoffLoadErrorHandlingPolicy

    private var stopOnAudioBecomingNoisy: Boolean = true
    private var pauseOnVolumeZero: Boolean = false
    private var previousVolume: Int = -1
    private var ignoreNextVolumeZero: Boolean = false
    private var showStationThumbnails: Boolean = true
    private var alarmFadeInSeconds: Int = 0
    private var volumeFadeJob: kotlinx.coroutines.Job? = null

    private val audioNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (stopOnAudioBecomingNoisy) {
                    player?.pause()
                }
            }
        }
    }


    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var libraryStatusJob: Job? = null


    private val stationChangeListener = @UnstableApi object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            libraryStatusJob?.cancel()
            val stationUuid = mediaItem?.mediaId
            if (stationUuid != null) {
                libraryStatusJob = serviceScope.launch {
                    libraryRepository.isStationInLibrary(stationUuid).collect {
                        sessionCallback.updateLibraryButton(stationUuid)
                    }
                }
            } else {
                sessionCallback.updateLibraryButton(null)
            }

            if (stationUuid == null) return

            val tagStation = mediaItem.localConfiguration?.tag as? RadioStation
            if (tagStation != null) {
                serviceScope.launch {
                    recentRepository.addRecentStation(tagStation)
                }
            } else {
                serviceScope.launch {
                    val dbStation = libraryRepository.getStationById(stationUuid)
                    if (dbStation != null) {
                        recentRepository.addRecentStation(dbStation)
                    }
                }
            }
        }
    }

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
                stopOnAudioBecomingNoisy = prefs.stopOnAudioBecomingNoisy
                pauseOnVolumeZero = prefs.pauseOnVolumeZero
                showStationThumbnails = prefs.showStationThumbnails
                alarmFadeInSeconds =
                    if (prefs.isAlarmVolumeTransitionEnabled) prefs.alarmVolumeTransitionSeconds else 0
            }
        }

        var retryToast: android.widget.Toast? = null
        serviceScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            retryStateTracker.retryToastEvent.collect {
                retryToast?.cancel()
                retryToast = android.widget.Toast.makeText(
                    this@PlaybackService,
                    getString(com.armanmaurya.internetradio.R.string.player_retrying_connection),
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
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(AmplitudeAudioProcessor(playerController::updateAmplitude)))
                    .build()
            }
        }

        val exoPlayer = ExoPlayer.Builder(this).setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory).setAudioAttributes(audioAttributes, true)
            .setDeviceVolumeControlEnabled(true).build()

        audioEffectsManager.attachToPlayer(exoPlayer)

        exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
        exoPlayer.setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)

        registerReceiver(audioNoisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))

        player = RadioPlayer(exoPlayer, retryStateTracker)


        player?.let {
            it.addListener(stationChangeListener)
            widgetUpdater.attachPlayer(it)
            trackObserver.attachPlayer(it)
            it.addListener(object : androidx.media3.common.Player.Listener {
                override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
                    val isZero = volume == 0 || muted
                    val wasNonZero = previousVolume > 0

                    if (isZero && wasNonZero) {
                        if (ignoreNextVolumeZero) {
                            ignoreNextVolumeZero = false
                        } else if (pauseOnVolumeZero) {
                            player?.pause()
                        }
                    } else if (!isZero) {
                        ignoreNextVolumeZero = false
                    }

                    previousVolume = if (muted) 0 else volume
                }
            })

            val intent = Intent(this, MobileActivity::class.java).apply {
                action = "com.armanmaurya.internetradio.ACTION_OPEN_PLAYER"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_player_sheet", true)
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            mediaLibrarySession = MediaLibrarySession.Builder(this, it, sessionCallback)
                .setSessionActivity(pendingIntent).setBitmapLoader(CoilBitmapLoader(this)).build()

            // Give the callback a reference to the session so it can push
            // custom layout updates (e.g. refreshing the heart icon) at any time
            sessionCallback.activeSession = mediaLibrarySession
            sessionCallback.onVolumeBoostChanged = audioEffectsManager::setVolumeBoost
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        isRunning = false
        trackObserver.detachPlayer()
        widgetUpdater.detachPlayer()
        widgetUpdater.pushStoppedWidgetUpdate()
        serviceScope.cancel()
        // Clear session ref first so the callback stops pushing updates
        sessionCallback.activeSession = null
        sessionCallback.onVolumeBoostChanged = null
        audioEffectsManager.release()
        try {
            unregisterReceiver(audioNoisyReceiver)
        } catch (e: Exception) {
            // Ignored
        }

        mediaLibrarySession?.run {
            player.removeListener(stationChangeListener)
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
            player != null && player.mediaItemCount > 0 && (player.isPlaying || (player.playWhenReady && player.playbackState == androidx.media3.common.Player.STATE_BUFFERING))

        if (isActivelyPlaying) {
            // Keep playback and widget active in foreground while audio is playing
            widgetUpdater.updateWidget()
        } else {
            // Not playing - clear widget state and stop service
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
            volumeFadeJob?.cancel()
            player?.volume = 1f
            player?.stop()
        } else if (action == "com.armanmaurya.internetradio.ACTION_WIDGET_PLAY_PAUSE" || action == "com.armanmaurya.internetradio.ACTION_WIDGET_NEXT" || action == "com.armanmaurya.internetradio.ACTION_WIDGET_PREVIOUS") {
            when (action) {
                "com.armanmaurya.internetradio.ACTION_WIDGET_PLAY_PAUSE" -> {
                    val p = player
                    if (p != null) {
                        when {
                            p.isPlaying || (p.playbackState == Player.STATE_BUFFERING && p.playWhenReady) -> p.pause()
                            p.mediaItemCount == 0 -> serviceScope.launch { restoreAndPlayLastStation() }
                            else -> {
                                if (p.playbackState == Player.STATE_IDLE) p.prepare(); p.play()
                            }
                        }
                    }
                }

                "com.armanmaurya.internetradio.ACTION_WIDGET_NEXT" -> {
                    val p = player
                    if (p != null) {
                        if (p.mediaItemCount == 0) {
                            serviceScope.launch { restoreAndPlayLastStation() }
                        } else if (p.hasNextMediaItem()) {
                            p.seekToNextMediaItem()
                        }
                    }
                }

                "com.armanmaurya.internetradio.ACTION_WIDGET_PREVIOUS" -> {
                    val p = player
                    if (p != null) {
                        if (p.mediaItemCount == 0) {
                            serviceScope.launch { restoreAndPlayLastStation() }
                        } else if (p.hasPreviousMediaItem()) {
                            p.seekToPreviousMediaItem()
                        }
                    }
                }
            }
        } else if (action == "com.armanmaurya.internetradio.ACTION_WIDGET_UPDATE") {
            widgetUpdater.updateWidget()
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
        volumeFadeJob?.cancel()

        val applySystemVolume = {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            val minVolume =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    audioManager.getStreamMinVolume(android.media.AudioManager.STREAM_MUSIC)
                } else {
                    0
                }
            val targetVolume = if (volumeLevel > 0f) {
                (volumeLevel * maxVolume).roundToInt()
                    .coerceIn(minVolume.coerceAtLeast(1), maxVolume)
            } else {
                minVolume
            }
            if (targetVolume == 0) ignoreNextVolumeZero = true
            audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVolume, 0)
        }

        if (volumeLevel >= 0f) {
            if (isSameStation && player?.playbackState == androidx.media3.common.Player.STATE_READY) {
                applySystemVolume()
            } else {
                val listener = object : androidx.media3.common.Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == androidx.media3.common.Player.STATE_READY) {
                            applySystemVolume()
                            player?.removeListener(this)
                        }
                    }
                }
                player?.addListener(listener)
            }
            if (transitionSeconds > 0) {
                player?.volume = 0f
                volumeFadeJob = serviceScope.launch {
                    val steps = transitionSeconds * 10
                    val volumeStep = 1.0f / steps
                    for (i in 1..steps) {
                        kotlinx.coroutines.delay(100)
                        player?.volume = (volumeStep * i).coerceIn(0f, 1f)
                    }
                    player?.volume = 1.0f
                }
            } else {
                player?.volume = 1f
            }
        } else {
            player?.volume = 1f
        }

        player?.playWhenReady = true
        if (!isSameStation) {
            player?.setMediaItem(mediaItem)
            player?.prepare()
        } else if (player?.playbackState == androidx.media3.common.Player.STATE_IDLE || player?.playbackState == androidx.media3.common.Player.STATE_ENDED) {
            player?.prepare()
        }
    }


    private suspend fun restoreAndPlayLastStation() {
        val p = player ?: return
        val lastStation = recentRepository.getAllRecent().first().firstOrNull() ?: run {
            widgetUpdater.pushStoppedWidgetUpdate()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(true)
            }
            stopSelf()
            return
        }

        val libraryStations = libraryRepository.getAllStations().first()
        val libraryIndex =
            libraryStations.indexOfFirst { it.stationUuid == lastStation.stationUuid }

        val mediaItems: List<androidx.media3.common.MediaItem>
        val startIndex: Int

        if (libraryIndex != -1) {
            // Found in library: load the full library as playlist
            mediaItems = libraryStations.map { station ->
                station.toMediaItem(this, showThumbnails = showStationThumbnails)
            }
            startIndex = libraryIndex
        } else {
            // Not in library: fall back to full recents list
            val recentStations = recentRepository.getAllRecent().first()
            val recentIndex =
                recentStations.indexOfFirst { it.stationUuid == lastStation.stationUuid }
                    .coerceAtLeast(0)
            mediaItems = recentStations.map { station ->
                station.toMediaItem(this, showThumbnails = showStationThumbnails)
            }
            startIndex = recentIndex
        }

        p.volume = 1f
        p.setMediaItems(mediaItems, startIndex, 0L)
        p.playWhenReady = true
        p.prepare()
    }
}
