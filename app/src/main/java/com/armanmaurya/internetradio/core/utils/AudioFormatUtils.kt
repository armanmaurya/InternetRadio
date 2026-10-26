package com.armanmaurya.internetradio.core.utils

enum class AudioFormat(val extension: String) {
    MP3("mp3"), AAC("aac"), OGG("ogg"), UNKNOWN("aac")
}

object AudioFormatUtils {

    fun isHlsContentType(ct: String): Boolean {
        val c = ct.lowercase()
        return c.contains("mpegurl") || c.contains("m3u")
    }

    fun isHlsUrl(url: String): Boolean {
        return url.contains(".m3u8", ignoreCase = true) || url.contains(".mpd", ignoreCase = true)
    }

    fun audioFormatFromMagicBytes(b: ByteArray): AudioFormat {
        if (b.size < 2) return AudioFormat.UNKNOWN
        return when {
            // OGG: "OggS"
            b.size >= 4 && b[0] == 0x4F.toByte() && b[1] == 0x67.toByte() &&
            b[2] == 0x67.toByte() && b[3] == 0x53.toByte() -> AudioFormat.OGG

            // AAC ADTS: FF F1 or FF F9
            b[0] == 0xFF.toByte() &&
            (b[1] == 0xF1.toByte() || b[1] == 0xF9.toByte()) -> AudioFormat.AAC

            // MP3 ID3 header: "ID3"
            b.size >= 3 && b[0] == 0x49.toByte() && b[1] == 0x44.toByte() &&
            b[2] == 0x33.toByte() -> AudioFormat.MP3

            // MP3 sync words
            b[0] == 0xFF.toByte() &&
            (b[1] == 0xFB.toByte() || b[1] == 0xFA.toByte() ||
             b[1] == 0xF3.toByte() || b[1] == 0xF2.toByte()) -> AudioFormat.MP3

            else -> AudioFormat.UNKNOWN
        }
    }
}
