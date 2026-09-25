@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.armanmaurya.internetradio.ui.mobile.screens.player

import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import com.armanmaurya.internetradio.ui.mobile.screens.player.components.SleepTimerDialog

import com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.history.HistoryTab
import com.armanmaurya.internetradio.ui.mobile.screens.player.components.TrackPill
import com.armanmaurya.internetradio.ui.mobile.screens.player.components.VolumeDialog
import com.armanmaurya.internetradio.ui.mobile.screens.player.components.TrackDialog
import com.armanmaurya.internetradio.ui.mobile.screens.player.components.Controls
import com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.recordings.RecordingsTab
import com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.about.AboutTab
import com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.lyrics.LyricsTab
import com.armanmaurya.internetradio.ui.shared.components.shimmerEffect
import androidx.compose.ui.zIndex
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.armanmaurya.internetradio.ui.shared.theme.LocalAppPreferences
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
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
import com.armanmaurya.internetradio.domain.model.PlaybackSource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.FilterQuality
import coil3.compose.SubcomposeAsyncImage
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.CastDevice
import com.armanmaurya.internetradio.domain.model.LyricsState
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity
import com.armanmaurya.internetradio.domain.model.PlaybackState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import com.armanmaurya.internetradio.domain.model.RecordingSession

import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.armanmaurya.internetradio.ui.shared.viewmodels.PlayerViewModel
import android.view.WindowManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

