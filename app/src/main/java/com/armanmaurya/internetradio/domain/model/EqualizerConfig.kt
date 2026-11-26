package com.armanmaurya.internetradio.domain.model

/**
 * Metadata for one fixed ISO octave band in the 10-band graphic equalizer.
 */
data class EqualizerBand(
    val index: Int,
    val centerFreqHz: Int,
    val shortLabel: String
)

/**
 * Built-in equalizer preset definition.
 */
data class EqualizerPreset(
    val id: String,
    val bandGainsDb: List<Float>,
    val preampDb: Float = 0f
)

/**
 * Active user equalizer configuration shared across playback services and settings UI.
 */
data class EqualizerConfig(
    val enabled: Boolean = false,
    val presetId: String = EqualizerPresets.FLAT_ID,
    val bandGainsDb: List<Float> = List(EqualizerPresets.BAND_COUNT) { 0f },
    val preampDb: Float = 0f
) {
    val isFlat: Boolean
        get() = preampDb == 0f && bandGainsDb.all { it == 0f }

    fun sanitized(): EqualizerConfig {
        val normalizedBands = List(EqualizerPresets.BAND_COUNT) { index ->
            val raw = bandGainsDb.getOrElse(index) { 0f }
            if (raw.isFinite()) raw.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB) else 0f
        }
        val normalizedPreamp = if (preampDb.isFinite()) {
            preampDb.coerceIn(MIN_PREAMP_DB, MAX_PREAMP_DB)
        } else {
            0f
        }
        val normalizedPreset = presetId.ifBlank { EqualizerPresets.CUSTOM_PRESET_ID }
        return copy(
            presetId = normalizedPreset,
            bandGainsDb = normalizedBands,
            preampDb = normalizedPreamp
        )
    }

    companion object {
        const val MIN_GAIN_DB = -12f
        const val MAX_GAIN_DB = 12f
        const val MIN_PREAMP_DB = -12f
        const val MAX_PREAMP_DB = 12f
    }
}

object EqualizerPresets {
    const val BAND_COUNT = 10
    const val FLAT_ID = "flat"
    const val VOCAL_ID = "vocal"
    const val BASS_BOOST_ID = "bass_boost"
    const val TREBLE_BOOST_ID = "treble_boost"
    const val ROCK_ID = "rock"
    const val POP_ID = "pop"
    const val JAZZ_ID = "jazz"
    const val CLASSICAL_ID = "classical"
    const val CUSTOM_PRESET_ID = "custom"

    val ISO_10_BANDS: List<EqualizerBand> = listOf(
        EqualizerBand(0, 31, "31"),
        EqualizerBand(1, 62, "62"),
        EqualizerBand(2, 125, "125"),
        EqualizerBand(3, 250, "250"),
        EqualizerBand(4, 500, "500"),
        EqualizerBand(5, 1000, "1k"),
        EqualizerBand(6, 2000, "2k"),
        EqualizerBand(7, 4000, "4k"),
        EqualizerBand(8, 8000, "8k"),
        EqualizerBand(9, 16000, "16k")
    )

    val BUILT_IN: List<EqualizerPreset> = listOf(
        EqualizerPreset(
            id = FLAT_ID,
            bandGainsDb = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            preampDb = 0f
        ),
        EqualizerPreset(
            id = VOCAL_ID,
            bandGainsDb = listOf(-3f, -2f, -1f, 0f, 2f, 4f, 4f, 2f, 0f, -1f),
            preampDb = 0f
        ),
        EqualizerPreset(
            id = BASS_BOOST_ID,
            bandGainsDb = listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
            preampDb = -2f
        ),
        EqualizerPreset(
            id = TREBLE_BOOST_ID,
            bandGainsDb = listOf(0f, 0f, 0f, 0f, 0f, 1f, 3f, 5f, 6f, 6f),
            preampDb = -2f
        ),
        EqualizerPreset(
            id = ROCK_ID,
            bandGainsDb = listOf(4f, 3f, 2f, -1f, -2f, -1f, 2f, 4f, 5f, 5f),
            preampDb = -1f
        ),
        EqualizerPreset(
            id = POP_ID,
            bandGainsDb = listOf(-1f, 2f, 4f, 4f, 2f, 0f, -1f, -1f, 2f, 3f),
            preampDb = -1f
        ),
        EqualizerPreset(
            id = JAZZ_ID,
            bandGainsDb = listOf(3f, 2f, 1f, 2f, -1f, -1f, 0f, 1f, 3f, 4f),
            preampDb = -1f
        ),
        EqualizerPreset(
            id = CLASSICAL_ID,
            bandGainsDb = listOf(4f, 3f, 2f, 1f, -1f, -1f, 0f, 2f, 3f, 4f),
            preampDb = -1f
        )
    )

    fun findById(id: String): EqualizerPreset? = BUILT_IN.firstOrNull { it.id == id }
}
