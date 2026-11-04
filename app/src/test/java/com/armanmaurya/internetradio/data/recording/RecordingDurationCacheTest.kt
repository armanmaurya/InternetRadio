package com.armanmaurya.internetradio.data.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RecordingDurationCacheTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val cacheFile: File
        get() = File(tempFolder.root, "durations.json")

    @Test
    fun saveIfChanged_durationIsAvailableAfterRestart() {
        RecordingDurationCache(cacheFile).apply {
            put("Rock FM/a.mp3", 100L, 200L, 3_000L)
            saveIfChanged()
        }

        assertEquals(3_000L, RecordingDurationCache(cacheFile).get("Rock FM/a.mp3", 100L, 200L))
    }

    @Test
    fun get_changedFile_missesCache() {
        val cache = RecordingDurationCache(cacheFile)
        cache.put("Rock FM/a.mp3", 100L, 200L, 3_000L)

        assertNull(cache.get("Rock FM/a.mp3", 101L, 200L))
        assertNull(cache.get("Rock FM/a.mp3", 100L, 201L))
    }

    @Test
    fun zeroDuration_isKeptForSessionButNotSaved() {
        val cache = RecordingDurationCache(cacheFile)
        cache.put("Rock FM/broken.mp3", 100L, 200L, 0L)
        cache.saveIfChanged()

        assertEquals(0L, cache.get("Rock FM/broken.mp3", 100L, 200L))
        assertNull(RecordingDurationCache(cacheFile).get("Rock FM/broken.mp3", 100L, 200L))
    }

    @Test
    fun corruptFile_startsEmptyAndCanBeSavedAgain() {
        cacheFile.writeText("{not json")

        val cache = RecordingDurationCache(cacheFile)
        assertNull(cache.get("Rock FM/a.mp3", 100L, 200L))

        cache.put("Rock FM/a.mp3", 100L, 200L, 3_000L)
        cache.saveIfChanged()
        assertEquals(3_000L, RecordingDurationCache(cacheFile).get("Rock FM/a.mp3", 100L, 200L))
    }

    @Test
    fun retainOnly_dropsOtherEntriesFromSavedFile() {
        RecordingDurationCache(cacheFile).apply {
            put("Rock FM/a.mp3", 100L, 200L, 3_000L)
            put("Rock FM/b.mp3", 100L, 200L, 4_000L)
            retainOnly(setOf("Rock FM/b.mp3"))
            saveIfChanged()
        }

        val reloaded = RecordingDurationCache(cacheFile)
        assertNull(reloaded.get("Rock FM/a.mp3", 100L, 200L))
        assertEquals(4_000L, reloaded.get("Rock FM/b.mp3", 100L, 200L))
    }

    @Test
    fun removeStation_leavesOtherStationsAndSimilarNames() {
        val cache = RecordingDurationCache(cacheFile)
        cache.put("Rock/a.mp3", 100L, 200L, 3_000L)
        cache.put("Rock FM/a.mp3", 100L, 200L, 4_000L)

        cache.removeStation("Rock")

        assertNull(cache.get("Rock/a.mp3", 100L, 200L))
        assertEquals(4_000L, cache.get("Rock FM/a.mp3", 100L, 200L))
    }

    @Test
    fun saveIfChanged_withoutChanges_writesNothing() {
        RecordingDurationCache(cacheFile).saveIfChanged()

        assertFalse(cacheFile.exists())
    }
}
