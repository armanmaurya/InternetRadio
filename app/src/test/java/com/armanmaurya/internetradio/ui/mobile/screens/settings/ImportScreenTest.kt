package com.armanmaurya.internetradio.ui.mobile.screens.settings

import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.data.backup.BackupStation
import com.armanmaurya.internetradio.data.backup.BackupTrack
import com.armanmaurya.internetradio.data.local.entity.StationEntity
import com.armanmaurya.internetradio.data.repository.FakeSettingsRepository
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.ui.shared.viewmodels.DirectTransactionRunner
import com.armanmaurya.internetradio.ui.shared.viewmodels.FakeLibraryRepository
import com.armanmaurya.internetradio.ui.shared.viewmodels.ImportViewModel
import com.armanmaurya.internetradio.ui.shared.viewmodels.RecordingTrackHistoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImportScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val library = FakeLibraryRepository()
    private val played = mutableListOf<RadioStation>()

    private fun station(uuid: String, name: String, plays: Int = 0) = BackupStation(
        stationUuid = uuid,
        name = name,
        url = "https://$uuid.example",
        tracks = (1..plays).map { BackupTrack(trackTitle = "Song $it", timestamp = it * 1000L) }.ifEmpty { null }
    )

    private fun show(vararg stations: BackupStation): ImportViewModel {
        val viewModel = ImportViewModel(library, RecordingTrackHistoryRepository(), FakeSettingsRepository(), DirectTransactionRunner(), context)
        runBlocking { viewModel.showReview(stations.toList()) }
        compose.setContent {
            ImportScreen(
                viewModel = viewModel,
                onClose = {},
                onPlayStation = { played += it },
                playingStationUuid = null,
                isPlaybackActive = false
            )
        }
        return viewModel
    }

    private fun importButton(count: Int) =
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.import_button, count, count))

    private fun stationCheckbox(name: String) = compose.onNode(isToggleable() and hasContentDescription(name))

    @Test
    fun stationCheckbox_changesWhatIsImported() {
        show(station("a", "Alpha"), station("b", "Bravo"))
        importButton(2).assertIsEnabled()

        stationCheckbox("Alpha").performClick()
        importButton(1).assertIsEnabled()

        stationCheckbox("Bravo").performClick()
        importButton(0).assertIsNotEnabled()
    }

    @Test
    fun tappingCard_playsStation_withoutChangingSelection() {
        show(station("a", "Alpha"))

        compose.onNodeWithText("Alpha").performClick()

        assertEquals(listOf("a"), played.map { it.stationUuid })
        importButton(1).assertIsEnabled()
    }

    @Test
    fun existingStation_offersReplaceChip_offByDefault() {
        library.entities["old"] = StationEntity(stationUuid = "old", name = "Mine", url = "https://mine")
        show(station("old", "Oldie", plays = 2))

        compose.onNodeWithText(context.getString(R.string.import_mode_replace)).assertIsNotSelected()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.import_plays, 2, 2)).assertIsEnabled()
    }

    @Test
    fun newStation_historyChip_needsTheStation() {
        show(station("a", "Alpha", plays = 2))
        val historyChip = compose.onNodeWithText(context.resources.getQuantityString(R.plurals.import_plays, 2, 2))
        historyChip.assertIsEnabled()

        compose.onNodeWithText(context.getString(R.string.import_part_station)).performClick()

        historyChip.assertIsNotEnabled()
        importButton(0).assertIsNotEnabled()
    }

    @Test
    fun search_onlyForLongLists_andFiltersStations() {
        show(*(1..21).map { station("s$it", "Station $it") }.toTypedArray(), station("z", "Zebra FM"))

        compose.onNodeWithContentDescription(context.getString(R.string.cd_search)).performClick()
        compose.onNode(hasText(context.getString(R.string.general_search))).performTextInput("zebra")

        compose.onNodeWithText("Zebra FM").assertExists()
        compose.onNodeWithText("Station 1").assertDoesNotExist()
    }

    @Test
    fun search_hiddenForShortLists() {
        show(station("a", "Alpha"))

        compose.onNodeWithContentDescription(context.getString(R.string.cd_search)).assertDoesNotExist()
    }
}
