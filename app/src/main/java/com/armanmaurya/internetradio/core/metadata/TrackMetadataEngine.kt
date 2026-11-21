package com.armanmaurya.internetradio.core.metadata

/**
 * Rich representation of a parsed and sanitized radio track.
 */
data class ParsedTrack(
    val artist: String?,
    val title: String,
    val album: String? = null,
    val raw: String
) {
    val formatted: String
        get() = if (!artist.isNullOrBlank()) "$artist - $title" else title
}

/**
 * Strategy interface for parsing specific broadcast metadata stream formats.
 */
interface TrackFormatParser {
    fun canParse(raw: String): Boolean
    fun parse(raw: String): ParsedTrack?
}

/**
 * Parser for playout automation streams using tilde (`~`) delimiters (e.g. RCS Zetta,
 * NexGen, Virgin Radio, RadioMediaset).
 *
 * Typical format:
 * ARTIST ~ TITLE ~ ALBUM ~ YEAR ~ EXTRA ~ DURATION ~ START_TIME ~ END_TIME ~ STATION
 */
class RcsTildePlayoutParser : TrackFormatParser {
    override fun canParse(raw: String): Boolean = raw.contains("~")

    override fun parse(raw: String): ParsedTrack? {
        val segments = raw.split("~")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !TrackMetadataEngine.isDiscardable(it, isMultiSegmentDelimiter = true) }

        if (segments.isEmpty()) return null

        return if (segments.size >= 2) {
            val artist = segments[0]
            val title = segments[1]
            val album = if (segments.size >= 3) segments[2] else null
            ParsedTrack(artist = artist, title = title, album = album, raw = raw)
        } else {
            ParsedTrack(artist = null, title = segments.first(), raw = raw)
        }
    }
}

/**
 * Parser for multi-field broadcast streams delimited by "||" or "|" (e.g. Dalet, Omnia, Hotmix).
 *
 * Typical format:
 * "GEORGE MICHAEL - Older || 21930 || S || 2fad6c44-df66-411d-89d9-09ee88efa6bd"
 */
class PipeBroadcastParser : TrackFormatParser {
    override fun canParse(raw: String): Boolean = raw.contains("||") || raw.contains("|")

    override fun parse(raw: String): ParsedTrack? {
        val delimiter = if (raw.contains("||")) "||" else "|"
        val valid = raw.split(delimiter)
            .map { it.trim() }
            .filter { !TrackMetadataEngine.isDiscardable(it, isMultiSegmentDelimiter = true) }

        if (valid.isEmpty()) return null

        val candidate = valid.first()
        return if (candidate.contains(" - ")) {
            val parts = candidate.split(" - ", limit = 2)
            ParsedTrack(artist = parts[0].trim(), title = parts[1].trim(), raw = raw)
        } else if (valid.size >= 2) {
            ParsedTrack(artist = valid[0], title = valid[1], raw = raw)
        } else {
            ParsedTrack(artist = null, title = candidate, raw = raw)
        }
    }
}

/**
 * Parser for standard radio stream format ("Artist - Title" with optional trailing metadata).
 */
class StandardHyphenParser : TrackFormatParser {
    override fun canParse(raw: String): Boolean =
        raw.contains(" - ") || raw.contains(" – ") || raw.contains(" — ")

    override fun parse(raw: String): ParsedTrack? {
        val normalized = raw.trim()
            .replace(" – ", " - ")
            .replace(" — ", " - ")

        val parts = normalized.split(" - ")
            .map { it.trim() }

        val filtered = parts.filterIndexed { index, part ->
            if (parts.size > 2 && index >= 2 && TrackMetadataEngine.NUMERIC_ID_REGEX.matches(part)) {
                false
            } else {
                !TrackMetadataEngine.isDiscardable(part, isMultiSegmentDelimiter = false)
            }
        }

        if (filtered.isEmpty()) return null

        return if (filtered.size >= 2) {
            val artist = filtered[0]
            val title = filtered.subList(1, filtered.size).joinToString(" - ")
            ParsedTrack(artist = artist, title = title, raw = raw)
        } else {
            ParsedTrack(artist = null, title = filtered.first(), raw = raw)
        }
    }
}

/**
 * Fallback parser for single titles, jingles, station IDs, or un-delimited streams.
 */
class FallbackTitleParser : TrackFormatParser {
    override fun canParse(raw: String): Boolean = true

    override fun parse(raw: String): ParsedTrack =
        ParsedTrack(artist = null, title = raw.trim(), raw = raw)
}

/**
 * Extensible engine for parsing, normalizing, and sanitizing radio broadcast stream metadata.
 */
object TrackMetadataEngine {

    val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    val HASH_REGEX = Regex("^[0-9a-fA-F]{16,}$")
    val NUMERIC_ID_REGEX = Regex("^\\d+$")
    val SEGMENT_CODE_REGEX = Regex("^(?i)[A-Z]|adv|ad|jingle|promo|sweeper$")
    val URL_REGEX = Regex("(?i)^(https?://\\S+|www\\.\\S+|[a-z0-9-]+(\\.[a-z0-9-]+)*\\.(com|org|net|fm|radio|tv|co|de|fr|uk|live|stream|audio)(/\\S*)?)$")
    val ISO_DATETIME_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}.*$")

    private val parsers: List<TrackFormatParser> = listOf(
        RcsTildePlayoutParser(),
        PipeBroadcastParser(),
        StandardHyphenParser(),
        FallbackTitleParser()
    )

    fun isDiscardable(segment: String, isMultiSegmentDelimiter: Boolean = false): Boolean {
        val trimmed = segment.trim()
        if (trimmed.isEmpty()) return true
        if (UUID_REGEX.matches(trimmed)) return true
        if (HASH_REGEX.matches(trimmed)) return true
        if (URL_REGEX.matches(trimmed)) return true
        if (ISO_DATETIME_REGEX.matches(trimmed)) return true
        if (isMultiSegmentDelimiter) {
            if (NUMERIC_ID_REGEX.matches(trimmed)) return true
            if (SEGMENT_CODE_REGEX.matches(trimmed)) return true
        }
        return false
    }

    fun parse(raw: String): ParsedTrack {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ParsedTrack(artist = null, title = "", raw = raw)
        val parser = parsers.firstOrNull { it.canParse(trimmed) } ?: FallbackTitleParser()
        return parser.parse(trimmed) ?: ParsedTrack(artist = null, title = trimmed, raw = raw)
    }

    fun sanitize(raw: String): String = parse(raw).formatted

    fun sanitizeTrackInfo(info: String): String {
        return sanitize(info)
            .replace(Regex("\\(.*?\\)|\\[.*?\\]|\\{.*?\\}"), "")
            .replace(Regex("(?i)\\b(and|feat\\.?|ft\\.?|with)\\b"), " ")
            .replace(Regex("[&,\\-\\|/~]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
