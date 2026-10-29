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
import com.armanmaurya.internetradio.core.utils.TrackSanitizer
import com.armanmaurya.internetradio.domain.repository.CoverArtRepository
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import com.armanmaurya.internetradio.domain.repository.TrackHistoryRepository
import com.armanmaurya.internetradio.ui.widget.WidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private var progressHeartbeatJob: Job? = null
    private var lastTrack: String? = null
    private var currentTrackId: Long? = null
    private var activeSliceStartMs: Long? = null
    private var historyTrack: HistoryTrack? = null
    private var resumeJob: Job? = null
    // Serialises history logging so a resume and a metadata event can't both insert a row
    private val historyMutex = Mutex()
    private var stationArtworkUri: Uri? = null
    private var showCoverArtInNotification: Boolean = true

    /** The track currently credited in history, kept across pauses so resuming can continue it. */
    private data class HistoryTrack(
        val stationUuid: String,
        val title: String,
        val rawTitle: String,
        val cleanedTitle: String? = null,
        val coverArtUrl: String? = null
    )

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
        flushActiveProgressAsync()
        stopProgressHeartbeat()
        attachedPlayer?.removeListener(this)
        attachedPlayer = null
        activeFetchJob?.cancel()
        activeFetchJob = null
        resumeJob?.cancel()
        resumeJob = null
        lastTrack = null
        currentTrackId = null
        activeSliceStartMs = null
        historyTrack = null
        stationArtworkUri = null
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        flushActiveProgressAsync()
        stopProgressHeartbeat()
        activeFetchJob?.cancel()
        activeFetchJob = null
        resumeJob?.cancel()
        resumeJob = null
        lastTrack = null
        currentTrackId = null
        activeSliceStartMs = null
        historyTrack = null
        if (mediaItem?.mediaMetadata?.artworkUri != null && mediaItem.mediaMetadata.artworkUri != Uri.EMPTY) {
            stationArtworkUri = mediaItem.mediaMetadata.artworkUri
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            if (activeSliceStartMs == null) {
                resumeHistoryTrack()
            }
        } else {
            flushActiveProgressAsync()
            stopProgressHeartbeat()
            if (attachedPlayer?.playWhenReady == false) {
                lastTrack = null
                currentTrackId = null
            }
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady) {
            flushActiveProgressAsync()
            stopProgressHeartbeat()
            lastTrack = null
            currentTrackId = null
        }
    }

    /**
     * Streams usually don't resend the title after a pause, so resuming re-logs the last track.
     * [TrackHistoryRepository.logTrack] continues the same row after a short pause and starts a
     * new one once the pause reaches the session gap.
     */
    private fun resumeHistoryTrack() {
        val track = historyTrack ?: return
        resumeJob?.cancel()
        resumeJob = scope.launch {
            historyMutex.withLock {
                if (activeSliceStartMs != null || historyTrack?.rawTitle != track.rawTitle) return@withLock
                val trackId = trackHistoryRepository.logTrack(track.stationUuid, track.title, track.rawTitle)
                currentTrackId = trackId
                // A row started after a long pause needs the cleaned title and cover art; reapplying is harmless
                if (trackId != null && track.cleanedTitle != null) {
                    trackHistoryRepository.updateTrackMetadata(trackId, track.cleanedTitle, track.coverArtUrl)
                }
                if (trackId != null && attachedPlayer?.isPlaying == true) {
                    activeSliceStartMs = System.currentTimeMillis()
                    startProgressHeartbeat()
                }
            }
        }
    }

    private fun startProgressHeartbeat() {
        progressHeartbeatJob?.cancel()
        progressHeartbeatJob = scope.launch {
            while (isActive) {
                delay(15_000L)
                if (attachedPlayer?.isPlaying == true) {
                    flushActiveProgressSuspend(keepSliceActive = true)
                }
            }
        }
    }

    private fun stopProgressHeartbeat() {
        progressHeartbeatJob?.cancel()
        progressHeartbeatJob = null
    }

    private fun flushActiveProgressAsync() {
        val id = currentTrackId
        val start = activeSliceStartMs
        activeSliceStartMs = null
        if (id != null && start != null) {
            val now = System.currentTimeMillis()
            val delta = (now - start).coerceAtLeast(0L)
            scope.launch {
                trackHistoryRepository.recordTrackListeningProgress(id, now, delta)
            }
        }
    }

    private suspend fun flushActiveProgressSuspend(keepSliceActive: Boolean) {
        val id = currentTrackId
        val start = activeSliceStartMs
        if (id != null && start != null) {
            val now = System.currentTimeMillis()
            val delta = (now - start).coerceAtLeast(0L)
            activeSliceStartMs = if (keepSliceActive) now else null
            trackHistoryRepository.recordTrackListeningProgress(id, now, delta)
        } else if (!keepSliceActive) {
            activeSliceStartMs = null
        }
    }

    @OptIn(UnstableApi::class)
    override fun onMetadata(metadata: Metadata) {
        val rawTrack = TrackParser.parse(metadata)
        if (lastTrack == rawTrack || rawTrack.isNullOrBlank()) return

        lastTrack = rawTrack
        val track = TrackSanitizer.sanitize(rawTrack)
        if (track.isBlank()) return

        val currentPlayer = attachedPlayer ?: return
        val currentMediaItem = currentPlayer.currentMediaItem ?: return
        val stationUuid = currentMediaItem.mediaId
        historyTrack = HistoryTrack(stationUuid = stationUuid, title = track, rawTitle = rawTrack)

        val fallbackArtUri = stationArtworkUri ?: currentMediaItem.mediaMetadata.artworkUri

        val baseExtras = currentMediaItem.mediaMetadata.extras?.let { Bundle(it) } ?: Bundle()
        baseExtras.remove("track_cover_art_url")
        baseExtras.putString("raw_track_name", rawTrack)
        baseExtras.putString("is_fetching_artwork", "true")

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
                val trackId = historyMutex.withLock {
                    flushActiveProgressSuspend(keepSliceActive = false)
                    stopProgressHeartbeat()

                    val loggedId = trackHistoryRepository.logTrack(stationUuid, track, rawTrack)
                    currentTrackId = loggedId
                    if (loggedId != null && currentPlayer.isPlaying) {
                        activeSliceStartMs = System.currentTimeMillis()
                        startProgressHeartbeat()
                    } else {
                        activeSliceStartMs = null
                    }
                    loggedId
                }

                val trackMetadata = withTimeoutOrNull(4000L.milliseconds) {
                    coverArtRepository.getTrackMetadata(track)
                }

                // lastTrack is cleared on pause, so check the history track to see if this song is still current
                if (historyTrack?.rawTitle != rawTrack) return@launch

                val trackName = trackMetadata?.trackName?.takeIf { it.isNotBlank() } ?: track
                val artistName = trackMetadata?.artistName?.takeIf { it.isNotBlank() }
                val coverArtUrl = trackMetadata?.coverArtUrl?.takeIf { it.isNotBlank() }

                if (trackId != null) {
                    val cleanedTitle = if (artistName != null) "$trackName - $artistName" else trackName
                    trackHistoryRepository.updateTrackMetadata(trackId, cleanedTitle, coverArtUrl)
                    // Remembered so a row started on resume gets the same cleaned title and cover art
                    historyTrack = historyTrack?.takeIf { it.rawTitle == rawTrack }
                        ?.copy(cleanedTitle = cleanedTitle, coverArtUrl = coverArtUrl)
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
                finalExtras.putString("raw_track_name", rawTrack)
                finalExtras.putString("is_fetching_artwork", "false")
                if (trackName.isNotBlank()) {
                    finalExtras.putString("clean_track_name", trackName)
                }
                if (!artistName.isNullOrBlank()) {
                    finalExtras.putString("clean_artist_name", artistName)
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
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("TrackObserver", "Error during cover art fetching", e)
                try {
                    val errExtras = currentMediaItem.mediaMetadata.extras?.let { Bundle(it) } ?: Bundle()
                    errExtras.putString("is_fetching_artwork", "false")
                    val errMetadata = currentMediaItem.mediaMetadata.buildUpon()
                        .setExtras(errExtras)
                        .build()
                    currentPlayer.replaceMediaItem(
                        currentPlayer.currentMediaItemIndex,
                        currentMediaItem.buildUpon().setMediaMetadata(errMetadata).build()
                    )
                } catch (_: Exception) {}
            }
        }
    }
}
