package com.armanmaurya.internetradio.core.media.prober.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AudioFrameParserTest {

    @Test
    fun testParseMp3Frame_validFrameWithSyncFollower() {
        // MPEG-1 Layer 3, 128 kbps, 44100 Hz, padding=0 -> frameSize = 417
        val frameSize = 417
        val buffer = ByteArray(frameSize + 4)

        // Frame 1 header: 0xFF, 0xFB, 0x90, 0x00
        buffer[0] = 0xFF.toByte()
        buffer[1] = 0xFB.toByte() // MPEG 1, Layer III
        buffer[2] = 0x90.toByte() // 128 kbps, 44100 Hz
        buffer[3] = 0x00.toByte()

        // Frame 2 header (for false-sync check): 0xFF, 0xFB, 0x90, 0x00
        buffer[frameSize] = 0xFF.toByte()
        buffer[frameSize + 1] = 0xFB.toByte()
        buffer[frameSize + 2] = 0x90.toByte()
        buffer[frameSize + 3] = 0x00.toByte()

        val result = AudioFrameParser.parseMp3Frame(buffer, 0)
        assertNotNull(result)
        assertEquals(128, result?.bitrateKbps)
        assertEquals(frameSize, result?.frameLength)
        assertEquals("MP3", result?.codec)
    }

    @Test
    fun testParseMp3Frame_falseSyncRejected() {
        val frameSize = 417
        val buffer = ByteArray(frameSize + 4)

        // Frame 1 header looks like sync
        buffer[0] = 0xFF.toByte()
        buffer[1] = 0xFB.toByte()
        buffer[2] = 0x90.toByte()
        buffer[3] = 0x00.toByte()

        // Next frame is random noise (not 0xFF)
        buffer[frameSize] = 0x12.toByte()
        buffer[frameSize + 1] = 0x34.toByte()

        val result = AudioFrameParser.parseMp3Frame(buffer, 0)
        assertNull("False sync word without subsequent frame sync should be rejected", result)
    }

    @Test
    fun testParseAacAdtsFrame_validFrame() {
        val frameSize = 371
        val buffer = ByteArray(frameSize + 8)

        // ADTS header: 0xFF, 0xF1, 0x50, 0x80, 0x6C, 0x40, 0xFC
        // sampleRateIndex = 4 (44100 Hz)
        // frameLength = 371 bytes
        buffer[0] = 0xFF.toByte()
        buffer[1] = 0xF1.toByte()
        buffer[2] = 0x50.toByte() // AAC-LC, 44100 Hz
        val fl = frameSize
        buffer[3] = ((fl shr 11) and 0x03).toByte()
        buffer[4] = ((fl shr 3) and 0xFF).toByte()
        buffer[5] = ((fl and 0x07) shl 5).toByte()
        buffer[6] = 0xFC.toByte()

        // Next ADTS frame header
        buffer[frameSize] = 0xFF.toByte()
        buffer[frameSize + 1] = 0xF1.toByte()

        val result = AudioFrameParser.parseAacAdtsFrame(buffer, 0)
        assertNotNull(result)
        assertEquals(128, result?.first)
    }

    @Test
    fun testParseId3v2_tagSkipping() {
        val tagPayloadSize = 200
        val buffer = ByteArray(10 + tagPayloadSize + 50)
        buffer[0] = 'I'.code.toByte()
        buffer[1] = 'D'.code.toByte()
        buffer[2] = '3'.code.toByte()
        buffer[3] = 3.toByte() // ID3v2.3
        buffer[4] = 0.toByte()
        buffer[5] = 0.toByte() // flags

        // synchsafe size: 200 = 0x00 0x00 0x01 0x48
        buffer[6] = 0.toByte()
        buffer[7] = 0.toByte()
        buffer[8] = 1.toByte()
        buffer[9] = 0x48.toByte()

        val (skipLength, _) = AudioFrameParser.parseId3v2(buffer)
        assertEquals(210, skipLength) // 10 header + 200 payload
    }

    @Test
    fun testParseOgg_opusAndVorbis() {
        val opusBuffer = "OggS\u0000\u0002...OpusHead...".toByteArray(Charsets.ISO_8859_1)
        val opusResult = AudioFrameParser.parse(opusBuffer)
        assertEquals("OPUS", opusResult.codec)

        val vorbisBuffer = "OggS\u0000\u0002...\u0001vorbis...".toByteArray(Charsets.ISO_8859_1)
        val vorbisResult = AudioFrameParser.parse(vorbisBuffer)
        assertEquals("VORBIS", vorbisResult.codec)
    }

    @Test
    fun testParseFlac() {
        val flacBuffer = "fLaC\u0000\u0000\u0022...".toByteArray(Charsets.ISO_8859_1)
        val result = AudioFrameParser.parse(flacBuffer)
        assertEquals("FLAC", result.codec)
    }

    @Test
    fun testParseWma() {
        val wmaBuffer = ByteArray(32)
        wmaBuffer[0] = 0x30.toByte()
        wmaBuffer[1] = 0x26.toByte()
        wmaBuffer[2] = 0xB2.toByte()
        wmaBuffer[3] = 0x75.toByte()
        val result = AudioFrameParser.parse(wmaBuffer)
        assertEquals("WMA", result.codec)
    }

    @Test
    fun testParseWebm() {
        val webmBuffer = "....Opus....".toByteArray(Charsets.ISO_8859_1)
        webmBuffer[0] = 0x1A.toByte()
        webmBuffer[1] = 0x45.toByte()
        webmBuffer[2] = 0xDF.toByte()
        webmBuffer[3] = 0xA3.toByte()
        val result = AudioFrameParser.parse(webmBuffer)
        assertEquals("OPUS", result.codec)
    }
}
