package com.armanmaurya.internetradio.data.recording

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.armanmaurya.internetradio.domain.controller.ActivePlaybackCoordinator
import com.armanmaurya.internetradio.domain.controller.ActivePlaybackMode
import com.armanmaurya.internetradio.domain.controller.PlayerController
import com.armanmaurya.internetradio.domain.controller.RecordingPlaybackController
import com.armanmaurya.internetradio.domain.controller.RecordingRouteTarget
import com.armanmaurya.internetradio.domain.model.RecordingFile
import com.armanmaurya.internetradio.service.RecordingPlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingPlaybackControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerController: PlayerController,
    private val activePlaybackCoordinator: ActivePlaybackCoordinator
) : RecordingPlaybackController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _currentRecording = MutableStateFlow<RecordingFile?>(null)
    override val currentRecording: StateFlow<RecordingFile?> = _currentRecording.asStateFlow()

    private val _playlist = MutableStateFlow<List<RecordingFile>>(emptyList())
    override val playlist: StateFlow<List<RecordingFile>> = _playlist.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    override val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    override val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _routeToRecordingTarget = MutableStateFlow<RecordingRouteTarget?>(null)
    override val routeToRecordingTarget: StateFlow<RecordingRouteTarget?> = _routeToRecordingTarget.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    override val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _isLooping = MutableStateFlow(false)
    override val isLooping: StateFlow<Boolean> = _isLooping.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    override val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _sleepTimerEndTimeMs = MutableStateFlow<Long?>(null)
    override val sleepTimerEndTimeMs: StateFlow<Long?> = _sleepTimerEndTimeMs.asStateFlow()

    private val _sleepTimerTotalDurationMs = MutableStateFlow(0L)
    override val sleepTimerTotalDurationMs: StateFlow<Long> = _sleepTimerTotalDurationMs.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    override val volume: StateFlow<Float> = _volume.asStateFlow()

    @Volatile
    private var service: RecordingPlaybackService? = null
    @Volatile
    private var player: ExoPlayer? = null
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var playerListener: Player.Listener? = null

    init {
        scope.launch {
            playerController.playbackSession.collect { session ->
                if (session.isPlaying) {
                    if (_isPlaying.value) {
                        pause()
                    }
                    activePlaybackCoordinator.setMode(ActivePlaybackMode.STATION)
                } else if (session.currentStation == null &&
                    _currentRecording.value != null &&
                    activePlaybackCoordinator.activeMode.value == ActivePlaybackMode.STATION
                ) {
                    activePlaybackCoordinator.setMode(ActivePlaybackMode.RECORDING)
                }
            }
        }
    }

    fun updateAmplitude(rms: Float) {
        _amplitude.value = rms
    }

    override fun getCurrentPositionRealtime(): Long {
        val p = player
        return if (p != null && _isPlaying.value) {
            p.currentPosition.coerceAtLeast(0L)
        } else {
            _currentPositionMs.value
        }
    }

    fun attachService(service: RecordingPlaybackService, exoPlayer: ExoPlayer) {
        this.service = service
        this.player = exoPlayer

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) {
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                    _amplitude.value = 0f
                    _currentPositionMs.value = exoPlayer.currentPosition
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    val dur = exoPlayer.duration
                    if (dur > 0) _durationMs.value = dur
                } else if (state == Player.STATE_ENDED) {
                    if (_isLooping.value) {
                        exoPlayer.seekTo(0)
                        exoPlayer.play()
                    } else {
                        val nextIdx = _currentIndex.value + 1
                        val list = _playlist.value
                        if (_currentIndex.value >= 0 && nextIdx in list.indices) {
                            play(list[nextIdx], list)
                        } else {
                            _isPlaying.value = false
                            _amplitude.value = 0f
                            stopProgressTracker()
                            exoPlayer.pause()
                            exoPlayer.seekTo(0)
                            _currentPositionMs.value = 0L
                        }
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                _currentPositionMs.value = newPosition.positionMs
            }
        }
        playerListener = listener
        exoPlayer.addListener(listener)
        _isPlaying.value = exoPlayer.isPlaying
        _currentPositionMs.value = exoPlayer.currentPosition
        if (exoPlayer.duration > 0) {
            _durationMs.value = exoPlayer.duration
        }
        exoPlayer.volume = _volume.value
        exoPlayer.setPlaybackSpeed(_playbackSpeed.value)
        exoPlayer.repeatMode = if (_isLooping.value) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        if (exoPlayer.isPlaying) {
            startProgressTracker()
        }
    }

    fun detachService(service: RecordingPlaybackService) {
        if (this.service == service) {
            playerListener?.let { player?.removeListener(it) }
            playerListener = null
            this.service = null
            this.player = null
            stopProgressTracker()
            _isPlaying.value = false
            _amplitude.value = 0f
        }
    }

    override fun play(recording: RecordingFile, playlist: List<RecordingFile>) {
        // Pause live radio stream so it doesn't play concurrently
        playerController.pause()
        activePlaybackCoordinator.setMode(ActivePlaybackMode.RECORDING)

        val resolvedPlaylist = when {
            playlist.isNotEmpty() -> playlist
            _playlist.value.any { it.uri == recording.uri } -> _playlist.value
            else -> listOf(recording)
        }
        val resolvedIndex = resolvedPlaylist.indexOfFirst { it.uri == recording.uri }.coerceAtLeast(0)
        _playlist.value = resolvedPlaylist
        _currentIndex.value = resolvedIndex

        val isSameRecording = _currentRecording.value?.uri == recording.uri
        _currentRecording.value = recording
        if (recording.durationMs > 0L) {
            _durationMs.value = recording.durationMs
        }

        val p = player
        if (isSameRecording && p != null) {
            p.volume = _volume.value
            p.setPlaybackSpeed(_playbackSpeed.value)
            p.play()
            return
        }

        val intent = Intent(context, RecordingPlaybackService::class.java).apply {
            action = RecordingPlaybackService.ACTION_PLAY
            putExtra(RecordingPlaybackService.EXTRA_RECORDING_URI, recording.uri.toString())
            putExtra(RecordingPlaybackService.EXTRA_FILE_NAME, recording.fileName)
            putExtra(RecordingPlaybackService.EXTRA_STATION_NAME, recording.stationName)
            putExtra(RecordingPlaybackService.EXTRA_DURATION_MS, recording.durationMs)
        }
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            context.startService(intent)
        }
    }

    override fun playIndex(index: Int) {
        val list = _playlist.value
        val target = list.getOrNull(index) ?: return
        play(target, list)
    }

    override fun next() {
        val list = _playlist.value
        val nextIndex = _currentIndex.value + 1
        if (_currentIndex.value >= 0 && nextIndex in list.indices) {
            play(list[nextIndex], list)
        }
    }

    override fun previous() {
        val list = _playlist.value
        val prevIndex = _currentIndex.value - 1
        if (prevIndex in list.indices) {
            play(list[prevIndex], list)
        }
    }

    override fun pause() {
        val p = player
        if (p != null) {
            p.pause()
        } else {
            val intent = Intent(context, RecordingPlaybackService::class.java).apply {
                action = RecordingPlaybackService.ACTION_PAUSE
            }
            context.startService(intent)
        }
        _isPlaying.value = false
        _amplitude.value = 0f
    }

    override fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            _currentRecording.value?.let { play(it) }
        }
    }

    override fun seekTo(positionMs: Long) {
        _currentPositionMs.value = positionMs
        val p = player
        if (p != null) {
            p.seekTo(positionMs)
        } else {
            val intent = Intent(context, RecordingPlaybackService::class.java).apply {
                action = RecordingPlaybackService.ACTION_SEEK
                putExtra(RecordingPlaybackService.EXTRA_SEEK_POSITION, positionMs)
            }
            context.startService(intent)
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        val p = player
        if (p != null) {
            p.setPlaybackSpeed(speed)
        } else {
            val intent = Intent(context, RecordingPlaybackService::class.java).apply {
                action = RecordingPlaybackService.ACTION_SET_SPEED
                putExtra(RecordingPlaybackService.EXTRA_SPEED, speed)
            }
            context.startService(intent)
        }
    }

    override fun setLooping(enabled: Boolean) {
        _isLooping.value = enabled
        val p = player
        val mode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        if (p != null) {
            p.repeatMode = mode
        } else {
            val intent = Intent(context, RecordingPlaybackService::class.java).apply {
                action = RecordingPlaybackService.ACTION_SET_REPEAT_MODE
                putExtra(RecordingPlaybackService.EXTRA_REPEAT_MODE, enabled)
            }
            context.startService(intent)
        }
    }

    override fun toggleLoop() {
        setLooping(!_isLooping.value)
    }

    override fun setSleepTimer(durationMillis: Long) {
        cancelSleepTimer()
        val endTime = System.currentTimeMillis() + durationMillis
        _sleepTimerEndTimeMs.value = endTime
        _sleepTimerTotalDurationMs.value = durationMillis

        sleepTimerJob = scope.launch {
            while (isActive) {
                val remaining = endTime - System.currentTimeMillis()
                if (remaining <= 0L) {
                    pause()
                    cancelSleepTimer()
                    break
                }
                delay(remaining.coerceAtMost(1000L))
            }
        }
    }

    override fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerEndTimeMs.value = null
        _sleepTimerTotalDurationMs.value = 0L
    }

    override fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _volume.value = clamped
        val p = player
        if (p != null) {
            p.volume = clamped
        } else {
            val intent = Intent(context, RecordingPlaybackService::class.java).apply {
                action = RecordingPlaybackService.ACTION_SET_VOLUME
                putExtra(RecordingPlaybackService.EXTRA_VOLUME, clamped)
            }
            context.startService(intent)
        }
    }

    override fun stop() {
        stopProgressTracker()
        cancelSleepTimer()
        _isPlaying.value = false
        _amplitude.value = 0f
        _currentRecording.value = null
        _playlist.value = emptyList()
        _currentIndex.value = -1
        _currentPositionMs.value = 0L
        _durationMs.value = 0L

        val p = player
        p?.stop()
        p?.clearMediaItems()

        val intent = Intent(context, RecordingPlaybackService::class.java).apply {
            action = RecordingPlaybackService.ACTION_STOP
        }
        context.startService(intent)

        if (playerController.playbackSession.value.currentStation != null) {
            activePlaybackCoordinator.setMode(ActivePlaybackMode.STATION)
        }
    }

    override fun syncCurrentRecording(recordings: List<RecordingFile>) {
        if (_playlist.value.isNotEmpty()) {
            val byPath = recordings.associateBy { it.file.absolutePath }
            val updatedPlaylist = _playlist.value.map { item -> byPath[item.file.absolutePath] ?: item }
            if (updatedPlaylist != _playlist.value) {
                _playlist.value = updatedPlaylist
            }
        }
        val current = _currentRecording.value ?: return
        // The file path is the same whether the recording was listed by file or through SAF
        val latest = recordings.firstOrNull { it.file.absolutePath == current.file.absolutePath } ?: return
        if (latest != current) {
            _currentRecording.value = latest
        }
    }

    override fun requestRouteToPlayingRecording() {
        val rec = _currentRecording.value ?: return
        routeToRecording(rec.stationName, rec.uri.toString())
    }

    override fun routeToRecording(stationName: String, recordingUri: String) {
        _routeToRecordingTarget.value = RecordingRouteTarget(
            stationName = stationName,
            recordingUri = recordingUri
        )
    }

    override fun clearRouteTarget() {
        _routeToRecordingTarget.value = null
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _isPlaying.value) {
                player?.let { p ->
                    _currentPositionMs.value = p.currentPosition
                    val dur = p.duration
                    if (dur > 0 && (_durationMs.value <= 0 || _durationMs.value == 1L)) {
                        _durationMs.value = dur
                    }
                }
                delay(200)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }
}
