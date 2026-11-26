package com.armanmaurya.internetradio.ui.tv.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.tv.material3.Switch
import com.armanmaurya.internetradio.domain.controller.CastController
import com.armanmaurya.internetradio.domain.model.CastDevice
import com.armanmaurya.internetradio.domain.model.CastPlaybackState
import com.armanmaurya.internetradio.domain.model.EqualizerConfig
import com.armanmaurya.internetradio.domain.model.EqualizerPresets
import com.armanmaurya.internetradio.domain.repository.EqualizerRepository
import com.armanmaurya.internetradio.ui.shared.theme.TvInternetRadioTheme
import com.armanmaurya.internetradio.ui.shared.viewmodels.EqualizerViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1280dp-h720dp")
class TvEqualizerPageTest {

    @get:Rule
    val compose = createComposeRule()

    private class FakeEqualizerRepository : EqualizerRepository {
        private val _config = MutableStateFlow(EqualizerConfig(enabled = true))
        override val equalizerConfig: StateFlow<EqualizerConfig> = _config.asStateFlow()

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

        override suspend fun flush() {}
    }

    private class FakeCastController : CastController {
        override val discoveredDevices = MutableStateFlow<List<CastDevice>>(emptyList())
        override val connectedDevice = MutableStateFlow<CastDevice?>(null)
        override val playbackState = MutableStateFlow(CastPlaybackState.IDLE)
        override val volume = MutableStateFlow(1.0)
        override val time = MutableStateFlow(0.0)
        override fun connectToDevice(device: CastDevice) {}
        override fun disconnect() {}
        override fun load(url: String, contentType: String, resumePosition: Double, title: String?, thumbnailUrl: String?) {}
        override fun play() {}
        override fun pause() {}
        override fun stop() {}
        override fun seek(time: Double) {}
        override fun setVolume(volume: Double) {}
    }

    @Test
    fun `renders tv equalizer page, preset dialog, and dpad band stepping`() {
        val repo = FakeEqualizerRepository()
        val viewModel = EqualizerViewModel(repo, FakeCastController())

        compose.setContent {
            TvInternetRadioTheme {
                TvEqualizerPage(viewModel = viewModel)
            }
        }

        compose.onNodeWithText("Equalizer").assertIsDisplayed()
        compose.onNodeWithText("Enable equalizer").assertIsDisplayed()
        compose.onNodeWithText("Presets").assertIsDisplayed()

        // Open preset selection dialog via TV click action and pick Rock
        compose.onNodeWithText("Presets").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("Rock").assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(EqualizerPresets.ROCK_ID, repo.equalizerConfig.value.presetId)

        // Focus 1k band column, enter edit mode, step D-pad Up twice rapidly without waiting, then exit
        compose.onNodeWithContentDescription("1k Hz band gain")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("1k Hz band gain")
            .performKeyInput {
                pressKey(Key.DirectionCenter)
                pressKey(Key.DirectionUp)
                pressKey(Key.DirectionUp)
                pressKey(Key.DirectionCenter)
            }
        compose.waitForIdle()
        assertEquals(1f, repo.equalizerConfig.value.bandGainsDb[5], 0.001f)
        assertEquals(EqualizerPresets.CUSTOM_PRESET_ID, repo.equalizerConfig.value.presetId)

        // Reset restores Flat and keeps focus on Presets rather than dropping out of Settings
        compose.onNodeWithText("Reset").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertTrue(repo.equalizerConfig.value.isFlat)
        compose.onNodeWithText("Presets").assertIsFocused()
    }

    @Test
    fun `tv settings equalizer row displays trailing switch alongside value`() {
        var clicked = false
        compose.setContent {
            TvInternetRadioTheme {
                SettingsRow(
                    title = "Equalizer",
                    value = "Rock",
                    icon = Icons.Default.Equalizer,
                    onClick = { clicked = true },
                    trailingPrefix = {
                        Switch(
                            checked = true,
                            onCheckedChange = null,
                            modifier = Modifier.testTag("tv_settings_switch")
                        )
                    }
                )
            }
        }

        compose.onNodeWithText("Equalizer").assertIsDisplayed()
        compose.onNodeWithText("Rock").assertIsDisplayed()
        compose.onNodeWithTag("tv_settings_switch", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Equalizer").performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(clicked)
    }
}
