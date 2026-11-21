package com.armanmaurya.internetradio.core.utils

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.Lyrics
import java.io.IOException

object LyricsExporter {

    private val ILLEGAL_FILENAME_CHARS = Regex("[\\\\/:*?\"<>|]")

    data class ExportPayload(
        val defaultFileName: String,
        val mimeType: String,
        val content: String
    )

    fun prepareExport(lyrics: Lyrics, defaultTitle: String? = null, defaultArtist: String? = null): ExportPayload {
        val title = lyrics.trackTitle ?: defaultTitle ?: "Unknown Track"
        val artist = lyrics.artistName ?: defaultArtist
        val isSynced = !lyrics.syncedLyrics.isNullOrEmpty()

        val baseName = if (!artist.isNullOrBlank()) "$artist - $title" else title
        val sanitizedBase = sanitizeFileName(baseName)

        val extension = if (isSynced) ".lrc" else ".txt"
        val fileName = "$sanitizedBase$extension"
        val content = if (isSynced) lyrics.toLrcContent() else lyrics.toPlainContent()

        return ExportPayload(
            defaultFileName = fileName,
            mimeType = "text/plain",
            content = content
        )
    }

    fun writeToUri(context: Context, uri: Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            }
            Toast.makeText(context, context.getString(R.string.lyrics_downloaded_success), Toast.LENGTH_SHORT).show()
            true
        } catch (_: IOException) {
            Toast.makeText(context, context.getString(R.string.lyrics_download_failed), Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(ILLEGAL_FILENAME_CHARS, "_").trim().take(100)
    }
}
