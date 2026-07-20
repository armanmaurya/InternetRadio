package com.armanmaurya.internetradio.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.data.repository.SettingsRepository
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.armanmaurya.internetradio.data.repository.RecentRepository
import com.armanmaurya.internetradio.data.repository.StationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val stationRepository: StationRepository,
    private val recentRepository: RecentRepository,
    private val recordingManager: RecordingManager
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller: MediaController? get() = if (controllerFuture?.isDone == true) controllerFuture?.get() else null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState = _playbackState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var activeStation: RadioStation? = null
    private var currentPlaylist: List<RadioStation> = emptyList()
    private var currentPlaybackSource: PlaybackSource = PlaybackSource.None
    private var isFetchingMore = false

    /**
     * Syncs the playback context from Android Auto.
     * By updating the playback source to Browse/Library/Recent, the controller's
     * existing onMediaItemTransition logic will dynamically fetch more items (pagination)
     * as the user skips forward in Android Auto.
     */
    fun syncAndroidAutoContext(stations: List<RadioStation>, startIndex: Int, source: PlaybackSource) {
        currentPlaylist = stations
        currentPlaybackSource = source
        val station = stations.getOrNull(startIndex)
        if (station != null) {
            activeStation = station
            _playbackState.update { it.copy(currentStation = station, currentTrack = null) }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            super.onEvents(player, events)
            _playbackState.update { 
                it.copy(
                    hasNext = player.hasNextMediaItem(),
                    hasPrevious = player.hasPreviousMediaItem()
                )
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            _playbackState.update {
                it.copy(
                    isLoading = state == Player.STATE_BUFFERING,
                    isError = state == Player.STATE_IDLE && controller?.playerError != null
                )
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem == null) {
                activeStation = null
                _playbackState.update { it.copy(currentStation = null) }
                return
            }

            val originalId = mediaItem.mediaId.substringAfter("|")
            if (originalId == activeStation?.stationUuid) {
                _playbackState.update { it.copy(currentStation = activeStation) }
                return
            }
            
            // Station has changed, stop previous recording
            recordingManager.stopRecording()

            val tagStation = currentPlaylist.find { it.stationUuid == originalId } 
                ?: mediaItem.localConfiguration?.tag as? RadioStation
                
            if (tagStation != null) {
                activeStation = tagStation
                _playbackState.update { it.copy(currentStation = activeStation, currentTrack = null) }
                scope.launch { recentRepository.addRecentStation(tagStation) }
            }
            
            // Check if we need to load more
            controller?.let { player ->
                val currentIndex = player.currentMediaItemIndex
                val itemCount = player.mediaItemCount
                if (itemCount - currentIndex <= 5 && !isFetchingMore && currentPlaybackSource is PlaybackSource.Browse) {
                    val browseSource = currentPlaybackSource as PlaybackSource.Browse
                    isFetchingMore = true
                    scope.launch {
                        stationRepository.filterStations(
                            name = browseSource.name.takeIf { it.isNotBlank() },
                            countryCode = browseSource.countryCode?.takeIf { it.isNotBlank() },
                            language = browseSource.language?.takeIf { it.isNotBlank() },
                            tagList = browseSource.tagList?.takeIf { it.isNotBlank() },
                            order = browseSource.order,
                            reverse = browseSource.reverse,
                            limit = 60,
                            offset = itemCount
                        ).onSuccess { newStations ->
                            if (newStations.isNotEmpty()) {
                                val existingIds = (0 until player.mediaItemCount)
                                    .mapNotNull { player.getMediaItemAt(it).mediaId }
                                    .toSet()
                                val uniqueNew = newStations.filter { it.stationUuid !in existingIds }
                                currentPlaylist = currentPlaylist + uniqueNew
                                val newMediaItems = uniqueNew.map { it.toMediaItem() }
                                player.addMediaItems(newMediaItems)
                            }
                        }
                        isFetchingMore = false
                    }
                }
            }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            val trackInfo = mediaMetadata.artist?.toString() ?: mediaMetadata.title?.toString()
            if (trackInfo != null && trackInfo.isNotBlank() && trackInfo != activeStation?.name) {
                _playbackState.update { it.copy(currentTrack = trackInfo) }
            } else {
                _playbackState.update { it.copy(currentTrack = null) }
            }
        }
    }

    init {
        initializeController()
    }

    private fun initializeController() {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            controller?.let { 
                it.addListener(playerListener)
                val currentItem = it.currentMediaItem
                if (currentItem != null) {
                    val station = currentPlaylist.find { s -> s.stationUuid == currentItem.mediaId } 
                        ?: currentItem.localConfiguration?.tag as? RadioStation
                    if (station != null) {
                        activeStation = station
                        _playbackState.update { state ->
                            state.copy(
                                isPlaying = it.isPlaying,
                                currentStation = station
                            )
                        }
                    }
                } else {
                    _playbackState.update { state ->
                        state.copy(
                            isPlaying = it.isPlaying,
                            currentStation = null
                        )
                    }
                    
                    scope.launch {
                        val prefs = settingsRepository.appPreferencesFlow.first()
                        val autoPlay = prefs.autoPlayOnStart
                        val station = recentRepository.getAllRecent().first().firstOrNull()
                        if (station != null) {
                            try {
                                if (autoPlay) {
                                    currentPlaylist = listOf(station)
                                    activeStation = station
                                    _playbackState.update { state ->
                                        state.copy(
                                            isPlaying = true,
                                            currentStation = station
                                        )
                                    }
                                    it.setMediaItem(station.toMediaItem())
                                    it.prepare()
                                    it.play()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        }, MoreExecutors.directExecutor())
    }

    fun play(stations: List<RadioStation>, startIndex: Int, source: PlaybackSource = PlaybackSource.None, playWhenReady: Boolean = true) {
        val player = controller ?: return
        if (stations.isEmpty() || startIndex !in stations.indices) return
        
        currentPlaybackSource = source
        currentPlaylist = stations
        val station = stations[startIndex]

        if (activeStation?.stationUuid == station.stationUuid) {
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            if (playWhenReady) player.play() else player.pause()
            return
        }

        activeStation = station
        _playbackState.update { it.copy(currentStation = station, currentTrack = null) }
        
        val mediaItems = stations.map { it.toMediaItem() }
        player.setMediaItems(mediaItems, startIndex, 0L)
        player.prepare()
        if (playWhenReady) player.play() else player.pause()
    }
    
    fun updateCurrentStation(updatedStation: RadioStation) {
        val player = controller ?: return
        if (activeStation?.stationUuid != updatedStation.stationUuid) return

        val urlChanged = activeStation?.url != updatedStation.url || activeStation?.urlResolved != updatedStation.urlResolved

        activeStation = updatedStation
        _playbackState.update { it.copy(currentStation = updatedStation) }
        
        currentPlaylist = currentPlaylist.map { 
            if (it.stationUuid == updatedStation.stationUuid) updatedStation else it 
        }

        if (urlChanged) {
            val currentIndex = player.currentMediaItemIndex
            if (currentIndex != -1) {
                val mediaItems = currentPlaylist.map { it.toMediaItem() }
                val position = player.currentPosition
                player.setMediaItems(mediaItems, currentIndex, position)
                player.prepare()
                player.play()
            }
        } else {
            val currentIndex = player.currentMediaItemIndex
            if (currentIndex != -1) {
                player.replaceMediaItem(currentIndex, updatedStation.toMediaItem())
            }
        }
    }
    
    fun next() {
        controller?.let { player ->
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
            }
        }
    }

    fun previous() {
        controller?.let { player ->
            if (player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem()
            }
        }
    }

    fun pause() {
        val player = controller ?: return
        if (player.isPlaying || player.playWhenReady) {
            player.pause()
        }
    }

    fun togglePlayPause() {
        val player = controller ?: return
        val isBuffering = player.playbackState == Player.STATE_BUFFERING && player.playWhenReady
        if (player.isPlaying || isBuffering) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            player.play()
        }
    }

    fun setVolume(volume: Float) {
        controller?.volume = volume
    }

    fun stop() {
        val player = controller ?: return
        player.stop()
        player.clearMediaItems()
        cancelSleepTimer()
    }

    private var timerJob: Job? = null

    fun setSleepTimer(durationMillis: Long) {
        timerJob?.cancel()
        val endTime = System.currentTimeMillis() + durationMillis
        _playbackState.update { it.copy(sleepTimerEndTime = endTime, sleepTimerTotalDuration = durationMillis) }
        
        timerJob = scope.launch {
            delay(durationMillis)
            stop()
            _playbackState.update { it.copy(sleepTimerEndTime = null, sleepTimerTotalDuration = 0L) }
        }
    }

    fun cancelSleepTimer() {
        timerJob?.cancel()
        timerJob = null
        _playbackState.update { it.copy(sleepTimerEndTime = null, sleepTimerTotalDuration = 0L) }
    }

    private fun MediaItem.toRadioStation(): RadioStation? {
        return localConfiguration?.tag as? RadioStation
    }
    
    private fun RadioStation.toMediaItem(): MediaItem {
        val artworkUriStr = if (favicon.endsWith(".svg", ignoreCase = true)) {
            SvgProxyProvider.createProxyUri(context, favicon)
        } else {
            favicon.takeIf { it.isNotBlank() }
        }
        val artworkUri = artworkUriStr?.let { android.net.Uri.parse(it) } ?: android.net.Uri.EMPTY

        return MediaItem.Builder()
            .setMediaId(this.stationUuid)
            .setUri(this.urlResolved)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(this.name)
                    .setArtworkUri(artworkUri)
                    .setExtras(android.os.Bundle().apply {
                        putString("stationName", this@toMediaItem.name)
                    })
                    .build()
            )
            .setTag(this)
            .build()
    }
}

sealed class PlaybackSource {
    data class Browse(
        val name: String,
        val countryCode: String?,
        val language: String?,
        val tagList: String?,
        val order: String,
        val reverse: Boolean
    ) : PlaybackSource()
    
    object Library : PlaybackSource()
    object Recent : PlaybackSource()
    object None : PlaybackSource()
}

data class PlaybackState(
    val currentStation: RadioStation? = null,
    val currentTrack: String? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val sleepTimerEndTime: Long? = null,
    val sleepTimerTotalDuration: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false
)
