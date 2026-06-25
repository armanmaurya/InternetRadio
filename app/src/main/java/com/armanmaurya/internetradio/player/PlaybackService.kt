package com.armanmaurya.internetradio.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.armanmaurya.internetradio.MainActivity
import com.armanmaurya.internetradio.data.model.RadioStation
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject
    lateinit var audioAttributes: AudioAttributes

    @Inject
    lateinit var autoCallback: AutoMediaLibraryCallback

    private var player: Player? = null
    private var mediaLibrarySession: MediaLibrarySession? = null

    /**
     * Watches for station changes so the ❤️ button on Android Auto's now-playing
     * screen always shows the correct filled / outline state.
     */
    private val stationChangeListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            autoCallback.updateFavoriteButton(mediaItem?.mediaId)
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
                        if (currentMediaItem.mediaMetadata.title?.toString() == trackTitle) return
                        
                        val newMetadata = currentMediaItem.mediaMetadata.buildUpon()
                            .setTitle(trackTitle)
                            .build()
                        val newMediaItem = currentMediaItem.buildUpon()
                            .setMediaMetadata(newMetadata)
                            .build()
                            
                        // Update metadata without interrupting playback
                        currentPlayer.replaceMediaItem(currentPlayer.currentMediaItemIndex, newMediaItem)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("Icy-MetaData" to "1"))

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)

        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player = object : androidx.media3.common.ForwardingPlayer(exoPlayer) {
            override fun play() {
                // Since stop() removes the notification, we let it pause() normally.
                // However, to prevent playing old buffered audio and crashing from a stale
                // connection when the user resumes, we force a fresh connection here.
                val item = currentMediaItem
                // Only reset if we are actually paused. This prevents interrupting 
                // playback if a redundant play() command is received.
                if (item != null && !playWhenReady) {
                    // Re-setting the item and preparing clears the old buffer 
                    // and establishes a new live connection.
                    setMediaItem(item)
                    prepare()
                }
                super.play()
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
        // Clear session ref first so the callback stops pushing updates
        autoCallback.activeSession = null
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
