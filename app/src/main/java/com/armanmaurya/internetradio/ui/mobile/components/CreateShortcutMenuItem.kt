package com.armanmaurya.internetradio.ui.mobile.components

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.controller.ShortcutController
import com.armanmaurya.internetradio.domain.model.RadioStation

/** Null outside the mobile app (previews), which hides the shortcut menu item. */
val LocalShortcutController = staticCompositionLocalOf<ShortcutController?> { null }

/**
 * "Create shortcut" for a library station, or a disabled "Shortcut added" when the station
 * already has one. Checked each time the menu opens, since the user can remove the icon any time.
 */
@Composable
fun CreateShortcutMenuItem(
    station: RadioStation,
    onDismissMenu: () -> Unit
) {
    val shortcutController = LocalShortcutController.current ?: return
    val context = LocalContext.current
    var hasShortcut by remember(station.stationUuid) { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(station.stationUuid) {
        hasShortcut = shortcutController.hasShortcut(station.stationUuid)
    }

    DropdownMenuItem(
        text = {
            Text(
                stringResource(
                    if (hasShortcut == true) R.string.action_shortcut_added else R.string.action_create_shortcut
                )
            )
        },
        onClick = {
            onDismissMenu()
            if (!shortcutController.pinStationShortcut(station)) {
                Toast.makeText(context, R.string.shortcut_not_supported, Toast.LENGTH_SHORT).show()
            }
        },
        enabled = hasShortcut == false,
        leadingIcon = {
            Icon(
                imageVector = if (hasShortcut == true) Icons.Default.Check else Icons.Default.Add,
                contentDescription = null
            )
        }
    )
}
