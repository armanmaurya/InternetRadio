package com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.recordings.components

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay5
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.PlayingVisualizer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.RecordingFile
import com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.history.TrackHistoryItem

@Composable
fun NowPlayingRecordingCard(
    recording: RecordingFile,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    playbackSpeed: Float = 1.0f,
    isLooping: Boolean = false,
    onPlayPauseClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit = {},
    onToggleLoop: () -> Unit = {},
    onRouteClick: () -> Unit,
    onStopClick: () -> Unit,
    showTrackList: Boolean? = null,
    onToggleTrackList: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val effectiveDuration = if (durationMs > 0L) durationMs else if (recording.durationMs > 0L) recording.durationMs else 1L
    val progress = if (effectiveDuration > 0L) {
        (currentPositionMs.toFloat() / effectiveDuration).coerceIn(0f, 1f)
    } else 0f

    val sortedTracks = remember(recording.tracks) { recording.tracks.sortedBy { it.offsetMs } }
    val activeTrack = remember(sortedTracks, currentPositionMs) {
        if (sortedTracks.isEmpty()) null
        else sortedTracks.lastOrNull { it.offsetMs <= currentPositionMs } ?: sortedTracks.first()
    }
    var internalShowTrackList by remember(recording.uri.toString()) { mutableStateOf(false) }
    val isTrackListVisible = showTrackList ?: internalShowTrackList

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                
                .clickable(onClick = onRouteClick)
                .animateContentSize(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        PlayingVisualizer(
                            isPlaybackActive = isPlaying,
                            barColor = MaterialTheme.colorScheme.primary,
                            barWidth = 3.dp,
                            maxBarHeight = 18.dp,
                            minBarHeight = 4.dp,
                            barCount = 4
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = recording.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(iterations = Int.MAX_VALUE)
                        )
                        if (activeTrack != null) {
                            val activeTrackLabel = if (!activeTrack.artist.isNullOrBlank()) {
                                "${activeTrack.title} • ${activeTrack.artist}"
                            } else {
                                activeTrack.title
                            }
                            Text(
                                text = activeTrackLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .basicMarquee(iterations = Int.MAX_VALUE)
                            )
                        }
                    }
                }

                // Slider & Time Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Slider(
                        value = progress,
                        onValueChange = { frac ->
                            val targetMs = (frac * effectiveDuration).toLong()
                            onSeek(targetMs)
                        },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    val formattedCurrent = DateUtils.formatElapsedTime(currentPositionMs / 1000L)
                    val formattedTotal = if (effectiveDuration > 1L) {
                        DateUtils.formatElapsedTime(effectiveDuration / 1000L)
                    } else null
                    val timeText = if (formattedTotal != null) "$formattedCurrent / $formattedTotal" else formattedCurrent

                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    var showSpeedMenu by remember { mutableStateOf(false) }
                    val speedOptions = remember { listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f) }

                    Box {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (playbackSpeed != 1.0f) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showSpeedMenu = true }
                        ) {
                            Text(
                                text = "${playbackSpeed}x",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (playbackSpeed != 1.0f) FontWeight.Bold else FontWeight.Medium,
                                color = if (playbackSpeed != 1.0f) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showSpeedMenu,
                            onDismissRequest = { showSpeedMenu = false }
                        ) {
                            speedOptions.forEach { speed ->
                                val isSelected = speed == playbackSpeed
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (speed == 1.0f) {
                                                stringResource(R.string.recording_speed_normal)
                                            } else {
                                                "${speed}x"
                                            },
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    trailingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        onSpeedChange(speed)
                                        showSpeedMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Outside the card: controls row including route, loop, backward 5s, play/pause, forward 5s, queue, and stop buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val buttonShape = RoundedCornerShape(12.dp)
            val defaultContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)

            // Route button: jumps directly to the file inside its folder
            FilledTonalIconButton(
                onClick = onRouteClick,
                shape = buttonShape,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    ,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = defaultContainerColor,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.NearMe,
                    contentDescription = stringResource(R.string.recording_cd_go_to_recording),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Infinite Loop button
            FilledTonalIconButton(
                onClick = onToggleLoop,
                shape = buttonShape,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    ,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (isLooping) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        defaultContainerColor
                    },
                    contentColor = if (isLooping) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            ) {
                Icon(
                    imageVector = if (isLooping) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = stringResource(
                        if (isLooping) R.string.recording_cd_loop_enabled else R.string.recording_cd_loop_disabled
                    ),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Backward 5 seconds button
            FilledTonalIconButton(
                onClick = { onSeek((currentPositionMs - 5_000L).coerceAtLeast(0L)) },
                shape = buttonShape,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    ,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = defaultContainerColor,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Replay5,
                    contentDescription = "-5s",
                    modifier = Modifier.size(20.dp)
                )
            }

            // Play / Pause button
            FilledTonalIconButton(
                onClick = onPlayPauseClick,
                shape = buttonShape,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    ,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) stringResource(R.string.player_cd_pause) else stringResource(R.string.player_cd_play),
                    modifier = Modifier.size(22.dp)
                )
            }

            // Forward 5 seconds button
            FilledTonalIconButton(
                onClick = { onSeek((currentPositionMs + 5_000L).coerceAtMost(effectiveDuration)) },
                shape = buttonShape,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    ,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = defaultContainerColor,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Forward5,
                    contentDescription = "+5s",
                    modifier = Modifier.size(20.dp)
                )
            }

            // Music Queue toggle button
            if (sortedTracks.isNotEmpty()) {
                FilledTonalIconButton(
                    onClick = {
                        if (onToggleTrackList != null) {
                            onToggleTrackList()
                        } else {
                            internalShowTrackList = !internalShowTrackList
                        }
                    },
                    shape = buttonShape,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        ,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (isTrackListVisible) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            defaultContainerColor
                        },
                        contentColor = if (isTrackListVisible) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = stringResource(R.string.player_tab_tracks),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Stop / Close button
            FilledTonalIconButton(
                onClick = onStopClick,
                shape = buttonShape,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    ,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = defaultContainerColor,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.recording_cd_stop_playback),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Outside the card: collapsible and expandable track list as a normal list (when managed internally)
        if (onToggleTrackList == null) {
            AnimatedVisibility(
                visible = isTrackListVisible && recording.tracks.isNotEmpty(),
                enter = fadeIn(animationSpec = tween(220)) + slideInVertically(
                    animationSpec = tween(250, easing = FastOutSlowInEasing),
                    initialOffsetY = { -it }
                ) + expandVertically(
                    animationSpec = tween(250, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top,
                    initialHeight = { 1 }
                ),
                exit = fadeOut(animationSpec = tween(180)) + slideOutVertically(
                    animationSpec = tween(230, easing = FastOutSlowInEasing),
                    targetOffsetY = { -it }
                ) + shrinkVertically(
                    animationSpec = tween(230, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top
                )
            ) {
                NowPlayingTrackQueue(
                    recording = recording,
                    currentPositionMs = currentPositionMs,
                    onSeek = onSeek
                )
            }
        }
    }
}

@Composable
fun NowPlayingTrackQueue(
    recording: RecordingFile,
    currentPositionMs: Long,
    visible: Boolean = true,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val sortedTracks = remember(recording.tracks) { recording.tracks.sortedBy { it.offsetMs } }
    val activeTrack = remember(sortedTracks, currentPositionMs) {
        if (sortedTracks.isEmpty()) null
        else sortedTracks.lastOrNull { it.offsetMs <= currentPositionMs } ?: sortedTracks.first()
    }

    if (visible && sortedTracks.isNotEmpty()) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 0.dp, bottom = 8.dp)
        ) {
            sortedTracks.forEach { track ->
                val isActiveTrack = track == activeTrack
                TrackHistoryItem(
                    track = track,
                    isActive = isActiveTrack,
                    onClick = { onSeek(track.offsetMs) }
                )
            }
        }
    }
}

