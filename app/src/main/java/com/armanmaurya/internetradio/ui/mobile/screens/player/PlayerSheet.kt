package com.armanmaurya.internetradio.ui.mobile.screens.player

import androidx.compose.ui.res.stringResource
import com.armanmaurya.internetradio.ui.mobile.screens.player.components.SleepTimerDialog

import com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.history.HistoryTab
import com.armanmaurya.internetradio.ui.mobile.screens.player.components.TrackPill
import com.armanmaurya.internetradio.ui.mobile.screens.player.components.TrackDialog
import com.armanmaurya.internetradio.ui.mobile.screens.player.components.Controls
import com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.recordings.RecordingsTab
import com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.about.AboutTab
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import coil3.compose.AsyncImage
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity
import com.armanmaurya.internetradio.player.PlaybackState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout

fun Modifier.collapseHeight(progress: Float) = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val height = (placeable.height * (1f - progress)).toInt().coerceAtLeast(0)
    layout(placeable.width, height) {
        placeable.placeRelative(0, (height - placeable.height) / 2) // center vertically while collapsing
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun PlayerSheetContent(
    isWidescreen: Boolean,
    playbackState: PlaybackState,
    isFavorite: Boolean,
    trackHistory: List<TrackHistoryEntity> = emptyList(),
    stationRecordings: List<com.armanmaurya.internetradio.data.repository.RecordingFile> = emptyList(),
    retryCountdown: Int? = null,
    progress: Float, // 0.0 (collapsed) to 1.0 (expanded)
    onTogglePlayPause: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSetSleepTimer: (Long) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onCollapse: () -> Unit,
    onExpand: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onEditStation: (RadioStation) -> Unit,
    isRecording: Boolean = false,
    recordingDuration: Long = 0L,
    amplitude: Float = 0f,
    onToggleRecording: () -> Unit,
    discoveredCastDevices: List<org.fcast.sender_sdk.DeviceInfo> = emptyList(),
    connectedCastDevice: org.fcast.sender_sdk.CastingDevice? = null,
    castVolume: Float = 1f,
    onCastVolumeChange: (Float) -> Unit = {},
    onConnectCastDevice: (org.fcast.sender_sdk.DeviceInfo) -> Unit = {},
    onDisconnectCastDevice: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val station = playbackState.currentStation ?: return
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var searchDialogTrack by remember { mutableStateOf<String?>(null) }
    var showCastDialog by remember { mutableStateOf(false) }

    if (showCastDialog) {
        com.armanmaurya.internetradio.ui.shared.components.CastDeviceDialog(
            devices = discoveredCastDevices,
            connectedDevice = connectedCastDevice,
            volume = castVolume,
            onVolumeChange = onCastVolumeChange,
            onConnect = {
                onConnectCastDevice(it)
                showCastDialog = false
            },
            onDisconnect = {
                onDisconnectCastDevice()
                showCastDialog = false
            },
            onDismiss = { showCastDialog = false }
        )
    }
    
    var wasRecording by remember { mutableStateOf(false) }
    LaunchedEffect(isRecording) {
        if (wasRecording && !isRecording) {
            android.widget.Toast.makeText(context, context.getString(R.string.player_recording_saved), android.widget.Toast.LENGTH_SHORT).show()
        }
        wasRecording = isRecording
    }
    
    LaunchedEffect(connectedCastDevice) {
        if (connectedCastDevice != null) {
            android.widget.Toast.makeText(context, "Connected", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    val bottomPagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 3 })

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

    val coroutineScope = rememberCoroutineScope()
    val historyProgressAnim = remember { Animatable(0f) }
    val historyProgress = historyProgressAnim.value

    // Close history when collapsing sheet
    LaunchedEffect(progress) {
        if (progress < 0.1f && historyProgress > 0f) {
            historyProgressAnim.snapTo(0f)
        }
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var wasAtTopWhenScrollStarted by remember { mutableStateOf(true) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            wasAtTopWhenScrollStarted = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }

    val maxDragDistance = with(density) { 600.dp.toPx() }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0 && historyProgressAnim.value < 1f) {
                    val delta = -available.y / maxDragDistance
                    coroutineScope.launch {
                        historyProgressAnim.snapTo((historyProgressAnim.value + delta).coerceIn(0f, 1f))
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && historyProgressAnim.value > 0f) {
                    val isDrag = source.toString().contains("Drag") || source.toString().contains("UserInput")
                    if (isDrag && wasAtTopWhenScrollStarted) {
                        val delta = -available.y / maxDragDistance
                        coroutineScope.launch {
                            historyProgressAnim.snapTo((historyProgressAnim.value + delta).coerceIn(0f, 1f))
                        }
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (historyProgressAnim.value > 0f && historyProgressAnim.value < 1f) {
                    if (available.y < -1000f) {
                        historyProgressAnim.animateTo(1f)
                    } else if (available.y > 1000f) {
                        historyProgressAnim.animateTo(0f)
                    } else {
                        historyProgressAnim.animateTo(if (historyProgressAnim.value > 0.5f) 1f else 0f)
                    }
                    return Velocity(0f, available.y)
                }
                return Velocity.Zero
            }
        }
    }

    SharedTransitionLayout {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clickable(enabled = progress < 0.1f, onClick = onExpand)
        ) {
            if (MaterialTheme.colorScheme.surfaceContainerLow == androidx.compose.ui.graphics.Color.Black) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            androidx.compose.foundation.shape.RoundedCornerShape(
                                topStart = 28.dp,
                                topEnd = 28.dp
                            )
                        )
                )
            }
        // --- Thumbnail Calculation ---
        val miniSize = 48.dp
        
        val baseExpandedSize = if (isWidescreen) {
            (screenHeight * 0.6f).coerceAtMost(screenWidth * 0.45f)
        } else {
            screenWidth - 32.dp
        }
        val historySize = if (isWidescreen) baseExpandedSize else 48.dp // Match exact collapsed size
        val actualExpandedSize = lerp(baseExpandedSize, historySize, historyProgress)
        
        val currentSize = lerp(miniSize, actualExpandedSize, progress)
        
        val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
        val cutoutLeftPadding = WindowInsets.displayCutout.asPaddingValues().calculateLeftPadding(layoutDirection)
        val cutoutRightPadding = WindowInsets.displayCutout.asPaddingValues().calculateRightPadding(layoutDirection)

        // Mini position (relative to sheet)
        val miniX = 16.dp + cutoutLeftPadding
        val miniY = 12.dp // (72 - 48) / 2 - perfectly centered in 72.dp row
        
        // Expanded position
        val baseExpandedX = if (isWidescreen) 32.dp + cutoutLeftPadding else (screenWidth - baseExpandedSize) / 2
        val historyExpandedX = if (isWidescreen) baseExpandedX else 24.dp + cutoutLeftPadding // Move to left edge of column
        val actualExpandedX = lerp(baseExpandedX, historyExpandedX, historyProgress)
        
        val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val baseExpandedY = if (isWidescreen) {
            val availableHeight = screenHeight - statusBarPadding
            statusBarPadding + (availableHeight - baseExpandedSize) / 2
        } else {
            84.dp + statusBarPadding
        }
        val historyExpandedY = if (isWidescreen) baseExpandedY else 22.dp + statusBarPadding // Shifted further down
        val actualExpandedY = lerp(baseExpandedY, historyExpandedY, historyProgress)
        
        val currentX = lerp(miniX, actualExpandedX, progress)
        val currentY = lerp(miniY, actualExpandedY, progress)

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
                .clip(RoundedCornerShape(12.dp))
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
                    .padding(start = 16.dp, end = 16.dp + cutoutRightPadding)
                    .alpha(1f - (progress * 5f).coerceIn(0f, 1f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Space for the moving thumbnail
                Spacer(modifier = Modifier.width(miniSize + 12.dp + cutoutLeftPadding))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = progress < 0.1f,
                            onClick = onExpand
                        ),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = station.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    val currentTrackText = if (retryCountdown != null) {
                        stringResource(R.string.player_retrying_in, retryCountdown)
                    } else if (playbackState.isLoading) {
                        stringResource(R.string.player_buffering)
                    } else {
                        playbackState.currentTrack ?: stringResource(R.string.player_no_track_data)
                    }
                    Text(
                        text = currentTrackText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .basicMarquee()
                            .pointerInput(currentTrackText) {
                                detectTapGestures(
                                    onTap = {
                                        if (progress < 0.1f) onExpand()
                                    },
                                    onLongPress = {
                                        if (playbackState.currentTrack != null) {
                                            clipboardManager.setText(AnnotatedString(currentTrackText))
                                            Toast.makeText(context, context.getString(R.string.player_copied_track_to_clipboard), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                    )
                }

                IconButton(
                    onClick = onPrevious,
                    enabled = playbackState.hasPrevious
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.player_cd_previous)
                    )
                }

                IconButton(onClick = onTogglePlayPause) {
                    Icon(
                        imageVector = if (playbackState.isPlaying || playbackState.isLoading) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                }

                IconButton(
                    onClick = onNext,
                    enabled = playbackState.hasNext
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.player_cd_next)
                    )
                }

                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = stringResource(R.string.player_cd_toggle_favorite),
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
            }
        }

        // --- Expanded Content (Fades in as we expand) ---
        if (progress > 0.1f) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .alpha((progress - 0.2f).coerceIn(0f, 0.8f) * 1.25f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp)
                        .collapseHeight(historyProgress)
                        .alpha(1f - historyProgress)
                ) {
                    IconButton(
                        onClick = onCollapse,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.player_cd_collapse),
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showCastDialog = true }) {
                            Icon(
                                imageVector = if (connectedCastDevice != null) Icons.Default.CastConnected else Icons.Default.Cast,
                                contentDescription = "Cast",
                                modifier = Modifier.size(28.dp),
                                tint = if (connectedCastDevice != null) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }

                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = stringResource(R.string.player_cd_toggle_favorite),
                                modifier = Modifier.size(32.dp),
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                    }
                    
                    if (station.bitrate > 0) {
                        val bitrateText = if (station.codec.isNotBlank()) {
                            stringResource(R.string.player_station_codec_bitrate, station.codec.uppercase(), station.bitrate.toString())
                        } else {
                            stringResource(R.string.player_station_bitrate_only, station.bitrate.toString())
                        }
                        Text(
                            text = bitrateText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                val contentModifier = if (isWidescreen) {
                    Modifier.fillMaxSize().padding(start = baseExpandedSize + 48.dp)
                } else {
                    Modifier.fillMaxSize()
                }

                Column(
                    modifier = contentModifier,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Thumbnail Placeholder and Mini Controls
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        // Placeholder for the moving thumbnail
                        if (!isWidescreen) {
                            Spacer(modifier = Modifier
                                .fillMaxWidth()
                                .height(
                                    lerp(
                                        52.dp + baseExpandedSize, // 84dp (Thumbnail Y) + size + 24dp (gap) - 56dp (Header Box) = 52dp + size
                                        72.dp, // Matches Row height
                                        historyProgress
                                    )
                                )
                            )
                        } else {
                            Spacer(modifier = Modifier.height(lerp(16.dp, 72.dp, historyProgress)))
                        }
    
                        // Mini controls when history is expanded
                    if (historyProgress > 0f) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp) // perfectly match the collapsed player height
                                .alpha(historyProgress),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Space for the moving thumbnail (only on mobile)
                            Spacer(modifier = Modifier.width(if (isWidescreen) 16.dp else 84.dp))

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = station.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    modifier = Modifier.basicMarquee()
                                )
                                val currentTrackText = if (playbackState.isLoading) stringResource(R.string.player_buffering) else playbackState.currentTrack ?: stringResource(R.string.player_no_track_data)
                                Text(
                                    text = currentTrackText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.basicMarquee()
                                )
                            }

                            IconButton(
                                onClick = onPrevious,
                                enabled = playbackState.hasPrevious
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = stringResource(R.string.player_cd_previous)
                                )
                            }

                            IconButton(onClick = onTogglePlayPause) {
                                Icon(
                                    imageVector = if (playbackState.isPlaying || playbackState.isLoading) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null
                                )
                            }

                            IconButton(
                                onClick = onNext,
                                enabled = playbackState.hasNext
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = stringResource(R.string.player_cd_next)
                                )
                            }

                            IconButton(onClick = onToggleFavorite) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = stringResource(R.string.player_cd_toggle_favorite),
                                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                )
                            }
                        }
                    }
                }

                // Player UI wrapper
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight((1f - historyProgress).coerceAtLeast(0.001f))
                        .alpha(1f - historyProgress),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    // Main controls content
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = station.name,
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Start,
                            maxLines = 1,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .weight(1f)
                                .basicMarquee()
                        )
                        
                        if (isFavorite) {
                            IconButton(
                                onClick = {
                                    onCollapse()
                                    onEditStation(station)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.edit_station_title),
                                    tint = LocalContentColor.current
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    val bufferingText = stringResource(R.string.player_buffering)
                    val noTrackDataText = stringResource(R.string.player_no_track_data)
                    val displayTrack = if (retryCountdown != null) {
                        stringResource(R.string.player_retrying_in, retryCountdown!!)
                    } else if (playbackState.isLoading) {
                        bufferingText
                    } else {
                        playbackState.currentTrack ?: noTrackDataText
                    }
                    val isSearchExpanded = searchDialogTrack != null

                    val canSearch = displayTrack != bufferingText && displayTrack != noTrackDataText
                    
                    TrackPill(
                        displayTrack = displayTrack,
                        canSearch = canSearch,
                        isSearchExpanded = isSearchExpanded,
                        onOpenSearch = { track -> searchDialogTrack = track }
                    )

                    // Waveform UI Pill
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = if (isRecording) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) 
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(
                                    start = 0.dp,
                                    end = if (isRecording) 16.dp else 0.dp,
                                    top = 8.dp,
                                    bottom = 8.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Waveform
                            ScrollingWaveform(
                                amplitude = if (playbackState.isPlaying) amplitude else 0f,
                                modifier = Modifier.weight(1f),
                                barColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                emptyColor = if (isRecording) MaterialTheme.colorScheme.error.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                            AnimatedVisibility(
                                visible = isRecording,
                                enter = androidx.compose.animation.expandHorizontally(expandFrom = Alignment.Start) + androidx.compose.animation.fadeIn(),
                                exit = androidx.compose.animation.shrinkHorizontally(shrinkTowards = Alignment.Start) + androidx.compose.animation.fadeOut()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(modifier = Modifier.width(16.dp))
                                    // Timer text
                                    val formattedDuration = String.format(
                                        Locale.getDefault(),
                                        "%02d:%02d",
                                        recordingDuration / 60,
                                        recordingDuration % 60
                                    )
                                    Text(
                                        text = formattedDuration,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                } // End of Main controls column

                // Spacer above controls to push them down
                Spacer(modifier = Modifier.weight(1f))

                // Controls Row
                Controls(
                    historyProgress = historyProgress,
                    sleepTimerEndTime = playbackState.sleepTimerEndTime,
                    remainingTime = remainingTime,
                    sleepTimerProgress = sleepTimerProgress,
                    hasPrevious = playbackState.hasPrevious,
                    hasNext = playbackState.hasNext,
                    isPlaying = playbackState.isPlaying,
                    isLoading = playbackState.isLoading,
                    isRecording = isRecording,
                    onOpenSleepTimer = { showSleepTimerDialog = true },
                    onPrevious = onPrevious,
                    onTogglePlayPause = onTogglePlayPause,
                    onNext = onNext,
                    onToggleRecording = onToggleRecording
                )

                // Spacer below controls to center them
                Spacer(modifier = Modifier.weight(1f))
                } // End of Player UI wrapper

                // Bottom Tabs (sits naturally below everything else, moves up as above content collapses)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                coroutineScope.launch {
                                    historyProgressAnim.snapTo((historyProgressAnim.value - delta / maxDragDistance).coerceIn(0f, 1f))
                                }
                            },
                            onDragStopped = { velocity ->
                                coroutineScope.launch {
                                    if (velocity < -1000f) {
                                        historyProgressAnim.animateTo(1f)
                                    } else if (velocity > 1000f) {
                                        historyProgressAnim.animateTo(0f)
                                    } else {
                                        historyProgressAnim.animateTo(if (historyProgressAnim.value > 0.5f) 1f else 0f)
                                    }
                                }
                            }
                        ),
                    horizontalArrangement = Arrangement.Center
                ) {
                    val isHistoryExpanded = historyProgress > 0.5f
                    
                    val tabWidth = 110.dp
                    val indicatorOffset by androidx.compose.animation.core.animateDpAsState(
                        targetValue = when (bottomPagerState.currentPage) {
                            0 -> 0.dp
                            1 -> tabWidth
                            else -> tabWidth * 2
                        },
                        animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
                        label = "indicatorOffset"
                    )

                    val tab1TextColor by androidx.compose.animation.animateColorAsState(
                        targetValue = if (isHistoryExpanded && bottomPagerState.currentPage == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "tab1Text"
                    )

                    val tab2TextColor by androidx.compose.animation.animateColorAsState(
                        targetValue = if (isHistoryExpanded && bottomPagerState.currentPage == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "tab2Text"
                    )

                    val tab3TextColor by androidx.compose.animation.animateColorAsState(
                        targetValue = if (isHistoryExpanded && bottomPagerState.currentPage == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "tab3Text"
                    )

                    val isPureBlack = MaterialTheme.colorScheme.surfaceContainerLow == androidx.compose.ui.graphics.Color.Black
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isHistoryExpanded) {
                                    if (isPureBlack) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                } else androidx.compose.ui.graphics.Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .then(
                                if (isHistoryExpanded && isPureBlack) Modifier.border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(12.dp)
                                ) else Modifier
                            )
                            .padding(4.dp)
                    ) {
                        // The sliding indicator
                        if (isHistoryExpanded) {
                            Box(modifier = Modifier.matchParentSize()) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = indicatorOffset)
                                        .width(tabWidth)
                                        .fillMaxHeight()
                                        .background(
                                            if (isPureBlack) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.surface,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .then(
                                            if (isPureBlack) Modifier.border(
                                                1.dp,
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                                RoundedCornerShape(10.dp)
                                            ) else Modifier
                                        )
                                )
                            }
                        }

                        // The texts
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Recent Tracks Tab
                            Box(
                                modifier = Modifier
                                    .width(tabWidth)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                        onClick = {
                                            if (bottomPagerState.currentPage != 0) {
                                                coroutineScope.launch {
                                                    launch { bottomPagerState.animateScrollToPage(0) }
                                                    if (!isHistoryExpanded) {
                                                        launch { historyProgressAnim.animateTo(1f) }
                                                    }
                                                }
                                            } else {
                                                coroutineScope.launch {
                                                    historyProgressAnim.animateTo(if (isHistoryExpanded) 0f else 1f)
                                                }
                                            }
                                        }
                                    )
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.player_tab_tracks),
                                    fontWeight = FontWeight.Bold,
                                    color = tab1TextColor
                                )
                            }

                            // Recordings Tab
                            Box(
                                modifier = Modifier
                                    .width(tabWidth)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                        onClick = {
                                            if (bottomPagerState.currentPage != 1) {
                                                coroutineScope.launch {
                                                    launch { bottomPagerState.animateScrollToPage(1) }
                                                    if (!isHistoryExpanded) {
                                                        launch { historyProgressAnim.animateTo(1f) }
                                                    }
                                                }
                                            } else {
                                                coroutineScope.launch {
                                                    historyProgressAnim.animateTo(if (isHistoryExpanded) 0f else 1f)
                                                }
                                            }
                                        }
                                    )
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.home_tab_recordings),
                                    fontWeight = FontWeight.Bold,
                                    color = tab2TextColor
                                )
                            }

                            // About Tab
                            Box(
                                modifier = Modifier
                                    .width(tabWidth)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                        onClick = {
                                            if (bottomPagerState.currentPage != 2) {
                                                coroutineScope.launch {
                                                    launch { bottomPagerState.animateScrollToPage(2) }
                                                    if (!isHistoryExpanded) {
                                                        launch { historyProgressAnim.animateTo(1f) }
                                                    }
                                                }
                                            } else {
                                                coroutineScope.launch {
                                                    historyProgressAnim.animateTo(if (isHistoryExpanded) 0f else 1f)
                                                }
                                            }
                                        }
                                    )
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.player_tab_about),
                                    fontWeight = FontWeight.Bold,
                                    color = tab3TextColor
                                )
                            }
                        }
                    }
                }

                // Tab Panel Content
                if (historyProgress > 0f) {
                    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
                    androidx.compose.foundation.pager.HorizontalPager(
                        state = bottomPagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(historyProgress.coerceAtLeast(0.01f))
                            .alpha(historyProgress)
                    ) { page ->
                        if (page == 0) {
                            HistoryTab(
                                trackHistory = trackHistory,
                                listState = listState,
                                nestedScrollConnection = nestedScrollConnection
                            )
                        } else if (page == 1) {
                            RecordingsTab(
                                stationRecordings = stationRecordings,
                                listState = listState,
                                nestedScrollConnection = nestedScrollConnection
                            )
                        } else if (page == 2) {
                            AboutTab(
                                station = station,
                                listState = listState,
                                nestedScrollConnection = nestedScrollConnection
                            )
                        }
                    }
                }
            }
        }
    }

        // DIALOG: visible when expanded — same sharedBounds key as pill = true container transform
        TrackDialog(
            searchDialogTrack = searchDialogTrack,
            onDismissRequest = { searchDialogTrack = null }
        )
    }
}


    if (showSleepTimerDialog) {
        SleepTimerDialog(
            activeTimerEndTime = playbackState.sleepTimerEndTime,
            onDismissRequest = { showSleepTimerDialog = false },
            onSetTimer = { onSetSleepTimer(it) },
            onCancelTimer = { onCancelSleepTimer() }
        )
    }
}


