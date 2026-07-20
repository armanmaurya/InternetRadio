package com.armanmaurya.internetradio.player

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
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.armanmaurya.internetradio.MainActivity
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.data.repository.TrackHistoryRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject
    lateinit var audioAttributes: AudioAttributes

    @Inject
    lateinit var autoCallback: AutoMediaLibraryCallback

    @Inject
    lateinit var trackHistoryRepository: TrackHistoryRepository

    @Inject
    lateinit var recordingManager: RecordingManager

    @Inject
    lateinit var retryStateTracker: RetryStateTracker

    @Inject
    lateinit var settingsRepository: com.armanmaurya.internetradio.data.repository.SettingsRepository

    private var player: Player? = null
    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var loadErrorHandlingPolicy: ExponentialBackoffLoadErrorHandlingPolicy
    
    private var stopOnAudioBecomingNoisy: Boolean = true
    
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

    /**
     * Watches for station changes so the ❤️ button on Android Auto's now-playing
     * screen always shows the correct filled / outline state.
     */
    private val stationChangeListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            autoCallback.updateLibraryButton(mediaItem?.mediaId)
        }

        override fun onMetadata(metadata: androidx.media3.common.Metadata) {
            for (i in 0 until metadata.length()) {
                val entry = metadata.get(i)
                if (entry is androidx.media3.extractor.metadata.icy.IcyInfo) {
                    val trackTitle = entry.title
                    if (!trackTitle.isNullOrBlank()) {
                        val currentPlayer = player ?: return
                        val currentMediaItem = currentPlayer.currentMediaItem ?: return
                        
                        // Avoid unnecessary updates
                        val currentExtras = currentMediaItem.mediaMetadata.extras
                        if (currentExtras?.getString("icy_title") == trackTitle) return
                        
                        val stationName = currentExtras?.getString("stationName")

                        val newExtras = android.os.Bundle(currentExtras ?: android.os.Bundle.EMPTY).apply {
                            putString("icy_title", trackTitle)
                        }

                        val newMetadataBuilder = currentMediaItem.mediaMetadata.buildUpon()
                            .setTitle(stationName)
                            .setArtist(trackTitle)
                            .setExtras(newExtras)
                            
                        val newMediaItem = currentMediaItem.buildUpon()
                            .setMediaMetadata(newMetadataBuilder.build())
                            .build()
                            
                        // Update metadata without interrupting playback
                        currentPlayer.replaceMediaItem(currentPlayer.currentMediaItemIndex, newMediaItem)
                        
                        // Log the track history
                        val stationUuid = currentMediaItem.mediaId
                        serviceScope.launch {
                            trackHistoryRepository.logTrack(stationUuid, trackTitle)
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        loadErrorHandlingPolicy = ExponentialBackoffLoadErrorHandlingPolicy(retryStateTracker)
        
        serviceScope.launch {
            settingsRepository.appPreferencesFlow.collect { prefs ->
                loadErrorHandlingPolicy.maxRetryDurationMs = prefs.maxRetryDuration
                stopOnAudioBecomingNoisy = prefs.stopOnAudioBecomingNoisy
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

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf("Icy-MetaData" to "1"))

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
                    .setAudioProcessors(arrayOf(AmplitudeAudioProcessor(recordingManager)))
                    .build()
            }
        }

        val exoPlayer = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .build()
            
        registerReceiver(audioNoisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))

        player = object : androidx.media3.common.ForwardingPlayer(exoPlayer) {
            override fun play() {
                // Since stop() removes the notification, we let it pause() normally.
                // However, to prevent playing old buffered audio and crashing from a stale
                // connection when the user resumes, we force a fresh connection here.
                val item = currentMediaItem
                // Only reset if we are actually paused. This prevents interrupting 
                // playback if a redundant play() command is received.
                if (item != null && !playWhenReady) {
                    retryStateTracker.reset()
                    // Seeking to the default position (the live edge) forces ExoPlayer 
                    // to discard the stale buffer and reconnect.
                    seekToDefaultPosition()
                    prepare()
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

            val intent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            mediaLibrarySession = MediaLibrarySession.Builder(this, it, autoCallback)
                .setSessionActivity(pendingIntent)
                .setBitmapLoader(CoilBitmapLoader(this))
                .build()

            // Give the callback a reference to the session so it can push
            // custom layout updates (e.g. refreshing the heart icon) at any time
            autoCallback.activeSession = mediaLibrarySession
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        serviceScope.cancel()
        // Clear session ref first so the callback stops pushing updates
        autoCallback.activeSession = null
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
        if (player != null) {
            if (!player.playWhenReady || player.mediaItemCount == 0) {
                stopSelf()
            }
        }
    }
}
