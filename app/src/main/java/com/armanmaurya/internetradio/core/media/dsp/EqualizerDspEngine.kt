package com.armanmaurya.internetradio.core.media.dsp

import com.armanmaurya.internetradio.domain.model.EqualizerConfig
import com.armanmaurya.internetradio.domain.model.EqualizerPresets
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Pure-Kotlin 10-band biquad equalizer DSP engine with preamp gain and soft-knee limiting.
 *
 * Uses Direct Form II Transposed second-order IIR sections (Robert Bristow-Johnson's Audio EQ
 * Cookbook): low-shelf at 31 Hz, peaking filters at 62 Hz..8 kHz (Q = sqrt(2)/octave-like), and
 * high-shelf at 16 kHz.
 *
 * Thread safety: [updateConfig] and [configure] build an immutable [CompiledStage] snapshot and
 * publish it via a volatile reference so the real-time audio loop allocates no heap objects.
 */
class EqualizerDspEngine {

    private class CompiledStage(
        val bypassed: Boolean,
        val preampLinear: Float,
        val activeCount: Int,
        val activeBandSlots: IntArray,
        // Packed [b0, b1, b2, a1, a2] per active filter.
        val coeffs: FloatArray
    )

    @Volatile
    private var config: EqualizerConfig = EqualizerConfig()

    @Volatile
    private var stage: CompiledStage = BYPASSED_STAGE

    private var sampleRate: Int = 0
    private var channelCount: Int = 0

    // Per-band, per-channel Direct Form II Transposed delay state: [band * channelCount + ch].
    private var z1: FloatArray = FloatArray(0)
    private var z2: FloatArray = FloatArray(0)

    fun configure(sampleRate: Int, channelCount: Int) {
        val validSampleRate = sampleRate.coerceAtLeast(0)
        val validChannels = channelCount.coerceAtLeast(0)
        val formatChanged = this.sampleRate != validSampleRate || this.channelCount != validChannels
        this.sampleRate = validSampleRate
        this.channelCount = validChannels

        if (formatChanged) {
            val stateSize = EqualizerPresets.BAND_COUNT * validChannels
            z1 = FloatArray(stateSize)
            z2 = FloatArray(stateSize)
        }
        recompile()
    }

    fun updateConfig(newConfig: EqualizerConfig) {
        config = newConfig.sanitized()
        recompile()
    }

    fun isBypassed(): Boolean = stage.bypassed || sampleRate <= 0 || channelCount <= 0

    fun resetState() {
        z1.fill(0f)
        z2.fill(0f)
    }

    /**
     * Processes interleaved 16-bit little-endian PCM bytes from [inputBuffer] into [outputBuffer]
     * without allocating or changing buffer byte order. Both buffers must have at least
     * [byteCount] remaining bytes aligned to frame boundaries.
     */
    fun processPcm16Le(inputBuffer: ByteBuffer, outputBuffer: ByteBuffer, byteCount: Int) {
        val currentStage = stage
        val channels = channelCount
        if (currentStage.bypassed || channels <= 0 || sampleRate <= 0) {
            val dup = inputBuffer.slice()
            dup.limit(byteCount)
            outputBuffer.put(dup)
            inputBuffer.position(inputBuffer.position() + byteCount)
            return
        }

        var inPos = inputBuffer.position()
        val endPos = inPos + byteCount
        val frameBytes = channels * 2
        val alignedEndPos = inPos + ((endPos - inPos) / frameBytes) * frameBytes

        while (inPos < alignedEndPos) {
            for (ch in 0 until channels) {
                val lo = inputBuffer.get(inPos).toInt() and 0xFF
                val hi = inputBuffer.get(inPos + 1).toInt() shl 8
                val sampleInt = (lo or hi).toShort().toInt()
                val normalized = sampleInt / 32768.0f

                val processed = processSample(normalized, ch, currentStage)
                val scaled = (processed * 32767.0f).roundToInt().coerceIn(-32768, 32767)

                outputBuffer.put((scaled and 0xFF).toByte())
                outputBuffer.put(((scaled ushr 8) and 0xFF).toByte())
                inPos += 2
            }
        }
        // Copy any trailing partial-frame bytes untouched so nothing is dropped.
        while (inPos < endPos) {
            outputBuffer.put(inputBuffer.get(inPos))
            inPos++
        }
        inputBuffer.position(endPos)
    }

    /**
     * In-place float processing helper for unit tests and float PCM pipelines.
     */
    fun processFloatInterleaved(samples: FloatArray, sampleCount: Int = samples.size) {
        val currentStage = stage
        val channels = channelCount
        if (currentStage.bypassed || channels <= 0 || sampleRate <= 0) return

        val alignedCount = (sampleCount / channels) * channels
        var idx = 0
        while (idx < alignedCount) {
            for (ch in 0 until channels) {
                samples[idx] = processSample(samples[idx], ch, currentStage)
                idx++
            }
        }
    }

