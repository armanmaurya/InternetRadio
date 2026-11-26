package com.armanmaurya.internetradio.data.repository

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.armanmaurya.internetradio.domain.model.EqualizerConfig
import com.armanmaurya.internetradio.domain.model.EqualizerPresets
import com.armanmaurya.internetradio.domain.repository.EqualizerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.equalizerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "equalizer",
    corruptionHandler = ReplaceFileCorruptionHandler(
        produceNewData = { emptyPreferences() }
    )
)

@Singleton
class EqualizerRepositoryImpl @VisibleForTesting internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope
) : EqualizerRepository {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        dataStore = context.equalizerDataStore,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    )

    private object Keys {
        val ENABLED = booleanPreferencesKey("eq_enabled")
        val PRESET_ID = stringPreferencesKey("eq_preset_id")
        val BAND_GAINS = stringPreferencesKey("eq_band_gains")
        val PREAMP_DB = floatPreferencesKey("eq_preamp_db")
    }

    private val _equalizerConfig = MutableStateFlow(EqualizerConfig())
    override val equalizerConfig: StateFlow<EqualizerConfig> = _equalizerConfig.asStateFlow()

    private val persistMutex = Mutex()
    private var debounceJob: Job? = null

    @Volatile
    private var hasUserMutation = false

    init {
        scope.launch {
            val prefs = dataStore.data
                .catch { exception ->
                    if (exception is IOException) emit(emptyPreferences())
                    else throw exception
                }
                .first()
            if (!hasUserMutation) {
                _equalizerConfig.value = decode(prefs)
            }
        }
    }

    override fun setEnabled(enabled: Boolean) {
        mutate(immediate = true) { it.copy(enabled = enabled) }
    }

    override fun selectPreset(presetId: String) {
        val preset = EqualizerPresets.findById(presetId) ?: return
        mutate(immediate = true) {
            it.copy(
                presetId = preset.id,
                bandGainsDb = preset.bandGainsDb,
                preampDb = preset.preampDb
            )
        }
    }

    override fun updateBandGain(bandIndex: Int, gainDb: Float) {
        if (bandIndex !in 0 until EqualizerPresets.BAND_COUNT) return
        mutate(immediate = false) { current ->
            val updatedBands = current.bandGainsDb.toMutableList()
            while (updatedBands.size < EqualizerPresets.BAND_COUNT) updatedBands.add(0f)
            updatedBands[bandIndex] = gainDb
            current.copy(
                presetId = EqualizerPresets.CUSTOM_PRESET_ID,
                bandGainsDb = updatedBands
            )
        }
    }

    override fun updatePreamp(preampDb: Float) {
        mutate(immediate = false) { current ->
            current.copy(
                presetId = EqualizerPresets.CUSTOM_PRESET_ID,
                preampDb = preampDb
            )
        }
    }

    override fun reset() {
        mutate(immediate = true) { current ->
            EqualizerConfig(enabled = current.enabled)
        }
    }

    override suspend fun flush() {
        debounceJob?.cancel()
        debounceJob = null
        persist(_equalizerConfig.value)
    }

    private fun mutate(immediate: Boolean, transform: (EqualizerConfig) -> EqualizerConfig) {
        hasUserMutation = true
        val updated = _equalizerConfig.updateAndGetSanitized(transform)
        debounceJob?.cancel()
        debounceJob = scope.launch {
            if (!immediate) {
                delay(PERSIST_DEBOUNCE_MS)
            }
            persist(updated)
        }
    }

    private inline fun MutableStateFlow<EqualizerConfig>.updateAndGetSanitized(
        transform: (EqualizerConfig) -> EqualizerConfig
    ): EqualizerConfig {
        while (true) {
            val prev = value
            val next = transform(prev).sanitized()
            if (compareAndSet(prev, next)) return next
        }
    }

    private suspend fun persist(config: EqualizerConfig) {
        persistMutex.withLock {
            dataStore.edit { prefs ->
                prefs[Keys.ENABLED] = config.enabled
                prefs[Keys.PRESET_ID] = config.presetId
                prefs[Keys.BAND_GAINS] = config.bandGainsDb.joinToString(",")
                prefs[Keys.PREAMP_DB] = config.preampDb
            }
        }
    }

    private fun decode(prefs: Preferences): EqualizerConfig {
        val enabled = prefs[Keys.ENABLED] ?: false
        val presetId = prefs[Keys.PRESET_ID] ?: EqualizerPresets.FLAT_ID
        val rawBands = prefs[Keys.BAND_GAINS]
            ?.split(",")
            ?.mapNotNull { it.trim().toFloatOrNull() }
            ?: List(EqualizerPresets.BAND_COUNT) { 0f }
        val preamp = prefs[Keys.PREAMP_DB] ?: 0f

        return EqualizerConfig(
            enabled = enabled,
            presetId = presetId,
            bandGainsDb = rawBands,
            preampDb = preamp
        ).sanitized()
    }

    companion object {
        private const val PERSIST_DEBOUNCE_MS = 250L
    }
}
