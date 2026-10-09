package com.armanmaurya.internetradio.core.media.prober.engine

import android.util.Log
import com.armanmaurya.internetradio.core.media.prober.ProbeResult
import com.armanmaurya.internetradio.core.media.prober.parser.AudioFrameParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.net.ProtocolException
import java.net.Socket
import java.net.URI
import javax.net.ssl.SSLSocketFactory

internal object IcyEngine {

    private const val TAG = "IcyEngine"

    suspend fun probe(
        url: String,
        initialName: String? = null,
        client: OkHttpClient
    ): ProbeResult? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Icy-MetaData", "1")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android) InternetRadio")
                .header("Accept", "*/*")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code !in 200..399) return null

                val contentType = response.header("Content-Type")?.lowercase() ?: ""
                var icyName = response.header("icy-name") ?: initialName
                var icyDescription = response.header("icy-description")
                var icyGenre = response.header("icy-genre")
                var icyUrl = response.header("icy-url")
                var detectedBitrate = response.header("icy-br")?.toIntOrNull() ?: 0

                // Parse ice-audio-info (e.g. ice-samplerate=44100;ice-bitrate=128;ice-channels=2)
                if (detectedBitrate == 0) {
                    val iceAudioInfo = response.header("ice-audio-info") ?: response.header("ice-bitrate")
                    if (!iceAudioInfo.isNullOrBlank()) {
                        val brMatch = Regex("""(?i)(?:bitrate|ice-bitrate)=(\d+)""").find(iceAudioInfo)
                        if (brMatch != null) {
                            detectedBitrate = brMatch.groupValues[1].toIntOrNull() ?: 0
                        }
                    }
                }

                // Fallback to x-audiocast-bitrate
                if (detectedBitrate == 0) {
                    detectedBitrate = response.header("x-audiocast-bitrate")?.toIntOrNull() ?: 0
                }

                var detectedCodec = when {
                    contentType.contains("flac") -> "FLAC"
                    contentType.contains("opus") -> "OPUS"
                    contentType.contains("vorbis") -> "VORBIS"
                    contentType.contains("ogg") -> "OGG"
                    contentType.contains("aacp") || contentType.contains("aac+") -> "AAC+"
                    contentType.contains("aac") -> "AAC"
                    contentType.contains("mpeg") || contentType.contains("mp3") -> "MP3"
                    contentType.contains("wav") || contentType.contains("wave") -> "WAV"
                    contentType.contains("wma") || contentType.contains("asf") -> "WMA"
                    else -> ""
                }

                val body = response.body ?: return null
                val byteStream = body.byteStream()

                // Check in-band ICY metadata if icy-metaint is present and title is still missing
                val icyMetaInt = response.header("icy-metaint")?.toIntOrNull() ?: 0
                if (icyName.isNullOrBlank() && icyMetaInt in 1..65536) {
                    try {
                        val inBandTitle = readInBandIcyTitle(byteStream, icyMetaInt)
                        if (!inBandTitle.isNullOrBlank()) {
                            icyName = inBandTitle
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "In-band ICY reading note: ${e.message}")
                    }
                }

                // Peek bytes from response body to parse audio frames
                try {
                    val peekBytes = response.peekBody(4096).bytes()
                    val frameResult = AudioFrameParser.parse(peekBytes, contentType)

                    if (detectedCodec.isBlank() && frameResult.codec.isNotBlank()) {
                        detectedCodec = frameResult.codec
                    }
                    if (detectedBitrate == 0 && frameResult.bitrate > 0) {
                        detectedBitrate = frameResult.bitrate
                    }
                    if (icyName.isNullOrBlank() && !frameResult.title.isNullOrBlank()) {
                        icyName = frameResult.title
                    }
                    if (icyGenre.isNullOrBlank() && !frameResult.genre.isNullOrBlank()) {
                        icyGenre = frameResult.genre
                    }
                    if (icyUrl.isNullOrBlank() && !frameResult.homepage.isNullOrBlank()) {
                        icyUrl = frameResult.homepage
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Audio frame parsing note: ${e.message}")
                }

                // If bitrate is still 0, measure throughput fallback over a short sample
                if (detectedBitrate == 0) {
                    detectedBitrate = measureThroughputBitrate(byteStream)
                }

                if (detectedCodec.isBlank() && detectedBitrate == 0 && icyName == null && icyGenre == null) {
                    return null
                }

                ProbeResult(
                    codec = detectedCodec.uppercase(),
                    bitrate = detectedBitrate,
                    name = icyName,
                    description = icyDescription,
                    genre = icyGenre,
                    homepage = icyUrl
                )
            }
        } catch (e: Exception) {
            // Handle legacy Shoutcast "ICY 200 OK" status line via direct socket fallback
            if (e is ProtocolException || e.message?.contains("ICY 200", ignoreCase = true) == true) {
                Log.d(TAG, "Detected legacy ICY 200 response, falling back to direct socket probe: ${e.message}")
                probeViaLegacySocket(url, initialName)
            } else {
                Log.d(TAG, "Error probing stream $url: ${e.message}")
                null
            }
        }
    }

    private fun probeViaLegacySocket(url: String, initialName: String?): ProbeResult? {
        return try {
            val uri = URI(url)
            val host = uri.host ?: return null
            val isHttps = uri.scheme.equals("https", ignoreCase = true)
            val port = if (uri.port > 0) uri.port else if (isHttps) 443 else 80
            val path = (uri.rawPath.takeIf { !it.isNullOrBlank() } ?: "/") + (if (uri.rawQuery != null) "?${uri.rawQuery}" else "")

            val socket: Socket = if (isHttps) {
                SSLSocketFactory.getDefault().createSocket(host, port)
            } else {
                Socket(host, port)
            }
            socket.soTimeout = 4000

            socket.getOutputStream().bufferedWriter(Charsets.US_ASCII).apply {
                write("GET $path HTTP/1.0\r\n")
                write("Host: $host\r\n")
                write("User-Agent: Mozilla/5.0 (Linux; Android) InternetRadio\r\n")
                write("Icy-MetaData: 1\r\n")
                write("Accept: */*\r\n")
                write("Connection: close\r\n\r\n")
                flush()
            }

            val inputStream = socket.getInputStream().buffered()
            var detectedBitrate = 0
            var icyName = initialName
            var icyGenre: String? = null
            var icyDescription: String? = null
            var icyUrl: String? = null
            var contentType = ""

            // Read headers line by line
            var line = readLineFromStream(inputStream)
            while (!line.isNullOrBlank()) {
                val lower = line.lowercase()
                when {
                    lower.startsWith("icy-br:") -> detectedBitrate = line.substringAfter(":").trim().toIntOrNull() ?: detectedBitrate
                    lower.startsWith("icy-name:") -> icyName = line.substringAfter(":").trim().takeIf { it.isNotBlank() } ?: icyName
                    lower.startsWith("icy-genre:") -> icyGenre = line.substringAfter(":").trim().takeIf { it.isNotBlank() }
                    lower.startsWith("icy-description:") -> icyDescription = line.substringAfter(":").trim().takeIf { it.isNotBlank() }
                    lower.startsWith("icy-url:") -> icyUrl = line.substringAfter(":").trim().takeIf { it.isNotBlank() }
                    lower.startsWith("content-type:") -> contentType = line.substringAfter(":").trim().lowercase()
                }
                line = readLineFromStream(inputStream)
            }

            val peekBytes = ByteArray(4096)
            var totalRead = 0
            val startTime = System.currentTimeMillis()
            while (totalRead < peekBytes.size && System.currentTimeMillis() - startTime < 1500) {
                val r = inputStream.read(peekBytes, totalRead, peekBytes.size - totalRead)
                if (r < 0) break
                totalRead += r
            }

            val frameResult = if (totalRead > 0) {
                AudioFrameParser.parse(peekBytes.copyOf(totalRead), contentType)
            } else {
                AudioFrameParser.AudioParseResult()
            }

            val codec = frameResult.codec.ifBlank {
                when {
                    contentType.contains("mpeg") || contentType.contains("mp3") -> "MP3"
                    contentType.contains("aac") -> "AAC"
                    contentType.contains("ogg") -> "OGG"
                    contentType.contains("flac") -> "FLAC"
                    else -> "MP3"
                }
            }

            val bitrate = if (detectedBitrate > 0) detectedBitrate else frameResult.bitrate
            socket.close()

            ProbeResult(
                codec = codec.uppercase(),
                bitrate = bitrate,
                name = icyName ?: frameResult.title,
                description = icyDescription,
                genre = icyGenre ?: frameResult.genre,
                homepage = icyUrl ?: frameResult.homepage
            )
        } catch (e: Exception) {
            Log.d(TAG, "Legacy socket probe failed: ${e.message}")
            null
        }
    }

    private fun readLineFromStream(stream: InputStream): String? {
        val sb = StringBuilder()
        var c = stream.read()
        if (c < 0) return null
        while (c >= 0 && c != '\n'.code) {
            if (c != '\r'.code) {
                sb.append(c.toChar())
            }
            c = stream.read()
        }
        return sb.toString()
    }

    private fun readInBandIcyTitle(stream: InputStream, metaInt: Int): String? {
        val skipped = stream.skip(metaInt.toLong())
        if (skipped < metaInt) {
            val remaining = (metaInt - skipped).toInt()
            val discardBuffer = ByteArray(remaining)
            var read = 0
            while (read < remaining) {
                val r = stream.read(discardBuffer, read, remaining - read)
                if (r < 0) return null
                read += r
            }
        }

        val lengthByte = stream.read()
        if (lengthByte <= 0) return null

        val metaLength = lengthByte * 16
        val metaBuffer = ByteArray(metaLength)
        var totalRead = 0
        while (totalRead < metaLength) {
            val r = stream.read(metaBuffer, totalRead, metaLength - totalRead)
            if (r < 0) break
            totalRead += r
        }

        val metaString = try {
            val utf8 = String(metaBuffer, 0, totalRead, Charsets.UTF_8)
            if (utf8.contains('\uFFFD')) String(metaBuffer, 0, totalRead, Charsets.ISO_8859_1) else utf8
        } catch (_: Exception) {
            String(metaBuffer, 0, totalRead, Charsets.ISO_8859_1)
        }

        val titleMatch = Regex("""StreamTitle='([^']*)'""").find(metaString)
        return titleMatch?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun measureThroughputBitrate(stream: InputStream): Int {
        return try {
            val buffer = ByteArray(4096)
            var totalBytes = 0L
            val startTime = System.currentTimeMillis()
            val sampleDurationMs = 350L

            while (System.currentTimeMillis() - startTime < sampleDurationMs) {
                val read = stream.read(buffer)
                if (read < 0) break
                totalBytes += read
            }

            val elapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(50L)
            val kbps = ((totalBytes * 8.0) / elapsedMs).toInt()
            if (kbps in 16..2000) kbps else 0
        } catch (_: Exception) {
            0
        }
    }
}
