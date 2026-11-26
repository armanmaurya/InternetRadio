package com.armanmaurya.internetradio.ui.mobile.screens.settings

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.armanmaurya.internetradio.domain.model.EqualizerConfig
import com.armanmaurya.internetradio.domain.model.EqualizerPresets
import com.armanmaurya.internetradio.ui.shared.viewmodels.EqualizerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h1000dp")
class EqualizerScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `renders presets, all 10 band labels, and disabled reset when flat`() {
        compose.setContent {
            EqualizerScreenContent(
                uiState = EqualizerUiState(config = EqualizerConfig(enabled = true), isCasting = false),
                onEnabledChange = {},
                onPresetSelected = {},
                onBandGainChange = { _, _ -> },
                onPreampChange = {},
                onSliderChangeFinished = {},
                onReset = {},
                onBackClick = {}
            )
        }

        compose.onNodeWithText("Equalizer").assertIsDisplayed()
        compose.onNodeWithText("Reset").assertIsNotEnabled()
        compose.onNodeWithText("Vocal / Talk").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Rock").performScrollTo().assertIsDisplayed()

        EqualizerPresets.ISO_10_BANDS.forEach { band ->
            compose.onNodeWithText(band.shortLabel).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `shows cast banner when casting and forwards preset and reset actions`() {
        var selectedPreset: String? = null
        var resetCalled = false

        val rockPreset = EqualizerPresets.findById(EqualizerPresets.ROCK_ID)!!
        val config = EqualizerConfig(
            enabled = true,
            presetId = rockPreset.id,
            bandGainsDb = rockPreset.bandGainsDb,
            preampDb = rockPreset.preampDb
        )

        compose.setContent {
            EqualizerScreenContent(
                uiState = EqualizerUiState(config = config, isCasting = true),
                onEnabledChange = {},
                onPresetSelected = { selectedPreset = it },
                onBandGainChange = { _, _ -> },
                onPreampChange = {},
                onSliderChangeFinished = {},
                onReset = { resetCalled = true },
                onBackClick = {}
            )
        }

        compose.onNodeWithText("Equalizer applies to device playback only, not active Cast devices.")
            .assertIsDisplayed()
        compose.onNodeWithText("Reset").assertIsEnabled().performClick()
        assertTrue(resetCalled)

        compose.onNodeWithText("Jazz").performScrollTo().performClick()
        assertEquals(EqualizerPresets.JAZZ_ID, selectedPreset)
    }

    @Test
    fun `back navigation and enable switch invoke callbacks`() {
        var backClicked = false
        var enabledValue: Boolean? = null

        compose.setContent {
            EqualizerScreenContent(
                uiState = EqualizerUiState(config = EqualizerConfig(enabled = false)),
                onEnabledChange = { enabledValue = it },
                onPresetSelected = {},
                onBandGainChange = { _, _ -> },
                onPreampChange = {},
                onSliderChangeFinished = {},
                onReset = {},
                onBackClick = { backClicked = true }
            )
        }

        compose.onNodeWithText("Enable equalizer").performClick()
        assertEquals(true, enabledValue)

        compose.onNodeWithContentDescription("Back").performClick()
        assertTrue(backClicked)
    }

    @Test
    fun `settings equalizer item shows toggle and separates row click from switch toggle`() {
        var clicked = false
        var toggledTo: Boolean? = null

        compose.setContent {
            EqualizerSettingsItem(
                equalizerConfig = EqualizerConfig(enabled = false, presetId = EqualizerPresets.ROCK_ID),
                shape = RoundedCornerShape(16.dp),
                onEnabledChange = { toggledTo = it },
                onClick = { clicked = true }
            )
        }

        compose.onNodeWithText("Equalizer").assertIsDisplayed()
        compose.onNodeWithText("Off").assertIsDisplayed()
        compose.onNodeWithContentDescription("Enable equalizer").assertIsOff().performClick()
        assertEquals(true, toggledTo)

        compose.onNodeWithText("Equalizer").performClick()
        assertTrue(clicked)
    }
}
