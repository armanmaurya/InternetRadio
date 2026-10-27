package com.armanmaurya.internetradio.core.media.recorder

import android.content.Context
import com.armanmaurya.internetradio.core.media.prober.parser.PlaylistResolver
import com.armanmaurya.internetradio.core.media.recorder.backend.DirectStreamRecorder
import com.armanmaurya.internetradio.core.media.recorder.backend.HlsStreamRecorder
import com.armanmaurya.internetradio.core.system.FileSystemFacade
import com.armanmaurya.internetradio.core.utils.AudioFormatUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface StreamRecorder {
    suspend fun record(
        config: RecordingConfig,
        onBytesWritten: (bytes: Long) -> Unit,
        onFileOpened: (stationFolderName: String, fileName: String) -> Unit = { _, _ -> },
        onTrackMetadata: (rawTrack: String, audioOffsetMs: Long) -> Unit = { _, _ -> },
        onAudioProgress: (audioDurationMs: Long) -> Unit = {},
        onReconnectingStateChanged: (Boolean) -> Unit = {}
    )
}

@Singleton
class DefaultStreamRecorder @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val fileSystemFacade: FileSystemFacade,
    @ApplicationContext private val context: Context
) : StreamRecorder {

    private val recordingHttpClient = okHttpClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .cache(null)
        .connectionPool(ConnectionPool(0, 1, TimeUnit.MILLISECONDS))
        .build()

    override suspend fun record(
        config: RecordingConfig,
        onBytesWritten: (bytes: Long) -> Unit,
        onFileOpened: (stationFolderName: String, fileName: String) -> Unit,
        onTrackMetadata: (rawTrack: String, audioOffsetMs: Long) -> Unit,
        onAudioProgress: (audioDurationMs: Long) -> Unit,
        onReconnectingStateChanged: (Boolean) -> Unit
    ) {
        val resolvedConfig = if (PlaylistResolver.isPlaylistUrl(config.url)) {
            val resolved = withContext(Dispatchers.IO) {
                PlaylistResolver.resolve(config.url, okHttpClient)
            }
            if (resolved != null && resolved.streamUrl.isNotBlank()) {
                config.copy(url = resolved.streamUrl)
            } else {
                config
            }
        } else {
            config
        }

        val sink = RecordingSink(fileSystemFacade, resolvedConfig.title, context)
        try {
            if (isHlsUrl(resolvedConfig.url)) {
                HlsStreamRecorder(recordingHttpClient).record(
                    config = resolvedConfig,
                    sink = sink,
                    onBytesWritten = onBytesWritten,
                    onFileOpened = onFileOpened,
                    onTrackMetadata = onTrackMetadata,
                    onAudioProgress = onAudioProgress,
                    onReconnectingStateChanged = onReconnectingStateChanged
                )
            } else {
                DirectStreamRecorder(
                    okHttpClient = recordingHttpClient,
                    onHlsDetected = { hlsConfig ->
                        HlsStreamRecorder(recordingHttpClient).record(
                            config = hlsConfig,
                            sink = sink,
                            onBytesWritten = onBytesWritten,
                            onFileOpened = onFileOpened,
                            onTrackMetadata = onTrackMetadata,
                            onAudioProgress = onAudioProgress,
                            onReconnectingStateChanged = onReconnectingStateChanged
                        )
                    }
                ).record(
                    config = resolvedConfig,
                    sink = sink,
                    onBytesWritten = onBytesWritten,
                    onFileOpened = onFileOpened,
                    onTrackMetadata = onTrackMetadata,
                    onAudioProgress = onAudioProgress,
                    onReconnectingStateChanged = onReconnectingStateChanged
                )
            }
        } finally {
            sink.close()
        }
    }

    companion object {
        fun isHlsUrl(url: String): Boolean = AudioFormatUtils.isHlsUrl(url)
    }
}
