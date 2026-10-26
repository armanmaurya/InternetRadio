package com.armanmaurya.internetradio.core.media.recorder.backend

import com.armanmaurya.internetradio.core.media.prober.parser.AudioFrameParser
import com.armanmaurya.internetradio.core.media.recorder.RecordingConfig
import com.armanmaurya.internetradio.core.media.recorder.RecordingSink
import com.armanmaurya.internetradio.core.media.recorder.RecorderException
import com.armanmaurya.internetradio.core.utils.AudioFormat
import com.armanmaurya.internetradio.core.utils.AudioFormatUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DirectStreamRecorder(
    private val okHttpClient: OkHttpClient,
    private val onHlsDetected: (suspend (RecordingConfig) -> Unit)? = null
) {

    suspend fun record(
        config: RecordingConfig,
        sink: RecordingSink,
        onBytesWritten: (bytes: Long) -> Unit,
        onFileOpened: (stationFolderName: String, fileName: String) -> Unit = { _, _ -> },
        onTrackMetadata: (rawTrack: String, audioOffsetMs: Long) -> Unit = { _, _ -> },
        onAudioProgress: (audioDurationMs: Long) -> Unit = {},
        onReconnectingStateChanged: (Boolean) -> Unit = {}
    ) {
        val requestBuilder = Request.Builder()
            .url(config.url)
            .header("Icy-MetaData", "1")
            .header("Connection", "close")

        if (!config.userAgent.isNullOrBlank()) {
            requestBuilder.header("User-Agent", config.userAgent)
        }

        val request = requestBuilder.build()
        val initialResponse = try {
            okHttpClient.newCall(request).execute()
        } catch (e: Exception) {
            throw RecorderException.StreamConnectionException(config.url, e)
        }

        if (!initialResponse.isSuccessful) {
            initialResponse.close()
            throw RecorderException.StreamConnectionException(
                config.url,
                java.io.IOException("HTTP error ${initialResponse.code}: ${initialResponse.message}")
            )
        }

        val contentType = initialResponse.header("Content-Type", "") ?: ""
        if (AudioFormatUtils.isHlsContentType(contentType) && onHlsDetected != null) {
            initialResponse.close()
            onHlsDetected(config)
            return
        }

        val initialIcyBr = initialResponse.header("icy-br")?.toIntOrNull()
        val rawBodyStream = initialResponse.body.byteStream()
        val icyMetaInt = initialResponse.header("icy-metaint")?.toIntOrNull() ?: 0

        var bitrateKbps = initialIcyBr ?: config.bitrateKbps?.takeIf { it > 0 } ?: 128

        val demuxedStream: InputStream = if (icyMetaInt > 0) {
            IcyDemuxInputStream(
                delegate = rawBodyStream,
                metaInt = icyMetaInt,
                initialAudioBytesOffset = 0L,
                onTrackMetadata = { },
                onTrackMetadataWithOffset = { title, byteOffset ->
                    val offsetMs = (byteOffset * 8L) / bitrateKbps
                    onTrackMetadata(title, offsetMs)
                }
            )
        } else {
            rawBodyStream
        }

        val bodyStream = PushbackInputStream(demuxedStream, 4)
        val magic = ByteArray(4)
        val magicRead = bodyStream.read(magic, 0, 4)
        if (magicRead > 0) {
            bodyStream.unread(magic, 0, magicRead)
        }

        if (magicRead >= 4 &&
            magic[0] == '#'.code.toByte() &&
            magic[1] == 'E'.code.toByte() &&
            magic[2] == 'X'.code.toByte() &&
            magic[3] == 'T'.code.toByte() &&
            onHlsDetected != null
        ) {
            initialResponse.close()
            onHlsDetected(config)
            return
        }

        if (initialIcyBr == null && config.bitrateKbps == null) {
            val probeResult = AudioFrameParser.parse(magic)
            if (probeResult.bitrate > 0) {
                bitrateKbps = probeResult.bitrate
            }
        }

        val format = AudioFormatUtils.audioFormatFromMagicBytes(magic)
        val outputStream = sink.open(format.extension) ?: run {
            initialResponse.close()
            throw RecorderException.SinkWriteException("Failed to open output sink for extension: ${format.extension}")
        }
        sink.finalFileName?.let { onFileOpened(sink.safeStationName, it) }

        var totalWritten = 0L
        var isReconnecting = false
        val oggState = if (format == AudioFormat.OGG) OggRemuxState() else null

        var currentStream: InputStream = bodyStream
        var currentResponse: Response? = initialResponse

        try {
            while (currentCoroutineContext().isActive) {
                try {
                    if (format == AudioFormat.OGG) {
                        coroutineScope {
                            OggRewriter.remuxStream(
                                input = currentStream,
                                output = outputStream,
                                scope = this,
                                job = null,
                                state = oggState!!,
                                onTrackMetadata = { },
                                onTrackMetadataWithOffset = { title, offsetMs ->
                                    onTrackMetadata(title, offsetMs)
                                },
                                onAudioProgress = onAudioProgress
                            ) { written ->
                                totalWritten += written
                                onBytesWritten(written.toLong())
                            }
                        }
                    } else {
                        val buf = ByteArray(config.bufferSizeBytes)
                        while (currentCoroutineContext().isActive) {
                            val len = currentStream.read(buf)
                            if (len == -1) break
                            outputStream.write(buf, 0, len)
                            totalWritten += len
                            val currentDurationMs = (totalWritten * 8L) / bitrateKbps
                            onAudioProgress(currentDurationMs)
                            onBytesWritten(len.toLong())
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Stream read failed (e.g. network lost or timeout), proceed to reconnect
                }

                if (!currentCoroutineContext().isActive) break

                try { currentResponse?.close() } catch (_: Exception) {}
                currentResponse = null

                if (!isReconnecting) {
                    isReconnecting = true
                    onReconnectingStateChanged(true)
                }

                val newResponse = attemptReconnect(request, config) ?: break
                currentResponse = newResponse

                val newRawBodyStream = newResponse.body.byteStream()
                val newIcyMetaInt = newResponse.header("icy-metaint")?.toIntOrNull() ?: 0
                val newIcyBr = newResponse.header("icy-br")?.toIntOrNull()
                if (newIcyBr != null && newIcyBr > 0) {
                    bitrateKbps = newIcyBr
                }
                currentStream = if (newIcyMetaInt > 0) {
                    IcyDemuxInputStream(
                        delegate = newRawBodyStream,
                        metaInt = newIcyMetaInt,
                        initialAudioBytesOffset = totalWritten,
                        onTrackMetadata = { },
                        onTrackMetadataWithOffset = { title, byteOffset ->
                            val offsetMs = (byteOffset * 8L) / bitrateKbps
                            onTrackMetadata(title, offsetMs)
                        }
                    )
                } else {
                    newRawBodyStream
                }

                if (isReconnecting) {
                    isReconnecting = false
                    onReconnectingStateChanged(false)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (currentCoroutineContext().isActive && e !is RecorderException) {
                throw RecorderException.StreamReadException("Stream recording error", e)
            }
        } finally {
            if (isReconnecting) {
                onReconnectingStateChanged(false)
            }
            try { currentResponse?.close() } catch (_: Exception) {}
            if (totalWritten > 0) {
                sink.finalizeFile()
            }
        }
    }

    private suspend fun attemptReconnect(
        request: Request,
        config: RecordingConfig
    ): Response? {
        val startTime = System.currentTimeMillis()
        var currentDelay = config.minRetryDelayMs

        while (currentCoroutineContext().isActive) {
            val elapsed = System.currentTimeMillis() - startTime
            val maxRetry = config.getMaxRetryDuration()
            if (maxRetry >= 0 && elapsed >= maxRetry) {
                return null
            }

            delay(currentDelay)
            if (!currentCoroutineContext().isActive) return null

            try {
                val resp = okHttpClient.newCall(request).execute()
                if (resp.isSuccessful) {
                    return resp
                }
                resp.close()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Connection failed, retry with backoff
            }

            currentDelay = (currentDelay * 2).coerceAtMost(config.maxRetryDelayMs)
        }
        return null
    }
}

internal class OggRemuxState {
    var initialSerialNumber: Int? = null
    var lastPageSequence: Long = -1L
    var lastGranulePosition: Long = 0L
    var isInitialStream: Boolean = true
    var sampleRate: Long? = null
}

internal object OggRewriter {
    // Standard Ogg CRC32 polynomial: 0x04c11db7
    private val crcTable = IntArray(256)

    init {
        for (i in 0 until 256) {
            var r = i shl 24
            for (j in 0 until 8) {
                r = if ((r and -0x80000000) != 0) {
                    (r shl 1) xor 0x04c11db7
                } else {
                    r shl 1
                }
            }
            crcTable[i] = r
        }
    }

    private fun updateCrc(crc: Int, data: ByteArray, offset: Int, length: Int): Int {
        var currentCrc = crc
        for (i in offset until offset + length) {
            val byteVal = data[i].toInt() and 0xFF
            val index = ((currentCrc ushr 24) xor byteVal) and 0xFF
            currentCrc = (currentCrc shl 8) xor crcTable[index]
        }
        return currentCrc
    }

    fun remuxStream(
        input: InputStream,
        output: OutputStream,
        scope: CoroutineScope,
        job: Job?,
        state: OggRemuxState = OggRemuxState(),
        onTrackMetadata: (String) -> Unit = {},
        onTrackMetadataWithOffset: ((String, Long) -> Unit)? = null,
        onAudioProgress: (Long) -> Unit = {},
        onBytesWritten: (Int) -> Unit
    ) {
        var firstGranulePosition = -1L
        var skippedHeadersCount = 0
        var lastWrittenGranule = -1L

        val headerBuf = ByteArray(27)
        val segmentTable = ByteArray(255)
        var pageData = ByteArray(65536)

        try {
            while (scope.isActive && (job == null || job.isActive)) {
                // Find OggS sync
                var synced = false
                while (scope.isActive && (job == null || job.isActive)) {
                    val b = input.read()
                    if (b == -1) return
                    if (b == 0x4F) { // 'O'
                        headerBuf[0] = b.toByte()
                        if (readFully(input, headerBuf, 3, 1)) {
                            if (headerBuf[1] == 0x67.toByte() && headerBuf[2] == 0x67.toByte() && headerBuf[3] == 0x53.toByte()) {
                                synced = true
                                break
                            }
                        }
                    }
                }

                if (!synced) break

                // Read the rest of the 27-byte header
                if (!readFully(input, headerBuf, 23, 4)) break

                // Read Granule Position (bytes 6-13, little endian)
                val buffer = ByteBuffer.wrap(headerBuf, 6, 8).order(ByteOrder.LITTLE_ENDIAN)
                val originalGranule = buffer.long

                // Read Stream Serial Number (bytes 14-17, little endian)
                val serialBuffer = ByteBuffer.wrap(headerBuf, 14, 4).order(ByteOrder.LITTLE_ENDIAN)
                val streamSerial = serialBuffer.int

                // Read segment count
                val segmentCount = headerBuf[26].toInt() and 0xFF

                // Read segment table
                if (segmentCount > 0) {
                    if (!readFully(input, segmentTable, segmentCount, 0)) break
                }

                var dataSize = 0
                for (i in 0 until segmentCount) {
                    dataSize += segmentTable[i].toInt() and 0xFF
                }

                // Resize pageData buffer if needed
                if (pageData.size < dataSize) {
                    pageData = ByteArray(dataSize)
                }

                // Read page data
                if (dataSize > 0) {
                    if (!readFully(input, pageData, dataSize, 0)) break
                    if (state.sampleRate == null) {
                        if (dataSize >= 16 && pageData[0] == 1.toByte() && pageData[1] == 'v'.code.toByte()) {
                            val sr = ByteBuffer.wrap(pageData, 12, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong()
                            if (sr in 8000L..192000L) state.sampleRate = sr
                        }
                        if (state.sampleRate == null) {
                            state.sampleRate = 48000L
                        }
                    }
                    extractOggTrackTitle(pageData, dataSize)?.let { title ->
                        onTrackMetadata(title)
                        val offsetMs = if (lastWrittenGranule > 0) {
                            (lastWrittenGranule * 1000L) / (state.sampleRate ?: 48000L)
                        } else 0L
                        onTrackMetadataWithOffset?.invoke(title, offsetMs)
                    }
                }

                if (!state.isInitialStream && skippedHeadersCount < 3) {
                    // For reconnected streams, the server resends the initial 3 header pages (BOS identification, comments, codebooks).
                    // We extract track title metadata above, but omit writing duplicate BOS/setup headers to maintain a single bitstream.
                    skippedHeadersCount++
                    continue
                }

                if (state.initialSerialNumber == null) {
                    state.initialSerialNumber = streamSerial
                }

                // Ensure the stream serial number matches the initial stream
                val serialOutBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                serialOutBuffer.putInt(state.initialSerialNumber!!)
                System.arraycopy(serialOutBuffer.array(), 0, headerBuf, 14, 4)

                // Clear BOS flag on reconnected streams so decoders treat it as a continuous bitstream
                if (!state.isInitialStream) {
                    headerBuf[5] = (headerBuf[5].toInt() and 0x02.inv()).toByte()
                }

                // Increment and rewrite page sequence number monotonically
                state.lastPageSequence++
                val seqBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                seqBuffer.putInt((state.lastPageSequence and 0xFFFFFFFFL).toInt())
                System.arraycopy(seqBuffer.array(), 0, headerBuf, 18, 4)

                // Rewrite Granule Position
                var newGranule = originalGranule
                if (originalGranule != -1L && originalGranule != 0L) {
                    if (firstGranulePosition == -1L) {
                        firstGranulePosition = originalGranule
                    }
                    val streamOffset = (originalGranule - firstGranulePosition).coerceAtLeast(0L)
                    newGranule = if (state.isInitialStream) {
                        streamOffset
                    } else {
                        state.lastGranulePosition + streamOffset
                    }

                    val newBuffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                    newBuffer.putLong(newGranule)
                    val newGranuleBytes = newBuffer.array()
                    System.arraycopy(newGranuleBytes, 0, headerBuf, 6, 8)
                    lastWrittenGranule = newGranule
                }

                // Clear the CRC field (bytes 22-25) before calculating
                headerBuf[22] = 0
                headerBuf[23] = 0
                headerBuf[24] = 0
                headerBuf[25] = 0

                // Calculate CRC
                var crc = 0
                crc = updateCrc(crc, headerBuf, 0, 27)
                if (segmentCount > 0) {
                    crc = updateCrc(crc, segmentTable, 0, segmentCount)
                }
                if (dataSize > 0) {
                    crc = updateCrc(crc, pageData, 0, dataSize)
                }

                // Write CRC (little endian)
                headerBuf[22] = (crc and 0xFF).toByte()
                headerBuf[23] = ((crc ushr 8) and 0xFF).toByte()
                headerBuf[24] = ((crc ushr 16) and 0xFF).toByte()
                headerBuf[25] = ((crc ushr 24) and 0xFF).toByte()

                // Write out the modified page
                output.write(headerBuf, 0, 27)
                if (segmentCount > 0) {
                    output.write(segmentTable, 0, segmentCount)
                }
                if (dataSize > 0) {
                    output.write(pageData, 0, dataSize)
                }

                val pageSize = 27 + segmentCount + dataSize
                onBytesWritten(pageSize)
                if (newGranule > 0) {
                    val currentDurationMs = (newGranule * 1000L) / (state.sampleRate ?: 48000L)
                    onAudioProgress(currentDurationMs)
                }
            }
        } finally {
            if (lastWrittenGranule >= 0) {
                state.lastGranulePosition = lastWrittenGranule
            }
            state.isInitialStream = false
        }
    }

    internal fun extractOggTrackTitle(data: ByteArray, length: Int): String? {
        if (length < 16) return null
        val commentOffset = when {
            length >= 15 &&
                data[0] == 0x03.toByte() &&
                data[1] == 'v'.code.toByte() &&
                data[2] == 'o'.code.toByte() &&
                data[3] == 'r'.code.toByte() &&
                data[4] == 'b'.code.toByte() &&
                data[5] == 'i'.code.toByte() &&
                data[6] == 's'.code.toByte() -> 7
            length >= 16 &&
                data[0] == 'O'.code.toByte() &&
                data[1] == 'p'.code.toByte() &&
                data[2] == 'u'.code.toByte() &&
                data[3] == 's'.code.toByte() &&
                data[4] == 'T'.code.toByte() &&
                data[5] == 'a'.code.toByte() &&
                data[6] == 'g'.code.toByte() &&
                data[7] == 's'.code.toByte() -> 8
            else -> return null
        }

        return try {
            val buf = ByteBuffer.wrap(data, 0, length).order(ByteOrder.LITTLE_ENDIAN)
            buf.position(commentOffset)
            if (buf.remaining() < 4) return null
            val vendorLen = buf.int
            if (vendorLen < 0 || buf.remaining() < vendorLen + 4) return null
            buf.position(buf.position() + vendorLen)

            val commentCount = buf.int
            if (commentCount <= 0 || commentCount > 256) return null

            var title: String? = null
            var artist: String? = null

            for (i in 0 until commentCount) {
                if (buf.remaining() < 4) break
                val cLen = buf.int
                if (cLen < 0 || buf.remaining() < cLen) break
                val comment = String(data, buf.position(), cLen, Charsets.UTF_8)
                buf.position(buf.position() + cLen)

                when {
                    comment.startsWith("TITLE=", ignoreCase = true) -> {
                        title = comment.substring(6).trim().takeIf { it.isNotBlank() }
                    }
                    comment.startsWith("ARTIST=", ignoreCase = true) -> {
                        artist = comment.substring(7).trim().takeIf { it.isNotBlank() }
                    }
                }
            }

            when {
                !artist.isNullOrBlank() && !title.isNullOrBlank() -> "$artist - $title"
                !title.isNullOrBlank() -> title
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray, length: Int, offset: Int): Boolean {
        var totalRead = 0
        while (totalRead < length) {
            val read = input.read(buffer, offset + totalRead, length - totalRead)
            if (read == -1) return false
            totalRead += read
        }
        return true
    }
}
