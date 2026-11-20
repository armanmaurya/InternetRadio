package com.armanmaurya.internetradio.data.backup.parser

import com.armanmaurya.internetradio.data.backup.BackupStation
import java.io.BufferedReader

/**
 * Extensible interface for parsing radio station playlists or backup files.
 * Implement this interface to add support for another app export format.
 */
interface StationFileParser {
    /**
     * Inspects the first few kilobytes of the file to determine if this parser can handle it.
     */
    fun canParse(header: String): Boolean

    /**
     * Parses the stream into a list of [BackupStation].
     */
    fun parse(reader: BufferedReader): List<BackupStation>
}
