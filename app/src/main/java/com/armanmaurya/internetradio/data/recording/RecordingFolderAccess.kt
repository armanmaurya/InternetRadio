package com.armanmaurya.internetradio.data.recording

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persisted SAF grant for the public recordings folder (Music/InternetRadio).
 *
 * Scoped storage only lets the app see files its current install created, and never lets it
 * create non-audio files in Music/. The tree grant works around both: sidecars can be written
 * next to recordings, and recordings from earlier installs stay visible.
 */
@Singleton
class RecordingFolderAccess @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class DocumentEntry(
        val documentId: String,
        val uri: Uri,
        val name: String,
        val isDirectory: Boolean,
        val sizeBytes: Long,
        val lastModified: Long
    )

    /** Document URI used to open the folder picker directly at Music/InternetRadio. */
    val initialPickerUri: Uri
        get() = DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, RECORDINGS_DOCUMENT_ID)

    fun hasAccess(): Boolean = grantedTreeUri() != null

    fun grantedTreeUri(): Uri? {
        return context.contentResolver.persistedUriPermissions.firstOrNull { permission ->
            permission.isReadPermission && permission.isWritePermission &&
                isRecordingsTree(permission.uri)
        }?.uri
    }

    /** Ensures the folder exists so the picker can open it. */
    fun ensureFolderExists() {
        runCatching {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                FOLDER_NAME
            ).mkdirs()
        }
    }

    /** Persists the grant only if [treeUri] is exactly Music/InternetRadio. */
    fun grantAccess(treeUri: Uri): Boolean {
        if (!isRecordingsTree(treeUri)) {
            // The picker grants a temporary permission even for a rejected folder; drop it
            runCatching {
                context.revokeUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            return false
        }
        return runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            true
        }.getOrDefault(false)
    }

    /** Lists the station folders inside Music/InternetRadio. */
    fun listStationFolders(treeUri: Uri): List<DocumentEntry> =
        listChildren(treeUri, DocumentsContract.getTreeDocumentId(treeUri)).filter { it.isDirectory }

    fun findStationFolder(treeUri: Uri, stationFolderName: String): DocumentEntry? =
        listStationFolders(treeUri).firstOrNull { it.name == stationFolderName }

    fun listChildren(treeUri: Uri, parentDocumentId: String): List<DocumentEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        val entries = mutableListOf<DocumentEntry>()
        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(0) ?: continue
                    entries += DocumentEntry(
                        documentId = documentId,
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                        name = cursor.getString(1).orEmpty(),
                        isDirectory = cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                        sizeBytes = if (cursor.isNull(3)) 0L else cursor.getLong(3),
                        lastModified = if (cursor.isNull(4)) 0L else cursor.getLong(4)
                    )
                }
            }
        }
        return entries
    }

    fun readText(uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    }.getOrNull()

    /** Creates or truncates [fileName] inside the station folder, creating the folder if needed. */
    fun writeText(treeUri: Uri, stationFolderName: String, fileName: String, mimeType: String, text: String): Boolean =
        writeBytes(treeUri, stationFolderName, fileName, mimeType, text.toByteArray(Charsets.UTF_8))

    /** Creates or truncates [fileName] inside the station folder, creating the folder if needed. */
    fun writeBytes(treeUri: Uri, stationFolderName: String, fileName: String, mimeType: String, bytes: ByteArray): Boolean {
        return runCatching {
            val stationFolder = findStationFolder(treeUri, stationFolderName)
                ?: createDocument(treeUri, DocumentsContract.getTreeDocumentId(treeUri), DocumentsContract.Document.MIME_TYPE_DIR, stationFolderName)
                ?: return false
            val target = listChildren(treeUri, stationFolder.documentId).firstOrNull { it.name == fileName }?.uri
                ?: createDocument(treeUri, stationFolder.documentId, mimeType, fileName)?.uri
                ?: return false
            context.contentResolver.openOutputStream(target, "wt")?.use {
                it.write(bytes)
            } != null
        }.getOrDefault(false)
    }

    fun delete(uri: Uri): Boolean = runCatching {
        DocumentsContract.deleteDocument(context.contentResolver, uri)
    }.getOrDefault(false)

    private fun createDocument(treeUri: Uri, parentDocumentId: String, mimeType: String, name: String): DocumentEntry? {
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocumentId)
        val created = DocumentsContract.createDocument(context.contentResolver, parentUri, mimeType, name) ?: return null
        val documentId = DocumentsContract.getDocumentId(created)
        return DocumentEntry(
            documentId = documentId,
            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
            name = name,
            isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR,
            sizeBytes = 0L,
            lastModified = System.currentTimeMillis()
        )
    }

    private fun isRecordingsTree(uri: Uri): Boolean {
        if (uri.authority != EXTERNAL_STORAGE_AUTHORITY) return false
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return false
        return isRecordingsTreeDocumentId(treeId)
    }

    companion object {
        private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
        private const val FOLDER_NAME = "InternetRadio"
        private const val RECORDINGS_DOCUMENT_ID = "primary:Music/$FOLDER_NAME"

        /** Only Music/InternetRadio on primary storage is accepted; parents and subfolders are not. */
        internal fun isRecordingsTreeDocumentId(treeDocumentId: String): Boolean =
            treeDocumentId.equals(RECORDINGS_DOCUMENT_ID, ignoreCase = true)
    }
}
