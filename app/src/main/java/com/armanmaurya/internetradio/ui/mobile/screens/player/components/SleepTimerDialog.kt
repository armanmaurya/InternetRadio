package com.armanmaurya.internetradio.ui.mobile.screens.player.components

import android.widget.NumberPicker
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.armanmaurya.internetradio.R
import kotlinx.coroutines.delay

@Composable
fun SleepTimerDialog(
    activeTimerEndTime: Long?,
    onDismissRequest: () -> Unit,
    onSetTimer: (Long) -> Unit,
    onCancelTimer: () -> Unit
) {
    if (activeTimerEndTime != null) {
        var remaining by remember { mutableLongStateOf(activeTimerEndTime - System.currentTimeMillis()) }
        LaunchedEffect(activeTimerEndTime) {
            while (true) {
                remaining = activeTimerEndTime - System.currentTimeMillis()
                delay(1000)
            }
        }
        
        AlertDialog(
            onDismissRequest = onDismissRequest,
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text(stringResource(R.string.player_sleep_timer_title)) },
            text = {
                val mins = (remaining / 60000).toInt() + 1
                Text(stringResource(R.string.player_timer_ends_in_msg, mins.toString()))
            },
            confirmButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(R.string.general_ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onCancelTimer()
                        onDismissRequest()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.player_turn_off_timer))
                }
            }
        )
    } else {
        var hours by remember { mutableIntStateOf(0) }
        var minutes by remember { mutableIntStateOf(15) }
        val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
        AlertDialog(
            onDismissRequest = onDismissRequest,
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text(stringResource(R.string.player_set_sleep_timer)) },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.general_hours), style = MaterialTheme.typography.labelMedium)
                        AndroidView(
                            factory = { context ->
                                NumberPicker(context).apply {
                                    minValue = 0
                                    maxValue = 23
                                    value = hours
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                        this.textColor = textColor
                                    }
                                    setOnValueChangedListener { _, _, newVal ->
                                        hours = newVal
                                    }
                                }
                            },
                            update = { view ->
                                view.value = hours
                            }
                        )
                    }
                    Text(":", style = MaterialTheme.typography.headlineMedium)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.general_minutes), style = MaterialTheme.typography.labelMedium)
                        AndroidView(
                            factory = { context ->
                                NumberPicker(context).apply {
                                    minValue = 0
                                    maxValue = 59
                                    value = minutes
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                        this.textColor = textColor
                                    }
                                    setOnValueChangedListener { _, _, newVal ->
                                        minutes = newVal
                                    }
                                }
                            },
                            update = { view ->
                                view.value = minutes
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val durationMillis = (hours * 60 * 60 * 1000L) + (minutes * 60 * 1000L)
                    if (durationMillis > 0) {
                        onSetTimer(durationMillis)
                    }
                    onDismissRequest()
                }) {
                    Text(stringResource(R.string.player_start_button))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(R.string.general_cancel))
                }
            }
        )
    }
}
