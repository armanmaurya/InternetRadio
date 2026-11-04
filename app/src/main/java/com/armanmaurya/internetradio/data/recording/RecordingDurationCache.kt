package com.armanmaurya.internetradio.data.recording

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

data class RecordingDurationEntry(
    val lastModified: Long = 0L,
    val sizeBytes: Long = 0L,
    val durationMs: Long = 0L
)

/**
 * Remembers recording durations across app starts, because reading one means opening the file
 * with MediaMetadataRetriever. An entry only counts while the file's date and size still match.
 */
@Singleton
class RecordingDurationCache internal constructor(private val cacheFile: File) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.cacheDir, CACHE_FILE_NAME))

    private val gson = Gson()
    private val entries: ConcurrentHashMap<String, RecordingDurationEntry> by lazy { readEntries() }
    private val hasUnsavedChanges = AtomicBoolean(false)

    fun get(key: String, lastModified: Long, sizeBytes: Long): Long? =
        entries[key]
            ?.takeIf { it.lastModified == lastModified && it.sizeBytes == sizeBytes }
            ?.durationMs

    fun put(key: String, lastModified: Long, sizeBytes: Long, durationMs: Long) {
        entries[key] = RecordingDurationEntry(lastModified, sizeBytes, durationMs)
        hasUnsavedChanges.set(true)
    }

    fun remove(key: String) {
        if (entries.remove(key) != null) hasUnsavedChanges.set(true)
    }

    fun removeStation(stationFolderName: String) {
        if (entries.keys.removeAll { it.startsWith("$stationFolderName/") }) {
            hasUnsavedChanges.set(true)
        }
    }

    fun retainOnly(keys: Set<String>) {
        if (entries.keys.retainAll(keys)) hasUnsavedChanges.set(true)
    }

    @Synchronized
    fun saveIfChanged() {
        if (!hasUnsavedChanges.getAndSet(false)) return
        // A zero means the file couldn't be read, so leave it to be retried next session
        val readable = entries.filterValues { it.durationMs > 0L }
        val saved = runCatching {
            val tempFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
            tempFile.writeText(gson.toJson(readable))
            check(tempFile.renameTo(cacheFile)) { "Could not replace ${cacheFile.name}" }
        }.isSuccess
        if (!saved) hasUnsavedChanges.set(true)
    }

    private fun readEntries(): ConcurrentHashMap<String, RecordingDurationEntry> {
        val type = TypeToken.getParameterized(
            Map::class.java,
            String::class.java,
            RecordingDurationEntry::class.java
        ).type
        val saved = runCatching {
            if (cacheFile.exists()) {
                gson.fromJson<Map<String, RecordingDurationEntry?>>(cacheFile.readText(), type)
            } else {
                null
            }
        }.getOrNull().orEmpty()

        return ConcurrentHashMap<String, RecordingDurationEntry>().apply {
            saved.forEach { (key, entry) -> if (entry != null) put(key, entry) }
        }
    }

    private companion object {
        const val CACHE_FILE_NAME = "recording_durations.json"
    }
}
