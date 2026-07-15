package com.armanmaurya.internetradio.ui.shared.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.armanmaurya.internetradio.R
import androidx.compose.ui.unit.dp
import org.fcast.sender_sdk.CastingDevice
import org.fcast.sender_sdk.DeviceInfo

@Composable
fun CastDeviceDialog(
    devices: List<DeviceInfo>,
    connectedDevice: CastingDevice?,
    onConnect: (DeviceInfo) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(if (connectedDevice != null) stringResource(R.string.cast_connected_to) else stringResource(R.string.cast_to_device)) 
        },
        text = {
            if (connectedDevice != null) {
                // Show connected device info
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CastConnected, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp).padding(bottom = 16.dp)
                    )
                    Text(
                        text = connectedDevice.name(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                // Show available devices list
                Column {
                    Text(
                        stringResource(R.string.cast_available_devices),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (devices.isEmpty()) {
                        Text(
                            stringResource(R.string.cast_no_devices_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn {
                            items(devices) { device ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onConnect(device) }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Cast, contentDescription = null)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = device.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (connectedDevice != null) {
                TextButton(onClick = { 
                    onDisconnect() 
                    onDismiss()
                }) {
                    Text(stringResource(R.string.cast_disconnect), color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cast_close))
                }
            }
        },
        dismissButton = {
            if (connectedDevice != null) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cast_close))
                }
            }
        }
    )
}
