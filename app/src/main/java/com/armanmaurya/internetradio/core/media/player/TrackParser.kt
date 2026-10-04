package com.armanmaurya.internetradio.core.media.player

import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.TextInformationFrame

@UnstableApi
object TrackParser {

    fun parse(metadata: Metadata): String? {
        var track: String? = null

        for (i in 0 until metadata.length()) {
            track = getTitle(metadata.get(i))
            if (track != null) break
        }

        return track
    }

    private fun getTitle(entry: Metadata.Entry): String? {
        return when (entry) {
            is IcyInfo -> entry.title
            is TextInformationFrame -> {
                if (entry.id == "TIT2" || entry.id == "TT2") entry.values.firstOrNull() else null
            }

            else -> null
        }
    }
}