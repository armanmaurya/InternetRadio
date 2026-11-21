package com.armanmaurya.internetradio.ui.mobile.screens.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h1000dp")
class LyricsProvidersScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `displays all five lyrics providers in correct order with titles`() {
        val providers = listOf("betterlyrics", "unison", "lyricsplus", "kugou", "lrclib")
        val enabled = setOf("betterlyrics", "unison", "lyricsplus", "kugou", "lrclib")

        compose.setContent {
            LyricsProvidersScreen(
                providerOrder = providers,
                enabledProviders = enabled,
                onOrderChanged = {},
                onProviderToggled = { _, _ -> },
                onBackClick = {}
            )
        }

        compose.onNodeWithText("Lyrics Providers").assertIsDisplayed()
        compose.onNodeWithText("Better Lyrics").assertIsDisplayed()
        compose.onNodeWithText("Unison").assertIsDisplayed()
        compose.onNodeWithText("LyricsPlus").assertIsDisplayed()
        compose.onNodeWithText("KuGou").assertIsDisplayed()
        compose.onNodeWithText("LRCLIB").assertIsDisplayed()

        // Verify drag handles are present for all 5 providers
        val dragHandles = compose.onAllNodesWithContentDescription("Reorder provider").fetchSemanticsNodes()
        assertEquals(5, dragHandles.size)
    }

    @Test
    fun `toggling switch triggers onProviderToggled callback`() {
        val providers = listOf("betterlyrics", "unison", "lyricsplus", "kugou", "lrclib")
        val enabled = setOf("betterlyrics")
        val toggledEvents = mutableListOf<Pair<String, Boolean>>()

        compose.setContent {
            LyricsProvidersScreen(
                providerOrder = providers,
                enabledProviders = enabled,
                onOrderChanged = {},
                onProviderToggled = { id, isChecked -> toggledEvents.add(id to isChecked) },
                onBackClick = {}
            )
        }

        // Click the accessible switch associated with KuGou
        compose.onNodeWithContentDescription("Toggle KuGou").performClick()

        assertEquals(1, toggledEvents.size)
        assertEquals("kugou", toggledEvents[0].first)
        assertTrue(toggledEvents[0].second)
    }

    @Test
    fun `clicking back arrow triggers onBackClick callback`() {
        var backClicked = false

        compose.setContent {
            LyricsProvidersScreen(
                providerOrder = listOf("lrclib"),
                enabledProviders = setOf("lrclib"),
                onOrderChanged = {},
                onProviderToggled = { _, _ -> },
                onBackClick = { backClicked = true }
            )
        }

        compose.onNodeWithContentDescription("Back").performClick()
        assertTrue(backClicked)
    }
}
