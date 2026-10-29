package com.armanmaurya.internetradio.domain.repository

import com.armanmaurya.internetradio.domain.model.RecordingFile
import com.armanmaurya.internetradio.domain.model.RecordingFolder
import kotlinx.coroutines.flow.SharedFlow

interface RecordingRepository {
    val recordingsChangedEvent: SharedFlow<Unit>
    suspend fun getRecordingFolders(): List<RecordingFolder>
    suspend fun getRecordingsForStation(stationName: String): List<RecordingFile>
    fun notifyRecordingsChanged()
    suspend fun deleteRecording(recording: RecordingFile): Boolean
    suspend fun deleteRecordings(recordings: List<RecordingFile>): Boolean
    suspend fun deleteRecordingFolders(stationNames: List<String>): Boolean
    suspend fun updateRecordingNote(recording: RecordingFile, note: String?)
}
