package com.armanmaurya.internetradio.ui.screens.player

import androidx.compose.animation.*
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import coil3.compose.AsyncImage
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.player.PlaybackState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun rememberRotationState(isPlaying: Boolean, speed: Float): Float {
    var rotation by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            var lastTime = withInfiniteAnimationFrameMillis { it }
            while (true) {
                val currentTime = withInfiniteAnimationFrameMillis { it }
                val delta = (currentTime - lastTime) / 1000f // seconds
                rotation = (rotation + delta * speed) % 360f
                lastTime = currentTime
            }
        }
    }
    return rotation
}

@Composable
fun PlayerSheetContent(
    playbackState: PlaybackState,
    isFavorite: Boolean,
    progress: Float, // 0.0 (collapsed) to 1.0 (expanded)
    onTogglePlayPause: () -> Unit,
    onStop: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSetSleepTimer: (Long) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onCollapse: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val station = playbackState.currentStation ?: return
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var isTimerOptionsExpanded by remember { mutableStateOf(false) }

    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(playbackState.sleepTimerEndTime) {
        if (playbackState.sleepTimerEndTime != null) {
            while (true) {
                currentTime = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    val remainingTime = playbackState.sleepTimerEndTime?.let { it - currentTime } ?: 0L
    val sleepTimerProgress = if (playbackState.sleepTimerTotalDuration > 0) {
        (remainingTime.toFloat() / playbackState.sleepTimerTotalDuration).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(enabled = progress < 0.1f, onClick = onExpand)
    ) {
        // ... rest of Box content ...
        // --- Thumbnail Calculation ---
        val miniSize = 48.dp
        val expandedSize = (screenWidth * 0.8f).coerceAtMost(320.dp)
        
        val currentSize = lerp(miniSize, expandedSize, progress)
        
        // Mini position (relative to sheet)
        val miniX = 16.dp
        val miniY = 12.dp // (72 - 48) / 2
        
        // Expanded position
        val expandedX = (screenWidth - expandedSize) / 2
        val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val expandedY = 100.dp + statusBarPadding
        
        val currentX = lerp(miniX, expandedX, progress)
        val currentY = lerp(miniY, expandedY, progress)

        val rotation = rememberRotationState(isPlaying = playbackState.isPlaying, speed = 30f)

        // --- The Moving Thumbnail ---
        AsyncImage(
            model = station.favicon.ifBlank { null },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = with(density) { currentX.toPx() }.roundToInt(),
                        y = with(density) { currentY.toPx() }.roundToInt()
                    )
                }
                .size(currentSize)
                .graphicsLayer { rotationZ = rotation }
                .clip(CircleShape)
                .background(Color(0xFF1E1E1E)),
            error = painterResource(id = R.drawable.ic_launcher_foreground),
            fallback = painterResource(id = R.drawable.ic_launcher_foreground)
        )

        // --- Mini Content (Fades out as we expand) ---
        if (progress < 0.9f) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(horizontal = 16.dp)
                    .alpha(1f - (progress * 5f).coerceIn(0f, 1f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Space for the moving thumbnail
                Spacer(modifier = Modifier.width(miniSize + 12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = station.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val currentTrackText = playbackState.currentTrack ?: if (playbackState.isLoading) "Buffering..." else buildString {
                        if (station.country.isNotBlank()) append(station.country)
                        if (station.country.isNotBlank() && station.language.isNotBlank()) append(" • ")
                        if (station.language.isNotBlank()) append(station.language)
                    }
                    Text(
                        text = currentTrackText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.pointerInput(currentTrackText) {
                            detectTapGestures(
                                onLongPress = {
                                    if (playbackState.currentTrack != null) {
                                        clipboardManager.setText(AnnotatedString(currentTrackText))
                                        Toast.makeText(context, "Copied track to clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    )
                }

                if (playbackState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = onTogglePlayPause) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                    }
                }

                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Toggle Favorite",
                        tint = if (isFavorite) Color.Red else LocalContentColor.current
                    )
                }

                IconButton(onClick = onStop) {
                    Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                }
            }
        }

        // --- Expanded Content (Fades in as we expand) ---
        if (progress > 0.1f) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .alpha((progress - 0.2f).coerceIn(0f, 0.8f) * 1.25f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCollapse) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Collapse",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Toggle Favorite",
                            modifier = Modifier.size(32.dp),
                            tint = if (isFavorite) Color.Red else LocalContentColor.current
                        )
                    }
                }

                // Placeholder for the moving thumbnail
                Spacer(modifier = Modifier.height(100.dp + expandedSize - 20.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = station.name,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = buildString {
                            if (station.country.isNotBlank()) append(station.country)
                            if (station.country.isNotBlank() && station.language.isNotBlank()) append(" • ")
                            if (station.language.isNotBlank()) append(station.language)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    if (playbackState.currentTrack != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = playbackState.currentTrack,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.pointerInput(playbackState.currentTrack) {
                                detectTapGestures(
                                    onLongPress = {
                                        clipboardManager.setText(AnnotatedString(playbackState.currentTrack))
                                        Toast.makeText(context, "Copied track to clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    if (playbackState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(64.dp))
                    } else {
                        FilledIconButton(
                            onClick = onTogglePlayPause,
                            modifier = Modifier.size(80.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }

                if (station.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = station.tags.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // --- Sleep Timer Controls at Bottom ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    AnimatedContent(
                        targetState = isTimerOptionsExpanded,
                        transitionSpec = {
                            (slideInHorizontally { -it } + fadeIn())
                                .togetherWith(slideOutHorizontally { -it } + fadeOut())
                                .using(SizeTransform(clip = false))
                        },
                        label = "TimerMorph"
                    ) { expanded ->
                        if (expanded) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(onClick = { isTimerOptionsExpanded = false }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }

                                if (playbackState.sleepTimerEndTime != null) {
                                    AssistChip(
                                        onClick = { 
                                            onCancelSleepTimer()
                                            isTimerOptionsExpanded = false
                                        },
                                        label = { Text("Turn off", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Default.TimerOff, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            labelColor = MaterialTheme.colorScheme.error,
                                            leadingIconContentColor = MaterialTheme.colorScheme.error
                                        ),
                                        shape = CircleShape
                                    )
                                }

                                listOf(1, 15, 30, 45, 60, 90, 120).forEach { mins ->
                                    AssistChip(
                                        onClick = {
                                            onSetSleepTimer(mins * 60 * 1000L)
                                            isTimerOptionsExpanded = false
                                        },
                                        label = { Text("$mins min") },
                                        shape = CircleShape
                                    )
                                }
                            }
                        } else {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(48.dp)
                            ) {
                                if (playbackState.sleepTimerEndTime != null) {
                                    CircularProgressIndicator(
                                        progress = { sleepTimerProgress },
                                        modifier = Modifier.fillMaxSize(),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 3.dp,
                                        strokeCap = StrokeCap.Round
                                    )
                                }
                                IconButton(
                                    onClick = { isTimerOptionsExpanded = true },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    if (playbackState.sleepTimerEndTime != null) {
                                        val mins = (remainingTime / 60000).toInt() + 1
                                        Text(
                                            text = mins.toString(),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Timer,
                                            contentDescription = "Sleep Timer",
                                            tint = LocalContentColor.current
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