    private fun processSample(input: Float, channel: Int, currentStage: CompiledStage): Float {
        var x = input * currentStage.preampLinear
        val activeCount = currentStage.activeCount
        val slots = currentStage.activeBandSlots
        val coeffs = currentStage.coeffs
        val channels = channelCount

        for (i in 0 until activeCount) {
            val bandSlot = slots[i]
            val stateIdx = bandSlot * channels + channel
            val cIdx = i * 5
            val b0 = coeffs[cIdx]
            val b1 = coeffs[cIdx + 1]
            val b2 = coeffs[cIdx + 2]
            val a1 = coeffs[cIdx + 3]
            val a2 = coeffs[cIdx + 4]

            val y = b0 * x + z1[stateIdx]
            z1[stateIdx] = b1 * x - a1 * y + z2[stateIdx]
            z2[stateIdx] = b2 * x - a2 * y
            x = y
        }

        return softLimit(x)
    }

    private fun softLimit(sample: Float): Float {
        if (!sample.isFinite()) return 0f
        if (sample > LIMITER_THRESHOLD) {
            val excess = sample - LIMITER_THRESHOLD
            return LIMITER_THRESHOLD + LIMITER_HEADROOM * tanh((excess / LIMITER_HEADROOM).toDouble()).toFloat()
        }
        if (sample < -LIMITER_THRESHOLD) {
            val excess = -sample - LIMITER_THRESHOLD
            return -(LIMITER_THRESHOLD + LIMITER_HEADROOM * tanh((excess / LIMITER_HEADROOM).toDouble()).toFloat())
        }
        return sample
    }

    private fun recompile() {
        val cfg = config
        val fs = sampleRate
        if (!cfg.enabled || cfg.isFlat || fs <= 0) {
            stage = BYPASSED_STAGE
            return
        }

        val preampLinear = dbToLinear(cfg.preampDb)
        val nyquistGuardHz = fs * MAX_NYQUIST_RATIO

        val tempSlots = IntArray(EqualizerPresets.BAND_COUNT)
        val tempCoeffs = FloatArray(EqualizerPresets.BAND_COUNT * 5)
        var activeCount = 0

        for (band in EqualizerPresets.ISO_10_BANDS) {
            val gainDb = cfg.bandGainsDb.getOrElse(band.index) { 0f }
            if (kotlin.math.abs(gainDb) < MIN_ACTIVE_GAIN_DB) continue
            if (band.centerFreqHz >= nyquistGuardHz) continue

            val type = when (band.index) {
                0 -> FilterType.LOW_SHELF
                EqualizerPresets.BAND_COUNT - 1 -> FilterType.HIGH_SHELF
                else -> FilterType.PEAKING
            }
            val coeffs = computeBiquadCoefficients(
                type = type,
                sampleRate = fs.toDouble(),
                centerFreqHz = band.centerFreqHz.toDouble(),
                gainDb = gainDb.toDouble()
            )
            tempSlots[activeCount] = band.index
            val base = activeCount * 5
            tempCoeffs[base] = coeffs[0]
            tempCoeffs[base + 1] = coeffs[1]
            tempCoeffs[base + 2] = coeffs[2]
            tempCoeffs[base + 3] = coeffs[3]
            tempCoeffs[base + 4] = coeffs[4]
            activeCount++
        }

        stage = if (activeCount == 0 && cfg.preampDb == 0f) {
            BYPASSED_STAGE
        } else {
            CompiledStage(
                bypassed = false,
                preampLinear = preampLinear,
                activeCount = activeCount,
                activeBandSlots = tempSlots.copyOf(activeCount),
                coeffs = tempCoeffs.copyOf(activeCount * 5)
            )
        }
    }

    private enum class FilterType {
        LOW_SHELF,
        PEAKING,
        HIGH_SHELF
    }

