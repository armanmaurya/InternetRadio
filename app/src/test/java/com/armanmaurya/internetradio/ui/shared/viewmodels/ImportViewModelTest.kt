package com.armanmaurya.internetradio.ui.shared.viewmodels

import android.content.ContextWrapper
import androidx.compose.ui.state.ToggleableState
import com.armanmaurya.internetradio.data.backup.BackupStarredTrack
import com.armanmaurya.internetradio.data.backup.BackupStation
import com.armanmaurya.internetradio.data.backup.BackupTrack
import com.armanmaurya.internetradio.data.local.entity.StationEntity
import com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity
import com.armanmaurya.internetradio.data.repository.FakeSettingsRepository
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

// Robolectric so the ViewModel's android.util.Log calls work
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ImportViewModelTest {

    private lateinit var library: FakeLibraryRepository
    private lateinit var history: RecordingTrackHistoryRepository
    private lateinit var settings: FakeSettingsRepository
    private lateinit var transactions: DirectTransactionRunner
    private lateinit var viewModel: ImportViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        library = FakeLibraryRepository()
        history = RecordingTrackHistoryRepository()
        settings = FakeSettingsRepository()
        transactions = DirectTransactionRunner()
        viewModel = ImportViewModel(library, history, settings, transactions, ContextWrapper(null))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val review get() = viewModel.uiState.value as ImportUiState.Review

    private fun station(uuid: String, plays: Int = 0, starred: Int = 0, name: String = "Station $uuid") = BackupStation(
        stationUuid = uuid,
        name = name,
        url = "https://$uuid.example",
        tracks = (1..plays).map { BackupTrack(trackTitle = "Song $it", timestamp = it * 1000L) }.ifEmpty { null },
        starredTracks = (1..starred).map { BackupStarredTrack(trackTitle = "Star $it") }.ifEmpty { null }
    )

    /** Imports, waits for the screen to close, and returns the result Settings would show. */
    private fun runImport(): ImportEvent? = runBlocking {
        viewModel.import()
        withTimeout(5_000) { viewModel.uiState.first { it == ImportUiState.Idle } }
        viewModel.result.value
    }

    private fun mine(uuid: String, name: String = "Mine", order: Int = 0) =
        StationEntity(stationUuid = uuid, name = name, url = "https://$uuid.mine", orderIndex = order)

    @Test
    fun newStations_comeInWhole_existingOnesKeepTheirDetails() = runBlocking {
        library.entities["old"] = StationEntity(stationUuid = "old", name = "Mine", url = "https://mine")

        viewModel.showReview(listOf(station("new", plays = 2, starred = 1), station("old", plays = 3, starred = 1)))

        val new = review.newStations.single()
        assertEquals(setOf(ImportPart.STATION, ImportPart.HISTORY, ImportPart.STARRED), new.selected)
        assertEquals(ToggleableState.On, new.state)

        val existing = review.existingStations.single()
        assertEquals(setOf(ImportPart.HISTORY, ImportPart.STARRED), existing.selected)
        assertEquals(ToggleableState.Indeterminate, existing.state)
        assertEquals(2, review.selectedCount)
    }

    @Test
    fun existingStation_withNothingElse_startsUnticked() = runBlocking {
        library.entities["old"] = StationEntity(stationUuid = "old", name = "Mine", url = "https://mine")

        viewModel.showReview(listOf(station("old")))

        assertEquals(ToggleableState.Off, review.existingStations.single().state)
        assertEquals(0, review.selectedCount)
    }

    @Test
    fun partsTheFileDoesNotHave_areNotOffered() = runBlocking {
        viewModel.showReview(listOf(station("a", plays = 2)))

        val item = review.newStations.single()
        assertEquals(listOf(ImportPart.STATION, ImportPart.HISTORY), item.availableParts)
        assertTrue(review.hasHistory)
        assertFalse(review.hasStarred)

        viewModel.setPart("a", ImportPart.STARRED, true)
        assertFalse(ImportPart.STARRED in review.newStations.single().selected)
    }

    @Test
    fun toggleStation_switchesBetweenAllAndNothing() = runBlocking {
        library.entities["old"] = StationEntity(stationUuid = "old", name = "Mine", url = "https://mine")
        viewModel.showReview(listOf(station("old", plays = 1)))

        viewModel.toggleStation("old") // partly ticked -> everything
        assertEquals(ToggleableState.On, review.existingStations.single().state)

        viewModel.toggleStation("old") // everything -> nothing
        assertEquals(ToggleableState.Off, review.existingStations.single().state)
    }

    @Test
    fun toggleSection_affectsOnlyThatSection() = runBlocking {
        library.entities["old"] = StationEntity(stationUuid = "old", name = "Mine", url = "https://mine")
        viewModel.showReview(listOf(station("new"), station("old")))

        viewModel.toggleSection(existing = false)
        assertEquals(ToggleableState.Off, review.sectionState(existing = false))
        assertEquals(ToggleableState.Off, review.sectionState(existing = true))

        viewModel.toggleSection(existing = true)
        assertEquals(ToggleableState.On, review.sectionState(existing = true))
        assertEquals(ToggleableState.Off, review.sectionState(existing = false))
    }

    @Test
    fun togglePartForAll_leavesOtherPartsAlone() = runBlocking {
        viewModel.showReview(listOf(station("a", plays = 1, starred = 1), station("b", plays = 1)))

        viewModel.togglePartForAll(ImportPart.HISTORY)

        assertEquals(ToggleableState.Off, review.partState(ImportPart.HISTORY))
        assertEquals(ToggleableState.On, review.partState(ImportPart.STARRED))
        assertTrue(review.stations.all { ImportPart.STATION in it.selected })

        viewModel.setPart("a", ImportPart.HISTORY, true)
        assertEquals(ToggleableState.Indeterminate, review.partState(ImportPart.HISTORY))
    }

    @Test
    fun historyLimit_isOnlyExceededByChosenHistory() = runBlocking {
        settings.prefsFlow.value = settings.prefsFlow.value.copy(trackHistoryLimit = 3)
        history.history["a"] = listOf(TrackHistoryEntity(stationUuid = "a", trackTitle = "Local", timestamp = 99_000L))

        viewModel.showReview(listOf(station("a", plays = 3), station("b", plays = 2)))

        assertEquals(4, review.playsNeeded) // 3 from the file + 1 local play
        assertTrue(review.exceedsHistoryLimit)

        viewModel.setPart("a", ImportPart.HISTORY, false)
        assertFalse(review.exceedsHistoryLimit)
    }

    @Test
    fun sameStationInSeveralFiles_isMergedIntoOneRow() = runBlocking {
        viewModel.showReview(
            listOf(
                station("a", plays = 2, name = "Old name"),
                station("a", plays = 3, starred = 1, name = "New name")
            ),
            failedFiles = 1
        )

        val item = review.newStations.single()
        assertEquals("New name", item.name)
        assertEquals(3, item.playCount) // timestamps 1000..3000, the 2 shared ones counted once
        assertEquals(1, item.starredCount)
        assertEquals(1, review.failedFiles)
    }

    @Test
    fun import_savesOnlyTheChosenParts() {
        library.entities["old"] = StationEntity(stationUuid = "old", name = "Mine", url = "https://mine")
        runBlocking {
            viewModel.showReview(
                listOf(station("new", plays = 2, starred = 1), station("old", plays = 1, starred = 2), station("skip", plays = 1))
            )
        }
        viewModel.setPart("new", ImportPart.STARRED, false)
        viewModel.toggleStation("skip")

        val event = runImport()

        assertEquals(ImportEvent.Finished(added = 1, updated = 1, failedFiles = 0), event)
        assertEquals("Mine", library.entities["old"]?.name) // details of an existing station kept
        assertEquals("Station new", library.entities["new"]?.name)
        assertNull(library.entities["skip"])
        assertEquals(mapOf("new" to 2, "old" to 1), history.importedPlays)
        assertEquals(mapOf("old" to 2), history.importedStars)
        assertEquals(ImportUiState.Idle, viewModel.uiState.value)
        assertEquals(1, transactions.runs) // everything saved in one transaction
    }

    @Test
    fun import_replacesExistingDetails_whenStationPartIsTicked() {
        library.entities["old"] = StationEntity(stationUuid = "old", name = "Mine", url = "https://mine")
        runBlocking { viewModel.showReview(listOf(station("old", name = "From file"))) }

        viewModel.setPart("old", ImportPart.STATION, true)
        runImport()

        assertEquals("From file", library.entities["old"]?.name)
    }

    @Test
    fun import_raisesHistoryLimit_onlyWhenAsked() {
        settings.prefsFlow.value = settings.prefsFlow.value.copy(trackHistoryLimit = 2)
        runBlocking { viewModel.showReview(listOf(station("a", plays = 5))) }

        viewModel.setKeepAllPlays(true)
        runImport()

        assertEquals(5, settings.prefsFlow.value.trackHistoryLimit)
    }

    @Test
    fun import_keepsHistoryLimit_byDefault() {
        settings.prefsFlow.value = settings.prefsFlow.value.copy(trackHistoryLimit = 2)
        runBlocking { viewModel.showReview(listOf(station("a", plays = 5))) }

        runImport()

        assertEquals(2, settings.prefsFlow.value.trackHistoryLimit)
    }

    @Test
    fun cancel_savesNothing() = runBlocking {
        viewModel.showReview(listOf(station("a", plays = 1)))

        viewModel.cancel()
        viewModel.import()

        assertEquals(ImportUiState.Idle, viewModel.uiState.value)
        assertTrue(library.entities.isEmpty())
        assertTrue(history.importedPlays.isEmpty())
    }

    // ── Replacing keeps the user's own data (1) ──────────────────────────────────

    @Test
    fun replacingStation_keepsOrderAndDates() {
        library.entities["old"] = mine("old", order = 7).copy(addedAt = 111L, lastPlayedAt = 222L)
        runBlocking { viewModel.showReview(listOf(station("old", name = "From file"))) }

        viewModel.setPart("old", ImportPart.STATION, true)
        runImport()

        val saved = library.entities.getValue("old")
        assertEquals("From file", saved.name)
        assertEquals(7, saved.orderIndex)
        assertEquals(111L, saved.addedAt)
        assertEquals(222L, saved.lastPlayedAt)
    }

    // ── New stations go after the user's own (2) ─────────────────────────────────

    @Test
    fun newStations_goAfterTheLibrary_inListedOrder() {
        library.entities["mine1"] = mine("mine1", order = 0)
        library.entities["mine2"] = mine("mine2", order = 4)
        runBlocking { viewModel.showReview(listOf(station("b", name = "Bravo"), station("a", name = "Alpha"))) }

        runImport()

        assertEquals(5, library.entities.getValue("a").orderIndex) // listed A to Z
        assertEquals(6, library.entities.getValue("b").orderIndex)
        assertEquals(4, library.entities.getValue("mine2").orderIndex)
    }

    // ── History and stars need the station when it is new (3) ────────────────────

    @Test
    fun newStation_leftOut_takesItsHistoryAndStarsWithIt() = runBlocking {
        viewModel.showReview(listOf(station("a", plays = 2, starred = 1)))

        viewModel.setPart("a", ImportPart.STATION, false)
        assertEquals(emptySet<ImportPart>(), review.newStations.single().selected)

        viewModel.setPart("a", ImportPart.HISTORY, true)
        assertEquals(emptySet<ImportPart>(), review.newStations.single().selected)
        assertFalse(review.newStations.single().canChoose(ImportPart.HISTORY))
    }

    @Test
    fun partForAll_skipsNewStationsThatAreLeftOut() = runBlocking {
        viewModel.showReview(listOf(station("a", plays = 1), station("b", plays = 1)))
        viewModel.toggleStation("b") // leave b out
        viewModel.togglePartForAll(ImportPart.HISTORY) // history off for a

        viewModel.togglePartForAll(ImportPart.HISTORY) // and back on: only a can take it

        assertEquals(ToggleableState.On, review.partState(ImportPart.HISTORY))
        assertEquals(emptySet<ImportPart>(), review.newStations.first { it.stationUuid == "b" }.selected)
    }

    @Test
    fun existingStation_canTakeHistory_withoutReplacingIt() = runBlocking {
        library.entities["old"] = mine("old")
        viewModel.showReview(listOf(station("old", plays = 2)))

        assertTrue(review.existingStations.single().canChoose(ImportPart.HISTORY))
        assertEquals(setOf(ImportPart.HISTORY), review.existingStations.single().selected)
    }

    // ── All or nothing (4) ───────────────────────────────────────────────────────

    @Test
    fun failedImport_reportsFailure_andPutsTheLimitBack() {
        settings.prefsFlow.value = settings.prefsFlow.value.copy(trackHistoryLimit = 2)
        runBlocking { viewModel.showReview(listOf(station("a", plays = 5))) }
        viewModel.setKeepAllPlays(true)
        history.failImports = true

        val result = runImport()

        assertEquals(ImportEvent.Failed, result)
        assertEquals(2, settings.prefsFlow.value.trackHistoryLimit)
    }
}
