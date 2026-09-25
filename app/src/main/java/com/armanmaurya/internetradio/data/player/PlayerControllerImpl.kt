package com.armanmaurya.internetradio.data.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.armanmaurya.internetradio.domain.controller.PlayerController
import com.armanmaurya.internetradio.domain.model.AppPreferences
import com.armanmaurya.internetradio.domain.model.LibrarySortOption
import com.armanmaurya.internetradio.domain.model.PlaybackSource
import com.armanmaurya.internetradio.domain.model.PlaybackState
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import com.armanmaurya.internetradio.service.PlaybackService
import com.armanmaurya.internetradio.service.playback.PlaybackSessionCallback
import com.armanmaurya.internetradio.service.playback.toMediaItem
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.armanmaurya.internetradio.core.media.prober.StreamProber
import com.armanmaurya.internetradio.domain.repository.RecentRepository
import com.armanmaurya.internetradio.domain.repository.StationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val stationRepository: StationRepository,
    private val recentRepository: RecentRepository,
    private val libraryRepository: com.armanmaurya.internetradio.domain.repository.LibraryRepository,
    private val okHttpClient: okhttp3.OkHttpClient,
    private val streamProber: StreamProber
) : PlayerController {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller: MediaController? get() = if (controllerFuture?.isDone == true) controllerFuture?.get() else null

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    override val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var activeStation: RadioStation? = null
    private var currentPlaylist: List<RadioStation> = emptyList()
    override val currentPlaylistSnapshot: List<RadioStation> get() = currentPlaylist
    private var currentPlaybackSource: PlaybackSource = PlaybackSource.None
    private var isFetchingMore = false


    /**
     * Syncs the playback context from Android Auto.
     * By updating the playback source to Browse/Library/Recent, the controller's
     * existing onMediaItemTransition logic will dynamically fetch more items (pagination)
     * as the user skips forward in Android Auto.
     */
    override fun syncAndroidAutoContext(stations: List<RadioStation>, startIndex: Int, source: PlaybackSource) {
        currentPlaylist = stations
        currentPlaybackSource = source
        val station = stations.getOrNull(startIndex)
        if (station != null) {
            activeStation = station
            _playbackState.update { it.copy(currentStation = station, currentPlaylist = currentPlaylist, currentPlaylistIndex = startIndex, currentTrack = null, trackStartTime = null, lyricsSyncOffsetMs = 0L, playbackSource = currentPlaybackSource) }
        }
    }

    override val currentPosition: Long
        get() = controller?.currentPosition ?: 0L

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val now = System.currentTimeMillis()
            _playbackState.update { state ->
                if (isPlaying) {
                    state.copy(
                        isPlaying = true,
                        sessionResumeTimeMs = now
                    )
                } else {
                    val activeTime = state.sessionResumeTimeMs?.let { now - it } ?: 0L
                    state.copy(
                        isPlaying = false,
                        sessionActiveDurationMs = state.sessionActiveDurationMs + activeTime,
                        sessionResumeTimeMs = null
                    )
                }
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            super.onEvents(player, events)
            _playbackState.update { state ->
                var updatedState = state.copy(
                    hasNext = player.hasNextMediaItem(),
                    hasPrevious = player.hasPreviousMediaItem()
                )
                
                if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                    updatedState = updatedState.copy(streamCodec = null, streamBitrate = null)
                    
                    val currentStation = updatedState.currentStation
                    val needsCodec = currentStation?.codec.isNullOrBlank() || currentStation?.codec?.uppercase() == "UNKNOWN"
                    val needsBitrate = currentStation?.bitrate == null || currentStation.bitrate == 0
                    
                    if (currentStation != null && (needsCodec || needsBitrate)) {
                        val url = currentStation.url
                        scope.launch {
                            val probeResult = streamProber.probe(url)
                            if (probeResult != null) {
                                _playbackState.update { s ->
                                    if (s.currentStation?.stationUuid == currentStation.stationUuid) {
                                        s.copy(
                                            streamCodec = probeResult.codec.takeIf { it.isNotBlank() },
                                            streamBitrate = probeResult.bitrate.takeIf { it > 0 }
                                        )
                                    } else {
                                        s
                                    }
                                }
                            }
                        }
                    }
                }
                
                updatedState
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            _playbackState.update {
                val isTransientIdle = state == Player.STATE_IDLE && controller?.playWhenReady == true && controller?.playerError == null
                it.copy(
                    isLoading = state == Player.STATE_BUFFERING || (it.isLoading && isTransientIdle),
                    isError = state == Player.STATE_IDLE && controller?.playerError != null
                )
            }
        }

        override fun onVolumeChanged(volume: Float) {
            _playbackState.update { current ->
                if (current.volume > 1.0f && volume == 1.0f) current
                else current.copy(volume = volume)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val currentIndex = controller?.currentMediaItemIndex ?: -1
            if (mediaItem == null) {
                activeStation = null
                _playbackState.update { it.copy(currentStation = null, currentPlaylistIndex = currentIndex) }
                return
            }

            val originalId = mediaItem.mediaId.substringAfter("|")
            if (originalId == activeStation?.stationUuid) {
                _playbackState.update { it.copy(currentStation = activeStation, currentPlaylistIndex = currentPlaylist.indexOf(activeStation).coerceAtLeast(0)) }
                return
            }
            
            val tagStation = currentPlaylist.find { it.stationUuid == originalId } 
                ?: mediaItem.localConfiguration?.tag as? RadioStation
                
            if (tagStation != null) {
                activeStation = tagStation
                _playbackState.update { 
                    it.copy(
                        currentStation = activeStation, 
                        currentPlaylistIndex = currentPlaylist.indexOf(tagStation).coerceAtLeast(0),
                        currentTrack = null, 
                        trackStartTime = null, 
                        trackCoverArtUri = null,
                        isFetchingArtwork = false,
                        lyricsSyncOffsetMs = 0L,
                        sessionActiveDurationMs = 0L,
                        sessionResumeTimeMs = null
                    ) 
                }
                scope.launch { recentRepository.addRecentStation(tagStation) }
                
                if (controller?.mediaItemCount == 1) {
                    scope.launch { linkSingleItemToContext(tagStation) }
                }
            } else {
                // Fallback for scheduled cold start: get FULL RadioStation from library DB
                scope.launch {
                    val dbStation = libraryRepository.getStationById(originalId)
                    if (dbStation != null) {
                        linkSingleItemToContext(dbStation)
                    }
                }
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
                                _playbackState.update { it.copy(currentPlaylist = currentPlaylist) }
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
            val artworkUri = mediaMetadata.extras?.getString("track_cover_art_url")
            val cleanTrackName = mediaMetadata.extras?.getString("clean_track_name")
            val cleanArtistName = mediaMetadata.extras?.getString("clean_artist_name")
            val rawTrackName = mediaMetadata.extras?.getString("icy_raw_title")
            val trackInfo = mediaMetadata.extras?.getString("icy_title") 
                ?: if (mediaMetadata.artist != null && mediaMetadata.title != null) {
                    "${mediaMetadata.artist} - ${mediaMetadata.title}"
                } else {
                    mediaMetadata.title?.toString() ?: mediaMetadata.artist?.toString()
                }
            if (trackInfo != null && trackInfo.isNotBlank() && trackInfo != activeStation?.name) {
                // Read the exact start time recorded by the background service. 
                // If it's -1, it means it's the tune-in track and we don't know the position.
                val exactStartTime = mediaMetadata.extras?.getLong("track_start_time")?.takeIf { it > 0L }
                val isFetchingArtwork = mediaMetadata.extras?.getString("is_fetching_artwork") == "true"
                
                _playbackState.update { it.copy(
                    currentTrack = trackInfo, 
                    trackStartTime = exactStartTime,
                    trackCoverArtUri = artworkUri,
                    isFetchingArtwork = isFetchingArtwork,
                    cleanTrackName = cleanTrackName,
                    cleanArtistName = cleanArtistName,
                    rawTrackName = rawTrackName
                ) }
            } else {
                _playbackState.update { it.copy(currentTrack = null, trackStartTime = null, trackCoverArtUri = artworkUri, isFetchingArtwork = false, cleanTrackName = null, cleanArtistName = null, rawTrackName = null) }
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
                
                val currentVolume = it.volume
                val isCurrentlyPlaying = it.isPlaying
                val isCurrentlyLoading = it.playbackState == Player.STATE_BUFFERING
                val isCurrentlyError = it.playbackState == Player.STATE_IDLE && it.playerError != null
                val hasNext = it.hasNextMediaItem()
                val hasPrevious = it.hasPreviousMediaItem()

                _playbackState.update { state ->
                    state.copy(
                        volume = currentVolume,
                        isLoading = isCurrentlyLoading,
                        isError = isCurrentlyError,
                        hasNext = hasNext,
                        hasPrevious = hasPrevious
                    )
                }

                val currentItem = it.currentMediaItem
                val currentIndex = it.currentMediaItemIndex
                if (currentItem != null) {
                    val originalId = currentItem.mediaId.substringAfter("|")
                    val station = currentPlaylist.find { s -> s.stationUuid == originalId } 
                        ?: currentItem.localConfiguration?.tag as? RadioStation
                    
                    if (station != null) {
                        activeStation = station
                        _playbackState.update { state ->
                            state.copy(
                                isPlaying = isCurrentlyPlaying,
                                currentStation = station,
                                currentPlaylist = currentPlaylist,
                                currentPlaylistIndex = currentIndex
                            )
                        }
                    } else {
                        // Fallback for scheduled cold start: get FULL RadioStation from library DB
                        scope.launch {
                            val dbStation = libraryRepository.getStationById(originalId)
                            if (dbStation != null) {
                                linkSingleItemToContext(dbStation)
                            }
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
                                    val libraryStations = getFilteredLibraryStations(prefs)
                                    val libraryIndex = libraryStations.indexOfFirst { s -> s.stationUuid == station.stationUuid }
                                    
                                    if (libraryIndex != -1) {
                                        currentPlaylist = libraryStations
                                        currentPlaybackSource = PlaybackSource.Library
                                        activeStation = station
                                        _playbackState.update { state ->
                                            state.copy(
                                                isLoading = true,
                                                currentStation = station,
                                                currentPlaylist = currentPlaylist,
                                                currentPlaylistIndex = libraryIndex,
                                                playbackSource = PlaybackSource.Library
                                            )
                                        }
                                        it.setMediaItems(libraryStations.map { s -> s.toMediaItem() }, libraryIndex, 0L)
                                    } else {
                                        val recentStations = recentRepository.getAllRecent().first()
                                        val recentIndex = recentStations.indexOfFirst { s -> s.stationUuid == station.stationUuid }.coerceAtLeast(0)
                                        currentPlaylist = recentStations
                                        currentPlaybackSource = PlaybackSource.Recent
                                        activeStation = station
                                        _playbackState.update { state ->
                                            state.copy(
                                                isLoading = true,
                                                currentStation = station,
                                                currentPlaylist = currentPlaylist,
                                                currentPlaylistIndex = recentIndex,
                                                playbackSource = PlaybackSource.Recent
                                            )
                                        }
                                        it.setMediaItems(recentStations.map { s -> s.toMediaItem() }, recentIndex, 0L)
                                    }
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

    override fun play(stations: List<RadioStation>, startIndex: Int, source: PlaybackSource, playWhenReady: Boolean) {
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
        _playbackState.update { 
            it.copy(
                isPlaying = false,
                isLoading = playWhenReady,
                currentStation = station, 
                currentPlaylist = currentPlaylist, 
                currentPlaylistIndex = startIndex, 
                currentTrack = null, 
                trackStartTime = null, 
                lyricsSyncOffsetMs = 0L, 
                playbackSource = currentPlaybackSource,
                sessionActiveDurationMs = 0L,
                sessionResumeTimeMs = null
            ) 
        }
        
        val mediaItems = stations.map { it.toMediaItem() }
        player.setMediaItems(mediaItems, startIndex, 0L)
        player.volume = 1f
        val boostResetArgs = android.os.Bundle().apply { putFloat("KEY_BOOST", 0f) }
        player.sendCustomCommand(PlaybackSessionCallback.COMMAND_SET_VOLUME_BOOST, boostResetArgs)
        player.prepare()
        if (playWhenReady) player.play() else player.pause()
    }

    
    override fun updateCurrentStation(updatedStation: RadioStation, oldUuid: String?) {
        val player = controller ?: return
        val targetUuid = oldUuid ?: updatedStation.stationUuid
        if (activeStation?.stationUuid != targetUuid) return

        val urlChanged = activeStation?.url != updatedStation.url || activeStation?.urlResolved != updatedStation.urlResolved

        activeStation = updatedStation
        _playbackState.update { it.copy(currentStation = updatedStation) }
        
        currentPlaylist = currentPlaylist.map { 
            if (it.stationUuid == targetUuid) updatedStation else it 
        }
        _playbackState.update { it.copy(currentPlaylist = currentPlaylist) }

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
    
    override fun playIndex(index: Int) {
        val player = controller ?: return
        if (index in 0 until player.mediaItemCount) {
            player.seekToDefaultPosition(index)
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            if (!player.isPlaying) {
                player.play()
            }
        }
    }

    override fun next() {
        controller?.let { player ->
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
            }
        }
    }

    override fun previous() {
        controller?.let { player ->
            if (player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem()
            }
        }
    }

    override fun pause() {
        val player = controller ?: return
        if (player.isPlaying || player.playWhenReady) {
            player.pause()
        }
    }

    override fun togglePlayPause() {
        val player = controller ?: return
        val isBuffering = player.playbackState == Player.STATE_BUFFERING && player.playWhenReady
        if (player.isPlaying || isBuffering) {
            player.pause()
        } else {
            _playbackState.update { it.copy(isLoading = true) }
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            player.play()
        }
    }

    override fun setVolume(volume: Float) {
        val clampedPlayerVolume = volume.coerceIn(0f, 1f)
        controller?.volume = clampedPlayerVolume
        val boost = if (volume > 1f) (volume - 1f).coerceIn(0f, 1f) else 0f
        val args = android.os.Bundle().apply { putFloat("KEY_BOOST", boost) }
        controller?.sendCustomCommand(PlaybackSessionCallback.COMMAND_SET_VOLUME_BOOST, args)
        _playbackState.update { it.copy(volume = volume) }
    }

    override fun stop() {
        val player = controller ?: return
        player.stop()
        player.clearMediaItems()
        cancelSleepTimer()
        _playbackState.update {
            it.copy(
                isPlaying = false,
                sessionActiveDurationMs = 0L,
                sessionResumeTimeMs = null
            )
        }
        _amplitude.value = 0f
    }

    private var timerJob: Job? = null

    override fun setSleepTimer(durationMillis: Long) {
        timerJob?.cancel()
        val endTime = System.currentTimeMillis() + durationMillis
        _playbackState.update { it.copy(sleepTimerEndTime = endTime, sleepTimerTotalDuration = durationMillis) }
        
        timerJob = scope.launch {
            delay(durationMillis)
            stop()
            _playbackState.update { it.copy(sleepTimerEndTime = null, sleepTimerTotalDuration = 0L) }
        }
    }

    override fun cancelSleepTimer() {
        timerJob?.cancel()
        timerJob = null
        _playbackState.update { it.copy(sleepTimerEndTime = null, sleepTimerTotalDuration = 0L) }
    }

    private fun MediaItem.toRadioStation(): RadioStation? {
        return localConfiguration?.tag as? RadioStation
    }
    
    private fun RadioStation.toMediaItem(): MediaItem = toMediaItem(context)

    private suspend fun getFilteredLibraryStations(prefs: AppPreferences): List<RadioStation> {
        val stations = when (prefs.librarySortOption) {
            LibrarySortOption.NAME_A_Z -> libraryRepository.getStationsByName().first()
            LibrarySortOption.NAME_Z_A -> libraryRepository.getStationsByNameDescending().first()
            LibrarySortOption.RECENTLY_PLAYED -> libraryRepository.getStationsByRecentlyPlayed().first()
            LibrarySortOption.LEAST_RECENTLY_PLAYED -> libraryRepository.getStationsByLeastRecentlyPlayed().first()
            LibrarySortOption.CUSTOM -> libraryRepository.getStationsByCustomOrder().first()
            LibrarySortOption.RECENTLY_ADDED -> libraryRepository.getAllStations().first()
            LibrarySortOption.OLDEST_ADDED -> libraryRepository.getStationsByOldestAdded().first()
        }

        return if (prefs.useFilterOnFavorites) {
            val hasCountryFilter = !prefs.selectedCountryCode.isNullOrBlank()
            val hasLanguageFilter = !prefs.selectedLanguage.isNullOrBlank()
            val hasTagFilter = prefs.selectedTags.isNotEmpty()

            if (!hasCountryFilter && !hasLanguageFilter && !hasTagFilter) {
                stations
            } else {
                stations.filter { s ->
                    val countryMatch = !hasCountryFilter || s.countryCode == prefs.selectedCountryCode
                    val languageMatch = !hasLanguageFilter || s.language == prefs.selectedLanguage
                    val tagsMatch = !hasTagFilter || prefs.selectedTags.any { it in s.tags }
                    countryMatch && languageMatch && tagsMatch
                }
            }
        } else {
            stations
        }
    }

    private suspend fun linkSingleItemToContext(station: RadioStation) {
        val player = controller ?: return
        val prefs = settingsRepository.appPreferencesFlow.first()
        
        val libraryStations = getFilteredLibraryStations(prefs)
        var index = libraryStations.indexOfFirst { s -> s.stationUuid == station.stationUuid }
        var sourceList = libraryStations
        var playbackSource: PlaybackSource = PlaybackSource.Library
        
        if (index == -1) {
            val recentStations = recentRepository.getAllRecent().first()
            index = recentStations.indexOfFirst { s -> s.stationUuid == station.stationUuid }
            sourceList = recentStations
            playbackSource = PlaybackSource.Recent
        }
        
        if (index != -1) {
            currentPlaylist = sourceList
            currentPlaybackSource = playbackSource
            activeStation = station
            
            _playbackState.update { state ->
                state.copy(
                    isPlaying = player.isPlaying,
                    currentStation = station,
                    currentPlaylist = currentPlaylist,
                    currentPlaylistIndex = index,
                    playbackSource = currentPlaybackSource
                )
            }
            
            if (player.mediaItemCount == 1) {
                val mediaItems = sourceList.map { it.toMediaItem() }
                
                if (index < mediaItems.size - 1) {
                    player.addMediaItems(1, mediaItems.subList(index + 1, mediaItems.size))
                }
                
                if (index > 0) {
                    player.addMediaItems(0, mediaItems.subList(0, index))
                }
            }
        } else {
            activeStation = station
            currentPlaylist = listOf(station)
            _playbackState.update { state ->
                state.copy(
                    isPlaying = player.isPlaying,
                    currentStation = station,
                    currentPlaylist = currentPlaylist,
                    currentPlaylistIndex = 0
                )
            }
            recentRepository.addRecentStation(station)
        }
    }

    override fun setLyricsSyncOffset(offsetMs: Long) {
        _playbackState.update { it.copy(lyricsSyncOffsetMs = offsetMs) }
    }

    override fun updateAmplitude(rms: Float) {
        _amplitude.value = rms
    }
}
