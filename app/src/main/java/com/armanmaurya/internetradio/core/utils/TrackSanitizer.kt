package com.armanmaurya.internetradio.core.utils

import com.armanmaurya.internetradio.core.metadata.TrackMetadataEngine

/**
 * Backward-compatible facade delegating to [TrackMetadataEngine].
 */
object TrackSanitizer {

    fun isDiscardable(segment: String, isMultiSegmentDelimiter: Boolean = false): Boolean =
        TrackMetadataEngine.isDiscardable(segment, isMultiSegmentDelimiter)

    /**
     * Cleans raw radio stream metadata by removing unwanted segments such as
     * broadcast delimiters ("||", "|", "~"), station automation IDs, segment codes,
     * UUIDs, hashes, and URLs.
     */
    fun sanitize(raw: String): String =
        TrackMetadataEngine.sanitize(raw)

    /**
     * Cleans track information specifically for iTunes / cover art search query.
     */
    fun sanitizeTrackInfo(info: String): String =
        TrackMetadataEngine.sanitizeTrackInfo(info)
}
