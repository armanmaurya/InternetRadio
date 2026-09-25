package com.armanmaurya.internetradio.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
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
import com.armanmaurya.internetradio.core.provider.SvgProxyProvider
import com.armanmaurya.internetradio.domain.controller.PlayerController
import com.armanmaurya.internetradio.domain.controller.WidgetController
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.repository.TrackHistoryRepository
import com.armanmaurya.internetradio.service.playback.PlaybackSessionCallback
import com.armanmaurya.internetradio.service.playback.engine.AmplitudeAudioProcessor
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
import com.armanmaurya.internetradio.domain.repository.CoverArtRepository
import com.armanmaurya.internetradio.domain.repository.LibraryRepository
import com.armanmaurya.internetradio.domain.repository.RecentRepository
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import kotlinx.coroutines.Job
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject
    lateinit var audioAttributes: AudioAttributes

    @Inject
    lateinit var sessionCallback: PlaybackSessionCallback

    @Inject
    lateinit var trackHistoryRepository: TrackHistoryRepository

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
    lateinit var coverArtRepository: CoverArtRepository

    @Inject
    lateinit var widgetController: WidgetController

    @Inject
    lateinit var okHttpClient: okhttp3.OkHttpClient

    private var player: Player? = null
    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var loadErrorHandlingPolicy: ExponentialBackoffLoadErrorHandlingPolicy
    
    private var stopOnAudioBecomingNoisy: Boolean = true
    private var pauseOnVolumeZero: Boolean = false
    private var previousVolume: Int = -1
    private var ignoreNextVolumeZero: Boolean = false
    private var showCoverArtInNotification: Boolean = true
    private var showStationThumbnails: Boolean = true
    private var alarmFadeInSeconds: Int = 0
    private var volumeFadeJob: kotlinx.coroutines.Job? = null
    private var activeTrackTitle: String? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentBoostFactor: Float = 0f
    
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


    private val stationChangeListener = @UnstableApi
    object : Player.Listener {
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

        override fun onMetadata(metadata: androidx.media3.common.Metadata) {
            var rawTrackTitle: String? = null
            var rawArtist: String? = null

            for (i in 0 until metadata.length()) {
                val entry = metadata.get(i)
                if (entry is androidx.media3.extractor.metadata.icy.IcyInfo) {
                    rawTrackTitle = entry.title
                } else if (entry is androidx.media3.extractor.metadata.id3.TextInformationFrame) {
                    if (entry.id == "TIT2" || entry.id == "TT2") {
                        rawTrackTitle = entry.values.firstOrNull()?.toString()
                    } else if (entry.id == "TPE1" || entry.id == "TP1") {
                        rawArtist = entry.values.firstOrNull()?.toString()
                    }
                }
            }

            if (!rawTrackTitle.isNullOrBlank()) {
                val parsed = com.armanmaurya.internetradio.core.utils.MetadataSanitizer.cleanAndParse(rawTrackTitle, rawArtist)
                val trackName = parsed.title
                val artistName = parsed.artist
                val trackTitle = parsed.combinedDisplay

                val currentPlayer = player ?: return
                val currentMediaItem = currentPlayer.currentMediaItem ?: return
                
                // Avoid unnecessary updates
                val currentExtras = currentMediaItem.mediaMetadata.extras
                val previousRawTitle = currentExtras?.getString("icy_raw_title")
                
                if (previousRawTitle == rawTrackTitle) return
                
                activeTrackTitle = trackTitle
                
                val stationName = currentExtras?.getString("stationName")
                val stationFaviconStr = currentExtras?.getString("stationFavicon")
                val stationFaviconUri = when {
                    !showStationThumbnails -> android.net.Uri.EMPTY
                    stationFaviconStr?.endsWith(".svg", ignoreCase = true) == true ->
                        android.net.Uri.parse(SvgProxyProvider.createProxyUri(this@PlaybackService, stationFaviconStr))
                    !stationFaviconStr.isNullOrBlank() -> android.net.Uri.parse(stationFaviconStr)
                    else -> android.net.Uri.EMPTY
                }

                val newExtras = android.os.Bundle(currentExtras ?: android.os.Bundle.EMPTY).apply {
                    putString("icy_raw_title", rawTrackTitle)
                    putString("icy_title", trackTitle)
                    putString("is_fetching_artwork", "true")
                    remove("track_cover_art_url") // Clear old cover art for the new track
                    
                    if (previousRawTitle == null) {
                        // First track since tuning in. We do not know when it actually started.
                        putLong("track_start_time", -1L)
                    } else {
                        // Real track change while listening! We know the exact start time.
                        putLong("track_start_time", currentPlayer.currentPosition)
                    }
                }

                val newMetadataBuilder = currentMediaItem.mediaMetadata.buildUpon()
                    .setTitle(trackName)
                    .setArtist(artistName)
                    .setAlbumTitle(stationName)
                    .setArtworkUri(stationFaviconUri) // Show station thumbnail while fetching track cover art
                    .setExtras(newExtras)
                    
                val newMediaItem = currentMediaItem.buildUpon()
                    .setMediaMetadata(newMetadataBuilder.build())
                    .build()
                    
                // Update metadata without interrupting playback
                currentPlayer.replaceMediaItem(currentPlayer.currentMediaItemIndex, newMediaItem)
                
                // Log the track history
                val stationUuid = currentMediaItem.mediaId
                serviceScope.launch {
                    try {
                        val trackId = trackHistoryRepository.logTrack(stationUuid, trackTitle, rawTrackTitle)
                        
                        // Fetch track cover art and cleaned metadata with a safe timeout
                        val metadata = withTimeoutOrNull(4000L) {
                            coverArtRepository.getTrackMetadata(trackName, artistName)
                        }
                        
                        // Determine the cleaned title
                        val cleanedTitle = if (metadata != null) {
                            val cTrack = metadata.trackName
                            val cArtist = metadata.artistName
                            when {
                                cTrack != null && cArtist != null -> "$cArtist - $cTrack"
                                cTrack != null -> cTrack
                                else -> trackTitle
                            }
                        } else {
                            trackTitle
                        }

                        if (trackId != null) {
                            trackHistoryRepository.updateTrackMetadata(trackId, cleanedTitle, metadata?.coverArtUrl)
                        }
                        
                        // Ensure track hasn't changed while fetching
                        if (activeTrackTitle == trackTitle) {
                            val updatedExtras = android.os.Bundle(newExtras).apply {
                                putString("is_fetching_artwork", "false")
                                putString("icy_title", cleanedTitle)
                                if (metadata?.trackName != null) putString("clean_track_name", metadata.trackName)
                                if (metadata?.artistName != null) putString("clean_artist_name", metadata.artistName)
                                if (metadata?.coverArtUrl != null) {
                                    putString("track_cover_art_url", metadata.coverArtUrl) // Make cover art available to internal UI
                                }
                            }
                            val metadataWithArt = newMetadataBuilder
                                .setTitle(metadata?.trackName ?: trackName)
                                .setArtist(metadata?.artistName ?: artistName)
                                .setArtworkUri(if (showCoverArtInNotification && metadata?.coverArtUrl != null) android.net.Uri.parse(metadata.coverArtUrl) else stationFaviconUri)
                                .setExtras(updatedExtras)
                                .setDescription(System.currentTimeMillis().toString()) // Force ExoPlayer to detect a metadata change
                                .build()
                            val itemWithArt = newMediaItem.buildUpon()
                                .setMediaMetadata(metadataWithArt)
                                .build()
                                
                            player?.let { p ->
                                for (i in 0 until p.mediaItemCount) {
                                    if (p.getMediaItemAt(i).mediaId == stationUuid) {
                                        val currentItemAtI = p.getMediaItemAt(i)
                                        val updatedItem = currentItemAtI.buildUpon()
                                            .setMediaMetadata(metadataWithArt)
                                            .build()
                                        p.replaceMediaItem(i, updatedItem)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("PlaybackService", "Error during cover art fetching", e)
                        if (activeTrackTitle == trackTitle) {
                            val fallbackExtras = android.os.Bundle(newExtras).apply {
                                putString("is_fetching_artwork", "false")
                            }
                            val fallbackMetadata = newMetadataBuilder
                                .setExtras(fallbackExtras)
                                .setDescription(System.currentTimeMillis().toString())
                                .build()
                            player?.let { p ->
                                for (i in 0 until p.mediaItemCount) {
                                    if (p.getMediaItemAt(i).mediaId == stationUuid) {
                                        val currentItemAtI = p.getMediaItemAt(i)
                                        val updatedItem = currentItemAtI.buildUpon()
                                            .setMediaMetadata(fallbackMetadata)
                                            .build()
                                        p.replaceMediaItem(i, updatedItem)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        var isRunning = false
        @Volatile
        private var instance: PlaybackService? = null

        fun requestWidgetUpdate() {
            val service = instance ?: return
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                service.updateWidget()
            } else {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    instance?.updateWidget()
                }
            }
        }
    }

    /** Receives widget control broadcasts when the service is already running in the foreground.
     *  This bypasses Android 12+ background startForegroundService restrictions on OEM devices. */
    private val widgetActionReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent?) {
            when (intent?.action) {
                "com.armanmaurya.internetradio.ACTION_WIDGET_PLAY_PAUSE" -> {
                    val p = player ?: return
                    when {
                        p.isPlaying || (p.playbackState == androidx.media3.common.Player.STATE_BUFFERING && p.playWhenReady) -> p.pause()
                        p.mediaItemCount == 0 -> serviceScope.launch { restoreAndPlayLastStation() }
                        else -> { if (p.playbackState == androidx.media3.common.Player.STATE_IDLE) p.prepare(); p.play() }
                    }
                }
                "com.armanmaurya.internetradio.ACTION_WIDGET_NEXT" ->
                    player?.takeIf { it.hasNextMediaItem() }?.seekToNextMediaItem()
                "com.armanmaurya.internetradio.ACTION_WIDGET_PREVIOUS" ->
                    player?.takeIf { it.hasPreviousMediaItem() }?.seekToPreviousMediaItem()
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        isRunning = true
        instance = this

        // Register the widget broadcast receiver so buttons work on all OEM launchers
        val widgetFilter = android.content.IntentFilter().apply {
            addAction("com.armanmaurya.internetradio.ACTION_WIDGET_PLAY_PAUSE")
            addAction("com.armanmaurya.internetradio.ACTION_WIDGET_NEXT")
            addAction("com.armanmaurya.internetradio.ACTION_WIDGET_PREVIOUS")
        }
        registerReceiver(widgetActionReceiver, widgetFilter, android.content.Context.RECEIVER_NOT_EXPORTED)

        loadErrorHandlingPolicy = ExponentialBackoffLoadErrorHandlingPolicy(retryStateTracker)
        
        serviceScope.launch {
            settingsRepository.appPreferencesFlow.collect { prefs ->
                loadErrorHandlingPolicy.maxRetryDurationMs = prefs.maxRetryDuration
                stopOnAudioBecomingNoisy = prefs.stopOnAudioBecomingNoisy
                pauseOnVolumeZero = prefs.pauseOnVolumeZero
                showCoverArtInNotification = prefs.showCoverArtInNotification
                showStationThumbnails = prefs.showStationThumbnails
                alarmFadeInSeconds = if (prefs.isAlarmVolumeTransitionEnabled) prefs.alarmVolumeTransitionSeconds else 0
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

        // A *network* interceptor fires on every hop including after redirects,
        // so "Icy-MetaData: 1" reaches the final streaming server even when the
        // station URL is a redirect (e.g. ondacero.es → streamtheworld.com).
        // OkHttp strips application-level headers / setDefaultRequestProperties
        // on cross-domain redirects, which is why ICY metadata was missing for
        // stations served through redirect endpoints.
        val streamingOkHttpClient = okHttpClient.newBuilder()
            .addNetworkInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Icy-MetaData", "1")
                    .build()
                chain.proceed(request)
            }
            .build()

        val dataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(streamingOkHttpClient)

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)
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

        val exoPlayer = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setDeviceVolumeControlEnabled(true)
            .build()

        exoPlayer.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                audioSessionId: Int
            ) {
                if (audioSessionId != androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) {
                    setupLoudnessEnhancer(audioSessionId)
                }
            }
        })
        if (exoPlayer.audioSessionId != androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) {
            setupLoudnessEnhancer(exoPlayer.audioSessionId)
        }
            
        exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
        exoPlayer.setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
            
        registerReceiver(audioNoisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))

        player = object : androidx.media3.common.ForwardingPlayer(exoPlayer) {
            override fun getAvailableCommands(): Player.Commands {
                val commands = super.getAvailableCommands()
                val builder = commands.buildUpon()
                    .remove(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    .remove(Player.COMMAND_SEEK_BACK)
                    .remove(Player.COMMAND_SEEK_FORWARD)

                if (mediaItemCount <= 1) {
                    builder
                        .remove(Player.COMMAND_SEEK_TO_NEXT)
                        .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                }
                return builder.build()
            }
            
            override fun isCurrentMediaItemDynamic(): Boolean {
                return true
            }

            override fun isCurrentMediaItemLive(): Boolean {
                return true
            }

            override fun isCurrentMediaItemSeekable(): Boolean {
                return false
            }

            override fun getDuration(): Long {
                return androidx.media3.common.C.TIME_UNSET
            }

            override fun hasNextMediaItem(): Boolean {
                return mediaItemCount > 1 && super.hasNextMediaItem()
            }
            
            override fun hasPreviousMediaItem(): Boolean {
                return mediaItemCount > 1 && super.hasPreviousMediaItem()
            }

            override fun play() {
                // Since stop() removes the notification, we let it pause() normally.
                val item = currentMediaItem
                if (item != null && !playWhenReady) {
                    retryStateTracker.reset()
                    if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
                        // The player was paused and likely has a stale buffer or a dead socket.
                        // We call stop() to drop the old connection and buffer, 
                        // then prepare() to connect fresh to the live edge.
                        super.stop()
                        super.prepare()
                    }
                }
                super.play()
            }
            
            override fun pause() {
                retryStateTracker.reset()
                super.pause()
            }
            
            override fun stop() {
                retryStateTracker.reset()
                super.stop()
            }
        }

        player?.let {
            it.addListener(stationChangeListener)
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

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateWidget()
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    updateWidget()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    updateWidget()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updateWidget()
                }

                override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                    val isFetchingArtwork = mediaMetadata.extras?.getString("is_fetching_artwork") == "true"
                    val isPlaying = player?.let { it.isPlaying || (it.playbackState == androidx.media3.common.Player.STATE_BUFFERING && it.playWhenReady) } ?: false
                    if (isPlaying && isFetchingArtwork) {
                        return
                    }
                    updateWidget()
                }
            })

            val intent = Intent(this, MobileActivity::class.java).apply {
                action = "com.armanmaurya.internetradio.ACTION_OPEN_PLAYER"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_player_sheet", true)
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            mediaLibrarySession = MediaLibrarySession.Builder(this, it, sessionCallback)
                .setSessionActivity(pendingIntent)
                .setBitmapLoader(CoilBitmapLoader(this))
                .build()

            // Give the callback a reference to the session so it can push
            // custom layout updates (e.g. refreshing the heart icon) at any time
            sessionCallback.activeSession = mediaLibrarySession
            sessionCallback.onVolumeBoostChanged = { boost ->
                currentBoostFactor = boost
                applyBoostGain(loudnessEnhancer, boost)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        isRunning = false
        widgetController.clearLatestPayload()
        if (instance == this) {
            instance = null
        }
        // Push a final stopped-state widget update before tearing down
        pushStoppedWidgetUpdate()
        serviceScope.cancel()
        // Clear session ref first so the callback stops pushing updates
        sessionCallback.activeSession = null
        sessionCallback.onVolumeBoostChanged = null
        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = null
        } catch (e: Exception) {
            // Ignored
        }
        try {
            unregisterReceiver(audioNoisyReceiver)
        } catch (e: Exception) {
            // Ignored
        }
        try {
            unregisterReceiver(widgetActionReceiver)
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
        val isActivelyPlaying = player != null && 
            player.mediaItemCount > 0 && 
            (player.isPlaying || (player.playWhenReady && player.playbackState == androidx.media3.common.Player.STATE_BUFFERING))

        if (isActivelyPlaying) {
            // Keep playback and widget active in foreground while audio is playing
            updateWidget()
        } else {
            // Not playing - clear widget state and stop service
            pushStoppedWidgetUpdate()
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
            val transitionSeconds = intent.getIntExtra("ALARM_TRANSITION_SECONDS", alarmFadeInSeconds)

            if (stationUuid != null && !stationUrl.isNullOrBlank()) {
                startStationPlayback(
                    stationUuid, stationUrl, stationName, stationFavicon,
                    volumeLevel, transitionSeconds
                )
            }
        } else if (action == "com.armanmaurya.internetradio.ACTION_STOP_PLAYBACK") {
            volumeFadeJob?.cancel()
            player?.volume = 1f
            player?.stop()
        } else if (action == "com.armanmaurya.internetradio.ACTION_WIDGET_PLAY_PAUSE" ||
                   action == "com.armanmaurya.internetradio.ACTION_WIDGET_NEXT" ||
                   action == "com.armanmaurya.internetradio.ACTION_WIDGET_PREVIOUS") {
            when (action) {
                "com.armanmaurya.internetradio.ACTION_WIDGET_PLAY_PAUSE" -> {
                    val p = player
                    if (p != null) {
                        when {
                            p.isPlaying || (p.playbackState == Player.STATE_BUFFERING && p.playWhenReady) -> p.pause()
                            p.mediaItemCount == 0 -> serviceScope.launch { restoreAndPlayLastStation() }
                            else -> { if (p.playbackState == Player.STATE_IDLE) p.prepare(); p.play() }
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
            updateWidget()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * Reads the current player state and pushes it to the widget via DataStore,
     * then triggers a Glance re-render for every placed widget instance.
     */
    private fun updateWidget() {
        val p = player ?: return
        
        // Read ExoPlayer state on the main thread
        val metadata   = p.currentMediaItem?.mediaMetadata
        val isPlaying = p.isPlaying || (p.playbackState == androidx.media3.common.Player.STATE_BUFFERING && p.playWhenReady)
        
        // Skip intermediate widget updates while artwork is being fetched for a live track.
        // Once artwork resolution finishes (or confirms none), is_fetching_artwork is set to "false",
        // triggering a single clean update with the resolved image and background palette.
        val isFetchingArtwork = metadata?.extras?.getString("is_fetching_artwork") == "true"
        if (isPlaying && isFetchingArtwork) {
            return
        }
        
        // If actively playing -> push live track info
        // If paused -> instantly push base station info
        val title = if (isPlaying) {
            metadata?.title?.toString() ?: getString(R.string.widget_nothing_playing)
        } else {
            metadata?.extras?.getString("stationName") ?: metadata?.title?.toString() ?: getString(R.string.widget_nothing_playing)
        }
        
        val trackCoverArtUrl = if (isPlaying) {
            metadata?.extras?.getString("track_cover_art_url")?.takeIf { it.isNotBlank() }
        } else null
        
        val stationFavicon = metadata?.extras?.getString("stationFavicon")?.takeIf { it.isNotBlank() }
        val isCoverArtFetched = isPlaying && trackCoverArtUrl != null
        
        val artworkUrl = if (isCoverArtFetched) {
            trackCoverArtUrl
        } else {
            stationFavicon
        }
        
        val stationThumbnailUrl = if (isCoverArtFetched) {
            stationFavicon
        } else null
        
        val artist = if (isPlaying) {
            metadata?.artist?.toString() ?: ""
        } else {
            ""
        }

        val hasNext    = p.hasNextMediaItem()
        val hasPrev    = p.hasPreviousMediaItem()
        val stationName = metadata?.extras?.getString("stationName")

        serviceScope.launch(Dispatchers.IO) {
            widgetController.updatePlayback(
                title               = title,
                artist              = artist,
                artworkUrl          = artworkUrl,
                isPlaying           = isPlaying,
                hasNext             = hasNext,
                hasPrev             = hasPrev,
                stationName         = stationName,
                stationThumbnailUrl = stationThumbnailUrl,
                isCoverArtFetched   = isCoverArtFetched,
            )
        }
    }

    /**
     * Pushes a stopped/idle state to the widget.
     * Call this when the service is about to be destroyed or when playback stops.
     */
    private fun pushStoppedWidgetUpdate() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val lastStation = recentRepository.getAllRecent().first().firstOrNull()
            widgetController.cleanStaleWidgetState(lastStation?.name, lastStation?.favicon)
        }
    }

    private fun startStationPlayback(
        stationUuid: String,
        stationUrl: String,
        stationName: String,
        stationFavicon: String,
        volumeLevel: Float,
        transitionSeconds: Int
    ) {
        val artworkUri = when {
            stationFavicon.endsWith(".svg", ignoreCase = true) ->
                android.net.Uri.parse(SvgProxyProvider.createProxyUri(this, stationFavicon))
            stationFavicon.isNotBlank() -> android.net.Uri.parse(stationFavicon)
            else -> android.net.Uri.EMPTY
        }
        val mediaItem = androidx.media3.common.MediaItem.Builder()
            .setMediaId(stationUuid)
            .setUri(stationUrl)
            .setLiveConfiguration(androidx.media3.common.MediaItem.LiveConfiguration.Builder().build())
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(stationName)
                    .setAlbumTitle(stationName)
                    .setArtworkUri(artworkUri)
                    .setExtras(android.os.Bundle().apply {
                        putString("stationName", stationName)
                        putString("stationFavicon", stationFavicon)
                    })
                    .build()
            )
            .build()

        val isSameStation = player?.currentMediaItem?.mediaId == stationUuid
        volumeFadeJob?.cancel()

        val applySystemVolume = {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            val minVolume = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                audioManager.getStreamMinVolume(android.media.AudioManager.STREAM_MUSIC)
            } else {
                0
            }
            val targetVolume = if (volumeLevel > 0f) {
                (volumeLevel * maxVolume).roundToInt().coerceIn(minVolume.coerceAtLeast(1), maxVolume)
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




    /**
     * Restores the last played station with a full playlist context, mirroring
     * the autoPlayOnStart strategy used by PlayerController:
     * 1. Check if the station is in the library → load full library as playlist
     * 2. Fall back to the full recents list as playlist
     */
    private suspend fun restoreAndPlayLastStation() {
        val p = player ?: return
        val lastStation = recentRepository.getAllRecent().first().firstOrNull() ?: run {
            widgetController.cleanStaleWidgetState(null, null)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(true)
            }
            stopSelf()
            return
        }

        val libraryStations = libraryRepository.getAllStations().first()
        val libraryIndex = libraryStations.indexOfFirst { it.stationUuid == lastStation.stationUuid }

        val mediaItems: List<androidx.media3.common.MediaItem>
        val startIndex: Int

        if (libraryIndex != -1) {
            // Found in library: load the full library as playlist
            mediaItems = libraryStations.map { station ->
                buildMediaItem(station)
            }
            startIndex = libraryIndex
        } else {
            // Not in library: fall back to full recents list
            val recentStations = recentRepository.getAllRecent().first()
            val recentIndex = recentStations.indexOfFirst { it.stationUuid == lastStation.stationUuid }.coerceAtLeast(0)
            mediaItems = recentStations.map { station ->
                buildMediaItem(station)
            }
            startIndex = recentIndex
        }

        p.volume = 1f
        p.setMediaItems(mediaItems, startIndex, 0L)
        p.playWhenReady = true
        p.prepare()
    }

    /**
     * Builds a MediaItem from a RadioStation, handling SVG artwork proxying.
     */
    private fun buildMediaItem(station: RadioStation): androidx.media3.common.MediaItem {
        val artworkUri = when {
            !showStationThumbnails -> android.net.Uri.EMPTY
            station.favicon.endsWith(".svg", ignoreCase = true) ->
                android.net.Uri.parse(SvgProxyProvider.createProxyUri(this, station.favicon))
            station.favicon.isNotBlank() -> android.net.Uri.parse(station.favicon)
            else -> android.net.Uri.EMPTY
        }
        return androidx.media3.common.MediaItem.Builder()
            .setMediaId(station.stationUuid)
            .setUri(station.urlResolved.ifBlank { station.url })
            .setLiveConfiguration(androidx.media3.common.MediaItem.LiveConfiguration.Builder().build())
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(station.name)
                    .setAlbumTitle(station.name)
                    .setArtworkUri(artworkUri)
                    .setExtras(android.os.Bundle().apply {
                        putString("stationName", station.name)
                        putString("stationFavicon", station.favicon)
                    })
                    .build()
            )
            .build()
    }

    @OptIn(UnstableApi::class)
    private fun setupLoudnessEnhancer(audioSessionId: Int) {
        if (audioSessionId == androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) return
        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = android.media.audiofx.LoudnessEnhancer(audioSessionId).apply {
                applyBoostGain(this, currentBoostFactor)
            }
        } catch (e: Exception) {
            android.util.Log.e("PlaybackService", "Failed to initialize LoudnessEnhancer", e)
            loudnessEnhancer = null
        }
    }

    private fun applyBoostGain(enhancer: android.media.audiofx.LoudnessEnhancer?, boost: Float) {
        if (enhancer == null) return
        try {
            if (boost > 0f) {
                val gainmB = (boost * 1000).toInt() // Up to +1000 mB (+10 dB) at 200%
                enhancer.setTargetGain(gainmB)
                enhancer.enabled = true
            } else {
                enhancer.setTargetGain(0)
                enhancer.enabled = false
            }
        } catch (e: Exception) {
            android.util.Log.e("PlaybackService", "Failed to apply LoudnessEnhancer gain", e)
        }
    }
}
