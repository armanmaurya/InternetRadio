package com.armanmaurya.internetradio.ui.mobile.screens.settings

import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.data.local.entity.StationEntity
import com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.ui.shared.viewmodels.ExportViewModel
import com.armanmaurya.internetradio.ui.shared.viewmodels.FakeFileSystemFacade
import com.armanmaurya.internetradio.ui.shared.viewmodels.FakeLibraryRepository
import com.armanmaurya.internetradio.ui.shared.viewmodels.FakeSystemFacade
import com.armanmaurya.internetradio.ui.shared.viewmodels.RecordingTrackHistoryRepository
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExportScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val library = FakeLibraryRepository()
    private val history = RecordingTrackHistoryRepository()
    private val played = mutableListOf<RadioStation>()

    private fun show() {
        library.entities["a"] = StationEntity(stationUuid = "a", name = "Alpha", url = "https://a", orderIndex = 0)
        library.entities["b"] = StationEntity(stationUuid = "b", name = "Bravo", url = "https://b", orderIndex = 1)
        history.history["a"] = listOf(TrackHistoryEntity(stationUuid = "a", trackTitle = "One", timestamp = 1L))
        val viewModel = ExportViewModel(library, history, FakeFileSystemFacade(), FakeSystemFacade(), context)
        viewModel.load()
        compose.setContent {
            ExportScreen(
                viewModel = viewModel,
                onClose = {},
                onPlayStation = { played += it },
                playingStationUuid = null,
                isPlaybackActive = false
            )
        }
        // load() reads the library off the main thread
        compose.waitUntil(5_000) {
            compose.onAllNodes(isToggleable() and hasContentDescription("Alpha")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun exportButton(count: Int) =
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.export_button, count, count))

    @Test
    fun everythingPicked_untickingChangesTheCount() {
        show()
        exportButton(2).assertIsEnabled()

        compose.onNode(isToggleable() and hasContentDescription("Alpha")).performClick()
        exportButton(1).assertIsEnabled()

        compose.onNode(isToggleable() and hasContentDescription("Bravo")).performClick()
        exportButton(0).assertIsNotEnabled()
    }

    @Test
    fun historySwitch_onlyWhenThereIsHistory() {
        show()

        compose.onNodeWithText(context.getString(R.string.import_part_history)).assertExists()
        compose.onNodeWithText(context.getString(R.string.import_part_starred)).assertDoesNotExist()
    }

    @Test
    fun playsChip_onlyOnStationsWithHistory_andLockedWhenUnpicked() {
        show()
        val playsChip = compose.onNodeWithText(context.resources.getQuantityString(R.plurals.import_plays, 1, 1))
        playsChip.assertIsEnabled()

        compose.onNode(isToggleable() and hasContentDescription("Alpha")).performClick()

        playsChip.assertIsNotEnabled()
    }

    @Test
    fun tappingCard_playsStation() {
        show()

        compose.onNodeWithText("Alpha").performClick()

        assertEquals(listOf("a"), played.map { it.stationUuid })
        exportButton(2).assertIsEnabled()
    }
}
