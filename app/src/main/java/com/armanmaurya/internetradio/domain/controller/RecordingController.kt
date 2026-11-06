package com.armanmaurya.internetradio.domain.controller

import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.model.RecordingSession
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface RecordingController {
    val activeSessions: StateFlow<Map<String, RecordingSession>>
    val recordingSavedEvent: SharedFlow<Unit>

    fun startRecording(station: RadioStation, fileNamePrefix: String = "")
    fun startRecordingStream(station: RadioStation, fileNamePrefix: String = ""): Boolean
    fun stopRecording(uuid: String)
    fun stopAllRecordings()
}
