package com.armanmaurya.internetradio.data.backup.parser

import com.armanmaurya.internetradio.data.backup.BackupStation
import com.armanmaurya.internetradio.data.backup.LibraryBackup
import com.google.gson.Gson
import java.io.BufferedReader

/**
 * Parses native InternetRadio JSON backup files.
 */
class JsonBackupParser : StationFileParser {
    override fun canParse(header: String): Boolean {
        val trimmed = header.trimStart().removePrefix("\uFEFF")
        return trimmed.startsWith("{")
    }

    override fun parse(reader: BufferedReader): List<BackupStation> {
        val backup = Gson().fromJson(reader, LibraryBackup::class.java)
        return backup?.stations.orEmpty()
    }
}
