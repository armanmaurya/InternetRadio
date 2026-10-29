package com.armanmaurya.internetradio.data.recording

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class StationInfoDto(
    val stationUuid: String = "",
    val name: String = "",
    val favicon: String = "",
    val url: String = ""
)

/**
 * Stores the station logo as `folder.jpg` and the station details as `station.json` in each
 * station's recording folder, so the recordings list (and other music players) can show the logo.
 *
 * Follows the same storage rules as [RecordingSidecarStore]: next to the recordings once
 * [RecordingFolderAccess] holds the grant, in app storage until then.
 */
@Singleton
class RecordingArtworkStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val folderAccess: RecordingFolderAccess
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val mutex = Mutex()

    private val appArtworkRoot: File
        get() = context.getExternalFilesDir("recordings_artwork")
            ?: File(context.filesDir, "recordings_artwork")

    /**
     * Downloads the station logo unless the folder already holds the logo for the same favicon.
     * Returns true when new artwork was written.
     */
    suspend fun saveStationArtwork(stationFolderName: String, station: RadioStation): Boolean = mutex.withLock {
        val favicon = station.favicon.trim()
        if (favicon.isBlank()) return@withLock false

        val treeUri = folderAccess.grantedTreeUri()
        val existing = readExisting(treeUri, stationFolderName)
        if (!needsArtworkUpdate(existing.info?.favicon, existing.hasArtwork, favicon)) return@withLock false

        val jpeg = downloadAsJpeg(favicon) ?: return@withLock false
        val info = gson.toJson(
            StationInfoDto(
                stationUuid = station.stationUuid,
                name = station.name,
                favicon = favicon,
                url = station.url
            )
        )

        if (treeUri != null) {
            val written = folderAccess.writeBytes(treeUri, stationFolderName, ARTWORK_FILE_NAME, ARTWORK_MIME_TYPE, jpeg)
            // Info is written only after the image, so a failed image write is retried next time
            if (written) {
                folderAccess.writeText(treeUri, stationFolderName, STATION_INFO_FILE_NAME, STATION_INFO_MIME_TYPE, info)
            }
            return@withLock written
        }

        runCatching {
            val stationDir = File(appArtworkRoot, stationFolderName)
            stationDir.mkdirs()
            File(stationDir, ARTWORK_FILE_NAME).writeBytes(jpeg)
            File(stationDir, STATION_INFO_FILE_NAME).writeText(info, Charsets.UTF_8)
            true
        }.getOrDefault(false)
    }

    /** Artwork kept in app storage, used while folder access is not granted. */
    fun appArtworkUri(stationFolderName: String): Uri? {
        val file = File(File(appArtworkRoot, stationFolderName), ARTWORK_FILE_NAME)
        return if (file.isFile) Uri.fromFile(file) else null
    }

    fun deleteAppArtwork(stationFolderName: String) {
        runCatching {
            val stationDir = File(appArtworkRoot, stationFolderName)
            if (stationDir.exists()) stationDir.deleteRecursively()
        }
    }

    /** Moves artwork kept in app storage next to the recordings. Existing folder artwork wins. */
    suspend fun migrateAppArtwork(treeUri: Uri) = mutex.withLock {
        val root = appArtworkRoot
        val stationDirs = root.listFiles { file -> file.isDirectory } ?: return@withLock

        for (stationDir in stationDirs) {
            val stationFolder = folderAccess.findStationFolder(treeUri, stationDir.name)
            if (stationFolder == null) {
                // The recording may still be pending, so only drop artwork nobody has recorded into for a while
                val isStale = System.currentTimeMillis() - stationDir.lastModified() > ORPHAN_RETENTION_MS
                if (isStale) stationDir.deleteRecursively()
                continue
            }

            val existingNames = folderAccess.listChildren(treeUri, stationFolder.documentId).map { it.name }.toSet()
            var moved = true
            stationDir.listFiles { file -> file.isFile && file.name in ARTWORK_FILE_NAMES }?.forEach { file ->
                if (file.name !in existingNames) {
                    val mimeType = if (file.name == ARTWORK_FILE_NAME) ARTWORK_MIME_TYPE else STATION_INFO_MIME_TYPE
                    moved = folderAccess.writeBytes(treeUri, stationDir.name, file.name, mimeType, file.readBytes()) && moved
                }
            }
            if (moved) stationDir.deleteRecursively()
        }
        if (root.listFiles()?.isEmpty() == true) root.delete()
    }

    private data class ExistingArtwork(val info: StationInfoDto?, val hasArtwork: Boolean)

    private fun readExisting(treeUri: Uri?, stationFolderName: String): ExistingArtwork {
        if (treeUri != null) {
            val stationFolder = folderAccess.findStationFolder(treeUri, stationFolderName)
                ?: return ExistingArtwork(null, false)
            val children = folderAccess.listChildren(treeUri, stationFolder.documentId).associateBy { it.name }
            val info = children[STATION_INFO_FILE_NAME]?.let { folderAccess.readText(it.uri) }?.let(::parseStationInfo)
            return ExistingArtwork(info, ARTWORK_FILE_NAME in children)
        }

        val stationDir = File(appArtworkRoot, stationFolderName)
        val info = runCatching {
            File(stationDir, STATION_INFO_FILE_NAME).takeIf { it.isFile }?.readText(Charsets.UTF_8)
        }.getOrNull()?.let(::parseStationInfo)
        return ExistingArtwork(info, File(stationDir, ARTWORK_FILE_NAME).isFile)
    }

    private fun parseStationInfo(json: String): StationInfoDto? =
        runCatching { gson.fromJson(json, StationInfoDto::class.java) }.getOrNull()

    private suspend fun downloadAsJpeg(url: String): ByteArray? = runCatching {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(ARTWORK_SIZE_PX)
            .allowHardware(false)
            .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
        val source = result.image.toBitmap()

        // JPEG has no alpha, so transparent logos are drawn over white instead of turning black
        val opaque = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(opaque).apply {
            drawColor(Color.WHITE)
            drawBitmap(source, 0f, 0f, null)
        }
        ByteArrayOutputStream().use { out ->
            opaque.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            opaque.recycle()
            out.toByteArray()
        }
    }.getOrNull()

    companion object {
        const val ARTWORK_FILE_NAME = "folder.jpg"
        const val STATION_INFO_FILE_NAME = "station.json"
        private val ARTWORK_FILE_NAMES = setOf(ARTWORK_FILE_NAME, STATION_INFO_FILE_NAME)
        private const val ARTWORK_MIME_TYPE = "image/jpeg"
        private const val STATION_INFO_MIME_TYPE = "application/json"
        private const val ARTWORK_SIZE_PX = 512
        private const val JPEG_QUALITY = 90
        private const val ORPHAN_RETENTION_MS = 24L * 60 * 60 * 1000

        /** True for files this store writes, which must not count as recordings or sidecars. */
        fun isArtworkFileName(name: String): Boolean = name in ARTWORK_FILE_NAMES

        internal fun needsArtworkUpdate(savedFavicon: String?, hasArtwork: Boolean, favicon: String): Boolean {
            if (favicon.isBlank()) return false
            return !hasArtwork || savedFavicon != favicon
        }
    }
}
