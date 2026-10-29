package com.armanmaurya.internetradio.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.asSharedFlow
import android.media.MediaMetadataRetriever
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import com.armanmaurya.internetradio.data.recording.RecordingFolderAccess
import com.armanmaurya.internetradio.data.recording.RecordingSidecarStore
import com.armanmaurya.internetradio.domain.model.RecordingFile
import com.armanmaurya.internetradio.domain.model.RecordingFolder
import com.armanmaurya.internetradio.domain.repository.RecordingRepository

@Singleton
class RecordingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileSystemFacade: com.armanmaurya.internetradio.core.system.FileSystemFacade,
    private val sidecarStore: RecordingSidecarStore,
    private val folderAccess: RecordingFolderAccess
) : RecordingRepository {

    private data class FileCacheKey(val lastModified: Long, val sizeBytes: Long)
    private val durationCache = ConcurrentHashMap<String, Pair<FileCacheKey, Long>>()

    private val recordingsRoot: File
        get() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "InternetRadio")

    private val supportedExtensions = listOf(".mp3", ".aac", ".m4a", ".ogg", ".flac", ".wav", ".opus", ".ts")

    private fun isRecordingFileName(name: String): Boolean =
        !name.startsWith(".") && supportedExtensions.any { ext -> name.endsWith(ext, ignoreCase = true) }

    private fun sanitizeFolderName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()

    // Keyed by station folder + file name so the cache survives switching between file and SAF URIs
    private fun durationCacheKey(stationFolderName: String, fileName: String): String =
        "$stationFolderName/$fileName"

    private fun getAudioDurationMs(
        cacheKey: String,
        lastModified: Long,
        sizeBytes: Long,
        setDataSource: (MediaMetadataRetriever) -> Unit
    ): Long {
        val currentKey = FileCacheKey(lastModified, sizeBytes)
        val cached = durationCache[cacheKey]
        if (cached != null && cached.first == currentKey) {
            return cached.second
        }

        var durationMs = 0L
        val retriever = MediaMetadataRetriever()
        try {
            setDataSource(retriever)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            // Fallback gracefully to 0L for unreadable or partially recorded files
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore release errors
            }
        }

        durationCache[cacheKey] = Pair(currentKey, durationMs)
        return durationMs
    }

    override suspend fun getRecordingFolders(): List<RecordingFolder> = withContext(Dispatchers.IO) {
        val treeUri = folderAccess.grantedTreeUri()
        treeUri?.let(sidecarStore::migrateAppSidecars)
        val stationNames = if (treeUri != null) {
            folderAccess.listStationFolders(treeUri).map { it.name }
        } else {
            recordingsRoot.listFiles { file -> file.isDirectory }?.map { it.name }.orEmpty()
        }

        stationNames
            .mapNotNull { stationName ->
                val recordings = listRecordings(stationName, stationName, treeUri)
                if (recordings.isEmpty()) null else RecordingFolder(stationName = stationName, recordings = recordings)
            }
            .sortedBy { it.stationName }
    }

    override suspend fun getRecordingsForStation(stationName: String): List<RecordingFile> = withContext(Dispatchers.IO) {
        val treeUri = folderAccess.grantedTreeUri()
        treeUri?.let(sidecarStore::migrateAppSidecars)
        listRecordings(sanitizeFolderName(stationName), stationName, treeUri)
    }

    private fun listRecordings(stationFolderName: String, stationName: String, treeUri: Uri?): List<RecordingFile> {
        val recordings = if (treeUri != null) {
            listRecordingsFromTree(treeUri, stationFolderName, stationName)
        } else {
            listRecordingsFromFiles(stationFolderName, stationName)
        }
        return recordings.sortedByDescending { it.lastModified }
    }

    private fun listRecordingsFromTree(treeUri: Uri, stationFolderName: String, stationName: String): List<RecordingFile> {
        val stationFolder = folderAccess.findStationFolder(treeUri, stationFolderName) ?: return emptyList()
        val children = folderAccess.listChildren(treeUri, stationFolder.documentId).filter { !it.isDirectory }
        val childrenByName = children.associateBy { it.name }

        return children.filter { isRecordingFileName(it.name) }.map { entry ->
            val sidecar = childrenByName[sidecarStore.sidecarFileName(entry.name)]
                ?.let { sidecarStore.readSidecar(it.uri) }
            RecordingFile(
                fileName = entry.name,
                file = File(File(recordingsRoot, stationFolderName), entry.name),
                uri = entry.uri,
                lastModified = entry.lastModified,
                sizeBytes = entry.sizeBytes,
                durationMs = getAudioDurationMs(
                    durationCacheKey(stationFolderName, entry.name),
                    entry.lastModified,
                    entry.sizeBytes
                ) { it.setDataSource(context, entry.uri) },
                stationName = stationName,
                tracks = sidecar?.tracks.orEmpty(),
                note = sidecar?.note
            )
        }
    }

    private fun listRecordingsFromFiles(stationFolderName: String, stationName: String): List<RecordingFile> {
        val stationDir = File(recordingsRoot, stationFolderName)
        if (!stationDir.isDirectory) return emptyList()
        val files = stationDir.listFiles { file -> file.isFile && isRecordingFileName(file.name) } ?: emptyArray()

        return files.map {
            val sidecar = sidecarStore.readSidecar(stationFolderName, it.name)
            RecordingFile(
                fileName = it.name,
                file = it,
                uri = Uri.fromFile(it),
                lastModified = it.lastModified(),
                sizeBytes = it.length(),
                durationMs = getAudioDurationMs(
                    durationCacheKey(stationFolderName, it.name),
                    it.lastModified(),
                    it.length()
                ) { retriever -> retriever.setDataSource(it.absolutePath) },
                stationName = stationName,
                tracks = sidecar?.tracks.orEmpty(),
                note = sidecar?.note
            )
        }
    }

    private val _recordingsChangedEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val recordingsChangedEvent: kotlinx.coroutines.flow.SharedFlow<Unit> = _recordingsChangedEvent.asSharedFlow()

    override fun notifyRecordingsChanged() {
        _recordingsChangedEvent.tryEmit(Unit)
    }

    override suspend fun deleteRecording(recording: RecordingFile): Boolean = withContext(Dispatchers.IO) {
        val deleted = deleteRecordingWithSidecar(recording)
        if (deleted) {
            notifyRecordingsChanged()
        }
        deleted
    }

    override suspend fun deleteRecordings(recordings: List<RecordingFile>): Boolean = withContext(Dispatchers.IO) {
        var allDeleted = true
        recordings.forEach { recording ->
            if (!deleteRecordingWithSidecar(recording)) {
                allDeleted = false
            }
        }
        notifyRecordingsChanged()
        allDeleted
    }

    private suspend fun deleteRecordingWithSidecar(recording: RecordingFile): Boolean {
        val deleted = fileSystemFacade.deleteAudioRecording(recording.uri)
        if (!deleted) return false

        val stationFolderName = sanitizeFolderName(recording.stationName)
        sidecarStore.deleteSidecar(stationFolderName, recording.fileName)
        durationCache.remove(durationCacheKey(stationFolderName, recording.fileName))
        deleteStationFolderIfEmpty(stationFolderName)
        return true
    }

    private fun deleteStationFolderIfEmpty(stationFolderName: String) {
        val treeUri = folderAccess.grantedTreeUri()
        if (treeUri != null) {
            val stationFolder = folderAccess.findStationFolder(treeUri, stationFolderName) ?: return
            if (folderAccess.listChildren(treeUri, stationFolder.documentId).isEmpty()) {
                folderAccess.delete(stationFolder.uri)
            }
            return
        }

        val stationDir = File(recordingsRoot, stationFolderName)
        if (stationDir.isDirectory && stationDir.listFiles()?.isEmpty() == true) {
            stationDir.delete()
        }
    }

    override suspend fun deleteRecordingFolders(stationNames: List<String>): Boolean = withContext(Dispatchers.IO) {
        var allDeleted = true
        val treeUri = folderAccess.grantedTreeUri()
        stationNames.forEach { stationName ->
            val safeStationName = sanitizeFolderName(stationName)
            val deleted = if (treeUri != null) {
                // Deleting the folder document removes the recordings and sidecars inside it
                folderAccess.findStationFolder(treeUri, safeStationName)?.let { folderAccess.delete(it.uri) } ?: false
            } else {
                sidecarStore.deleteStationSidecars(safeStationName)
                fileSystemFacade.deleteAudioRecordingFolder(safeStationName)
            }
            if (deleted) {
                durationCache.keys.removeAll { it.startsWith("$safeStationName/") }
            } else {
                allDeleted = false
            }
        }
        notifyRecordingsChanged()
        allDeleted
    }

    override suspend fun updateRecordingNote(recording: RecordingFile, note: String?) = withContext(Dispatchers.IO) {
        sidecarStore.saveNote(sanitizeFolderName(recording.stationName), recording.fileName, note)
        notifyRecordingsChanged()
    }
}
