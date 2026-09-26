package com.armanmaurya.internetradio.domain.model

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow
import java.io.File

data class RecordingFolder(
    val stationName: String,
    val recordings: List<RecordingFile>
)

data class RecordingFile(
    val fileName: String,
    val file: File,
    val uri: Uri,
    val lastModified: Long,
    val sizeBytes: Long,
    val durationMs: Long = 0L
)

data class RecordingSession(
    val station: RadioStation,
    val startTimeMs: Long = System.currentTimeMillis(),
    val durationSeconds: StateFlow<Long>,
    @Volatile var bytesWritten: Long = 0L
)