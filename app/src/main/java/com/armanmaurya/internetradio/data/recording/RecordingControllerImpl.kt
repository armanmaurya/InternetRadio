package com.armanmaurya.internetradio.data.recording

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.armanmaurya.internetradio.core.media.recorder.RecordingConfig
import com.armanmaurya.internetradio.core.media.recorder.StreamRecorder
import com.armanmaurya.internetradio.core.utils.TrackSanitizer
import com.armanmaurya.internetradio.domain.controller.RecordingController
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.model.RecordingSession
import com.armanmaurya.internetradio.domain.model.RecordingTrack
import com.armanmaurya.internetradio.domain.repository.CoverArtRepository
import com.armanmaurya.internetradio.domain.repository.RecordingRepository
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import com.armanmaurya.internetradio.service.RecordingService
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Collections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class RecordingControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordingRepository: RecordingRepository,
    private val streamRecorder: StreamRecorder,
    private val coverArtRepository: CoverArtRepository,
    private val sidecarStore: RecordingSidecarStore,
    private val artworkStore: RecordingArtworkStore,
    private val settingsRepository: SettingsRepository
) : RecordingController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeRecordings = mutableMapOf<String, RecordingSession>()
    private val activeJobs = mutableMapOf<String, Job>()

    private val _activeSessions = MutableStateFlow<Map<String, RecordingSession>>(emptyMap())
    override val activeSessions: StateFlow<Map<String, RecordingSession>> = _activeSessions.asStateFlow()

    private val _recordingSavedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val recordingSavedEvent: SharedFlow<Unit> = _recordingSavedEvent.asSharedFlow()

    override fun startRecording(station: RadioStation) {
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
            putExtra("STATION_JSON", Gson().toJson(station))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    }

    override fun startRecordingStream(station: RadioStation): Boolean {
        if (activeRecordings.containsKey(station.stationUuid)) return false

        val durationFlow = MutableStateFlow(0L)
        val isReconnectingFlow = MutableStateFlow(false)
        val recordingSession = RecordingSession(
            station = station,
            durationSeconds = durationFlow.asStateFlow(),
            isReconnecting = isReconnectingFlow.asStateFlow()
        )
        activeRecordings[station.stationUuid] = recordingSession
        _activeSessions.update { activeRecordings.toMap() }

        val recordedTracks = Collections.synchronizedList(mutableListOf<RecordingTrack>())
        var openedStationFolder: String = station.name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        var openedFileName: String? = null
        var lastRawTrack: String? = null
        var artworkRequested = false

        val job = scope.launch {
            var currentMaxRetryDuration = 300_000L
            val prefJob = launch {
                settingsRepository.appPreferencesFlow.collect { prefs ->
                    currentMaxRetryDuration = prefs.maxRetryDuration
                }
            }
            try {
                val stationBitrate = station.bitrate.takeIf { it > 0 }
                streamRecorder.record(
                    config = RecordingConfig(
                        url = station.urlResolved,
                        title = station.name,
                        bitrateKbps = stationBitrate,
                        maxRetryDurationMs = currentMaxRetryDuration,
                        maxRetryDurationProvider = { currentMaxRetryDuration }
                    ),
                    onBytesWritten = { bytes ->
                        recordingSession.bytesWritten += bytes
                    },
                    onAudioProgress = { audioDurationMs ->
                        durationFlow.value = audioDurationMs / 1000L
                    },
                    onFileOpened = { stationFolder, fileName ->
                        openedStationFolder = stationFolder
                        openedFileName = fileName
                        val snapshot = synchronized(recordedTracks) { recordedTracks.toList() }
                        if (snapshot.isNotEmpty()) {
                            sidecarStore.saveTracks(stationFolder, fileName, snapshot)
                        }
                        if (!artworkRequested) {
                            artworkRequested = true
                            scope.launch {
                                if (artworkStore.saveStationArtwork(stationFolder, station)) {
                                    recordingRepository.notifyRecordingsChanged()
                                }
                            }
                        }
                    },
                    onTrackMetadata = { rawTrack, audioOffsetMs ->
                        val trimmedRaw = rawTrack.trim()
                        if (trimmedRaw.isNotEmpty() && trimmedRaw != lastRawTrack) {
                            lastRawTrack = trimmedRaw
                            val sanitized = TrackSanitizer.sanitize(trimmedRaw)
                            val isDuplicate = synchronized(recordedTracks) {
                                recordedTracks.lastOrNull()?.title.equals(sanitized, ignoreCase = true)
                            }
                            if (sanitized.isNotBlank() && !isDuplicate) {
                                val now = System.currentTimeMillis()
                                val initialTrack = RecordingTrack(
                                    title = sanitized,
                                    artist = null,
                                    rawTitle = trimmedRaw,
                                    coverArtUrl = null,
                                    offsetMs = audioOffsetMs,
                                    timestampMs = now
                                )
                                val snapshot = synchronized(recordedTracks) {
                                    recordedTracks.add(initialTrack)
                                    recordedTracks.toList()
                                }
                                openedFileName?.let { fn ->
                                    sidecarStore.saveTracks(openedStationFolder, fn, snapshot)
                                }

                                scope.launch {
                                    val metadata = withTimeoutOrNull(4000L.milliseconds) {
                                        runCatching { coverArtRepository.getTrackMetadata(sanitized) }.getOrNull()
                                    }
                                    if (metadata != null) {
                                        val cleanTitle = metadata.trackName?.takeIf { it.isNotBlank() } ?: sanitized
                                        val cleanArtist = metadata.artistName?.takeIf { it.isNotBlank() }
                                        val coverUrl = metadata.coverArtUrl?.takeIf { it.isNotBlank() }
                                        val updatedSnapshot = synchronized(recordedTracks) {
                                            val idx = recordedTracks.indexOfFirst {
                                                it.timestampMs == now && it.rawTitle == trimmedRaw
                                            }
                                            if (idx >= 0) {
                                                recordedTracks[idx] = recordedTracks[idx].copy(
                                                    title = cleanTitle,
                                                    artist = cleanArtist,
                                                    coverArtUrl = coverUrl
                                                )
                                            }
                                            recordedTracks.toList()
                                        }
                                        openedFileName?.let { fn ->
                                            sidecarStore.saveTracks(openedStationFolder, fn, updatedSnapshot)
                                            recordingRepository.notifyRecordingsChanged()
                                        }
                                    }
                                }
                            }
                        }
                    },
                    onReconnectingStateChanged = { reconnecting ->
                        isReconnectingFlow.value = reconnecting
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("RecordingController", "Recording interrupted for ${station.name}: ${e.message}", e)
            } finally {
                prefJob.cancel()
                if (recordingSession.bytesWritten > 0) {
                    val finalTracks = synchronized(recordedTracks) { recordedTracks.toList() }
                    val fn = openedFileName
                    if (fn != null && finalTracks.isNotEmpty()) {
                        sidecarStore.saveTracks(openedStationFolder, fn, finalTracks)
                    }
                }
                onRecordingStopped(station.stationUuid, recordingSession.bytesWritten)
            }
        }
        activeJobs[station.stationUuid] = job
        return true
    }

    override fun stopRecording(uuid: String) {
        activeJobs.remove(uuid)?.cancel()
    }

    override fun stopAllRecordings() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
    }

    private fun onRecordingStopped(uuid: String, bytesWritten: Long) {
        activeRecordings.remove(uuid)
        activeJobs.remove(uuid)
        _activeSessions.update { activeRecordings.toMap() }
        if (bytesWritten > 0) {
            _recordingSavedEvent.tryEmit(Unit)
        }
        recordingRepository.notifyRecordingsChanged()
    }
}
