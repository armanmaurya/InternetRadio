package com.armanmaurya.internetradio.service.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.repository.LibraryRepository
import com.armanmaurya.internetradio.domain.repository.RecentRepository
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages playlist construction, cold-start playback restoration, and tracks station changes
 * to update playback history and library status (AntennaPod queue pattern).
 */
@Singleton
class PlaybackQueueManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryRepository: LibraryRepository,
    private val recentRepository: RecentRepository,
    private val settingsRepository: SettingsRepository
) : Player.Listener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var attachedPlayer: Player? = null
    private var sessionCallback: PlaybackSessionCallback? = null
    private var libraryStatusJob: Job? = null
    private var showStationThumbnails: Boolean = true

    init {
        scope.launch {
            settingsRepository.appPreferencesFlow.collect { prefs ->
                showStationThumbnails = prefs.showStationThumbnails
            }
        }
    }

    fun attachPlayer(player: Player, callback: PlaybackSessionCallback) {
        detachPlayer()
        attachedPlayer = player
        sessionCallback = callback
        player.addListener(this)
    }

    fun detachPlayer() {
        attachedPlayer?.removeListener(this)
        attachedPlayer = null
        sessionCallback = null
        libraryStatusJob?.cancel()
        libraryStatusJob = null
    }

    @OptIn(UnstableApi::class)
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        libraryStatusJob?.cancel()
        val stationUuid = mediaItem?.mediaId
        if (stationUuid != null) {
            libraryStatusJob = scope.launch {
                libraryRepository.isStationInLibrary(stationUuid).collect {
                    sessionCallback?.updateLibraryButton(stationUuid)
                }
            }
        } else {
            sessionCallback?.updateLibraryButton(null)
        }

        if (stationUuid == null) return

        val tagStation = mediaItem.localConfiguration?.tag as? RadioStation
        if (tagStation != null) {
            scope.launch {
                recentRepository.addRecentStation(tagStation)
            }
        } else {
            scope.launch {
                val dbStation = libraryRepository.getStationById(stationUuid)
                if (dbStation != null) {
                    recentRepository.addRecentStation(dbStation)
                }
            }
        }
    }

    /**
     * Reconstructs the playlist and plays the most recently played station on cold start.
     * Returns true if a station was restored and queued, or false if recents was empty.
     */
    suspend fun restoreAndPlayLastStation(player: Player? = null): Boolean {
        val targetPlayer = player ?: attachedPlayer ?: return false
        val lastStation = recentRepository.getAllRecent().first().firstOrNull() ?: return false

        val libraryStations = libraryRepository.getAllStations().first()
        val libraryIndex =
            libraryStations.indexOfFirst { it.stationUuid == lastStation.stationUuid }

        val mediaItems: List<MediaItem>
        val startIndex: Int

        if (libraryIndex != -1) {
            mediaItems = libraryStations.map { station ->
                station.toMediaItem(context, showThumbnails = showStationThumbnails)
            }
            startIndex = libraryIndex
        } else {
            val recentStations = recentRepository.getAllRecent().first()
            val recentIndex =
                recentStations.indexOfFirst { it.stationUuid == lastStation.stationUuid }
                    .coerceAtLeast(0)
            mediaItems = recentStations.map { station ->
                station.toMediaItem(context, showThumbnails = showStationThumbnails)
            }
            startIndex = recentIndex
        }

        targetPlayer.volume = 1f
        targetPlayer.setMediaItems(mediaItems, startIndex, 0L)
        targetPlayer.playWhenReady = true
        targetPlayer.prepare()
        return true
    }
}
