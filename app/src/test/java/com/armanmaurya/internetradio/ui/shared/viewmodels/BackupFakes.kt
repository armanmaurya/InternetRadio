package com.armanmaurya.internetradio.ui.shared.viewmodels

import android.net.Uri
import android.os.ParcelFileDescriptor
import com.armanmaurya.internetradio.core.system.FileSystemFacade
import com.armanmaurya.internetradio.core.system.SystemFacade
import com.armanmaurya.internetradio.data.local.entity.StarredTrackEntity
import com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity
import com.armanmaurya.internetradio.domain.repository.TrackHistoryRepository
import com.armanmaurya.internetradio.domain.repository.TransactionRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/** Keeps history and stars per station in memory; imports add to them. */
internal class RecordingTrackHistoryRepository : TrackHistoryRepository {
    val history = mutableMapOf<String, List<TrackHistoryEntity>>()
    val starred = mutableMapOf<String, List<StarredTrackEntity>>()
    val importedPlays = mutableMapOf<String, Int>()
    val importedStars = mutableMapOf<String, Int>()

    /** Set to make the next import throw, as a failed database write would. */
    var failImports = false

    override suspend fun importTracks(stationUuid: String, tracks: List<TrackHistoryEntity>) {
        if (failImports) throw IllegalStateException("disk full")
        importedPlays[stationUuid] = tracks.size
        history[stationUuid] = history[stationUuid].orEmpty() + tracks
    }

    override suspend fun importStarredTracks(stationUuid: String, tracks: List<StarredTrackEntity>) {
        if (failImports) throw IllegalStateException("disk full")
        importedStars[stationUuid] = tracks.size
        starred[stationUuid] = starred[stationUuid].orEmpty() + tracks
    }

    override suspend fun getTrackTimestamps(stationUuid: String): List<Long> = history[stationUuid].orEmpty().map { it.timestamp }

    override suspend fun countTracksPerStation(): Map<String, Int> =
        history.mapValues { it.value.size }.filterValues { it > 0 }

    override suspend fun countStarredTracksPerStation(): Map<String, Int> =
        starred.mapValues { it.value.size }.filterValues { it > 0 }

    override suspend fun getAllTracksForStation(stationUuid: String): List<TrackHistoryEntity> = history[stationUuid].orEmpty()
    override suspend fun getAllStarredTracksForStation(stationUuid: String): List<StarredTrackEntity> = starred[stationUuid].orEmpty()

    override suspend fun logTrack(stationUuid: String, trackTitle: String, rawTrackTitle: String): Long? = null
    override suspend fun updateTrackMetadata(id: Long, newTrackTitle: String, coverArtUrl: String?) {}
    override fun getTrackHistory(stationUuid: String, limit: Int): Flow<List<TrackHistoryEntity>> = flowOf(emptyList())
    override suspend fun updateCoverArt(stationUuid: String, trackTitle: String, coverArtUrl: String) {}
    override fun getStarredTracks(stationUuid: String): Flow<List<TrackHistoryEntity>> = flowOf(emptyList())
    override suspend fun toggleTrackStarred(stationUuid: String, trackTitle: String, isStarred: Boolean) {}
    override suspend fun setTrackStarredById(id: Long, isStarred: Boolean) {}
    override suspend fun isTrackStarred(stationUuid: String, trackTitle: String): Boolean = false
    override suspend fun recordTrackListeningProgress(id: Long, endTimestamp: Long, addedDurationMs: Long) {}
}

/** Runs the block directly; the fakes have nothing to roll back. */
internal class DirectTransactionRunner : TransactionRunner {
    var runs = 0
    override suspend fun <T> run(block: suspend () -> T): T {
        runs++
        return block()
    }
}

internal class FakeSystemFacade : SystemFacade {
    override fun getAppVersionName(): String = "1.0-test"
    override fun getAppVersionCode(): Long = 1
}

/** Captures what is written to any output stream; [canOpen] false acts like an unwritable file. */
internal class FakeFileSystemFacade : FileSystemFacade {
    val written = ByteArrayOutputStream()
    var canOpen = true

    override suspend fun openOutputStream(fileUri: Uri): OutputStream? = if (canOpen) written else null

    override suspend fun createBackupFile(dirUri: Uri, fileName: String): OutputStream? = null
    override suspend fun createAudioRecordingFile(folderName: String, fileName: String, mimeType: String): Pair<Uri, ParcelFileDescriptor>? = null
    override suspend fun finalizeAudioRecordingFile(fileUri: Uri, newFileName: String) {}
    override suspend fun deleteAudioRecording(fileUri: Uri): Boolean = false
    override suspend fun deleteAudioRecordingFolder(folderName: String): Boolean = false
    override suspend fun openInputStream(fileUri: Uri): InputStream? = null
}
