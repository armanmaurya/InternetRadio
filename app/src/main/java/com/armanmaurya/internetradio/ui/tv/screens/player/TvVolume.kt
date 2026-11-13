package com.armanmaurya.internetradio.ui.tv.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.armanmaurya.internetradio.R
import kotlin.math.roundToInt

private const val VOLUME_STEP_PERCENT = 5

/** "85%" for a 0..2 player volume. */
fun volumePercentLabel(volume: Float): String = "${(volume * 100).roundToInt()}%"

/**
 * In-app volume, adjusted with Left/Right in 5% steps (hold to keep going). Goes up to 200% when
 * volume boost is on. OK or Back closes it.
 */
@Composable
fun TvVolumeDialog(
    volume: Float,
    maxVolume: Float,
    onVolumeChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val maxPercent = (maxVolume * 100).roundToInt()
    // Local copy so quick repeated presses don't read a stale value from the player.
    var percent by remember { mutableIntStateOf((volume * 100).roundToInt().coerceIn(0, maxPercent)) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }

    fun step(direction: Int) {
        val next = (percent + direction * VOLUME_STEP_PERCENT).coerceIn(0, maxPercent)
        if (next != percent) {
            percent = next
            onVolumeChange(next / 100f)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.width(480.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .padding(24.dp)
                    .focusRequester(focusRequester)
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (event.key) {
                            Key.DirectionLeft -> { step(-1); true }
                            Key.DirectionRight -> { step(1); true }
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { onDismiss(); true }
                            // Swallow Up/Down so focus can't leave the dialog's only target.
                            Key.DirectionUp, Key.DirectionDown -> true
                            else -> false
                        }
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.player_cd_volume),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                    if (percent > 100) {
                        Text(
                            text = stringResource(R.string.player_volume_boost_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                        Box(Modifier.width(12.dp))
                    }
                    Text(text = "$percent%", style = MaterialTheme.typography.headlineSmall)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = null)
                    VolumeBar(
                        fraction = percent / maxPercent.toFloat(),
                        // Marks where 100% is on the boosted scale.
                        normalMark = if (maxPercent > 100) 100f / maxPercent else null,
                        isBoosted = percent > 100,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun VolumeBar(
    fraction: Float,
    normalMark: Float?,
    isBoosted: Boolean,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier = modifier
            .height(12.dp)
            .background(scheme.onSurface.copy(alpha = 0.15f), RoundedCornerShape(50))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(if (isBoosted) scheme.tertiary else scheme.primary, RoundedCornerShape(50))
        )
        if (normalMark != null) {
            Box(
                modifier = Modifier
                    .offset(x = maxWidth * normalMark - 1.dp)
                    .size(width = 2.dp, height = 12.dp)
                    .border(1.dp, scheme.onSurface.copy(alpha = 0.6f))
            )
        }
    }
}
