package com.armanmaurya.internetradio.player

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.fcast.sender_sdk.CastContext
import org.fcast.sender_sdk.CastingDevice
import org.fcast.sender_sdk.DeviceConnectionState
import org.fcast.sender_sdk.DeviceDiscovererEventHandler
import org.fcast.sender_sdk.DeviceEventHandler
import org.fcast.sender_sdk.DeviceInfo
import org.fcast.sender_sdk.KeyEvent
import org.fcast.sender_sdk.LoadRequest
import org.fcast.sender_sdk.MediaEvent
import org.fcast.sender_sdk.NsdDeviceDiscoverer
import org.fcast.sender_sdk.PlaybackState
import org.fcast.sender_sdk.Source
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CastController @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val castContext = CastContext()
    private val deviceDiscoverer: NsdDeviceDiscoverer
    
    private val _discoveredDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val discoveredDevices: StateFlow<List<DeviceInfo>> = _discoveredDevices.asStateFlow()

    private val _connectedDevice = MutableStateFlow<CastingDevice?>(null)
    val connectedDevice: StateFlow<CastingDevice?> = _connectedDevice.asStateFlow()

    private val _playbackState = MutableStateFlow<PlaybackState?>(null)
    val playbackState: StateFlow<PlaybackState?> = _playbackState.asStateFlow()

    private val _volume = MutableStateFlow(1.0)
    val volume: StateFlow<Double> = _volume.asStateFlow()

    private val _time = MutableStateFlow(0.0)
    val time: StateFlow<Double> = _time.asStateFlow()
    
    private val discoveryEventHandler = object : DeviceDiscovererEventHandler {
        override fun deviceAvailable(deviceInfo: DeviceInfo) {
            _discoveredDevices.update { current ->
                val updated = current.toMutableList()
                updated.removeIf { it.name == deviceInfo.name }
                updated.add(deviceInfo)
                updated
            }
        }

        override fun deviceChanged(deviceInfo: DeviceInfo) {
            _discoveredDevices.update { current ->
                val updated = current.toMutableList()
                val index = updated.indexOfFirst { it.name == deviceInfo.name }
                if (index != -1) {
                    updated[index] = deviceInfo
                } else {
                    updated.add(deviceInfo)
                }
                updated
            }
        }

        override fun deviceRemoved(deviceName: String) {
            _discoveredDevices.update { current ->
                current.filterNot { it.name == deviceName }
            }
        }
    }

    init {
        deviceDiscoverer = NsdDeviceDiscoverer(context, discoveryEventHandler)
    }

    fun connectToDevice(deviceInfo: DeviceInfo) {
        if (deviceInfo.port != 0.toUShort() && deviceInfo.addresses.isNotEmpty()) {
            val newDevice = castContext.createDeviceFromInfo(deviceInfo)
            newDevice.connect(null, createDeviceEventHandler(newDevice), 1000u)
            _connectedDevice.value = newDevice
        }
    }

    fun disconnect() {
        // Drop the reference.
        _connectedDevice.value = null
        _playbackState.value = null
    }

    private var pendingLoadUrl: String? = null
    private var pendingTitle: String? = null
    private var pendingThumbnailUrl: String? = null

    fun load(url: String, contentType: String = "audio/mpeg", resumePosition: Double = 0.0, title: String? = null, thumbnailUrl: String? = null) {
        pendingLoadUrl = url
        pendingTitle = title
        pendingThumbnailUrl = thumbnailUrl
        
        val metadata = if (title != null) org.fcast.sender_sdk.Metadata(title, thumbnailUrl ?: "") else null

        try {
            _connectedDevice.value?.load(
                LoadRequest.Url(
                    contentType = contentType,
                    url = url,
                    resumePosition = resumePosition,
                    speed = null,
                    volume = null,
                    metadata = metadata,
                    requestHeaders = null
                )
            )
        } catch (e: Exception) {
            Log.e("CastController", "Load failed: ${e.message}")
            disconnect()
        }
    }

    fun play() {
        try {
            _connectedDevice.value?.resumePlayback()
        } catch (e: Exception) {
            disconnect()
        }
    }

    fun pause() {
        try {
            _connectedDevice.value?.pausePlayback()
        } catch (e: Exception) {
            disconnect()
        }
    }

    fun stop() {
        try {
            _connectedDevice.value?.stopPlayback()
        } catch (e: Exception) {
            disconnect()
        }
    }

    fun seek(time: Double) {
        // _connectedDevice.value?.seek(time)
    }
    
    fun setVolume(volume: Double) {
        _connectedDevice.value?.changeVolume(volume)
        _volume.value = volume
    }

    private fun createDeviceEventHandler(device: CastingDevice) = object : DeviceEventHandler {
        override fun connectionStateChanged(state: DeviceConnectionState) {
            Log.d("CastController", "Connection state changed: $state")
            if (state is DeviceConnectionState.Connected) {
                pendingLoadUrl?.let { url ->
                    val metadata = if (pendingTitle != null) org.fcast.sender_sdk.Metadata(pendingTitle!!, pendingThumbnailUrl ?: "") else null
                    device.load(
                        LoadRequest.Url(
                            contentType = "audio/mpeg",
                            url = url,
                            resumePosition = 0.0,
                            speed = null,
                            volume = null,
                            metadata = metadata,
                            requestHeaders = null
                        )
                    )
                    pendingLoadUrl = null
                }
            } else if (state !is DeviceConnectionState.Connecting) {
                 disconnect()
            }
        }

        override fun volumeChanged(volume: Double) {
            _volume.value = volume
        }

        override fun timeChanged(time: Double) {
            _time.value = time
        }

        override fun playbackStateChanged(state: PlaybackState) {
            _playbackState.value = state
        }

        override fun durationChanged(duration: Double) {}

        override fun speedChanged(speed: Double) {}

        override fun sourceChanged(source: Source) {}

        override fun keyEvent(event: KeyEvent) {}

        override fun mediaEvent(event: MediaEvent) {}

        override fun playbackError(message: String) {
            Log.e("CastController", "Playback error: $message")
        }
    }
}
