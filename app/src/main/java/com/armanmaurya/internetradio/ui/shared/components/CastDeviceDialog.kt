package com.armanmaurya.internetradio.ui.shared.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.CastDevice

/** Shared-bounds key linking the player's cast button to this dialog (container transform). */
const val CAST_CONTAINER_KEY = "cast_container"

/** Shared-element key for the cast icon, which travels from the button into the dialog. */
const val CAST_ICON_KEY = "cast_icon"

private val DialogShape = RoundedCornerShape(28.dp)

/**
 * Cast device picker drawn inside the player (not a separate window) so it can grow out of the
 * cast button with a shared-bounds container transform, like the volume and sleep timer dialogs.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.CastDeviceDialog(
    showDialog: Boolean,
    devices: List<CastDevice>,
    connectedDevice: CastDevice?,
    onConnect: (CastDevice) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(enabled = showDialog, onBack = onDismiss)

    AnimatedVisibility(
        visible = showDialog,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(300)),
        modifier = Modifier.zIndex(100f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .sharedBounds(
                        sharedContentState = rememberSharedContentState(key = CAST_CONTAINER_KEY),
                        animatedVisibilityScope = this@AnimatedVisibility,
                        enter = fadeIn(tween(300)),
                        exit = fadeOut(tween(300)),
                        boundsTransform = { _, _ -> tween(durationMillis = 350) },
                        clipInOverlayDuringTransition = OverlayClip(DialogShape)
                    )
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, DialogShape)
                    .clip(DialogShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Consume clicks so the scrim doesn't dismiss
                    )
                    .padding(24.dp)
            ) {
                Icon(
                    imageVector = if (connectedDevice != null) Icons.Default.CastConnected else Icons.Default.Cast,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .sharedElement(
                            sharedContentState = rememberSharedContentState(key = CAST_ICON_KEY),
                            animatedVisibilityScope = this@AnimatedVisibility,
                            boundsTransform = { _, _ -> tween(durationMillis = 350) }
                        )
                        .size(32.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (connectedDevice != null)
                        stringResource(R.string.cast_connected_to)
                    else
                        stringResource(R.string.cast_to_device),
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (connectedDevice != null) {
                    Text(
                        text = connectedDevice.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        text = stringResource(R.string.cast_available_devices),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (devices.isEmpty()) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
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

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (connectedDevice != null) {
                        TextButton(onClick = {
                            onDisconnect()
                            onDismiss()
                        }) {
                            Text(
                                stringResource(R.string.cast_disconnect),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cast_close))
                    }
                }
            }
        }
    }
}
