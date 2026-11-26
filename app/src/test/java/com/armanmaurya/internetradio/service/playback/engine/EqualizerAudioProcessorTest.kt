package com.armanmaurya.internetradio.service.playback.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import com.armanmaurya.internetradio.domain.model.EqualizerConfig
import com.armanmaurya.internetradio.domain.model.EqualizerPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class EqualizerAudioProcessorTest {

    @Test
    fun `configures pcm16 and transforms buffer when enabled`() {
        val processor = EqualizerAudioProcessor()
        val format = AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)
        val configured = processor.configure(format)
        assertEquals(format, configured)
        assertTrue(processor.isActive)

        processor.updateConfig(
            EqualizerConfig(
                enabled = true,
                bandGainsDb = MutableList(EqualizerPresets.BAND_COUNT) { 0f }.apply { this[5] = 6f }
            )
        )

        val bytes = ByteArray(1024)
        for (i in 0 until 512) {
            val s = (8000 * kotlin.math.sin(2.0 * Math.PI * 1000.0 * (i / 2) / 48_000.0)).toInt().toShort()
            bytes[i * 2] = (s.toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = ((s.toInt() ushr 8) and 0xFF).toByte()
        }

        val input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        processor.queueInput(input)
        val out = processor.output

        assertEquals(1024, out.remaining())
        val outBytes = ByteArray(out.remaining()).also { out.get(it) }
        assertFalse(bytes.contentEquals(outBytes))
    }

    @Test(expected = UnhandledAudioFormatException::class)
    fun `rejects non pcm16 encodings so Media3 converts upstream`() {
        val processor = EqualizerAudioProcessor()
        processor.configure(AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT))
    }

    @Test
    fun `flush and reset succeed cleanly`() {
        val processor = EqualizerAudioProcessor()
        processor.configure(AudioFormat(44_100, 1, C.ENCODING_PCM_16BIT))
        processor.flush(StreamMetadata.DEFAULT)
        processor.reset()
        assertFalse(processor.isActive)
    }
}
