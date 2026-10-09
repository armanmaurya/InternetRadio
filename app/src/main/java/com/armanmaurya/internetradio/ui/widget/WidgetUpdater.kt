package com.armanmaurya.internetradio.ui.widget

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.controller.WidgetController
import com.armanmaurya.internetradio.domain.repository.RecentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decoupled widget synchronization manager (AntennaPod pattern).
 * Observes audio playback events and delegates Glance widget state updates,
 * keeping PlaybackService focused strictly on audio session coordination.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val widgetController: WidgetController,
    private val recentRepository: RecentRepository
) : Player.Listener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var attachedPlayer: Player? = null

    fun attachPlayer(player: Player) {
        attachedPlayer?.removeListener(this)
        attachedPlayer = player
        player.addListener(this)
        updateWidget(player)
    }

    fun detachPlayer() {
        attachedPlayer?.removeListener(this)
        attachedPlayer = null
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        updateWidget(attachedPlayer)
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        updateWidget(attachedPlayer)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        updateWidget(attachedPlayer)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        updateWidget(attachedPlayer)
    }

    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
        updateWidget(attachedPlayer)
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        updateWidget(attachedPlayer)
    }

    /**
     * Reads current playback metadata and pushes it to the widget via WidgetController.
     */
    fun updateWidget(player: Player? = attachedPlayer) {
        val p = player ?: return

        val metadata = p.currentMediaItem?.mediaMetadata
        val isPlaying =
            p.isPlaying || (p.playbackState == Player.STATE_BUFFERING && p.playWhenReady)

        val stationName = metadata?.extras?.getString("stationName")
        val stationFavicon =
            metadata?.extras?.getString("stationFavicon")?.takeIf { it.isNotBlank() }
                ?: (p.currentMediaItem?.localConfiguration?.tag as? com.armanmaurya.internetradio.domain.model.RadioStation)?.favicon
                ?: (p.currentMediaItem?.localConfiguration?.tag as? String)?.takeIf { it.isNotBlank() }

        val mediaArtworkUri = metadata?.artworkUri?.toString()?.takeIf { it.isNotBlank() }

        val trackCoverArtUrl = metadata?.extras?.getString("track_cover_art_url")?.takeIf { it.isNotBlank() }
            ?: mediaArtworkUri?.takeIf { it != stationFavicon }

        val isCoverArtFetched = !trackCoverArtUrl.isNullOrBlank()

        val artworkUrl = if (isCoverArtFetched) {
            trackCoverArtUrl
        } else {
            stationFavicon ?: mediaArtworkUri
        }

        val stationThumbnailUrl = if (isCoverArtFetched) {
            stationFavicon
        } else null

        val title = metadata?.title?.toString()?.takeIf { it.isNotBlank() }
            ?: stationName
            ?: context.getString(R.string.widget_nothing_playing)

        val artist = metadata?.artist?.toString() ?: ""

        val hasNext = p.hasNextMediaItem()
        val hasPrev = p.hasPreviousMediaItem()

        scope.launch(Dispatchers.IO) {
            widgetController.updatePlayback(
                title = title,
                artist = artist,
                artworkUrl = artworkUrl,
                isPlaying = isPlaying,
                hasNext = hasNext,
                hasPrev = hasPrev,
                stationName = stationName,
                stationThumbnailUrl = stationThumbnailUrl,
                isCoverArtFetched = isCoverArtFetched,
            )
        }
    }

    /**
     * Pushes a stopped/idle state to the widget.
     * Clears cached payloads and updates with last-played station.
     */
    fun pushStoppedWidgetUpdate() {
        scope.launch(Dispatchers.IO) {
            widgetController.clearLatestPayload()
            val lastStation = recentRepository.getAllRecent().first().firstOrNull()
            widgetController.cleanStaleWidgetState(lastStation?.name, lastStation?.favicon)
        }
    }

    /**
     * Handles widget actions forwarded via Intent from WidgetControlReceiver.
     * Returns true if the action was recognized and handled.
     */
    fun handleWidgetAction(action: String, onRestorePlayback: () -> Unit): Boolean {
        val p = attachedPlayer ?: return false
        when (action) {
            ACTION_WIDGET_PLAY_PAUSE -> {
                when {
                    p.isPlaying || (p.playbackState == Player.STATE_BUFFERING && p.playWhenReady) -> p.pause()
                    p.mediaItemCount == 0 -> onRestorePlayback()
                    else -> {
                        if (p.playbackState == Player.STATE_IDLE) p.prepare()
                        p.play()
                    }
                }
                return true
            }
            ACTION_WIDGET_NEXT -> {
                if (p.mediaItemCount == 0) {
                    onRestorePlayback()
                } else if (p.hasNextMediaItem()) {
                    p.seekToNextMediaItem()
                }
                return true
            }
            ACTION_WIDGET_PREVIOUS -> {
                if (p.mediaItemCount == 0) {
                    onRestorePlayback()
                } else if (p.hasPreviousMediaItem()) {
                    p.seekToPreviousMediaItem()
                }
                return true
            }
            ACTION_WIDGET_UPDATE -> {
                updateWidget()
                return true
            }
            else -> return false
        }
    }

    companion object {
        const val ACTION_WIDGET_PLAY_PAUSE = "com.armanmaurya.internetradio.ACTION_WIDGET_PLAY_PAUSE"
        const val ACTION_WIDGET_NEXT = "com.armanmaurya.internetradio.ACTION_WIDGET_NEXT"
        const val ACTION_WIDGET_PREVIOUS = "com.armanmaurya.internetradio.ACTION_WIDGET_PREVIOUS"
        const val ACTION_WIDGET_UPDATE = "com.armanmaurya.internetradio.ACTION_WIDGET_UPDATE"
    }
}
