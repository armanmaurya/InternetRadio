package com.armanmaurya.internetradio.data.recording

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.armanmaurya.internetradio.domain.model.RecordingTrack
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class RecordingSidecarDto(
    val stationName: String = "",
    val fileName: String = "",
    val tracks: List<RecordingTrack> = emptyList(),
    val note: String? = null
)

/**
 * Stores `<recording>.json` sidecars.
 *
 * Once [RecordingFolderAccess] holds the grant for Music/InternetRadio, sidecars live next to the
 * recording in Music/InternetRadio/<station>/. Until then they are kept in app storage, and
 * [migrateAppSidecars] moves them next to their recordings after access is granted.
 */
@Singleton
class RecordingSidecarStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val folderAccess: RecordingFolderAccess
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private val appSidecarRoot: File
        get() = context.getExternalFilesDir("recordings_sidecar")
            ?: File(context.filesDir, "recordings_sidecar")

    private val musicInternetRadioRoot: File
        get() = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "InternetRadio"
        )

    @Synchronized
    fun saveTracks(
        stationFolderName: String,
        fileName: String,
        tracks: List<RecordingTrack>
    ) {
        if (fileName.isBlank() || tracks.isEmpty()) return
        val existingNote = readSidecar(stationFolderName, fileName)?.note
        val json = serializeSidecar(
            RecordingSidecarDto(
                stationName = stationFolderName,
                fileName = fileName,
                tracks = tracks.sortedBy { it.offsetMs },
                note = existingNote
            )
        )

        writeSidecar(stationFolderName, fileName, json)
    }

    @Synchronized
    fun saveNote(
        stationFolderName: String,
        fileName: String,
        note: String?
    ) {
        if (fileName.isBlank()) return
        val existingTracks = readSidecar(stationFolderName, fileName)?.tracks.orEmpty()
        val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }

        if (cleanNote == null && existingTracks.isEmpty()) {
            deleteSidecar(stationFolderName, fileName)
            return
        }

        val json = serializeSidecar(
            RecordingSidecarDto(
                stationName = stationFolderName,
                fileName = fileName,
                tracks = existingTracks.sortedBy { it.offsetMs },
                note = cleanNote
            )
        )

        writeSidecar(stationFolderName, fileName, json)
    }

    private fun writeSidecar(stationFolderName: String, fileName: String, json: String) {
        val safeStation = sanitizeFolderName(stationFolderName)
        val treeUri = folderAccess.grantedTreeUri()
        if (treeUri != null) {
            folderAccess.writeText(treeUri, safeStation, sidecarFileName(fileName), SIDECAR_MIME_TYPE, json)
            return
        }

        runCatching {
            val stationDir = File(appSidecarRoot, safeStation)
            stationDir.mkdirs()
            File(stationDir, sidecarFileName(fileName)).writeText(json, Charsets.UTF_8)
        }
    }

    fun readSidecar(stationFolderName: String, fileName: String): RecordingSidecarDto? {
        val safeStation = sanitizeFolderName(stationFolderName)
        val treeUri = folderAccess.grantedTreeUri()
        if (treeUri != null) {
            val stationFolder = folderAccess.findStationFolder(treeUri, safeStation) ?: return null
            val sidecar = folderAccess.listChildren(treeUri, stationFolder.documentId)
                .firstOrNull { it.name == sidecarFileName(fileName) } ?: return null
            return readSidecar(sidecar.uri)
        }

        // Sidecars written next to the recording on Android 10 and below are still honoured
        return readSidecarFile(File(File(appSidecarRoot, safeStation), sidecarFileName(fileName)))
            ?: readSidecarFile(File(File(musicInternetRadioRoot, safeStation), sidecarFileName(fileName)))
    }

    /** Reads a sidecar document already located through a folder listing. */
    fun readSidecar(sidecarUri: Uri): RecordingSidecarDto? =
        folderAccess.readText(sidecarUri)?.let(::parseUsableSidecar)

    private fun readSidecarFile(sidecarFile: File): RecordingSidecarDto? = runCatching {
        sidecarFile.takeIf { it.isFile && it.canRead() }?.readText(Charsets.UTF_8)
    }.getOrNull()?.let(::parseUsableSidecar)

    fun deleteSidecar(stationFolderName: String, fileName: String) {
        val safeStation = sanitizeFolderName(stationFolderName)
        val treeUri = folderAccess.grantedTreeUri()
        if (treeUri != null) {
            folderAccess.findStationFolder(treeUri, safeStation)?.let { stationFolder ->
                folderAccess.listChildren(treeUri, stationFolder.documentId)
                    .firstOrNull { it.name == sidecarFileName(fileName) }
                    ?.let { folderAccess.delete(it.uri) }
            }
        } else {
            runCatching {
                val adjacent = File(File(musicInternetRadioRoot, safeStation), sidecarFileName(fileName))
                if (adjacent.exists()) adjacent.delete()
            }
        }

        runCatching {
            val stationDir = File(appSidecarRoot, safeStation)
            val sidecar = File(stationDir, sidecarFileName(fileName))
            if (sidecar.exists()) sidecar.delete()
            deleteIfEmpty(stationDir)
        }
    }

    fun deleteStationSidecars(stationName: String) {
        val safeStation = sanitizeFolderName(stationName)
        val treeUri = folderAccess.grantedTreeUri()
        if (treeUri != null) {
            folderAccess.findStationFolder(treeUri, safeStation)?.let { stationFolder ->
                folderAccess.listChildren(treeUri, stationFolder.documentId)
                    .filter { !it.isDirectory && it.name.endsWith(SIDECAR_EXTENSION, ignoreCase = true) }
                    .forEach { folderAccess.delete(it.uri) }
            }
        } else {
            runCatching {
                File(musicInternetRadioRoot, safeStation)
                    .listFiles { f -> f.isFile && f.name.endsWith(SIDECAR_EXTENSION, ignoreCase = true) }
                    ?.forEach { it.delete() }
            }
        }

        runCatching {
            val appDir = File(appSidecarRoot, safeStation)
            if (appDir.exists()) appDir.deleteRecursively()
        }
    }

    /**
     * Moves sidecars kept in app storage next to their recordings. Sidecars whose recording no
     * longer exists are dropped. Safe to call repeatedly; it is a no-op once app storage is empty.
     */
    @Synchronized
    fun migrateAppSidecars(treeUri: Uri) {
        val root = appSidecarRoot
        val stationDirs = root.listFiles { file -> file.isDirectory } ?: return

        for (stationDir in stationDirs) {
            val sidecarFiles = stationDir.listFiles { file ->
                file.isFile && file.name.endsWith(SIDECAR_EXTENSION, ignoreCase = true)
            }.orEmpty()
            if (sidecarFiles.isEmpty()) {
                deleteIfEmpty(stationDir)
                continue
            }

            val stationFolder = folderAccess.findStationFolder(treeUri, stationDir.name)
            val existing = stationFolder
                ?.let { folderAccess.listChildren(treeUri, it.documentId) }
                .orEmpty()
                .associateBy { it.name }

            for (sidecarFile in sidecarFiles) {
                val recordingFileName = sidecarFile.name.removeSuffix(SIDECAR_EXTENSION)
                val appSidecar = readSidecarFile(sidecarFile)
                if (appSidecar == null) {
                    sidecarFile.delete()
                    continue
                }
                if (recordingFileName !in existing) {
                    // A recording in progress is hidden as a pending file, so keep fresh sidecars around
                    val isStale = System.currentTimeMillis() - sidecarFile.lastModified() > ORPHAN_RETENTION_MS
                    if (isStale) sidecarFile.delete()
                    continue
                }

                val folderEntry = existing[sidecarFile.name]
                val folderSidecar = folderEntry?.let { readSidecar(it.uri) }
                val merged = mergeSidecars(
                    folderSidecar = folderSidecar,
                    folderModified = folderEntry?.lastModified ?: 0L,
                    appSidecar = appSidecar,
                    appModified = sidecarFile.lastModified()
                )
                val written = folderAccess.writeText(
                    treeUri,
                    stationDir.name,
                    sidecarFile.name,
                    SIDECAR_MIME_TYPE,
                    serializeSidecar(merged)
                )
                if (written) sidecarFile.delete()
            }
            deleteIfEmpty(stationDir)
        }
        deleteIfEmpty(root)
    }

    fun sidecarFileName(recordingFileName: String): String = "$recordingFileName$SIDECAR_EXTENSION"

    private fun deleteIfEmpty(dir: File) {
        if (dir.isDirectory && dir.listFiles()?.isEmpty() == true) dir.delete()
    }

    private fun parseUsableSidecar(json: String): RecordingSidecarDto? {
        val parsed = runCatching { parseSidecarDto(json) }.getOrNull() ?: return null
        return parsed.takeIf { it.tracks.isNotEmpty() || !it.note.isNullOrBlank() }
    }

    internal fun serializeSidecar(dto: RecordingSidecarDto): String = gson.toJson(dto)

    internal fun parseSidecarDto(json: String): RecordingSidecarDto? {
        if (json.isBlank()) return null
        val dto = gson.fromJson(json, RecordingSidecarDto::class.java) ?: return null
        return dto.copy(
            tracks = dto.tracks.orEmpty().sortedBy { it.offsetMs },
            note = dto.note?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    internal fun parseSidecar(json: String): List<RecordingTrack> {
        return parseSidecarDto(json)?.tracks.orEmpty()
    }

    private fun sanitizeFolderName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()

    companion object {
        private const val SIDECAR_EXTENSION = ".json"
        private const val SIDECAR_MIME_TYPE = "application/json"
        private const val ORPHAN_RETENTION_MS = 24L * 60 * 60 * 1000

        /**
         * Combines a sidecar already next to the recording with one from app storage.
         * Values from the more recently written copy win; empty values fall back to the other copy.
         * App storage can be newer when access was revoked and later granted again.
         */
        internal fun mergeSidecars(
            folderSidecar: RecordingSidecarDto?,
            folderModified: Long,
            appSidecar: RecordingSidecarDto,
            appModified: Long
        ): RecordingSidecarDto {
            if (folderSidecar == null) return appSidecar
            val (newer, older) = if (appModified > folderModified) {
                appSidecar to folderSidecar
            } else {
                folderSidecar to appSidecar
            }
            return newer.copy(
                stationName = newer.stationName.ifBlank { older.stationName },
                fileName = newer.fileName.ifBlank { older.fileName },
                tracks = newer.tracks.ifEmpty { older.tracks },
                note = newer.note ?: older.note
            )
        }
    }
}
