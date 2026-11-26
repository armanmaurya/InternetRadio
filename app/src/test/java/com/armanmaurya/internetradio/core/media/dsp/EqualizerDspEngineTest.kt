package com.armanmaurya.internetradio.core.media.dsp

import com.armanmaurya.internetradio.domain.model.EqualizerConfig
import com.armanmaurya.internetradio.domain.model.EqualizerPresets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class EqualizerDspEngineTest {

    @Test
    fun `disabled or flat config leaves pcm16 bytes identical`() {
        val engine = EqualizerDspEngine()
        engine.configure(sampleRate = 48_000, channelCount = 2)
        engine.updateConfig(EqualizerConfig(enabled = false, bandGainsDb = List(10) { 6f }))

        assertTrue(engine.isBypassed())

        val inputBytes = createSinePcm16(freqHz = 1000.0, sampleRate = 48_000, frames = 512, channels = 2)
        val input = ByteBuffer.wrap(inputBytes.copyOf())
        val output = ByteBuffer.allocate(inputBytes.size)
        engine.processPcm16Le(input, output, inputBytes.size)

        assertArrayEquals(inputBytes, output.array())

        engine.updateConfig(EqualizerConfig(enabled = true, presetId = EqualizerPresets.FLAT_ID))
        assertTrue(engine.isBypassed())
    }

    @Test
    fun `boosting 1kHz band amplifies 1kHz sine and leaves 62Hz near unity`() {
        val sampleRate = 48_000
        val engine = EqualizerDspEngine()
        engine.configure(sampleRate = sampleRate, channelCount = 1)
        val gains = MutableList(EqualizerPresets.BAND_COUNT) { 0f }.apply {
            this[5] = 6f // 1 kHz +6 dB (~2x amplitude)
        }
        engine.updateConfig(EqualizerConfig(enabled = true, bandGainsDb = gains))
        assertFalse(engine.isBypassed())

        val tone1k = createSineFloat(freqHz = 1000.0, sampleRate = sampleRate, frames = 4096, amplitude = 0.2f)
        val inRms1k = rms(tone1k, start = 1024)
        engine.processFloatInterleaved(tone1k)
        val outRms1k = rms(tone1k, start = 1024)
        val gainRatio1k = outRms1k / inRms1k
        assertTrue("Expected ~2.0x boost at 1kHz, got $gainRatio1k", gainRatio1k in 1.80f..2.20f)

        engine.resetState()

        val tone62 = createSineFloat(freqHz = 62.0, sampleRate = sampleRate, frames = 4096, amplitude = 0.2f)
        val inRms62 = rms(tone62, start = 1024)
        engine.processFloatInterleaved(tone62)
        val outRms62 = rms(tone62, start = 1024)
        val gainRatio62 = outRms62 / inRms62
        assertTrue("Expected near-unity at 62Hz, got $gainRatio62", gainRatio62 in 0.90f..1.10f)
    }

    @Test
    fun `cutting 1kHz band attenuates 1kHz sine`() {
        val sampleRate = 48_000
        val engine = EqualizerDspEngine()
        engine.configure(sampleRate = sampleRate, channelCount = 1)
        val gains = MutableList(EqualizerPresets.BAND_COUNT) { 0f }.apply {
            this[5] = -6f // 1 kHz -6 dB (~0.5x amplitude)
        }
        engine.updateConfig(EqualizerConfig(enabled = true, bandGainsDb = gains))

        val tone1k = createSineFloat(freqHz = 1000.0, sampleRate = sampleRate, frames = 4096, amplitude = 0.3f)
        val inRms = rms(tone1k, start = 1024)
        engine.processFloatInterleaved(tone1k)
        val outRms = rms(tone1k, start = 1024)
        val ratio = outRms / inRms

        assertTrue("Expected ~0.5x cut at 1kHz, got $ratio", ratio in 0.42f..0.58f)
    }

    @Test
    fun `low shelf 31Hz and high shelf 16kHz shape spectrum without instability`() {
        val sampleRate = 48_000
        val engine = EqualizerDspEngine()
        engine.configure(sampleRate = sampleRate, channelCount = 2)
        val gains = MutableList(EqualizerPresets.BAND_COUNT) { 0f }.apply {
            this[0] = 6f // 31 Hz low shelf
            this[9] = -6f // 16 kHz high shelf
        }
        engine.updateConfig(EqualizerConfig(enabled = true, bandGainsDb = gains))

        val lowTone = createSineFloat(freqHz = 30.0, sampleRate = sampleRate, frames = 4096, channels = 2, amplitude = 0.2f)
        val lowIn = rms(lowTone, start = 2048)
        engine.processFloatInterleaved(lowTone)
        val lowOut = rms(lowTone, start = 2048)
        assertTrue("Low shelf should boost 30 Hz, ratio=${lowOut / lowIn}", lowOut / lowIn > 1.35f)

        engine.resetState()

        val highTone = createSineFloat(freqHz = 16_000.0, sampleRate = sampleRate, frames = 4096, channels = 2, amplitude = 0.2f)
        val highIn = rms(highTone, start = 2048)
        engine.processFloatInterleaved(highTone)
        val highOut = rms(highTone, start = 2048)
        assertTrue("High shelf should cut 16 kHz, ratio=${highOut / highIn}", highOut / highIn < 0.72f)
    }

    @Test
    fun `sample rate switching with nyquist guard stays finite and stable`() {
        val engine = EqualizerDspEngine()
        engine.updateConfig(
            EqualizerConfig(
                enabled = true,
                bandGainsDb = List(EqualizerPresets.BAND_COUNT) { 12f },
                preampDb = 6f
            )
        )

        // Low-bitrate talk radio stream at 22050 Hz (16kHz band exceeds Nyquist guard)
        engine.configure(sampleRate = 22_050, channelCount = 1)
        val lowRateSamples = createSineFloat(freqHz = 1000.0, sampleRate = 22_050, frames = 2048, amplitude = 0.5f)
        engine.processFloatInterleaved(lowRateSamples)
        assertTrue(lowRateSamples.all { it.isFinite() && abs(it) <= 1.0f })

        // Switch back to 44100 Hz stereo
        engine.configure(sampleRate = 44_100, channelCount = 2)
        val stereoSamples = createSineFloat(freqHz = 2000.0, sampleRate = 44_100, frames = 2048, channels = 2, amplitude = 0.5f)
        engine.processFloatInterleaved(stereoSamples)
        assertTrue(stereoSamples.all { it.isFinite() && abs(it) <= 1.0f })
    }

    @Test
    fun `pcm16 soft limiter prevents wrap overflow under heavy boost`() {
        val engine = EqualizerDspEngine()
        engine.configure(sampleRate = 48_000, channelCount = 2)
        engine.updateConfig(
            EqualizerConfig(
                enabled = true,
                bandGainsDb = List(EqualizerPresets.BAND_COUNT) { 12f },
                preampDb = 12f
            )
        )

        // Full-scale positive peak (0x7FFF) repeated
        val sampleCount = 512
        val input = ByteBuffer.allocate(sampleCount * 2)
        repeat(sampleCount) {
            input.put(0xFF.toByte())
            input.put(0x7F.toByte())
        }
        input.flip()

        val output = ByteBuffer.allocate(sampleCount * 2)
        engine.processPcm16Le(input, output, sampleCount * 2)
        output.flip()

        while (output.remaining() >= 2) {
            val lo = output.get().toInt() and 0xFF
            val hi = output.get().toInt() shl 8
            val sample = (lo or hi).toShort().toInt()
            // Must remain strongly positive rather than wrapping negative
            assertTrue("Expected positive limited sample, got $sample", sample > 20_000)
        }
    }

    @Test
    fun `frequency response curve reflects disabled vs boosted bands`() {
        val disabled = EqualizerConfig(
            enabled = false,
            bandGainsDb = List(EqualizerPresets.BAND_COUNT) { 8f }
        )
        val flatCurve = EqualizerDspEngine.computeFrequencyResponseDb(disabled, pointCount = 32)
        assertEquals(32, flatCurve.size)
        assertTrue(flatCurve.all { it == 0f })

        val boosted = EqualizerConfig(
            enabled = true,
            bandGainsDb = MutableList(EqualizerPresets.BAND_COUNT) { 0f }.apply { this[5] = 6f },
            preampDb = 2f
        )
        val boostedCurve = EqualizerDspEngine.computeFrequencyResponseDb(boosted, pointCount = 32)
        assertTrue(boostedCurve.max() > 7.5f)
    }

    private fun createSineFloat(
        freqHz: Double,
        sampleRate: Int,
        frames: Int,
        channels: Int = 1,
        amplitude: Float = 0.5f
    ): FloatArray {
        val out = FloatArray(frames * channels)
        for (i in 0 until frames) {
            val sample = (amplitude * sin(2.0 * PI * freqHz * i / sampleRate)).toFloat()
            for (ch in 0 until channels) {
                out[i * channels + ch] = sample
            }
        }
        return out
    }

    private fun createSinePcm16(
        freqHz: Double,
        sampleRate: Int,
        frames: Int,
        channels: Int
    ): ByteArray {
        val floats = createSineFloat(freqHz, sampleRate, frames, channels, amplitude = 0.5f)
        val bytes = ByteArray(floats.size * 2)
        for (i in floats.indices) {
            val s = (floats[i] * 32767f).toInt().coerceIn(-32768, 32767)
            bytes[i * 2] = (s and 0xFF).toByte()
            bytes[i * 2 + 1] = ((s ushr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private fun rms(samples: FloatArray, start: Int = 0): Float {
        var sum = 0.0
        var count = 0
        for (i in start until samples.size) {
            val v = samples[i].toDouble()
            sum += v * v
            count++
        }
        return if (count > 0) sqrt(sum / count).toFloat() else 0f
    }
}
