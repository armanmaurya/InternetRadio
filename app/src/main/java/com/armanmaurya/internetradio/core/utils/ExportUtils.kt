package com.armanmaurya.internetradio.core.utils

import android.content.Context
import android.net.Uri
import com.armanmaurya.internetradio.data.local.entity.toEntity
import com.armanmaurya.internetradio.data.backup.LibraryBackup
import com.armanmaurya.internetradio.data.backup.toBackupStation
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportUtils {
    suspend fun exportStation(context: Context, uri: Uri, station: RadioStation, fileSystemFacade: com.armanmaurya.internetradio.core.system.FileSystemFacade, systemFacade: com.armanmaurya.internetradio.core.system.SystemFacade): Result<Unit> {
        return try {
            val versionName = systemFacade.getAppVersionName()

            val exportedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                .format(Date())

            val backupStation = station.toEntity(isFavorite = true).toBackupStation()

            val backup = LibraryBackup(
                exportedAt = exportedAt,
                appVersion = versionName ?: "unknown",
                stations = listOf(backupStation)
            )
            val json = Gson().toJson(backup)
            fileSystemFacade.openOutputStream(uri)?.use { stream ->
                stream.write(json.toByteArray())
            } ?: return Result.failure(Exception("Could not open file for writing"))

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
