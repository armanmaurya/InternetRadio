package com.armanmaurya.internetradio.core.media.prober.parser

internal object AudioFrameParser {

    data class AudioParseResult(
        val codec: String = "",
        val bitrate: Int = 0,
        val title: String? = null,
        val genre: String? = null,
        val homepage: String? = null
    )

    private val MPEG1_LAYER3_BITRATES = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
    private val MPEG1_LAYER2_BITRATES = intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384)
    private val MPEG2_LAYER3_BITRATES = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)
    private val MPEG2_LAYER2_BITRATES = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)
    private val MPEG1_SAMPLE_RATES = intArrayOf(44100, 48000, 32000)
    private val MPEG2_SAMPLE_RATES = intArrayOf(22050, 24000, 16000)
    private val MPEG25_SAMPLE_RATES = intArrayOf(11025, 12000, 8000)

    private val AAC_SAMPLE_RATES = intArrayOf(
        96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350
    )

    fun parse(bytes: ByteArray, contentType: String? = null): AudioParseResult {
        if (bytes.size < 4) return AudioParseResult()

        val ct = contentType?.lowercase() ?: ""

        // 1. WAV / PCM Container (RIFF ... WAVE)
        if (bytes.size >= 12 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'A'.code.toByte() &&
            bytes[10] == 'V'.code.toByte() && bytes[11] == 'E'.code.toByte()
        ) {
            return parseWav(bytes)
        }

        // 2. WMA / ASF Container
        if (bytes.size >= 16 && bytes[0] == 0x30.toByte() && bytes[1] == 0x26.toByte() &&
            bytes[2] == 0xB2.toByte() && bytes[3] == 0x75.toByte()
        ) {
            return AudioParseResult(codec = "WMA")
        }

        // 3. WebM / Matroska Container (EBML 0x1A 0x45 0xDF 0xA3)
        if (bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() &&
            bytes[2] == 0xDF.toByte() && bytes[3] == 0xA3.toByte()
        ) {
            val str = String(bytes, Charsets.ISO_8859_1)
            val codec = if (str.contains("Opus", ignoreCase = true)) "OPUS" else "VORBIS"
            return AudioParseResult(codec = codec)
        }

        // 4. OGG Container Check
        if (bytes[0] == 0x4F.toByte() && bytes[1] == 0x67.toByte() &&
            bytes[2] == 0x67.toByte() && bytes[3] == 0x53.toByte()
        ) {
            return parseOgg(bytes)
        }

        // 5. FLAC Check
        if ((bytes[0] == 'f'.code.toByte() && bytes[1] == 'L'.code.toByte() &&
                bytes[2] == 'a'.code.toByte() && bytes[3] == 'C'.code.toByte()) ||
            (bytes[0] == 0x7F.toByte() && bytes[1] == 'F'.code.toByte() &&
                bytes[2] == 'L'.code.toByte() && bytes[3] == 'A'.code.toByte())
        ) {
            return AudioParseResult(codec = "FLAC")
        }

        // 6. MPEG-TS stream (HLS chunks, 188-byte packets starting with 0x47)
        if (bytes[0] == 0x47.toByte() && (bytes.size < 188 || bytes[188] == 0x47.toByte())) {
            val tsResult = parseMpegTs(bytes)
            if (tsResult.codec.isNotBlank()) {
                return tsResult
            }
        }

        // 7. ID3v2 Tag Handling
        var (offset, id3Metadata) = parseId3v2(bytes)

        // Ensure offset stays within scan boundary
        if (offset >= bytes.size - 4) {
            offset = 0
        }

        val scanEnd = (bytes.size - 4).coerceAtMost(offset + 4096)

        // 8. Scan for MP3, MP2, or AAC ADTS sync words
        for (i in offset until scanEnd) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = bytes[i + 1].toInt() and 0xFF

            // AAC ADTS sync: 12 bits 0xFFF
            if (b0 == 0xFF && (b1 and 0xF6) == 0xF0) {
                val aacResult = parseAacAdtsFrame(bytes, i)
                if (aacResult != null) {
                    val (bitrate, sampleRate) = aacResult
                    val codec = if (ct.contains("aacp") || ct.contains("aac+") || sampleRate <= 24000) "AAC+" else "AAC"
                    return AudioParseResult(
                        codec = codec,
                        bitrate = bitrate,
                        title = id3Metadata.title,
                        genre = id3Metadata.genre,
                        homepage = id3Metadata.homepage
                    )
                }
            }

            // MPEG Audio frame sync: 11 bits 0xFFE
            if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) {
                val mpegResult = parseMp3Frame(bytes, i)
                if (mpegResult != null) {
                    val (bitrate, _, codecName) = mpegResult
                    return AudioParseResult(
                        codec = codecName,
                        bitrate = bitrate,
                        title = id3Metadata.title,
                        genre = id3Metadata.genre,
                        homepage = id3Metadata.homepage
                    )
                }
            }
        }

        // Fallback if ID3 was present but no frame found in scanned window
        if (id3Metadata.hasAnyData() || bytes.size >= 3 && bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()) {
            return AudioParseResult(
                codec = "MP3",
                bitrate = 0,
                title = id3Metadata.title,
                genre = id3Metadata.genre,
                homepage = id3Metadata.homepage
            )
        }

        return AudioParseResult()
    }

    private fun parseWav(bytes: ByteArray): AudioParseResult {
        val str = String(bytes, 0, bytes.size.coerceAtMost(64), Charsets.ISO_8859_1)
        val fmtIdx = str.indexOf("fmt ")
        if (fmtIdx >= 0 && fmtIdx + 16 < bytes.size) {
            val channels = (bytes[fmtIdx + 10].toInt() and 0xFF) or ((bytes[fmtIdx + 11].toInt() and 0xFF) shl 8)
            val sampleRate = (bytes[fmtIdx + 12].toInt() and 0xFF) or
                    ((bytes[fmtIdx + 13].toInt() and 0xFF) shl 8) or
                    ((bytes[fmtIdx + 14].toInt() and 0xFF) shl 16) or
                    ((bytes[fmtIdx + 15].toInt() and 0xFF) shl 24)
            val bitsPerSample = if (fmtIdx + 22 < bytes.size) {
                (bytes[fmtIdx + 22].toInt() and 0xFF) or ((bytes[fmtIdx + 23].toInt() and 0xFF) shl 8)
            } else 16

            val bitrate = if (sampleRate > 0 && channels > 0) {
                ((sampleRate.toLong() * channels * bitsPerSample) / 1000).toInt()
            } else 0

            return AudioParseResult(codec = "WAV", bitrate = bitrate)
        }
        return AudioParseResult(codec = "WAV")
    }

    private fun parseMpegTs(bytes: ByteArray): AudioParseResult {
        // Strip 4-byte TS packet headers and look for PES audio payloads
        var offset = 0
        while (offset + 188 <= bytes.size && offset < 1880) {
            if (bytes[offset] == 0x47.toByte()) {
                val payloadStart = offset + 4
                for (j in payloadStart until (offset + 184)) {
                    val b0 = bytes[j].toInt() and 0xFF
                    val b1 = bytes[j + 1].toInt() and 0xFF
                    if (b0 == 0xFF && (b1 and 0xF6) == 0xF0) {
                        val aac = parseAacAdtsFrame(bytes, j)
                        if (aac != null) return AudioParseResult(codec = "AAC", bitrate = aac.first)
                    }
                    if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) {
                        val mp3 = parseMp3Frame(bytes, j)
                        if (mp3 != null) return AudioParseResult(codec = mp3.codec, bitrate = mp3.bitrateKbps)
                    }
                }
            }
            offset += 188
        }
        return AudioParseResult()
    }

    private fun parseOgg(bytes: ByteArray): AudioParseResult {
        val str = String(bytes, Charsets.ISO_8859_1)
        val codec = when {
            str.contains("OpusHead") -> "OPUS"
            str.contains("\u0001vorbis") -> "VORBIS"
            else -> "OGG"
        }

        var bitrate = 0
        if (codec == "VORBIS") {
            val vorbisIdx = str.indexOf("\u0001vorbis")
            if (vorbisIdx >= 0 && vorbisIdx + 20 < bytes.size) {
                val nominalBitrateOffset = vorbisIdx + 16
                val b0 = bytes[nominalBitrateOffset].toInt() and 0xFF
                val b1 = bytes[nominalBitrateOffset + 1].toInt() and 0xFF
                val b2 = bytes[nominalBitrateOffset + 2].toInt() and 0xFF
                val b3 = bytes[nominalBitrateOffset + 3].toInt() and 0xFF
                val nominalBitrate = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
                if (nominalBitrate > 0) {
                    bitrate = if (nominalBitrate > 1000) nominalBitrate / 1000 else nominalBitrate
                }
            }
        }

        val title = extractVorbisComment(str, "TITLE=")
        val genre = extractVorbisComment(str, "GENRE=")
        return AudioParseResult(codec = codec, bitrate = bitrate, title = title, genre = genre)
    }

    private fun extractVorbisComment(content: String, key: String): String? {
        val idx = content.indexOf(key, ignoreCase = true)
        if (idx < 0) return null
        val start = idx + key.length
        val end = content.indexOfAny(charArrayOf('\u0000', '\n', '\r'), start).let {
            if (it < 0) (start + 64).coerceAtMost(content.length) else it
        }
        return content.substring(start, end).trim().takeIf { it.isNotBlank() }
    }

    internal data class MpegFrameInfo(val bitrateKbps: Int, val frameLength: Int, val codec: String)

    internal fun parseMp3Frame(bytes: ByteArray, offset: Int): MpegFrameInfo? {
        if (offset + 4 > bytes.size) return null

        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = bytes[offset + 1].toInt() and 0xFF
        val b2 = bytes[offset + 2].toInt() and 0xFF

        val versionBits = (b1 shr 3) and 0x03
        val layerBits = (b1 shr 1) and 0x03

        if (versionBits == 1 || layerBits == 0) return null // Reserved

        val bitrateIndex = (b2 shr 4) and 0x0F
        val sampleRateIndex = (b2 shr 2) and 0x03
        val padding = (b2 shr 1) and 0x01

        if (bitrateIndex == 0 || bitrateIndex == 15 || sampleRateIndex == 3) return null

        val isMpeg1 = versionBits == 3
        val isLayer3 = layerBits == 1
        val isLayer2 = layerBits == 2

        val codecName = when {
            isLayer3 -> "MP3"
            isLayer2 -> "MP2"
            else -> "MP1"
        }

        val bitrateKbps = when {
            isMpeg1 && isLayer3 -> MPEG1_LAYER3_BITRATES.getOrNull(bitrateIndex) ?: 0
            isMpeg1 && isLayer2 -> MPEG1_LAYER2_BITRATES.getOrNull(bitrateIndex) ?: 0
            !isMpeg1 && isLayer3 -> MPEG2_LAYER3_BITRATES.getOrNull(bitrateIndex) ?: 0
            !isMpeg1 && isLayer2 -> MPEG2_LAYER2_BITRATES.getOrNull(bitrateIndex) ?: 0
            else -> MPEG1_LAYER3_BITRATES.getOrNull(bitrateIndex) ?: 0
        }

        if (bitrateKbps == 0) return null

        val sampleRate = when (versionBits) {
            3 -> MPEG1_SAMPLE_RATES.getOrNull(sampleRateIndex) ?: 44100
            2 -> MPEG2_SAMPLE_RATES.getOrNull(sampleRateIndex) ?: 22050
            else -> MPEG25_SAMPLE_RATES.getOrNull(sampleRateIndex) ?: 11025
        }

        val frameLength = when (layerBits) {
            1 -> { // Layer 3
                val coeff = if (isMpeg1) 144 else 72
                (coeff * bitrateKbps * 1000 / sampleRate) + padding
            }
            2 -> { // Layer 2
                (144 * bitrateKbps * 1000 / sampleRate) + padding
            }
            else -> (12 * bitrateKbps * 1000 / sampleRate) + padding * 4
        }

        if (frameLength <= 4) return null

        // False-sync check: if buffer has enough bytes, verify next frame sync word
        if (offset + frameLength + 1 < bytes.size) {
            val nextB0 = bytes[offset + frameLength].toInt() and 0xFF
            val nextB1 = bytes[offset + frameLength + 1].toInt() and 0xFF
            if (nextB0 != 0xFF || (nextB1 and 0xE0) != 0xE0) {
                return null // False sync word!
            }
        }

        return MpegFrameInfo(bitrateKbps, frameLength, codecName)
    }

    internal fun parseAacAdtsFrame(bytes: ByteArray, offset: Int): Pair<Int, Int>? {
        if (offset + 7 > bytes.size) return null

        val b2 = bytes[offset + 2].toInt() and 0xFF
        val b3 = bytes[offset + 3].toInt() and 0xFF
        val b4 = bytes[offset + 4].toInt() and 0xFF
        val b5 = bytes[offset + 5].toInt() and 0xFF

        val sampleRateIndex = (b2 shr 2) and 0x0F
        val sampleRate = AAC_SAMPLE_RATES.getOrNull(sampleRateIndex) ?: return null

        val frameLength = ((b3 and 0x03) shl 11) or (b4 shl 3) or ((b5 and 0xE0) shr 5)
        if (frameLength <= 7) return null

        // False-sync check: verify next ADTS sync word
        if (offset + frameLength + 1 < bytes.size) {
            val nextB0 = bytes[offset + frameLength].toInt() and 0xFF
            val nextB1 = bytes[offset + frameLength + 1].toInt() and 0xFF
            if (nextB0 != 0xFF || (nextB1 and 0xF6) != 0xF0) {
                return null
            }
        }

        val frameDuration = 1024.0 / sampleRate
        val bitrate = Math.round((frameLength * 8.0) / (frameDuration * 1000.0)).toInt()

        return Pair(bitrate, sampleRate)
    }

    internal fun parseId3v2(bytes: ByteArray): Pair<Int, AudioParseResult> {
        if (bytes.size < 10) return Pair(0, AudioParseResult())
        if (bytes[0] != 'I'.code.toByte() || bytes[1] != 'D'.code.toByte() || bytes[2] != '3'.code.toByte()) {
            return Pair(0, AudioParseResult())
        }

        val flags = bytes[5].toInt() and 0xFF
        val s1 = bytes[6].toInt() and 0x7F
        val s2 = bytes[7].toInt() and 0x7F
        val s3 = bytes[8].toInt() and 0x7F
        val s4 = bytes[9].toInt() and 0x7F
        val tagSize = (s1 shl 21) or (s2 shl 14) or (s3 shl 7) or s4
        val hasFooter = (flags and 0x10) != 0
        val totalId3Length = 10 + tagSize + (if (hasFooter) 10 else 0)

        // Parse basic text frames within available bytes
        val maxScan = bytes.size.coerceAtMost(totalId3Length)
        val id3String = String(bytes, 0, maxScan, Charsets.ISO_8859_1)

        val title = extractId3Frame(id3String, "TIT2")
        val genre = extractId3Frame(id3String, "TCON")
        val homepage = extractId3Frame(id3String, "WOAR") ?: extractId3Frame(id3String, "WXXX")

        return Pair(totalId3Length, AudioParseResult(title = title, genre = genre, homepage = homepage))
    }

    private fun extractId3Frame(id3Text: String, frameId: String): String? {
        val idx = id3Text.indexOf(frameId)
        if (idx < 0 || idx + 10 > id3Text.length) return null
        val contentStart = idx + 10 // Skip 4-byte ID + 4-byte size + 2-byte flags
        val contentEnd = id3Text.indexOf('\u0000', contentStart).let {
            if (it < 0) (contentStart + 64).coerceAtMost(id3Text.length) else it
        }
        return id3Text.substring(contentStart, contentEnd).trim { it <= ' ' || it == '\u0000' }.takeIf { it.isNotBlank() }
    }

    private fun AudioParseResult.hasAnyData(): Boolean =
        !title.isNullOrBlank() || !genre.isNullOrBlank() || !homepage.isNullOrBlank()
}
