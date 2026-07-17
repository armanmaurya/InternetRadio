package com.armanmaurya.internetradio.player

import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.sqrt

import androidx.media3.common.audio.AudioProcessor.AudioFormat

class AmplitudeAudioProcessor(
    private val recordingManager: RecordingManager
) : BaseAudioProcessor() {

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        recordingManager.setAudioFormat(inputAudioFormat.sampleRate, inputAudioFormat.channelCount, inputAudioFormat.bytesPerFrame)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val limit = inputBuffer.limit()
        val position = inputBuffer.position()
        val remaining = limit - position

        if (remaining > 0) {
            // Calculate RMS amplitude assuming 16-bit PCM
            var sumSquares = 0.0
            val count = remaining / 2
            
            for (i in position until limit step 2) {
                val byte1 = inputBuffer.get(i).toInt() and 0xFF
                val byte2 = inputBuffer.get(i + 1).toInt() shl 8
                val sample = (byte1 or byte2).toShort()
                val normalized = sample / 32768.0 // -1.0 to 1.0
                sumSquares += normalized * normalized
            }
            
            val rms = if (count > 0) sqrt(sumSquares / count).toFloat() else 0f
            recordingManager.updateAmplitude(rms)
            
            if (recordingManager.isRecording.value) {
                // Extract PCM bytes and write to encoder
                val pcmData = ByteArray(remaining)
                val originalPos = inputBuffer.position()
                inputBuffer.get(pcmData)
                inputBuffer.position(originalPos)
                recordingManager.writeBytes(pcmData, 0, remaining)
            }
        }
        
        // Pass the buffer through unchanged
        val size = inputBuffer.remaining()
        if (size > 0) {
            val outputBuffer = replaceOutputBuffer(size)
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
        }
    }
}
