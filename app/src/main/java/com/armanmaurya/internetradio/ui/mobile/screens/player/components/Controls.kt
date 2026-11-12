@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.armanmaurya.internetradio.ui.mobile.screens.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.IconToggleButtonShapes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.ui.mobile.screens.player.collapseHeight

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.Controls(
    historyProgress: Float,
    showSleepTimerDialog: Boolean,
    sleepTimerEndTime: Long?,
    remainingTime: Long,
    sleepTimerProgress: Float,
    hasPrevious: Boolean,
    hasNext: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    isRecording: Boolean,
    onOpenSleepTimer: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onToggleRecording: () -> Unit
) {
    // Expressive button group: the pressed button briefly widens while its neighbours shrink,
    // and every button morphs its shape on press (and on state for play/pause and record).
    val sleepInteraction = remember { MutableInteractionSource() }
    val previousInteraction = remember { MutableInteractionSource() }
    val playInteraction = remember { MutableInteractionSource() }
    val nextInteraction = remember { MutableInteractionSource() }
    val recordInteraction = remember { MutableInteractionSource() }

    ButtonGroup(
        overflowIndicator = {},
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .collapseHeight(historyProgress)
            .alpha(1f - historyProgress),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        customItem(
            buttonGroupContent = {
                Box(
                    modifier = Modifier
                        .weight(0.7f)
                        .animateWidth(sleepInteraction)
                        .height(64.dp)
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !showSleepTimerDialog,
                        enter = fadeIn(tween(300)),
                        exit = fadeOut(tween(300))
                    ) {
                        FilledTonalIconButton(
                            onClick = onOpenSleepTimer,
                            shapes = IconButtonShapes(
                                shape = RoundedCornerShape(20.dp),
                                pressedShape = RoundedCornerShape(12.dp)
                            ),
                            interactionSource = sleepInteraction,
                            modifier = Modifier
                                .sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "sleep_timer_container"),
                                    animatedVisibilityScope = this@AnimatedVisibility,
                                    enter = fadeIn(tween(300)),
                                    exit = fadeOut(tween(300)),
                                    boundsTransform = { _, _ -> tween(durationMillis = 350) },
                                    clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(20.dp))
                                )
                                .fillMaxSize()
                        ) {
                            if (sleepTimerEndTime != null) {
                                Box(contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(
                                        progress = { sleepTimerProgress },
                                        modifier = Modifier.size(28.dp),
                                        color = LocalContentColor.current,
                                        trackColor = LocalContentColor.current.copy(alpha = 0.2f),
                                        strokeWidth = 2.dp
                                    )
                                    val mins = (remainingTime / 60000).toInt() + 1
                                    Text(
                                        text = mins.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = LocalContentColor.current
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = stringResource(R.string.player_sleep_timer_title),
                                    modifier = Modifier
                                        .sharedElement(
                                            sharedContentState = rememberSharedContentState(key = "sleep_timer_icon"),
                                            animatedVisibilityScope = this@AnimatedVisibility,
                                            boundsTransform = { _, _ -> tween(durationMillis = 350) }
                                        )
                                        .size(28.dp)
                                )
                            }
                        }
                    }
                }
            },
            menuContent = {}
        )

        customItem(
            buttonGroupContent = {
                FilledIconButton(
                    onClick = onPrevious,
                    enabled = hasPrevious,
                    shapes = IconButtonShapes(
                        shape = CircleShape,
                        pressedShape = RoundedCornerShape(16.dp)
                    ),
                    interactionSource = previousInteraction,
                    modifier = Modifier
                        .animateWidth(previousInteraction)
                        .size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.player_cd_previous),
                        modifier = Modifier.size(32.dp)
                    )
                }
            },
            menuContent = {}
        )

        customItem(
            buttonGroupContent = {
                // Paused: full pill. Playing: rounded rectangle. Pressed: squarer still.
                FilledIconToggleButton(
                    checked = isPlaying,
                    onCheckedChange = { onTogglePlayPause() },
                    shapes = IconToggleButtonShapes(
                        shape = CircleShape,
                        pressedShape = RoundedCornerShape(8.dp),
                        checkedShape = RoundedCornerShape(16.dp)
                    ),
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    interactionSource = playInteraction,
                    modifier = Modifier
                        .weight(2f)
                        .animateWidth(playInteraction)
                        .height(64.dp)
                ) {
                    if (isLoading) {
                        LoadingIndicator(
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            },
            menuContent = {}
        )

        customItem(
            buttonGroupContent = {
                FilledIconButton(
                    onClick = onNext,
                    enabled = hasNext,
                    shapes = IconButtonShapes(
                        shape = CircleShape,
                        pressedShape = RoundedCornerShape(16.dp)
                    ),
                    interactionSource = nextInteraction,
                    modifier = Modifier
                        .animateWidth(nextInteraction)
                        .size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.player_cd_next),
                        modifier = Modifier.size(32.dp)
                    )
                }
            },
            menuContent = {}
        )

        customItem(
            buttonGroupContent = {
                // Recording changes the shape (rounded -> squarer) as well as the colour.
                FilledTonalIconToggleButton(
                    checked = isRecording,
                    onCheckedChange = { onToggleRecording() },
                    shapes = IconToggleButtonShapes(
                        shape = RoundedCornerShape(20.dp),
                        pressedShape = RoundedCornerShape(12.dp),
                        checkedShape = RoundedCornerShape(10.dp)
                    ),
                    colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        checkedContainerColor = MaterialTheme.colorScheme.error,
                        checkedContentColor = MaterialTheme.colorScheme.onError
                    ),
                    interactionSource = recordInteraction,
                    modifier = Modifier
                        .weight(0.7f)
                        .animateWidth(recordInteraction)
                        .height(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = stringResource(R.string.player_cd_record),
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            menuContent = {}
        )
    }
}
