package com.armanmaurya.internetradio.ui.shared.viewmodels

import android.net.Uri
import androidx.compose.ui.state.ToggleableState
import androidx.test.core.app.ApplicationProvider
import com.armanmaurya.internetradio.data.backup.LibraryBackup
import com.armanmaurya.internetradio.data.local.entity.StarredTrackEntity
import com.armanmaurya.internetradio.data.local.entity.StationEntity
import com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExportViewModelTest {

    private lateinit var library: FakeLibraryRepository
    private lateinit var history: RecordingTrackHistoryRepository
    private lateinit var files: FakeFileSystemFacade
    private lateinit var viewModel: ExportViewModel

    private val fileUri: Uri = Uri.parse("content://test/stations.json")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        library = FakeLibraryRepository()
        history = RecordingTrackHistoryRepository()
        files = FakeFileSystemFacade()
        viewModel = ExportViewModel(library, history, files, FakeSystemFacade(), ApplicationProvider.getApplicationContext())

        library.entities["b"] = StationEntity(stationUuid = "b", name = "Bravo", url = "https://b", orderIndex = 1)
        library.entities["a"] = StationEntity(stationUuid = "a", name = "Alpha", url = "https://a", orderIndex = 0)
        history.history["a"] = listOf(
            TrackHistoryEntity(stationUuid = "a", trackTitle = "One", timestamp = 1L),
            TrackHistoryEntity(stationUuid = "a", trackTitle = "Two", timestamp = 2L)
        )
        history.starred["b"] = listOf(StarredTrackEntity(stationUuid = "b", trackTitle = "Fav"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun loadAndWait(): ExportUiState.Choosing = runBlocking {
        viewModel.load()
        withTimeout(5_000) { viewModel.uiState.first { it is ExportUiState.Choosing } } as ExportUiState.Choosing
    }

    private val choosing get() = viewModel.uiState.value as ExportUiState.Choosing

    /** Exports, waits for the screen to close, and returns what was written. */
    private fun exportAndRead(): LibraryBackup = runBlocking {
        viewModel.export(fileUri)
        withTimeout(5_000) { viewModel.uiState.first { it == ExportUiState.Idle } }
        Gson().fromJson(files.written.toString(Charsets.UTF_8.name()), LibraryBackup::class.java)
    }

    @Test
    fun load_listsLibraryInOwnOrder_allPicked_withCounts() {
        val state = loadAndWait()

        assertEquals(listOf("Alpha", "Bravo"), state.stations.map { it.name })
        assertTrue(state.stations.all { it.isPicked })
        assertEquals(2, state.stations[0].playCount)
        assertEquals(1, state.stations[1].starredCount)
        assertEquals(ToggleableState.On, state.allState)
    }

    @Test
    fun toggles_changeSelection() {
        loadAndWait()

        viewModel.toggleStation("a")
        assertEquals(ToggleableState.Indeterminate, choosing.allState)
        assertEquals(1, choosing.selectedCount)

        viewModel.toggleAll()
        assertEquals(ToggleableState.On, choosing.allState)
        viewModel.toggleAll()
        assertEquals(0, choosing.selectedCount)
    }

    @Test
    fun export_writesOnlyPickedStations_withHistoryAndStars() {
        loadAndWait()
        viewModel.toggleStation("b")

        val backup = exportAndRead()

        assertEquals(listOf("a"), backup.stations!!.map { it.stationUuid })
        assertEquals(2, backup.stations!!.single().tracks!!.size)
        assertEquals(ExportResult.Exported(1), viewModel.result.value)
    }

    @Test
    fun export_leavesOutHistoryAndStars_whenSwitchedOff() {
        loadAndWait()
        viewModel.togglePartForAll(ImportPart.HISTORY)
        viewModel.togglePartForAll(ImportPart.STARRED)
        assertEquals(ToggleableState.Off, choosing.partState(ImportPart.HISTORY))

        val backup = exportAndRead()

        assertEquals(listOf("a", "b"), backup.stations!!.map { it.stationUuid })
        assertTrue(backup.stations!!.all { it.tracks == null && it.starredTracks == null })
    }

    @Test
    fun perStationPart_onlyChangesThatStation() {
        history.history["b"] = listOf(TrackHistoryEntity(stationUuid = "b", trackTitle = "Three", timestamp = 3L))
        loadAndWait()

        viewModel.setPart("a", ImportPart.HISTORY, false)
        assertEquals(ToggleableState.Indeterminate, choosing.partState(ImportPart.HISTORY))

        val backup = exportAndRead()
        val byUuid = backup.stations!!.associateBy { it.stationUuid }
        assertNull(byUuid.getValue("a").tracks)
        assertEquals(1, byUuid.getValue("b").tracks!!.size)
        assertEquals(1, byUuid.getValue("b").starredTracks!!.size)
    }

    @Test
    fun unpickedStation_locksItsParts_andKeepsThemForLater() {
        loadAndWait()

        viewModel.toggleStation("a")
        assertFalse(choosing.stations[0].canChoose(ImportPart.HISTORY))
        viewModel.setPart("a", ImportPart.HISTORY, false)
        assertTrue(ImportPart.HISTORY in choosing.stations[0].selected)

        viewModel.toggleStation("a")
        val backup = exportAndRead()
        assertEquals(2, backup.stations!!.first { it.stationUuid == "a" }.tracks!!.size)
    }

    @Test
    fun export_reportsFailure_whenFileCannotBeOpened() = runBlocking {
        loadAndWait()
        files.canOpen = false

        viewModel.export(fileUri)
        withTimeout(5_000) { viewModel.uiState.first { it == ExportUiState.Idle } }

        assertEquals(ExportResult.Failed(reason = null), viewModel.result.value)
    }

    @Test
    fun cancel_writesNothing() {
        loadAndWait()

        viewModel.cancel()
        viewModel.export(fileUri)

        assertEquals(ExportUiState.Idle, viewModel.uiState.value)
        assertEquals(0, files.written.size())
        assertNull(viewModel.result.value)
    }
}
