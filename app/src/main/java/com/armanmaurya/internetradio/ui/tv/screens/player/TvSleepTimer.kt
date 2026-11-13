package com.armanmaurya.internetradio.ui.tv.screens.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme

import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.armanmaurya.internetradio.R
import kotlinx.coroutines.delay

private val SleepTimerOptionsMinutes = listOf(15, 30, 45, 60, 90, 120)

/** Whole minutes left on the sleep timer (rounded up), or null when it's off. Ticks while shown. */
@Composable
fun rememberSleepTimerMinutesLeft(endTime: Long?): Int? {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endTime) {
        while (endTime != null) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }
    return endTime?.let { ((it - now).coerceAtLeast(0L) / 60_000L).toInt() + 1 }
}

/** Sleep timer picker: preset durations, plus "Turn Off Timer" while one is running. */
@Composable
fun TvSleepTimerDialog(
    endTime: Long?,
    onSetTimer: (Long) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val minutesLeft = rememberSleepTimerMinutesLeft(endTime)
    val firstItemFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            firstItemFocus.requestFocus()
        } catch (_: Exception) {
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.width(420.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = stringResource(R.string.player_sleep_timer_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 12.dp)
                )
                if (minutesLeft != null) {
                    Text(
                        text = pluralStringResource(R.plurals.player_timer_ends_in_msg, minutesLeft, minutesLeft),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
                    )
                }

                if (endTime != null) {
                    ListItem(
                        selected = false,
                        onClick = {
                            onCancelTimer()
                            onDismiss()
                        },
                        headlineContent = { Text(stringResource(R.string.player_turn_off_timer)) },
                        leadingContent = { Icon(Icons.Default.TimerOff, contentDescription = null) },
                        scale = ListItemDefaults.scale(focusedScale = 1.02f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(firstItemFocus)
                    )
                }

                SleepTimerOptionsMinutes.forEachIndexed { index, minutes ->
                    ListItem(
                        selected = false,
                        onClick = {
                            onSetTimer(minutes * 60_000L)
                            onDismiss()
                        },
                        headlineContent = { Text(stringResource(R.string.player_timer_min, minutes)) },
                        leadingContent = { Icon(Icons.Default.Timer, contentDescription = null) },
                        scale = ListItemDefaults.scale(focusedScale = 1.02f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (endTime == null && index == 0) Modifier.focusRequester(firstItemFocus)
                                else Modifier
                            )
                    )
                }
            }
        }
    }
}
