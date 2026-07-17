package com.armanmaurya.internetradio.player

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.armanmaurya.internetradio.data.model.RadioStation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    fun updateAmplitude(rms: Float) {
        _amplitude.value = rms
    }

    private var outputStream: OutputStream? = null
    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    var currentStation: RadioStation? = null
        private set

    private var isPlaying = false
    private var currentUri: android.net.Uri? = null
    private var currentFile: File? = null
    private var bytesWritten = 0L
    private var pfd: android.os.ParcelFileDescriptor? = null
    private var encoder: PcmToAacEncoder? = null
    
    private val audioChannel = Channel<ByteArray>(capacity = 100, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var encodingJob: Job? = null
    
    private var sampleRate = 44100
    private var channelCount = 2
    private var bytesPerFrame = 4

    fun setAudioFormat(sampleRate: Int, channelCount: Int, bytesPerFrame: Int) {
        this.sampleRate = sampleRate
        this.channelCount = channelCount
        this.bytesPerFrame = bytesPerFrame
    }

    fun setPlaying(playing: Boolean) {
        isPlaying = playing
    }

    fun startRecording(station: RadioStation) {
        if (_isRecording.value) return
        
        currentStation = station
        val timestamp = SimpleDateFormat("d MMMM yyyy hh-mm a", Locale.getDefault()).format(Date())
        val extension = "m4a"
        val safeStationName = station.name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val fileName = "$safeStationName $timestamp.$extension"
        val folderName = "InternetRadio/$safeStationName"

        try {
            bytesWritten = 0L
            currentUri = null
            currentFile = null
            pfd = null
            encoder = null
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$folderName")
                }
                val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
                currentUri = uri
                
                uri?.let {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        pfd = context.contentResolver.openFileDescriptor(it, "rw")
                        encoder = PcmToAacEncoder(sampleRate, channelCount, bytesPerFrame, pfd?.fileDescriptor, null)
                    } else {
                        // Fallback for Q without O? That doesn't exist, Q is 29, O is 26.
                    }
                }
            } else {
                val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                val folder = File(musicDir, folderName)
                if (!folder.exists()) {
                    folder.mkdirs()
                }
                val file = File(folder, fileName)
                currentFile = file
                encoder = PcmToAacEncoder(sampleRate, channelCount, bytesPerFrame, null, file.absolutePath)
            }

            if (encoder != null) {
                _isRecording.value = true
                _recordingDuration.value = 0L
                startTimer()
                startEncodingJob()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            encoder?.stop()
            encoder = null
            pfd?.close()
            pfd = null
        }
    }

    fun stopRecording() {
        if (!_isRecording.value) return
        
        _isRecording.value = false
        timerJob?.cancel()
        timerJob = null
        
        encodingJob?.cancel()
        encodingJob = null
        
        // Clear any remaining audio chunks
        while (audioChannel.tryReceive().isSuccess) { }
        
        try {
            encoder?.stop()
            
            if (bytesWritten == 0L) {
                currentUri?.let { context.contentResolver.delete(it, null, null) }
                currentFile?.let { if (it.exists()) it.delete() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            encoder = null
            try { pfd?.close() } catch (e: Exception) {}
            pfd = null
            currentUri = null
            currentFile = null
            currentStation = null
            bytesWritten = 0L
        }
    }

    fun writeBytes(buffer: ByteArray, offset: Int, length: Int) {
        if (_isRecording.value && isPlaying) {
            val chunk = if (offset == 0 && length == buffer.size) {
                buffer
            } else {
                buffer.copyOfRange(offset, offset + length)
            }
            audioChannel.trySend(chunk)
        }
    }

    private fun startEncodingJob() {
        encodingJob?.cancel()
        encodingJob = scope.launch {
            for (buffer in audioChannel) {
                if (_isRecording.value && isPlaying) {
                    try {
                        encoder?.encode(buffer, 0, buffer.size)
                        bytesWritten += buffer.size
                    } catch (e: Exception) {
                        e.printStackTrace()
                        stopRecording() // Stop if write fails
                        break
                    }
                }
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (_isRecording.value) {
                delay(1000)
                if (isPlaying) {
                    _recordingDuration.update { it + 1 }
                }
            }
        }
    }
}




