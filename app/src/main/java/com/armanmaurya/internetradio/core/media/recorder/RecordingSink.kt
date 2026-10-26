package com.armanmaurya.internetradio.core.media.recorder

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.armanmaurya.internetradio.core.system.FileSystemFacade
import com.armanmaurya.internetradio.core.utils.FormatUtils
import java.io.Closeable
import java.io.FileOutputStream
import java.io.OutputStream

open class RecordingSink(
    private val fileSystemFacade: FileSystemFacade? = null,
    private val title: String,
    private val context: Context? = null
) : Closeable {

    val safeStationName: String = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    var outputStream: OutputStream? = null
        private set
    private var pfd: ParcelFileDescriptor? = null
    private var uri: Uri? = null
    var finalFileName: String? = null
        private set

    open suspend fun open(ext: String): OutputStream? {
        val ctx = context ?: return null
        val fs = fileSystemFacade ?: return null
        val safeTitle = safeStationName
        val timestamp = FormatUtils.getRecordingTimestamp(ctx)
        val fileName = "$safeTitle $timestamp.$ext"
        val partFileName = "$fileName.part"
        val folderName = "InternetRadio/$safeTitle"
        val mimeType = when (ext) {
            "mp3" -> "audio/mpeg"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            else  -> "audio/mpeg"
        }

        val result = fs.createAudioRecordingFile(folderName, partFileName, mimeType) ?: return null
        uri = result.first
        pfd = result.second
        finalFileName = fileName
        outputStream = FileOutputStream(result.second.fileDescriptor)
        return outputStream
    }

    suspend fun finalizeFile() {
        try { outputStream?.flush() } catch (_: Exception) {}
        close()
        val targetUri = uri
        val targetName = finalFileName
        if (targetUri != null && targetName != null) {
            try {
                fileSystemFacade?.finalizeAudioRecordingFile(targetUri, targetName)
            } catch (_: Exception) {}
        }
    }

    override fun close() {
        try { outputStream?.close() } catch (_: Exception) {}
        try { pfd?.close() } catch (_: Exception) {}
    }
}
