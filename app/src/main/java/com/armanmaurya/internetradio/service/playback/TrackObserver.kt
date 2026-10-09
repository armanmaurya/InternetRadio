package com.armanmaurya.internetradio.service.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.armanmaurya.internetradio.core.media.player.TrackParser
import com.armanmaurya.internetradio.domain.repository.CoverArtRepository
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import com.armanmaurya.internetradio.domain.repository.TrackHistoryRepository
import com.armanmaurya.internetradio.ui.widget.WidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * Dedicated player listener responsible for observing playback metadata events,
 * parsing stream ICY tags, asynchronous cover art enrichment, and track history logging.
 * Decouples metadata coordination from the core PlaybackService lifecycle (AntennaPod pattern).
 */
@Singleton
class TrackObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackHistoryRepository: TrackHistoryRepository,
    private val coverArtRepository: CoverArtRepository,
    private val settingsRepository: SettingsRepository,
    private val widgetUpdater: WidgetUpdater
) : Player.Listener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var attachedPlayer: Player? = null
    private var activeFetchJob: Job? = null
    private var lastTrack: String? = null
    private var stationArtworkUri: Uri? = null
    private var showCoverArtInNotification: Boolean = true

    init {
        scope.launch {
            settingsRepository.appPreferencesFlow.collect { prefs ->
                showCoverArtInNotification = prefs.showCoverArtInNotification
            }
        }
    }

    fun attachPlayer(player: Player) {
        attachedPlayer?.removeListener(this)
        attachedPlayer = player
        player.addListener(this)

        val currentMediaItem = player.currentMediaItem
        if (currentMediaItem?.mediaMetadata?.artworkUri != null && currentMediaItem.mediaMetadata.artworkUri != Uri.EMPTY) {
            stationArtworkUri = currentMediaItem.mediaMetadata.artworkUri
        }
    }

    fun detachPlayer() {
        attachedPlayer?.removeListener(this)
        attachedPlayer = null
        activeFetchJob?.cancel()
        activeFetchJob = null
        lastTrack = null
        stationArtworkUri = null
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        activeFetchJob?.cancel()
        activeFetchJob = null
        lastTrack = null
        if (mediaItem?.mediaMetadata?.artworkUri != null && mediaItem.mediaMetadata.artworkUri != Uri.EMPTY) {
            stationArtworkUri = mediaItem.mediaMetadata.artworkUri
        }
    }

    @OptIn(UnstableApi::class)
    override fun onMetadata(metadata: Metadata) {
        val track = TrackParser.parse(metadata)
        if (lastTrack == track || track.isNullOrBlank()) return

        lastTrack = track
        val currentPlayer = attachedPlayer ?: return
        val currentMediaItem = currentPlayer.currentMediaItem ?: return
        val stationUuid = currentMediaItem.mediaId

        val fallbackArtUri = stationArtworkUri ?: currentMediaItem.mediaMetadata.artworkUri

        val baseExtras = currentMediaItem.mediaMetadata.extras?.let { Bundle(it) } ?: Bundle()
        baseExtras.remove("track_cover_art_url")

        val baseMetadata = currentMediaItem.mediaMetadata.buildUpon()
            .setTitle(track)
            .setArtist(null)
            .setArtworkUri(fallbackArtUri)
            .setExtras(baseExtras)
            .build()

        currentPlayer.replaceMediaItem(
            currentPlayer.currentMediaItemIndex,
            currentMediaItem.buildUpon().setMediaMetadata(baseMetadata).build()
        )
        widgetUpdater.updateWidget(currentPlayer)

        activeFetchJob?.cancel()
        activeFetchJob = scope.launch {
            try {
                val trackId = trackHistoryRepository.logTrack(stationUuid, track)

                val trackMetadata = withTimeoutOrNull(4000L.milliseconds) {
                    coverArtRepository.getTrackMetadata(track)
                }

                if (lastTrack != track) return@launch

                val trackName = trackMetadata?.trackName?.takeIf { it.isNotBlank() } ?: track
                val artistName = trackMetadata?.artistName?.takeIf { it.isNotBlank() }
                val coverArtUrl = trackMetadata?.coverArtUrl?.takeIf { it.isNotBlank() }

                if (trackId != null) {
                    val cleanedTitle = if (artistName != null) "$artistName - $trackName" else trackName
                    trackHistoryRepository.updateTrackMetadata(trackId, cleanedTitle, coverArtUrl)
                }

                val finalArtworkUri = if (showCoverArtInNotification && coverArtUrl != null) {
                    coverArtUrl.toUri()
                } else {
                    fallbackArtUri
                }

                val finalExtras = currentMediaItem.mediaMetadata.extras?.let { Bundle(it) } ?: Bundle()
                if (coverArtUrl != null) {
                    finalExtras.putString("track_cover_art_url", coverArtUrl)
                } else {
                    finalExtras.remove("track_cover_art_url")
                }

                val finalMetadata = currentMediaItem.mediaMetadata.buildUpon()
                    .setTitle(trackName)
                    .setArtist(artistName)
                    .setArtworkUri(finalArtworkUri)
                    .setExtras(finalExtras)
                    .build()

                currentPlayer.let { p ->
                    for (i in 0 until p.mediaItemCount) {
                        if (p.getMediaItemAt(i).mediaId == stationUuid) {
                            val itemAtI = p.getMediaItemAt(i)
                            p.replaceMediaItem(
                                i,
                                itemAtI.buildUpon().setMediaMetadata(finalMetadata).build()
                            )
                        }
                    }
                    widgetUpdater.updateWidget(p)
                }
            } catch (e: Exception) {
                Log.e("TrackObserver", "Error during cover art fetching", e)
            }
        }
    }
}
