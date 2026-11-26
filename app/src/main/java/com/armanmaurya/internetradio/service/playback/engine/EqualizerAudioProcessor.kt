package com.armanmaurya.internetradio.service.playback.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.armanmaurya.internetradio.core.media.dsp.EqualizerDspEngine
import com.armanmaurya.internetradio.domain.model.EqualizerConfig
import java.nio.ByteBuffer

/**
 * Media3 [BaseAudioProcessor] adapter that delegates PCM-16 equalization to [EqualizerDspEngine].
 * Each [androidx.media3.exoplayer.ExoPlayer] instance must own its own processor instance because
 * filter delay lines are per-stream.
 */
@UnstableApi
class EqualizerAudioProcessor(
    private val engine: EqualizerDspEngine = EqualizerDspEngine()
) : BaseAudioProcessor() {

    fun updateConfig(config: EqualizerConfig) {
        engine.updateConfig(config)
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        if (inputAudioFormat.sampleRate <= 0 || inputAudioFormat.channelCount <= 0) {
            return AudioFormat.NOT_SET
        }
        engine.configure(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining <= 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        if (engine.isBypassed()) {
            outputBuffer.put(inputBuffer)
        } else {
            engine.processPcm16Le(inputBuffer, outputBuffer, remaining)
        }
        outputBuffer.flip()
    }

    override fun onFlush(streamMetadata: StreamMetadata) {
        engine.resetState()
    }

    override fun onReset() {
        engine.resetState()
    }
}
