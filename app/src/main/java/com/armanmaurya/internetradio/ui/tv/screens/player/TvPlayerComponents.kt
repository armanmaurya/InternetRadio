package com.armanmaurya.internetradio.ui.tv.screens.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import java.util.Locale

/** Large primary button, used for Play/Pause. */
@Composable
fun TvPlayerPrimaryButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier,
        scale = ButtonDefaults.scale(focusedScale = 1.1f),
        colors = ButtonDefaults.colors(
            containerColor = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
            focusedContainerColor = scheme.primary,
            focusedContentColor = scheme.onPrimary
        ),
        shape = ButtonDefaults.shape(shape = CircleShape),
        contentPadding = PaddingValues(18.dp)
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(36.dp))
    }
}

/** Round tonal icon button (previous/next, library, edit). */
@Composable
fun TvPlayerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier,
        scale = ButtonDefaults.scale(focusedScale = 1.1f),
        colors = ButtonDefaults.colors(
            containerColor = scheme.surfaceVariant.copy(alpha = 0.6f),
            contentColor = if (isActive) scheme.primary else scheme.onSurface,
            focusedContainerColor = scheme.inverseSurface,
            focusedContentColor = scheme.inverseOnSurface
        ),
        shape = ButtonDefaults.shape(shape = CircleShape),
        contentPadding = PaddingValues(14.dp)
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(26.dp))
    }
}

/** Tonal pill button with icon and label (Tracks, Lyrics). [isSelected] marks an open panel. */
@Composable
fun TvPlayerPillButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier,
        scale = ButtonDefaults.scale(focusedScale = 1.08f),
        colors = ButtonDefaults.colors(
            containerColor = if (isSelected) scheme.secondaryContainer else scheme.surfaceVariant.copy(alpha = 0.6f),
            contentColor = if (isSelected) scheme.onSecondaryContainer else scheme.onSurface,
            focusedContainerColor = scheme.inverseSurface,
            focusedContentColor = scheme.inverseOnSurface
        ),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(50)),
        contentPadding = PaddingValues(start = 18.dp, end = 22.dp, top = 14.dp, bottom = 14.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Box(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
    }
}

/** "128 kbps · MP3 · India" built from what the stream and station report. */
fun stationInfoLine(bitrate: Int, codec: String, countryCode: String): String {
    val parts = buildList {
        if (bitrate > 0) add("$bitrate kbps")
        if (codec.isNotBlank() && !codec.equals("UNKNOWN", ignoreCase = true)) add(codec.uppercase())
        if (countryCode.isNotBlank()) {
            val country = Locale("", countryCode).getDisplayCountry(Locale.getDefault())
            if (country.isNotBlank() && !country.equals(countryCode, ignoreCase = true)) add(country)
        }
    }
    return parts.joinToString(" · ")
}
