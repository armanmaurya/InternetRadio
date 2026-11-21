package com.armanmaurya.internetradio.ui.mobile.screens.player.components

import android.widget.NumberPicker
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.armanmaurya.internetradio.R
import kotlinx.coroutines.delay

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.SleepTimerDialog(
    showDialog: Boolean,
    activeTimerEndTime: Long?,
    timerProgress: Float = 0f,
    onDismissRequest: () -> Unit,
    onSetTimer: (Long) -> Unit,
    onCancelTimer: () -> Unit
) {
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
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .sharedBounds(
                        sharedContentState = rememberSharedContentState(key = "sleep_timer_container"),
                        animatedVisibilityScope = this@AnimatedVisibility,
                        enter = fadeIn(tween(300)),
                        exit = fadeOut(tween(300)),
                        boundsTransform = { _, _ -> tween(durationMillis = 350) },
                        clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(28.dp))
                    )
                    .widthIn(min = 280.dp, max = 360.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(28.dp)
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Consume clicks inside the dialog
                    )
                    .padding(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .sharedElement(
                                sharedContentState = rememberSharedContentState(key = "sleep_timer_icon"),
                                animatedVisibilityScope = this@AnimatedVisibility,
                                boundsTransform = { _, _ -> tween(durationMillis = 350) }
                            )
                            .size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.player_sleep_timer_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                if (activeTimerEndTime != null) {
                    var remaining by remember(activeTimerEndTime) {
                        mutableLongStateOf((activeTimerEndTime - System.currentTimeMillis()).coerceAtLeast(0L))
                    }
                    LaunchedEffect(activeTimerEndTime) {
                        while (true) {
                            val diff = activeTimerEndTime - System.currentTimeMillis()
                            if (diff <= 0L) {
                                remaining = 0L
                                onCancelTimer()
                                break
                            }
                            remaining = diff
                            delay(1000)
                        }
                    }
                    
                    val safeRemaining = remaining.coerceAtLeast(0L)
                    val mins = ((safeRemaining + 59999L) / 60000L).toInt().coerceAtLeast(0)
                    Text(
                        text = pluralStringResource(
                            R.plurals.player_timer_ends_in_msg,
                            mins,
                            mins
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    LinearProgressIndicator(
                        progress = { timerProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                onCancelTimer()
                                onDismissRequest()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(stringResource(R.string.player_turn_off_timer))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = onDismissRequest) {
                            Text(stringResource(R.string.general_ok))
                        }
                    }
                } else {
                    var selectedMinutes by remember { mutableIntStateOf(15) }
                    val options = listOf(15, 30, 45, 60, 90, 120)
                    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            options.take(3).forEach { mins ->
                                val isSelected = selectedMinutes == mins
                                val label = stringResource(R.string.player_timer_min, mins)
                                
                                val buttonColors = if (isSelected) {
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f),
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                
                                Button(
                                    onClick = { selectedMinutes = mins },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = buttonColors
                                ) {
                                    Text(label)
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            options.drop(3).forEach { mins ->
                                val isSelected = selectedMinutes == mins
                                val label = stringResource(R.string.player_timer_min, mins)
                                
                                val buttonColors = if (isSelected) {
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f),
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                
                                Button(
                                    onClick = { selectedMinutes = mins },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = buttonColors
                                ) {
                                    Text(label)
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismissRequest) {
                            Text(stringResource(R.string.general_cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = {
                            val durationMillis = selectedMinutes * 60 * 1000L
                            if (durationMillis > 0) {
                                onSetTimer(durationMillis)
                            }
                            onDismissRequest()
                        }) {
                            Text(stringResource(R.string.player_start_button))
                        }
                    }
                }
            }
        }
    }
}
