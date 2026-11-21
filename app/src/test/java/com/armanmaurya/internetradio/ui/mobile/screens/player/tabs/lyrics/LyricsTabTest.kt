package com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.lyrics

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.armanmaurya.internetradio.domain.lyrics.LyricsProviderInfo
import com.armanmaurya.internetradio.domain.model.LrcLine
import com.armanmaurya.internetradio.domain.model.LrcWord
import com.armanmaurya.internetradio.domain.model.Lyrics
import com.armanmaurya.internetradio.ui.shared.viewmodels.PlayerViewModel.LyricsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h1000dp")
class LyricsTabTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `displays lyrics lines, attribution and download button on success`() {
        val testLyrics = Lyrics(
            plainLyrics = null,
            syncedLyrics = listOf(
                LrcLine(timestampMs = 0L, text = "First line of song"),
                LrcLine(timestampMs = 5000L, text = "Second line of song")
            ),
            providerInfo = LyricsProviderInfo("kugou", "KuGou", "https://kugou.com"),
            locatorToken = "123|abc",
            trackTitle = "Song Title",
            artistName = "Artist Name"
        )

        var downloadedLyrics: Lyrics? = null

        compose.setContent {
            LyricsTab(
                listState = rememberLazyListState(),
                nestedScrollConnection = object : NestedScrollConnection {},
                lyricsUiState = LyricsUiState.Success(testLyrics),
                trackStartTime = System.currentTimeMillis(),
                syncOffsetMs = 0L,
                isPlaying = true,
                getCurrentPosition = { 2000L },
                onSyncOffsetChange = {},
                onDownloadLyrics = { downloadedLyrics = it }
            )
        }

        // Verify lyric lines are rendered
        compose.onNodeWithText("First line of song").assertIsDisplayed()
        compose.onNodeWithText("Second line of song").assertIsDisplayed()

        // Verify provider attribution and line synced indicator
        compose.onNodeWithText("KuGou", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("Line")[0].assertIsDisplayed()

        // Verify Download Lyrics button is rendered and clickable
        compose.onNodeWithContentDescription("Download Lyrics").assertIsDisplayed().performClick()
        assertNotNull(downloadedLyrics)
        assertEquals("kugou", downloadedLyrics?.providerInfo?.id)
        assertEquals("kugou:123|abc", downloadedLyrics?.locator)
    }

