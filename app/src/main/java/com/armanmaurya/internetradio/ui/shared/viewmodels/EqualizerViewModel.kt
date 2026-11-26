package com.armanmaurya.internetradio.ui.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.domain.controller.CastController
import com.armanmaurya.internetradio.domain.model.EqualizerConfig
import com.armanmaurya.internetradio.domain.repository.EqualizerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EqualizerUiState(
    val config: EqualizerConfig = EqualizerConfig(),
    val isCasting: Boolean = false
)

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val equalizerRepository: EqualizerRepository,
    castController: CastController
) : ViewModel() {

    val uiState: StateFlow<EqualizerUiState> = combine(
        equalizerRepository.equalizerConfig,
        castController.connectedDevice.map { it != null }
    ) { config, isCasting ->
        EqualizerUiState(
            config = config,
            isCasting = isCasting
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EqualizerUiState(
            config = equalizerRepository.equalizerConfig.value,
            isCasting = castController.connectedDevice.value != null
        )
    )

    fun setEnabled(enabled: Boolean) {
        equalizerRepository.setEnabled(enabled)
    }

    fun selectPreset(presetId: String) {
        equalizerRepository.selectPreset(presetId)
    }

    fun updateBandGain(bandIndex: Int, gainDb: Float) {
        equalizerRepository.updateBandGain(bandIndex, gainDb)
    }

    fun stepBandGain(bandIndex: Int, deltaDb: Float) {
        val current = equalizerRepository.equalizerConfig.value.bandGainsDb
            .getOrElse(bandIndex) { 0f }
        equalizerRepository.updateBandGain(bandIndex, current + deltaDb)
    }

    fun updatePreamp(preampDb: Float) {
        equalizerRepository.updatePreamp(preampDb)
    }

    fun stepPreamp(deltaDb: Float) {
        val current = equalizerRepository.equalizerConfig.value.preampDb
        equalizerRepository.updatePreamp(current + deltaDb)
    }

    fun onSliderChangeFinished() {
        viewModelScope.launch {
            equalizerRepository.flush()
        }
    }

    fun reset() {
        equalizerRepository.reset()
    }
}
