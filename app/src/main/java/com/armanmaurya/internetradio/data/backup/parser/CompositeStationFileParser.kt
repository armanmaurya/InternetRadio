package com.armanmaurya.internetradio.data.backup.parser

import com.armanmaurya.internetradio.data.backup.BackupStation
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompositeStationFileParser(
    private val parsers: List<StationFileParser>
) {
    @Inject
    constructor() : this(
        listOf(
            JsonBackupParser(),
            M3uPlaylistParser()
        )
    )

    /**
     * Inspects the file header without consuming the stream, selects the matching parser,
     * and returns the parsed stations. Returns null if no parser recognizes the format.
     */
    fun parse(inputStream: InputStream): List<BackupStation>? {
        val buffered = inputStream.buffered()
        buffered.mark(4096)
        val headerBytes = ByteArray(4096)
        val readBytes = buffered.read(headerBytes)
        buffered.reset()
        if (readBytes <= 0) return null

        val header = String(headerBytes, 0, readBytes, Charsets.UTF_8)
            .trimStart('\uFEFF', ' ', '\t', '\r', '\n')

        val parser = parsers.firstOrNull { it.canParse(header) } ?: return null
        return parser.parse(buffered.bufferedReader(Charsets.UTF_8))
    }
}
