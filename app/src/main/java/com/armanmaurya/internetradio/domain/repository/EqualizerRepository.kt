package com.armanmaurya.internetradio.domain.repository

import com.armanmaurya.internetradio.domain.model.EqualizerConfig
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for the equalizer configuration, providing low-latency in-memory state
 * updates for active audio processors alongside persisted DataStore backing.
 */
interface EqualizerRepository {
    val equalizerConfig: StateFlow<EqualizerConfig>

    fun setEnabled(enabled: Boolean)
    fun selectPreset(presetId: String)
    fun updateBandGain(bandIndex: Int, gainDb: Float)
    fun updatePreamp(preampDb: Float)
    fun reset()
    suspend fun flush()
}
