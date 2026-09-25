package com.armanmaurya.internetradio.data.recording

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.armanmaurya.internetradio.core.media.recorder.RecordingConfig
import com.armanmaurya.internetradio.core.media.recorder.StreamRecorder
import com.armanmaurya.internetradio.domain.controller.RecordingController
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.model.RecordingSession
import com.armanmaurya.internetradio.domain.repository.RecordingRepository
import com.armanmaurya.internetradio.service.RecordingService
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordingRepository: RecordingRepository,
    private val streamRecorder: StreamRecorder
) : RecordingController {

    private val scope = CoroutineScope(Dispatchers.IO)
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
        val recordingSession = RecordingSession(
            station = station,
            durationSeconds = durationFlow.asStateFlow()
        )
        activeRecordings[station.stationUuid] = recordingSession
        _activeSessions.update { activeRecordings.toMap() }

        val job = scope.launch {
            launch {
                while (isActive) {
                    delay(1000)
                    durationFlow.update { it + 1 }
                }
            }
            try {
                streamRecorder.record(
                    RecordingConfig(
                        url = station.urlResolved,
                        title = station.name
                    )
                ) { bytes ->
                    recordingSession.bytesWritten += bytes
                }
            } finally {
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