    @Test
    fun `displays word synced indicator and highlights word by word when words are present`() {
        val testLyrics = Lyrics(
            plainLyrics = null,
            syncedLyrics = listOf(
                LrcLine(
                    timestampMs = 0L,
                    text = "Never gonna give",
                    words = listOf(
                        LrcWord(startMs = 0L, endMs = 500L, text = "Never "),
                        LrcWord(startMs = 500L, endMs = 1000L, text = "gonna "),
                        LrcWord(startMs = 1000L, endMs = 2000L, text = "give")
                    )
                )
            ),
            providerInfo = LyricsProviderInfo("betterlyrics", "Better Lyrics", "https://betterlyrics.net"),
            locatorToken = "Queen|Radio",
            trackTitle = "Radio",
            artistName = "Queen"
        )

        compose.setContent {
            LyricsTab(
                listState = rememberLazyListState(),
                nestedScrollConnection = object : NestedScrollConnection {},
                lyricsUiState = LyricsUiState.Success(testLyrics),
                trackStartTime = 10000L,
                syncOffsetMs = 0L,
                isPlaying = true,
                getCurrentPosition = { 10800L },
                onSyncOffsetChange = {}
            )
        }

        // Verify "Word" is displayed in button
        compose.onAllNodesWithText("Word")[0].assertIsDisplayed()
        compose.onNodeWithText("Better Lyrics", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Never gonna give").assertIsDisplayed()
    }

    @Test
    fun `displays not available message when lyrics state is empty`() {
        compose.setContent {
            LyricsTab(
                listState = rememberLazyListState(),
                nestedScrollConnection = object : NestedScrollConnection {},
                lyricsUiState = LyricsUiState.NotAvailable,
                trackStartTime = null,
                syncOffsetMs = 0L,
                isPlaying = false,
                getCurrentPosition = { 0L },
                onSyncOffsetChange = {}
            )
        }

        compose.onNodeWithText("Lyrics not available").assertIsDisplayed()
    }

    @Test
    fun `displays plain lyrics when trackStartTime is null on tune-in`() {
        val testLyrics = Lyrics(
            plainLyrics = null,
            syncedLyrics = listOf(
                LrcLine(timestampMs = 0L, text = "Tune-in track lyric line")
            ),
            providerInfo = LyricsProviderInfo("kugou", "KuGou", "https://kugou.com"),
            locatorToken = "kugou:123",
            trackTitle = "Live Radio Track",
            artistName = "Artist"
        )

        compose.setContent {
            LyricsTab(
                listState = rememberLazyListState(),
                nestedScrollConnection = object : NestedScrollConnection {},
                lyricsUiState = LyricsUiState.Success(testLyrics),
                trackStartTime = null,
                syncOffsetMs = 0L,
                isPlaying = true,
                getCurrentPosition = { 1000L },
                onSyncOffsetChange = {}
            )
        }

        // Must display the lyric line and provider, never "Lyrics not available"
        compose.onNodeWithText("Tune-in track lyric line").assertIsDisplayed()
        compose.onNodeWithText("KuGou", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Plain").assertIsDisplayed()
    }

    @Test
    fun `replaces button text with offset seconds when offset is non-zero to save space`() {
        val testLyrics = Lyrics(
            plainLyrics = null,
            syncedLyrics = listOf(
                LrcLine(timestampMs = 0L, text = "First line")
            ),
            providerInfo = LyricsProviderInfo("lrclib", "LRCLIB", "https://lrclib.net")
        )

        compose.setContent {
            LyricsTab(
                listState = rememberLazyListState(),
                nestedScrollConnection = object : NestedScrollConnection {},
                lyricsUiState = LyricsUiState.Success(testLyrics),
                trackStartTime = 1000L,
                syncOffsetMs = 1500L,
                isPlaying = true,
                getCurrentPosition = { 2000L },
                onSyncOffsetChange = {}
            )
        }

        // Offset +1.5s replaces "Line", saving horizontal space
        compose.onNodeWithText("+1.5s").assertIsDisplayed()
    }

    @Test
    fun `displays options button on the left and opens options bottom sheet`() {
        val testLyrics = Lyrics(
            plainLyrics = "Some lyrics",
            syncedLyrics = null,
            providerInfo = LyricsProviderInfo("lrclib", "LRCLIB", "https://lrclib.net")
        )

        compose.setContent {
            LyricsTab(
                listState = rememberLazyListState(),
                nestedScrollConnection = object : NestedScrollConnection {},
                lyricsUiState = LyricsUiState.Success(testLyrics),
                trackStartTime = null,
                syncOffsetMs = 0L,
                isPlaying = true,
                getCurrentPosition = { 0L },
                onSyncOffsetChange = {},
                currentTrack = "My Track",
                currentArtist = "My Artist"
            )
        }

        // Left button: Options and search
        compose.onNodeWithContentDescription("Lyrics options and search").assertIsDisplayed().performClick()

        // Verify sheet opened
        compose.onNodeWithText("Providers").assertIsDisplayed()
        compose.onNodeWithText("Auto (Recommended)").assertIsDisplayed()
        compose.onNodeWithText("Reset to Track").assertIsDisplayed()
        compose.onNodeWithText("Search").assertIsDisplayed()
    }

    @Test
    fun `empty state displays search or edit query button that opens options sheet`() {
        compose.setContent {
            LyricsTab(
                listState = rememberLazyListState(),
                nestedScrollConnection = object : NestedScrollConnection {},
                lyricsUiState = LyricsUiState.NotAvailable,
                trackStartTime = null,
                syncOffsetMs = 0L,
                isPlaying = false,
                getCurrentPosition = { 0L },
                onSyncOffsetChange = {},
                currentTrack = "Radio Track",
                currentArtist = "Radio Artist"
            )
        }

        compose.onNodeWithText("Search or Edit Query").assertIsDisplayed().performClick()
        compose.onNodeWithText("Providers").assertIsDisplayed()
    }
}
