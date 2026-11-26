package com.armanmaurya.internetradio.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.armanmaurya.internetradio.domain.model.EqualizerConfig
import com.armanmaurya.internetradio.domain.model.EqualizerPresets
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class EqualizerRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `band updates switch preset to custom, clamp values, and persist across restarts`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repoScope = TestScope(dispatcher)
        val storeFile = tempFolder.newFile("equalizer.preferences_pb")

        val dataStore = PreferenceDataStoreFactory.create(
            scope = repoScope,
            produceFile = { storeFile }
        )

        val repo = EqualizerRepositoryImpl(dataStore, repoScope)
        advanceUntilIdle()

        assertFalse(repo.equalizerConfig.value.enabled)
        assertEquals(EqualizerPresets.FLAT_ID, repo.equalizerConfig.value.presetId)

        repo.setEnabled(true)
        repo.selectPreset(EqualizerPresets.ROCK_ID)
        assertEquals(EqualizerPresets.ROCK_ID, repo.equalizerConfig.value.presetId)

        // Dragging band clamps out-of-range gain and moves preset to Custom immediately in memory
        repo.updateBandGain(2, 25f)
        assertEquals(EqualizerPresets.CUSTOM_PRESET_ID, repo.equalizerConfig.value.presetId)
        assertEquals(EqualizerConfig.MAX_GAIN_DB, repo.equalizerConfig.value.bandGainsDb[2], 0.001f)

        repo.updatePreamp(-20f)
        assertEquals(EqualizerConfig.MIN_PREAMP_DB, repo.equalizerConfig.value.preampDb, 0.001f)

        advanceTimeBy(300L)
        advanceUntilIdle()

        // Reload with a second repository instance backed by the same file
        val reloaded = EqualizerRepositoryImpl(dataStore, repoScope)
        advanceUntilIdle()

        assertTrue(reloaded.equalizerConfig.value.enabled)
        assertEquals(EqualizerPresets.CUSTOM_PRESET_ID, reloaded.equalizerConfig.value.presetId)
        assertEquals(EqualizerConfig.MAX_GAIN_DB, reloaded.equalizerConfig.value.bandGainsDb[2], 0.001f)
        assertEquals(EqualizerConfig.MIN_PREAMP_DB, reloaded.equalizerConfig.value.preampDb, 0.001f)

        reloaded.reset()
        advanceUntilIdle()
        assertTrue(reloaded.equalizerConfig.value.enabled)
        assertTrue(reloaded.equalizerConfig.value.isFlat)
        assertEquals(EqualizerPresets.FLAT_ID, reloaded.equalizerConfig.value.presetId)
    }

    @Test
    fun `flush persists pending debounced band changes immediately`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repoScope = TestScope(dispatcher)
        val storeFile = tempFolder.newFile("equalizer_flush.preferences_pb")

        val dataStore = PreferenceDataStoreFactory.create(
            scope = repoScope,
            produceFile = { storeFile }
        )

        val repo = EqualizerRepositoryImpl(dataStore, repoScope)
        advanceUntilIdle()

        repo.setEnabled(true)
        repo.updateBandGain(5, 4.5f)
        repo.flush()
        advanceUntilIdle()

        val reloaded = EqualizerRepositoryImpl(dataStore, repoScope)
        advanceUntilIdle()

        assertEquals(4.5f, reloaded.equalizerConfig.value.bandGainsDb[5], 0.001f)
    }
}