fun Modifier.collapseHeight(progress: Float) = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val height = (placeable.height * (1f - progress)).toInt().coerceAtLeast(0)
    layout(placeable.width, height) {
        placeable.placeRelative(0, (height - placeable.height) / 2) // center vertically while collapsing
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberPlayerSheetProgress(
    scaffoldState: BottomSheetScaffoldState,
    bottomInset: Dp
): State<Float> {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val imeInsets = WindowInsets.ime
    val peekHeightPx = with(density) { (72.dp + bottomInset).toPx() }

    return remember(scaffoldState, screenHeightPx, peekHeightPx, imeInsets) {
        derivedStateOf {
            val imeHeightPx = imeInsets.getBottom(density).toFloat()
            val fullHeight = screenHeightPx - imeHeightPx

            val currentOffset = try {
                scaffoldState.bottomSheetState.requireOffset()
            } catch (e: Exception) {
                fullHeight - peekHeightPx
            }

            val totalRange = fullHeight - peekHeightPx
            if (totalRange > 0) {
                (1f - (currentOffset / totalRange)).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerBottomSheet(
    scaffoldState: BottomSheetScaffoldState,
    progress: Float,
    isWidescreen: Boolean,
    bottomInset: Dp,
    onEditStation: (RadioStation) -> Unit,
    modifier: Modifier = Modifier,
    keepScreenOn: Boolean = false,
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()
    val isFavorite by playerViewModel.isFavorite.collectAsStateWithLifecycle()
    val trackHistory by playerViewModel.trackHistory.collectAsStateWithLifecycle()
    val stationRecordings by playerViewModel.stationRecordings.collectAsStateWithLifecycle()
    val activeSessions by playerViewModel.activeSessions.collectAsStateWithLifecycle()
    val isRecording by playerViewModel.isCurrentStationRecording.collectAsStateWithLifecycle()
    val recordingDuration by playerViewModel.currentRecordingDuration.collectAsStateWithLifecycle()
    val amplitude by playerViewModel.amplitude.collectAsStateWithLifecycle()
    val retryCountdown by playerViewModel.retryCountdown.collectAsStateWithLifecycle()
    val discoveredCastDevices by playerViewModel.discoveredCastDevices.collectAsStateWithLifecycle()
    val connectedCastDevice by playerViewModel.connectedCastDevice.collectAsStateWithLifecycle()
    val castPlaybackState by playerViewModel.castPlaybackState.collectAsStateWithLifecycle()
    val castVolume by playerViewModel.castVolume.collectAsStateWithLifecycle()
    val lyricsState by playerViewModel.lyricsState.collectAsStateWithLifecycle()

    val effectivePlaybackState = if (connectedCastDevice != null) {
        playbackState.copy(
            isPlaying = castPlaybackState.isPlaying,
            isLoading = castPlaybackState.isBuffering
        )
    } else {
        playbackState
    }

    // BackHandler to collapse sheet when expanded
    BackHandler(enabled = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) {
        scope.launch {
            scaffoldState.bottomSheetState.partialExpand()
        }
    }

    // Keep screen awake when player is expanded if setting is enabled
    val isPlayerExpanded = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded
    val shouldKeepScreenOn = keepScreenOn && isPlayerExpanded
    val context = LocalContext.current
    DisposableEffect(shouldKeepScreenOn) {
        val window = (context as? android.app.Activity)?.window
        if (shouldKeepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Handle Swipe to Dismiss (Stop playback when swiped away)
    LaunchedEffect(scaffoldState.bottomSheetState.currentValue) {
        if (scaffoldState.bottomSheetState.currentValue == SheetValue.Hidden && playbackState.currentStation != null) {
            playerViewModel.stop()
        }
    }

    // Handle Re-appearing (Show player when a station starts playing) and Hiding (when playback stops)
    LaunchedEffect(playbackState.currentStation) {
        if (playbackState.currentStation != null && scaffoldState.bottomSheetState.currentValue == SheetValue.Hidden) {
            if (scaffoldState.bottomSheetState.targetValue != SheetValue.Expanded) {
                scaffoldState.bottomSheetState.partialExpand()
            }
        } else if (playbackState.currentStation == null && scaffoldState.bottomSheetState.currentValue != SheetValue.Hidden) {
            scaffoldState.bottomSheetState.hide()
        }
    }

    // Hide software keyboard when sheet expands
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(scaffoldState.bottomSheetState.targetValue) {
        if (scaffoldState.bottomSheetState.targetValue == SheetValue.Expanded) {
            keyboardController?.hide()
        }
    }

    val enableSwipeToDismiss by remember(
        progress,
        scaffoldState.bottomSheetState.currentValue,
        scaffoldState.bottomSheetState.targetValue
    ) {
        derivedStateOf {
            progress == 0f && scaffoldState.bottomSheetState.currentValue == SheetValue.PartiallyExpanded && scaffoldState.bottomSheetState.targetValue == SheetValue.PartiallyExpanded
        }
    }
    val currentSwipeAllowed by rememberUpdatedState(enableSwipeToDismiss)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.Settled) {
                true
            } else {
                currentSwipeAllowed
            }
        }
    )

    LaunchedEffect(playbackState.currentStation) {
        if (playbackState.currentStation != null) {
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .heightIn(min = 72.dp + bottomInset)
    ) {
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = enableSwipeToDismiss,
            enableDismissFromEndToStart = enableSwipeToDismiss,
            gesturesEnabled = enableSwipeToDismiss,
            onDismiss = {
                scope.launch { scaffoldState.bottomSheetState.hide() }
            },
            backgroundContent = {
                if (dismissState.dismissDirection != SwipeToDismissBoxValue.Settled) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .padding(start = 16.dp, end = 16.dp)
                            .alpha(1f - (progress * 5f).coerceIn(0f, 1f)),
                        contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                            Alignment.CenterStart
                        } else if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                            Alignment.CenterEnd
                        } else {
                            Alignment.Center
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = stringResource(R.string.player_cd_stop),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        ) {
            PlayerSheetContent(
                isWidescreen = isWidescreen,
                playbackState = effectivePlaybackState,
                isFavorite = isFavorite,
                trackHistory = trackHistory,
                stationRecordings = stationRecordings,
                activeSessions = activeSessions,
                retryCountdown = retryCountdown,
                lyricsState = lyricsState,
                progress = progress,
                onTogglePlayPause = playerViewModel::togglePlayPause,
                onToggleFavorite = playerViewModel::toggleFavorite,
                onSetSleepTimer = playerViewModel::setSleepTimer,
                onCancelSleepTimer = playerViewModel::cancelSleepTimer,
                onCollapse = {
                    scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                },
                onExpand = {
                    scope.launch { scaffoldState.bottomSheetState.expand() }
                },
                onNext = playerViewModel::next,
                onPrevious = playerViewModel::previous,
                onPlayIndex = playerViewModel::playIndex,
                onEditStation = { station ->
                    scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                    onEditStation(station)
                },
                isRecording = isRecording,
                recordingDuration = recordingDuration,
                amplitude = amplitude,
                onToggleRecording = playerViewModel::toggleRecording,
                onSyncOffsetChange = playerViewModel::setLyricsSyncOffset,
                discoveredCastDevices = discoveredCastDevices,
                volume = castVolume.toFloat(),
                onVolumeChange = playerViewModel::setVolume,
                connectedCastDevice = connectedCastDevice,
                onConnectCastDevice = playerViewModel::connectToCastDevice,
                onDisconnectCastDevice = playerViewModel::disconnectCastDevice,
                onDeleteRecording = playerViewModel::deleteRecording,
                onStopRecording = playerViewModel::stopRecording,
                getCurrentPosition = { playerViewModel.currentPosition }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun PlayerSheetContent(
    isWidescreen: Boolean,
    playbackState: PlaybackState,
    isFavorite: Boolean,
    trackHistory: List<TrackHistoryEntity> = emptyList(),
    stationRecordings: List<com.armanmaurya.internetradio.domain.model.RecordingFile>? = null,
    activeSessions: Map<String, RecordingSession> = emptyMap(),
    retryCountdown: Int? = null,
    lyricsState: LyricsState = LyricsState.Loading,
    progress: Float, // 0.0 (collapsed) to 1.0 (expanded)
    onTogglePlayPause: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSetSleepTimer: (Long) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onCollapse: () -> Unit,
    onExpand: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onPlayIndex: (Int) -> Unit,
    onEditStation: (RadioStation) -> Unit,
    isRecording: Boolean = false,
    recordingDuration: Long = 0L,
    amplitude: Float = 0f,
    onToggleRecording: () -> Unit,
    onSyncOffsetChange: (Long) -> Unit,
    discoveredCastDevices: List<CastDevice> = emptyList(),
    connectedCastDevice: CastDevice? = null,
    volume: Float = 1f,
    onVolumeChange: (Float) -> Unit = {},
    onConnectCastDevice: (CastDevice) -> Unit = {},
    onDisconnectCastDevice: () -> Unit = {},
    onDeleteRecording: (com.armanmaurya.internetradio.domain.model.RecordingFile) -> Unit,
    onStopRecording: (String) -> Unit = {},
    getCurrentPosition: () -> Long,
    modifier: Modifier = Modifier
) {
    val station = playbackState.currentStation ?: return
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val clipboardManager = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val uriHandler = LocalUriHandler.current
    val castConnectedMessage = stringResource(R.string.player_cast_connected)
    val trackCopiedMessage = stringResource(R.string.player_copied_track_to_clipboard)

    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var searchDialogTrack by remember { mutableStateOf<String?>(null) }
    var showCastDialog by remember { mutableStateOf(false) }
    var showCoverArt by remember { mutableStateOf(false) }

    if (showCastDialog) {
        com.armanmaurya.internetradio.ui.shared.components.CastDeviceDialog(
            devices = discoveredCastDevices,
            connectedDevice = connectedCastDevice,
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



    LaunchedEffect(connectedCastDevice) {
        if (connectedCastDevice != null) {
            android.widget.Toast.makeText(context, castConnectedMessage, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    val bottomPagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 4 })

    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(playbackState.sleepTimerEndTime) {
        if (playbackState.sleepTimerEndTime != null) {
            while (true) {
                currentTime = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    val displayCodec = (if (!isFavorite || station.codec.isBlank() || station.codec.equals("UNKNOWN", ignoreCase = true)) playbackState.streamCodec else null) ?: station.codec
    val displayBitrate = (if (!isFavorite || station.bitrate <= 0) playbackState.streamBitrate else null) ?: station.bitrate

    val hasBitrate = displayBitrate > 0
    val hasCodec = displayCodec.isNotBlank() && displayCodec.uppercase() != "UNKNOWN"
    val hasInfo = hasBitrate || hasCodec
    var userToggledTimer by rememberSaveable(station.stationUuid) { mutableStateOf(false) }
    val showTimer = if (hasInfo) userToggledTimer else true

    var showVolumeDialog by remember { mutableStateOf(false) }
    val volumeLevel = if (connectedCastDevice != null) volume else playbackState.volume
    val appPreferences = LocalAppPreferences.current

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

    BackHandler(enabled = historyProgress > 0f) {
        coroutineScope.launch {
            historyProgressAnim.animateTo(0f)
        }
    }

    val historyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val recordingsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val lyricsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val aboutListState = androidx.compose.foundation.lazy.rememberLazyListState()

    val currentListState = when (bottomPagerState.currentPage) {
        0 -> historyListState
        1 -> recordingsListState
        2 -> lyricsListState
        else -> aboutListState
    }

    var wasAtTopWhenScrollStarted by remember { mutableStateOf(true) }

    LaunchedEffect(currentListState.isScrollInProgress) {
        if (currentListState.isScrollInProgress) {
            wasAtTopWhenScrollStarted = currentListState.firstVisibleItemIndex == 0 && currentListState.firstVisibleItemScrollOffset == 0
        }
    }

    val maxDragDistance = with(density) { 600.dp.toPx() }

    val nestedScrollConnection = remember(currentListState) {
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
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
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
            (screenWidth - 32.dp).coerceAtMost((screenHeight - 460.dp).coerceAtLeast(48.dp))
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
            68.dp + statusBarPadding
        }
        val historyExpandedY = if (isWidescreen) baseExpandedY else 22.dp + statusBarPadding // Shifted further down
        val actualExpandedY = lerp(baseExpandedY, historyExpandedY, historyProgress)
        
        val currentX = lerp(miniX, actualExpandedX, progress)
        val currentY = lerp(miniY, actualExpandedY, progress)

        // --- The Moving Thumbnail ---
        val playlist = if (playbackState.currentPlaylist.isEmpty()) listOfNotNull(station) else playbackState.currentPlaylist
        val playlistSize = playlist.size
        
        val isInfinite = playbackState.playbackSource !is PlaybackSource.Browse && playlistSize > 1
        val pageCount = if (isInfinite) Int.MAX_VALUE else playlistSize
        val startIndex = playbackState.currentPlaylistIndex.coerceAtLeast(0)
        
        val initialPage = if (isInfinite) {
            val middle = Int.MAX_VALUE / 2
            middle - (middle % playlistSize) + startIndex
        } else {
            startIndex
        }

        val pagerState = androidx.compose.runtime.key(isInfinite) {
            androidx.compose.foundation.pager.rememberPagerState(
                initialPage = initialPage,
                pageCount = { pageCount }
            )
        }

        LaunchedEffect(playbackState.currentPlaylistIndex, playlistSize) {
            val targetIndex = playbackState.currentPlaylistIndex
            if (targetIndex >= 0 && playlistSize > 0) {
                val currentIndex = if (isInfinite) pagerState.currentPage % playlistSize else pagerState.currentPage
                if (targetIndex != currentIndex) {
                    if (isInfinite) {
                        var diff = targetIndex - currentIndex
                        val half = playlistSize / 2
                        if (diff > half) diff -= playlistSize
                        else if (diff < -half) diff += playlistSize
                        pagerState.animateScrollToPage(pagerState.currentPage + diff)
                    } else {
                        pagerState.animateScrollToPage(targetIndex)
                    }
                }
            }
        }

        LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
            if (!pagerState.isScrollInProgress && playlistSize > 1) {
                val currentIndex = if (isInfinite) pagerState.currentPage % playlistSize else pagerState.currentPage
                if (currentIndex != playbackState.currentPlaylistIndex) {
                    onPlayIndex(currentIndex)
                }
            }
        }

        val currentScale = if (baseExpandedSize.value > 0f) currentSize.value / baseExpandedSize.value else 1f

        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.foundation.LocalOverscrollFactory provides null
        ) {
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = with(density) { currentX.toPx() }.roundToInt(),
                            y = with(density) { currentY.toPx() }.roundToInt()
                        )
                    }
                    .size(baseExpandedSize)
                    .graphicsLayer {
                        scaleX = currentScale
                        scaleY = currentScale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                        clip = true
                        shape = RoundedCornerShape(12.dp / currentScale)
                    },
                userScrollEnabled = progress > 0.5f // Only allow swiping when expanded
            ) { page ->
            val pageStation = if (isInfinite && playlistSize > 0) playlist.getOrNull(page % playlistSize) ?: station else playlist.getOrNull(page) ?: station
            val isCurrentPlayingStation = pageStation.stationUuid == station.stationUuid
            
            val hasCoverArt = !playbackState.trackCoverArtUri.isNullOrBlank()
            val isFetching = playbackState.isFetchingArtwork
            val isShowingCover = isCurrentPlayingStation && showCoverArt && (hasCoverArt || isFetching)

            val visualOverlaySize = lerp(lerp(16.dp, 48.dp, progress), 16.dp, historyProgress)
            val overlaySize = visualOverlaySize / currentScale

            val visualOverlayPadding = lerp(lerp(2.dp, 8.dp, progress), 2.dp, historyProgress)
            val overlayPadding = visualOverlayPadding / currentScale

            val coverFraction by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (isShowingCover) 1f else 0f,
                label = "coverFraction"
            )

            val animatedThumbSize = androidx.compose.ui.unit.lerp(baseExpandedSize, overlaySize, coverFraction)
            val animatedThumbPadding = androidx.compose.ui.unit.lerp(0.dp, overlayPadding, coverFraction)
            val animatedCornerRadius = androidx.compose.ui.unit.lerp(0.dp, 6.dp / currentScale, coverFraction)
            val coverAlpha = coverFraction

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isRecording && isCurrentPlayingStation) {
                            Modifier.border(
                                width = 2.dp / currentScale,
                                color = androidx.compose.ui.graphics.Color(0xFFCC0000).copy(alpha = 1f - (progress * 5f).coerceIn(0f, 1f)),
                                shape = RoundedCornerShape(12.dp / currentScale)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clickable { showCoverArt = !showCoverArt }
            ) {
                if (appPreferences.showStationThumbnails && isCurrentPlayingStation && (hasCoverArt || isFetching)) {
                    if (hasCoverArt) {
                        SubcomposeAsyncImage(
                            model = coil3.request.ImageRequest.Builder(LocalContext.current)
                                .data(playbackState.trackCoverArtUri)
                                .size(coil3.size.Size.ORIGINAL)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.High,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(coverAlpha)
                        )
                    } else if (isFetching) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(coverAlpha)
                                .shimmerEffect()
                        )
                    }
                }

                if (appPreferences.showStationThumbnails && pageStation.favicon.isNotBlank()) {
                    SubcomposeAsyncImage(
                        model = coil3.request.ImageRequest.Builder(LocalContext.current)
                            .data(pageStation.favicon.ifBlank { null })
                            .size(coil3.size.Size.ORIGINAL)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(animatedThumbPadding)
                            .size(animatedThumbSize)
                            .clip(RoundedCornerShape(animatedCornerRadius))
                            .then(
                                if (isShowingCover) {
                                    Modifier
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(1.dp / currentScale, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(animatedCornerRadius))
                                } else {
                                    Modifier
                                }
                            ),
                        error = {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                    contentDescription = null,
                                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { scaleX = 1.6f; scaleY = 1.6f }
                                )
                            }
                        },
                        loading = {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                    contentDescription = null,
                                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { scaleX = 1.6f; scaleY = 1.6f }
                                )
                            }
                        }
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(animatedThumbPadding)
                            .size(animatedThumbSize)
                            .clip(RoundedCornerShape(animatedCornerRadius))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .then(
                                if (isShowingCover) {
                                    Modifier
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(1.dp / currentScale, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(animatedCornerRadius))
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { scaleX = 1.6f; scaleY = 1.6f }
                        )
                    }
                }
            }
        }
        }

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
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
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
                            .basicMarquee(iterations = Int.MAX_VALUE)
                            .pointerInput(currentTrackText) {
                                detectTapGestures(
                                    onTap = {
                                        if (progress < 0.1f) onExpand()
                                    },
                                    onLongPress = {
                                        if (playbackState.currentTrack != null) {
                                            clipboardManager.setText(AnnotatedString(currentTrackText))
                                            Toast.makeText(context, trackCopiedMessage, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                    )
                }

                IconButton(
                    onClick = onPrevious,
                    enabled = playbackState.hasPrevious && playbackState.currentPlaylist.size > 1
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.player_cd_previous)
                    )
                }

                IconButton(onClick = onTogglePlayPause) {
                    if (playbackState.isLoading) {
                        androidx.compose.material3.LoadingIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                    }
                }

                IconButton(
                    onClick = onNext,
                    enabled = playbackState.hasNext && playbackState.currentPlaylist.size > 1
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
                        .padding(top = 8.dp)
                        .collapseHeight(historyProgress)
                        .alpha(1f - historyProgress)
                ) {
                    FilledTonalIconButton(
                        onClick = onCollapse,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.player_cd_collapse),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showCastDialog = true }) {
                            Icon(
                                imageVector = if (connectedCastDevice != null) Icons.Default.CastConnected else Icons.Default.Cast,
                                contentDescription = stringResource(R.string.player_cd_cast),
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
                    
                    val sessionActiveDurationMs = playbackState.sessionActiveDurationMs
                    val sessionResumeTimeMs = playbackState.sessionResumeTimeMs
                    
                    var timerSeconds by remember { mutableLongStateOf(0L) }

                    LaunchedEffect(sessionActiveDurationMs, sessionResumeTimeMs, playbackState.isPlaying, playbackState.isLoading) {
                        if (playbackState.isPlaying && !playbackState.isLoading && sessionResumeTimeMs != null) {
                            while (true) {
                                val currentElapsedMs = sessionActiveDurationMs + (System.currentTimeMillis() - sessionResumeTimeMs)
                                timerSeconds = (currentElapsedMs / 1000).coerceAtLeast(0L)
                                delay(1000L)
                            }
                        } else {
                            timerSeconds = (sessionActiveDurationMs / 1000).coerceAtLeast(0L)
                        }
                    }

                    val hours = timerSeconds / 3600
                    val minutes = (timerSeconds % 3600) / 60
                    val seconds = timerSeconds % 60
                    val timerString = if (hours > 0) {
                        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
                    } else {
                        String.format(Locale.US, "%02d:%02d", minutes, seconds)
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = hasInfo) { userToggledTimer = !userToggledTimer }
                            .animateContentSize()
                            .padding(
                                start = 12.dp,
                                end = if (hasInfo) 8.dp else 12.dp,
                                top = 6.dp,
                                bottom = 6.dp
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            AnimatedContent(
                                targetState = showTimer,
                                label = "TimerTransition"
                            ) { isShowingTimer ->
                                if (isShowingTimer) {
                                    Text(
                                        text = timerString,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    val infoText = if (hasBitrate && hasCodec) {
                                        stringResource(R.string.player_station_codec_bitrate, displayCodec.uppercase(), displayBitrate.toString())
                                    } else if (hasBitrate) {
                                        stringResource(R.string.player_station_bitrate_only, displayBitrate.toString())
                                    } else {
                                        displayCodec.uppercase()
                                    }
                                    Text(
                                        text = infoText,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (hasInfo) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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
                                        28.dp + baseExpandedSize, // 68dp (Thumbnail Y) + size + 16dp (gap) - 56dp (Header Box) = 28dp + size
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
                                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                )
                                val currentTrackText = if (playbackState.isLoading) stringResource(R.string.player_buffering) else playbackState.currentTrack ?: stringResource(R.string.player_no_track_data)
                                Text(
                                    text = currentTrackText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                )
                            }

                            IconButton(
                                onClick = onPrevious,
                                enabled = playbackState.hasPrevious && playbackState.currentPlaylist.size > 1
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = stringResource(R.string.player_cd_previous)
                                )
                            }

                            IconButton(onClick = onTogglePlayPause) {
                                if (playbackState.isLoading) {
                                    androidx.compose.material3.LoadingIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null
                                    )
                                }
                            }

                            IconButton(
                                onClick = onNext,
                                enabled = playbackState.hasNext && playbackState.currentPlaylist.size > 1
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
                        .alpha(1f - historyProgress)
                        .verticalScroll(rememberScrollState()),
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
                                .defaultMinSize(minHeight = 48.dp)
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
                                    .basicMarquee(iterations = Int.MAX_VALUE)
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
                        } else if (playbackState.currentTrack != null) {
                            playbackState.currentTrack!!
                        } else if (playbackState.isLoading) {
                            bufferingText
                        } else {
                            noTrackDataText
                        }
                        
                        val isSearchExpanded = searchDialogTrack != null
                        val canSearch = displayTrack.isNotBlank() && displayTrack != bufferingText && displayTrack != noTrackDataText
                        
                        TrackPill(
                            displayTrack = displayTrack.ifBlank { noTrackDataText },
                            trackCoverArtUri = if (canSearch) playbackState.trackCoverArtUri else null,
                            isFetchingArtwork = if (canSearch) playbackState.isFetchingArtwork else false,
                            canSearch = canSearch,
                            isSearchExpanded = isSearchExpanded,
                            onOpenSearch = { track -> searchDialogTrack = track }
                        )

                    // Waveform and Volume UI
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        val controlsInnerWidth = maxWidth
                        val controlsGaps = 8.dp * 4
                        val controlsFixedWidths = 64.dp * 2
                        val controlsRemainingWidth = controlsInnerWidth - controlsFixedWidths - controlsGaps
                        val recordButtonWidth = controlsRemainingWidth * (0.7f / 3.4f)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Waveform Pill
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp)
                                    .background(
                                        color = if (isRecording) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) 
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(20.dp)
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

                            // Volume Button
                            Box(
                                modifier = Modifier
                                    .width(recordButtonWidth)
                                    .height(64.dp)
                            ) {
                                val isBoosted = volumeLevel > 1.0f
                                val volumeIcon = when {
                                    volumeLevel == 0f -> Icons.AutoMirrored.Filled.VolumeOff
                                    volumeLevel < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
                                    else -> Icons.AutoMirrored.Filled.VolumeUp
                                }

                                androidx.compose.animation.AnimatedVisibility(
                                    visible = !showVolumeDialog,
                                    enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(300)),
                                    exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(300))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .sharedBounds(
                                                sharedContentState = rememberSharedContentState(key = "volume_container"),
                                                animatedVisibilityScope = this@AnimatedVisibility,
                                                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(300)),
                                                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(300)),
                                                boundsTransform = { _, _ -> androidx.compose.animation.core.tween(durationMillis = 350) },
                                                clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(20.dp))
                                            )
                                            .fillMaxSize()
                                            .background(
                                                color = if (isBoosted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(20.dp)
                                            )
                                            .clip(RoundedCornerShape(20.dp))
                                            .clickable { showVolumeDialog = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = volumeIcon,
                                            contentDescription = stringResource(R.string.player_cd_volume),
                                            tint = if (isBoosted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier
                                                .sharedElement(
                                                    sharedContentState = rememberSharedContentState(key = "volume_icon"),
                                                    animatedVisibilityScope = this@AnimatedVisibility,
                                                    boundsTransform = { _, _ -> androidx.compose.animation.core.tween(durationMillis = 350) }
                                                )
                                                .size(28.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } // End of Main controls column

                Spacer(modifier = Modifier.height(12.dp))

                // Controls Row
                Controls(
                    historyProgress = historyProgress,
                    showSleepTimerDialog = showSleepTimerDialog,
                    sleepTimerEndTime = playbackState.sleepTimerEndTime,
                    remainingTime = remainingTime,
                    sleepTimerProgress = sleepTimerProgress,
                    hasPrevious = playbackState.hasPrevious && playbackState.currentPlaylist.size > 1,
                    hasNext = playbackState.hasNext && playbackState.currentPlaylist.size > 1,
                    isPlaying = playbackState.isPlaying,
                    isLoading = playbackState.isLoading,
                    isRecording = isRecording,
                    onOpenSleepTimer = { showSleepTimerDialog = true },
                    onPrevious = onPrevious,
                    onTogglePlayPause = onTogglePlayPause,
                    onNext = onNext,
                    onToggleRecording = onToggleRecording
                )
                } // End of Player UI wrapper

                // Bottom Tabs (sits naturally below everything else, moves up as above content collapses)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
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
                    contentAlignment = Alignment.Center
                ) {
                    val isHistoryExpanded = historyProgress > 0.5f

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

                    val tab4TextColor by androidx.compose.animation.animateColorAsState(
                        targetValue = if (isHistoryExpanded && bottomPagerState.currentPage == 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "tab4Text"
                    )

                    val isPureBlack = MaterialTheme.colorScheme.surfaceContainerLow == androidx.compose.ui.graphics.Color.Black
                    
                    val scrollState = rememberScrollState()
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val tabWidths = remember { androidx.compose.runtime.mutableStateListOf(0.dp, 0.dp, 0.dp, 0.dp) }
                    val tabOffsets = remember { androidx.compose.runtime.mutableStateListOf(0.dp, 0.dp, 0.dp, 0.dp) }

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier.horizontalScroll(scrollState)
                        ) {
                            if (isHistoryExpanded) {
                                val currentOffset = tabOffsets.getOrElse(bottomPagerState.currentPage) { 0.dp }
                                val currentWidth = tabWidths.getOrElse(bottomPagerState.currentPage) { 0.dp }
                                
                                val animOffset by androidx.compose.animation.core.animateDpAsState(currentOffset, label = "offset")
                                val animWidth by androidx.compose.animation.core.animateDpAsState(currentWidth, label = "width")
                                
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .offset(x = animOffset)
                                        .width(animWidth)
                                        .height(36.dp)
                                        .padding(horizontal = 4.dp)
                                        .zIndex(-1f)
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
                            
                            val tabs = listOf(
                                stringResource(R.string.player_tab_tracks) to tab1TextColor,
                                stringResource(R.string.home_tab_recordings) to tab2TextColor,
                                stringResource(R.string.player_tab_lyrics) to tab3TextColor,
                                stringResource(R.string.player_tab_about) to tab4TextColor
                            )
                            
                            Row {
                                tabs.forEachIndexed { index, (title, color) ->
                                    Box(
                                        modifier = Modifier
                                            .onGloballyPositioned { coords ->
                                                val width = with(density) { coords.size.width.toDp() }
                                                val offset = with(density) { coords.positionInParent().x.toDp() }
                                                if (tabWidths[index] != width) tabWidths[index] = width
                                                if (tabOffsets[index] != offset) tabOffsets[index] = offset
                                            }
                                            .clickable(
                                                indication = null,
                                                interactionSource = remember { MutableInteractionSource() },
                                                onClick = {
                                                    if (bottomPagerState.currentPage != index) {
                                                        coroutineScope.launch {
                                                            launch { bottomPagerState.animateScrollToPage(index) }
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
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = title,
                                            fontWeight = FontWeight.Bold,
                                            color = color
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Tab Panel Content
                if (historyProgress > 0f) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.foundation.LocalOverscrollFactory provides null
                    ) {
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
                                listState = historyListState,
                                nestedScrollConnection = nestedScrollConnection
                            )
                        } else if (page == 1) {
                            RecordingsTab(
                                activeSessions = activeSessions,
                                stationRecordings = stationRecordings,
                                listState = recordingsListState,
                                nestedScrollConnection = nestedScrollConnection,
                                onStopRecording = onStopRecording,
                                onDeleteRecording = onDeleteRecording
                            )
                        } else if (page == 2) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                            ) {
                                LyricsTab(
                                    listState = lyricsListState,
                                    nestedScrollConnection = nestedScrollConnection,
                                    lyricsState = lyricsState,
                                    trackStartTime = playbackState.trackStartTime,
                                    syncOffsetMs = playbackState.lyricsSyncOffsetMs,
                                    isPlaying = playbackState.isPlaying,
                                    getCurrentPosition = getCurrentPosition,
                                    onSyncOffsetChange = onSyncOffsetChange
                                )
                            }
                        } else if (page == 3) {
                            AboutTab(
                                station = station,
                                listState = aboutListState,
                                nestedScrollConnection = nestedScrollConnection
                            )
                        }
                    }
                    }
                }
            }
        }
    }

        // DIALOG: visible when expanded — same sharedBounds key as pill = true container transform
        TrackDialog(
            searchDialogTrack = searchDialogTrack,
            rawTrackName = playbackState.rawTrackName,
            trackCoverArtUri = playbackState.trackCoverArtUri,
            isFetchingArtwork = playbackState.isFetchingArtwork,
            onDismissRequest = { searchDialogTrack = null }
        )

        VolumeDialog(
            showDialog = showVolumeDialog,
            onDismissRequest = { showVolumeDialog = false },
            volume = volumeLevel,
            onVolumeChange = onVolumeChange,
            isCastActive = connectedCastDevice != null
        )

        SleepTimerDialog(
            showDialog = showSleepTimerDialog,
            activeTimerEndTime = playbackState.sleepTimerEndTime,
            timerProgress = sleepTimerProgress,
            onDismissRequest = { showSleepTimerDialog = false },
            onSetTimer = { onSetSleepTimer(it) },
            onCancelTimer = { onCancelSleepTimer() }
        )
    }
}
}