    companion object {
        private const val PEAKING_Q = 1.1 // Smooth overlap across octave-spaced ISO bands
        private const val MIN_ACTIVE_GAIN_DB = 0.05f
        private const val MAX_NYQUIST_RATIO = 0.45f
        private const val LIMITER_THRESHOLD = 0.9f
        private const val LIMITER_HEADROOM = 1.0f - LIMITER_THRESHOLD
        private const val REFERENCE_SAMPLE_RATE = 48_000.0

        private val BYPASSED_STAGE = CompiledStage(
            bypassed = true,
            preampLinear = 1f,
            activeCount = 0,
            activeBandSlots = IntArray(0),
            coeffs = FloatArray(0)
        )

        private fun dbToLinear(db: Float): Float = 10.0.pow(db / 20.0).toFloat()

        private fun computeBiquadCoefficients(
            type: FilterType,
            sampleRate: Double,
            centerFreqHz: Double,
            gainDb: Double
        ): FloatArray {
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * centerFreqHz / sampleRate
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)

            val b0: Double
            val b1: Double
            val b2: Double
            val a0: Double
            val a1: Double
            val a2: Double

            when (type) {
                FilterType.PEAKING -> {
                    val alpha = sinW0 / (2.0 * PEAKING_Q)
                    b0 = 1.0 + alpha * a
                    b1 = -2.0 * cosW0
                    b2 = 1.0 - alpha * a
                    a0 = 1.0 + alpha / a
                    a1 = -2.0 * cosW0
                    a2 = 1.0 - alpha / a
                }
                FilterType.LOW_SHELF -> {
                    val alpha = sinW0 / sqrt(2.0) // S = 1.0
                    val beta = 2.0 * sqrt(a) * alpha
                    b0 = a * ((a + 1.0) - (a - 1.0) * cosW0 + beta)
                    b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0)
                    b2 = a * ((a + 1.0) - (a - 1.0) * cosW0 - beta)
                    a0 = (a + 1.0) + (a - 1.0) * cosW0 + beta
                    a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW0)
                    a2 = (a + 1.0) + (a - 1.0) * cosW0 - beta
                }
                FilterType.HIGH_SHELF -> {
                    val alpha = sinW0 / sqrt(2.0) // S = 1.0
                    val beta = 2.0 * sqrt(a) * alpha
                    b0 = a * ((a + 1.0) + (a - 1.0) * cosW0 + beta)
                    b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0)
                    b2 = a * ((a + 1.0) + (a - 1.0) * cosW0 - beta)
                    a0 = (a + 1.0) - (a - 1.0) * cosW0 + beta
                    a1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW0)
                    a2 = (a + 1.0) - (a - 1.0) * cosW0 - beta
                }
            }

            return floatArrayOf(
                (b0 / a0).toFloat(),
                (b1 / a0).toFloat(),
                (b2 / a0).toFloat(),
                (a1 / a0).toFloat(),
                (a2 / a0).toFloat()
            )
        }

        /**
         * Computes the combined frequency response in dB across [pointCount] log-spaced frequencies
         * from 20 Hz to 20 kHz for UI curve preview and diagnostics.
         */
        fun computeFrequencyResponseDb(
            config: EqualizerConfig,
            pointCount: Int = 64,
            sampleRate: Double = REFERENCE_SAMPLE_RATE
        ): List<Float> {
            val sanitized = config.sanitized()
            if (pointCount <= 0) return emptyList()
            if (!sanitized.enabled) return List(pointCount) { 0f }

            val activeFilters = EqualizerPresets.ISO_10_BANDS.mapNotNull { band ->
                val gain = sanitized.bandGainsDb.getOrElse(band.index) { 0f }
                if (kotlin.math.abs(gain) < MIN_ACTIVE_GAIN_DB || band.centerFreqHz >= sampleRate * MAX_NYQUIST_RATIO) {
                    null
                } else {
                    val type = when (band.index) {
                        0 -> FilterType.LOW_SHELF
                        EqualizerPresets.BAND_COUNT - 1 -> FilterType.HIGH_SHELF
                        else -> FilterType.PEAKING
                    }
                    computeBiquadCoefficients(type, sampleRate, band.centerFreqHz.toDouble(), gain.toDouble())
                }
            }

            val minLog = log10(20.0)
            val maxLog = log10(20_000.0)
            val denom = (pointCount - 1).coerceAtLeast(1)

            return List(pointCount) { idx ->
                val freq = 10.0.pow(minLog + (maxLog - minLog) * idx / denom)
                val w = 2.0 * PI * freq / sampleRate
                val cos1 = cos(w)
                val sin1 = sin(w)
                val cos2 = cos(2.0 * w)
                val sin2 = sin(2.0 * w)

                var totalMagnitudeSq = 1.0
                for (c in activeFilters) {
                    val numRe = c[0] + c[1] * cos1 + c[2] * cos2
                    val numIm = -c[1] * sin1 - c[2] * sin2
                    val denRe = 1.0 + c[3] * cos1 + c[4] * cos2
                    val denIm = -c[3] * sin1 - c[4] * sin2
                    val denSq = (denRe * denRe + denIm * denIm).coerceAtLeast(1e-18)
                    totalMagnitudeSq *= (numRe * numRe + numIm * numIm) / denSq
                }

                val filterDb = 10.0 * log10(totalMagnitudeSq.coerceAtLeast(1e-12))
                (sanitized.preampDb + filterDb.toFloat())
            }
        }
    }
}
