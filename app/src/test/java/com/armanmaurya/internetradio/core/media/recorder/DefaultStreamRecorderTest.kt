package com.armanmaurya.internetradio.core.media.recorder

import com.armanmaurya.internetradio.core.media.recorder.backend.DirectStreamRecorder
import com.armanmaurya.internetradio.core.utils.AudioFormatUtils
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DefaultStreamRecorderTest {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    @Test
    fun isHlsUrl_identifiesHlsAndDashCorrectly() {
        assertTrue(AudioFormatUtils.isHlsUrl("https://example.com/live/playlist.m3u8"))
        assertTrue(AudioFormatUtils.isHlsUrl("https://example.com/live/playlist.m3u8?token=12345"))
        assertTrue(AudioFormatUtils.isHlsUrl("https://example.com/live/STREAM.M3U8"))
        assertTrue(AudioFormatUtils.isHlsUrl("https://example.com/manifest.mpd"))
        assertTrue(AudioFormatUtils.isHlsUrl("https://example.com/manifest.mpd?query=abc"))

        assertFalse(AudioFormatUtils.isHlsUrl("https://kathy.torontocast.com:2695/stream"))
        assertFalse(AudioFormatUtils.isHlsUrl("http://stream.example.com:8000/live"))
        assertFalse(AudioFormatUtils.isHlsUrl("http://stream.example.com/audio.mp3"))
        assertFalse(AudioFormatUtils.isHlsUrl("http://stream.example.com/audio.aac"))
        assertFalse(AudioFormatUtils.isHlsUrl("http://stream.example.com/audio.ogg"))

        assertTrue(DefaultStreamRecorder.isHlsUrl("https://example.com/live/playlist.m3u8"))
        assertFalse(DefaultStreamRecorder.isHlsUrl("https://kathy.torontocast.com:2695/stream"))
    }

    @Test
    fun directStreamRecorder_sendsConnectionCloseAndIcyHeaders() = runBlocking {
        val server = ServerSocket(0)
        val port = server.localPort
        val executor = Executors.newSingleThreadExecutor()

        val capturedHeaders = mutableMapOf<String, String>()

        executor.submit {
            val socket = server.accept()
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val firstLine = reader.readLine()
            var line = reader.readLine()
            while (!line.isNullOrEmpty()) {
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val key = line.substring(0, colon).trim().lowercase()
                    val value = line.substring(colon + 1).trim()
                    capturedHeaders[key] = value
                }
                line = reader.readLine()
            }
            val out = socket.getOutputStream()
            out.write("HTTP/1.1 200 OK\r\nContent-Type: audio/mpeg\r\nConnection: close\r\n\r\n".toByteArray())
            out.flush()
            socket.close()
            server.close()
        }

        val recorder = DirectStreamRecorder(
            okHttpClient = okHttpClient,
            onHlsDetected = null
        )

        val sink = RecordingSink(title = "Test Station")
        val config = RecordingConfig(
            url = "http://127.0.0.1:$port/stream",
            title = "Test Station"
        )

        try {
            recorder.record(
                config = config,
                sink = sink,
                onBytesWritten = {},
                onFileOpened = { _, _ -> },
                onTrackMetadata = { _, _ -> },
                onAudioProgress = {},
                onReconnectingStateChanged = {}
            )
        } catch (_: Exception) {
            // Sink.open will fail because context is null, which is expected
        }

        executor.shutdown()
        assertEquals("close", capturedHeaders["connection"])
        assertEquals("1", capturedHeaders["icy-metadata"])
    }

    @Test
    fun directStreamRecorder_triggersHlsFallbackWhenContentTypeIsMpegUrl() = runBlocking {
        val server = ServerSocket(0)
        val port = server.localPort
        val executor = Executors.newSingleThreadExecutor()

        executor.submit {
            val socket = server.accept()
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            var line = reader.readLine()
            while (!line.isNullOrEmpty()) {
                line = reader.readLine()
            }
            val out = socket.getOutputStream()
            val body = "#EXTM3U\n#EXTINF:10,\nsegment1.ts\n"
            val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/vnd.apple.mpegurl\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body"
            out.write(resp.toByteArray())
            out.flush()
            socket.close()
            server.close()
        }

        var hlsDetected = false
        val recorder = DirectStreamRecorder(
            okHttpClient = okHttpClient,
            onHlsDetected = { hlsDetected = true }
        )

        val sink = RecordingSink(title = "Test Station")
        val config = RecordingConfig(
            url = "http://127.0.0.1:$port/stream",
            title = "Test Station"
        )

        recorder.record(
            config = config,
            sink = sink,
            onBytesWritten = {},
            onFileOpened = { _, _ -> },
            onTrackMetadata = { _, _ -> },
            onAudioProgress = {},
            onReconnectingStateChanged = {}
        )

        executor.shutdown()
        assertTrue("Expected onHlsDetected to be called when Content-Type is mpegurl", hlsDetected)
    }

    @Test
    fun directStreamRecorder_triggersHlsFallbackWhenBodyStartsWithExtM3u() = runBlocking {
        val server = ServerSocket(0)
        val port = server.localPort
        val executor = Executors.newSingleThreadExecutor()

        executor.submit {
            val socket = server.accept()
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            var line = reader.readLine()
            while (!line.isNullOrEmpty()) {
                line = reader.readLine()
            }
            val out = socket.getOutputStream()
            val body = "#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:10,\nsegment1.ts\n"
            val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body"
            out.write(resp.toByteArray())
            out.flush()
            socket.close()
            server.close()
        }

        var hlsDetected = false
        val recorder = DirectStreamRecorder(
            okHttpClient = okHttpClient,
            onHlsDetected = { hlsDetected = true }
        )

        val sink = RecordingSink(title = "Test Station")
        val config = RecordingConfig(
            url = "http://127.0.0.1:$port/stream",
            title = "Test Station"
        )

        recorder.record(
            config = config,
            sink = sink,
            onBytesWritten = {},
            onFileOpened = { _, _ -> },
            onTrackMetadata = { _, _ -> },
            onAudioProgress = {},
            onReconnectingStateChanged = {}
        )

        executor.shutdown()
        assertTrue("Expected onHlsDetected to be called when body starts with #EXT", hlsDetected)
    }
}
