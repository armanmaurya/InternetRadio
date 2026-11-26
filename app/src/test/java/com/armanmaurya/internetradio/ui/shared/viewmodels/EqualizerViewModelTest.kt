package com.armanmaurya.internetradio.ui.shared.viewmodels

import com.armanmaurya.internetradio.domain.controller.CastController
import com.armanmaurya.internetradio.domain.model.CastDevice
import com.armanmaurya.internetradio.domain.model.CastPlaybackState
import com.armanmaurya.internetradio.domain.model.EqualizerConfig
import com.armanmaurya.internetradio.domain.model.EqualizerPresets
import com.armanmaurya.internetradio.domain.repository.EqualizerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EqualizerViewModelTest {

    private class FakeEqualizerRepository : EqualizerRepository {
        private val _config = MutableStateFlow(EqualizerConfig())
        override val equalizerConfig: StateFlow<EqualizerConfig> = _config.asStateFlow()
        var flushCalls = 0

        override fun setEnabled(enabled: Boolean) {
            _config.value = _config.value.copy(enabled = enabled)
        }

        override fun selectPreset(presetId: String) {
            val preset = EqualizerPresets.findById(presetId) ?: return
            _config.value = _config.value.copy(
                presetId = preset.id,
                bandGainsDb = preset.bandGainsDb,
                preampDb = preset.preampDb
            )
        }

        override fun updateBandGain(bandIndex: Int, gainDb: Float) {
            val bands = _config.value.bandGainsDb.toMutableList()
            bands[bandIndex] = gainDb
            _config.value = _config.value.copy(
                presetId = EqualizerPresets.CUSTOM_PRESET_ID,
                bandGainsDb = bands
            ).sanitized()
        }

        override fun updatePreamp(preampDb: Float) {
            _config.value = _config.value.copy(
                presetId = EqualizerPresets.CUSTOM_PRESET_ID,
                preampDb = preampDb
            ).sanitized()
        }

        override fun reset() {
            _config.value = EqualizerConfig(enabled = _config.value.enabled)
        }

        override suspend fun flush() {
            flushCalls++
        }
    }

    private class FakeCastController : CastController {
        override val discoveredDevices = MutableStateFlow<List<CastDevice>>(emptyList())
        override val connectedDevice = MutableStateFlow<CastDevice?>(null)
        override val playbackState = MutableStateFlow(CastPlaybackState.IDLE)
        override val volume = MutableStateFlow(1.0)
        override val time = MutableStateFlow(0.0)

        override fun connectToDevice(device: CastDevice) {
            connectedDevice.value = device
        }

        override fun disconnect() {
            connectedDevice.value = null
        }

        override fun load(url: String, contentType: String, resumePosition: Double, title: String?, thumbnailUrl: String?) {}
        override fun play() {}
        override fun pause() {}
        override fun stop() {}
        override fun seek(time: Double) {}
        override fun setVolume(volume: Double) {}
    }

    private lateinit var repository: FakeEqualizerRepository
    private lateinit var castController: FakeCastController
    private lateinit var viewModel: EqualizerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeEqualizerRepository()
        castController = FakeCastController()
        viewModel = EqualizerViewModel(repository, castController)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `updates config and reflects active cast connection in uiState`() = runTest {
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        assertFalse(viewModel.uiState.value.config.enabled)
        assertFalse(viewModel.uiState.value.isCasting)

        viewModel.setEnabled(true)
        viewModel.selectPreset(EqualizerPresets.BASS_BOOST_ID)
        assertTrue(viewModel.uiState.value.config.enabled)
        assertEquals(EqualizerPresets.BASS_BOOST_ID, viewModel.uiState.value.config.presetId)

        viewModel.updateBandGain(4, 3.5f)
        viewModel.onSliderChangeFinished()
        assertEquals(EqualizerPresets.CUSTOM_PRESET_ID, viewModel.uiState.value.config.presetId)
        assertEquals(3.5f, viewModel.uiState.value.config.bandGainsDb[4], 0.001f)
        assertEquals(1, repository.flushCalls)

        castController.connectToDevice(CastDevice(id = "tv-1", name = "Living Room TV"))
        assertTrue(viewModel.uiState.value.isCasting)

        viewModel.reset()
        assertTrue(viewModel.uiState.value.config.isFlat)
        collector.cancel()
    }
}
