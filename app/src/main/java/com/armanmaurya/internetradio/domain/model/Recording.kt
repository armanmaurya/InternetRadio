package com.armanmaurya.internetradio.domain.model

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow
import java.io.File

data class RecordingFolder(
    val stationName: String,
    val recordings: List<RecordingFile>
)

data class RecordingTrack(
    val title: String,
    val artist: String? = null,
    val rawTitle: String = title,
    val coverArtUrl: String? = null,
    val offsetMs: Long,
    val timestampMs: Long
)

data class RecordingFile(
    val fileName: String,
    val file: File,
    val uri: Uri,
    val lastModified: Long,
    val sizeBytes: Long,
    val durationMs: Long = 0L,
    val stationName: String = file.parentFile?.name ?: "",
    val tracks: List<RecordingTrack> = emptyList(),
    val note: String? = null
)

data class RecordingSession(
    val station: RadioStation,
    val startTimeMs: Long = System.currentTimeMillis(),
    val durationSeconds: StateFlow<Long>,
    val isReconnecting: StateFlow<Boolean> = kotlinx.coroutines.flow.MutableStateFlow(false),
    @Volatile var bytesWritten: Long = 0L
)