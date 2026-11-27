@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.armanmaurya.internetradio.ui.mobile.screens.player

import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.armanmaurya.internetradio.core.utils.LyricsExporter
import com.armanmaurya.internetradio.ui.mobile.screens.player.components.SleepTimerDialog

import com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.history.HistoryTab
import com.armanmaurya.internetradio.ui.mobile.screens.player.components.TrackPill
import com.armanmaurya.internetradio.ui.mobile.screens.player.components.VolumeDialog
import com.armanmaurya.internetradio.ui.mobile.screens.player.components.TrackDialog
import com.armanmaurya.internetradio.ui.mobile.screens.player.components.Controls
import com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.recordings.RecordingsTab
import android.text.format.DateUtils
import com.armanmaurya.internetradio.ui.mobile.screens.player.components.AnimatedMiniModeSwitcherBar
import com.armanmaurya.internetradio.ui.mobile.screens.player.components.HeaderPairButton
import com.armanmaurya.internetradio.ui.mobile.screens.player.components.MINI_MODE_BAR_HEIGHT
import com.armanmaurya.internetradio.ui.mobile.screens.player.components.PlayerBottomTabsBar
import com.armanmaurya.internetradio.ui.mobile.screens.player.components.PlayerModeSegmentedPill
import com.armanmaurya.internetradio.ui.mobile.screens.player.components.RecordingArtworkContent
import com.armanmaurya.internetradio.ui.mobile.screens.player.components.RecordingFolderTabPage
import com.armanmaurya.internetradio.ui.mobile.screens.player.components.RecordingNoteEditDialog
import com.armanmaurya.internetradio.ui.mobile.screens.player.components.RecordingSeekBarSection
import com.armanmaurya.internetradio.ui.mobile.screens.player.components.RecordingSpeedDialog
import com.armanmaurya.internetradio.ui.mobile.screens.player.components.RecordingTracksTabPage
import com.armanmaurya.internetradio.ui.mobile.screens.player.components.SPEED_CONTAINER_KEY
import com.armanmaurya.internetradio.ui.mobile.screens.player.components.SPEED_VALUE_KEY
import com.armanmaurya.internetradio.ui.mobile.screens.player.components.formatPlaybackSpeed
import com.armanmaurya.internetradio.ui.mobile.screens.player.components.rememberBottomDrawerNestedScroll
import com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.about.AboutTab
import com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.about.RecordingAboutTab
import com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.lyrics.LyricsTab
import com.armanmaurya.internetradio.ui.shared.components.shimmerEffect
import com.armanmaurya.internetradio.domain.controller.ActivePlaybackMode
import com.armanmaurya.internetradio.domain.model.RecordingFile
import com.armanmaurya.internetradio.ui.shared.viewmodels.RecordingPlayerUiState
import com.armanmaurya.internetradio.ui.shared.viewmodels.RecordingPlayerViewModel
import androidx.compose.ui.zIndex
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
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
import com.armanmaurya.internetradio.ui.shared.components.CastDeviceDialog
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
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
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.FilterQuality
import coil3.compose.SubcomposeAsyncImage
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.CastDevice
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity
import com.armanmaurya.internetradio.ui.shared.viewmodels.PlayerUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout

import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.armanmaurya.internetradio.ui.shared.viewmodels.PlayerViewModel
import android.view.WindowManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

fun Modifier.collapseHeight(progress: Float) = this
    .clipToBounds()
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val height = (placeable.height * (1f - progress)).toInt().coerceAtLeast(0)
        layout(placeable.width, height) {
            if (height > 0) {
                placeable.placeRelative(0, (height - placeable.height) / 2) // center vertically while collapsing
            }
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
fun calculatePlayerSheetProgress(
    currentValue: SheetValue,
    targetValue: SheetValue,
    currentOffset: Float?,
    screenHeightPx: Float,
    peekHeightPx: Float
): Float {
    if (currentOffset == null) {
        return if (targetValue == SheetValue.Expanded) 1f else 0f
    }
    if (currentOffset <= 0f) return 1f
    val totalRange = screenHeightPx - peekHeightPx
    if (totalRange <= 0f) return if (targetValue == SheetValue.Expanded) 1f else 0f
    return (1f - (currentOffset / totalRange)).coerceIn(0f, 1f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberPlayerSheetProgress(
    scaffoldState: BottomSheetScaffoldState,
    bottomInset: Dp,
    extraPeekHeight: Dp = 0.dp
): State<Float> {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val imeInsets = WindowInsets.ime
    val peekHeightPx = with(density) { (72.dp + extraPeekHeight + bottomInset).toPx() }

    return remember(scaffoldState, screenHeightPx, peekHeightPx, imeInsets) {
        derivedStateOf {
            val imeHeightPx = imeInsets.getBottom(density).toFloat()
            val fullHeight = screenHeightPx - imeHeightPx

            val currentOffset = try {
                scaffoldState.bottomSheetState.requireOffset()
            } catch (e: Exception) {
                null
            }

            calculatePlayerSheetProgress(
                currentValue = scaffoldState.bottomSheetState.currentValue,
                targetValue = scaffoldState.bottomSheetState.targetValue,
                currentOffset = currentOffset,
                screenHeightPx = fullHeight,
                peekHeightPx = peekHeightPx
            )
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
    onDialogOpenChange: (Boolean) -> Unit = {},
    onNavigateToRecording: (RecordingFile) -> Unit = {},
    playerViewModel: PlayerViewModel = hiltViewModel(),
    recordingPlayerViewModel: RecordingPlayerViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    val uiState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val recordingUiState by recordingPlayerViewModel.uiState.collectAsStateWithLifecycle()
    val activeMode by recordingPlayerViewModel.activeMode.collectAsStateWithLifecycle()
    val discoveredCastDevices by playerViewModel.discoveredCastDevices.collectAsStateWithLifecycle()

    val hasStation = uiState.currentStation != null
    val hasRecording = recordingUiState.currentRecording != null
    val hasMedia = hasStation || hasRecording
    val effectiveMode = when {
        activeMode == ActivePlaybackMode.RECORDING && hasRecording -> ActivePlaybackMode.RECORDING
        hasStation -> ActivePlaybackMode.STATION
        hasRecording -> ActivePlaybackMode.RECORDING
        else -> ActivePlaybackMode.STATION
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
        if (scaffoldState.bottomSheetState.currentValue == SheetValue.Hidden && hasMedia) {
            if (effectiveMode == ActivePlaybackMode.RECORDING) {
                recordingPlayerViewModel.stop()
            } else {
                playerViewModel.stop()
            }
        }
    }

    // Handle Re-appearing (Show player when media starts playing) and Hiding (when playback stops)
    LaunchedEffect(hasMedia) {
        if (hasMedia && scaffoldState.bottomSheetState.currentValue == SheetValue.Hidden) {
            if (scaffoldState.bottomSheetState.targetValue != SheetValue.Expanded) {
                scaffoldState.bottomSheetState.partialExpand()
            }
        } else if (!hasMedia && scaffoldState.bottomSheetState.currentValue != SheetValue.Hidden) {
            scaffoldState.bottomSheetState.hide()
        }
    }

    // Hide software keyboard when sheet settles in Expanded state and clear any stuck dragTarget
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(
        scaffoldState.bottomSheetState.currentValue,
        scaffoldState.bottomSheetState.isAnimationRunning
    ) {
        when (scaffoldState.bottomSheetState.currentValue) {
            SheetValue.Expanded -> {
                focusManager.clearFocus()
                keyboardController?.hide()
                if (scaffoldState.bottomSheetState.isAnimationRunning &&
                    scaffoldState.bottomSheetState.targetValue == SheetValue.Expanded
                ) {
                    scaffoldState.bottomSheetState.expand()
                }
            }
            SheetValue.PartiallyExpanded -> {
                if (scaffoldState.bottomSheetState.isAnimationRunning &&
                    scaffoldState.bottomSheetState.targetValue == SheetValue.PartiallyExpanded
                ) {
                    scaffoldState.bottomSheetState.partialExpand()
                }
            }
            SheetValue.Hidden -> Unit
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

    LaunchedEffect(hasMedia, effectiveMode) {
        if (hasMedia) {
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    val sheetContext = androidx.compose.ui.platform.LocalContext.current
    var pendingHistoryPayload by remember { mutableStateOf<LyricsExporter.ExportPayload?>(null) }
    val historyExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && pendingHistoryPayload != null) {
            LyricsExporter.writeToUri(sheetContext, uri, pendingHistoryPayload!!.content)
        }
        pendingHistoryPayload = null
    }

    val extraMiniHeight by animateDpAsState(
        targetValue = if (hasStation && hasRecording) MINI_MODE_BAR_HEIGHT else 0.dp,
        label = "miniModeSheetHeight"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .heightIn(min = 72.dp + extraMiniHeight + bottomInset)
    ) {
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = enableSwipeToDismiss,
            enableDismissFromEndToStart = enableSwipeToDismiss,
            gesturesEnabled = enableSwipeToDismiss,
            onDismiss = {
                if (hasStation && hasRecording) {
                    if (effectiveMode == ActivePlaybackMode.RECORDING) {
                        recordingPlayerViewModel.stop()
                    } else {
                        playerViewModel.stop()
                    }
                    scope.launch { dismissState.snapTo(SwipeToDismissBoxValue.Settled) }
                } else {
                    scope.launch { scaffoldState.bottomSheetState.hide() }
                }
            },
            backgroundContent = {
                if (dismissState.dismissDirection != SwipeToDismissBoxValue.Settled) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp + extraMiniHeight)
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
                uiState = uiState,
                recordingUiState = recordingUiState,
                activeMode = effectiveMode,
                progress = progress,
                onSelectMode = recordingPlayerViewModel::setActiveMode,
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
                onToggleRecording = playerViewModel::toggleRecording,
                onSyncOffsetChange = playerViewModel::setLyricsSyncOffset,
                onSearchLyrics = playerViewModel::searchLyrics,
                onSelectLyricsProvider = playerViewModel::selectLyricsProvider,
                onResetLyricsQuery = playerViewModel::resetLyricsQuery,
                discoveredCastDevices = discoveredCastDevices,
                onVolumeChange = playerViewModel::setVolume,
                onConnectCastDevice = playerViewModel::connectToCastDevice,
                onDisconnectCastDevice = playerViewModel::disconnectCastDevice,
                onDeleteRecording = playerViewModel::deleteRecording,
                getCurrentPosition = { playerViewModel.currentPosition },
                onToggleTrackStarred = playerViewModel::toggleTrackStarred,
                onLoadMoreHistory = playerViewModel::loadMoreHistory,
                onToggleCurrentTrackStarred = {
                    val trackTitle = uiState.cleanTrackName ?: uiState.currentTrack
                    if (!trackTitle.isNullOrBlank()) {
                        playerViewModel.toggleTrackStarred(trackTitle, !uiState.isCurrentTrackStarred)
                    }
                },
                onDownloadHistoryLyrics = { track ->
                    scope.launch {
                        val lyrics = playerViewModel.getLyricsForHistoryTrack(track)
                        if (lyrics != null) {
                            val payload = LyricsExporter.prepareExport(lyrics, defaultTitle = track.trackTitle, defaultArtist = track.artist)
                            pendingHistoryPayload = payload
                            historyExportLauncher.launch(payload.defaultFileName)
                        } else {
                            android.widget.Toast.makeText(sheetContext, sheetContext.getString(R.string.player_lyrics_not_available), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onToggleRecordingPlayPause = recordingPlayerViewModel::togglePlayPause,
                onSeekRecording = recordingPlayerViewModel::seekTo,
                onRecordingPrevious = recordingPlayerViewModel::previous,
                onRecordingNext = recordingPlayerViewModel::next,
                onPlayRecordingItem = recordingPlayerViewModel::playRecording,
                onRecordingSpeedChange = recordingPlayerViewModel::setPlaybackSpeed,
                onToggleRecordingLoop = recordingPlayerViewModel::toggleLoop,
                onSetRecordingSleepTimer = recordingPlayerViewModel::setSleepTimer,
                onCancelRecordingSleepTimer = recordingPlayerViewModel::cancelSleepTimer,
                onRecordingVolumeChange = recordingPlayerViewModel::setVolume,
                onRecordingSyncOffsetChange = recordingPlayerViewModel::setLyricsSyncOffset,
                onSearchRecordingLyrics = recordingPlayerViewModel::searchLyrics,
                onSelectRecordingLyricsProvider = recordingPlayerViewModel::selectLyricsProvider,
                onResetRecordingLyricsQuery = recordingPlayerViewModel::resetLyricsQuery,
                getRecordingPositionRealtime = recordingPlayerViewModel::getCurrentPositionRealtime,
                onSaveRecordingNote = recordingPlayerViewModel::updateNote,
                onDeleteRecordingItem = recordingPlayerViewModel::deleteRecording,
                onNavigateToRecording = onNavigateToRecording,
                onDialogOpenChange = onDialogOpenChange
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun PlayerSheetContent(
    isWidescreen: Boolean,
    uiState: PlayerUiState,
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
    onToggleRecording: () -> Unit,
    onSyncOffsetChange: (Long) -> Unit,
    recordingUiState: RecordingPlayerUiState = RecordingPlayerUiState(),
    activeMode: ActivePlaybackMode = ActivePlaybackMode.STATION,
    onSelectMode: (ActivePlaybackMode) -> Unit = {},
    onSearchLyrics: ((String, String?) -> Unit)? = null,
    onSelectLyricsProvider: ((String?) -> Unit)? = null,
    onResetLyricsQuery: (() -> Unit)? = null,
    discoveredCastDevices: List<CastDevice> = emptyList(),
    onVolumeChange: (Float) -> Unit = {},
    onConnectCastDevice: (CastDevice) -> Unit = {},
    onDisconnectCastDevice: () -> Unit = {},
    onDeleteRecording: (RecordingFile) -> Unit,
    getCurrentPosition: () -> Long,
    onToggleTrackStarred: (TrackHistoryEntity) -> Unit = {},
    onLoadMoreHistory: () -> Unit = {},
    onToggleCurrentTrackStarred: () -> Unit = {},
    onDownloadHistoryLyrics: ((TrackHistoryEntity) -> Unit)? = null,
    onToggleRecordingPlayPause: () -> Unit = {},
    onSeekRecording: (Long) -> Unit = {},
    onRecordingPrevious: () -> Unit = {},
    onRecordingNext: () -> Unit = {},
    onPlayRecordingItem: (RecordingFile, List<RecordingFile>) -> Unit = { _, _ -> },
    onRecordingSpeedChange: (Float) -> Unit = {},
    onToggleRecordingLoop: () -> Unit = {},
    onSetRecordingSleepTimer: (Long) -> Unit = {},
    onCancelRecordingSleepTimer: () -> Unit = {},
    onRecordingVolumeChange: (Float) -> Unit = {},
    onRecordingSyncOffsetChange: (Long) -> Unit = {},
    onSearchRecordingLyrics: (String, String?) -> Unit = { _, _ -> },
    onSelectRecordingLyricsProvider: (String?) -> Unit = {},
    onResetRecordingLyricsQuery: () -> Unit = {},
    getRecordingPositionRealtime: () -> Long = { 0L },
    onSaveRecordingNote: (RecordingFile, String?) -> Unit = { _, _ -> },
    onDeleteRecordingItem: (RecordingFile) -> Unit = {},
    onNavigateToRecording: (RecordingFile) -> Unit = {},
    onDialogOpenChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val playbackState = uiState
    val station = playbackState.currentStation
    val recording = recordingUiState.currentRecording
    if (station == null && recording == null) return

    val isRecordingMode = activeMode == ActivePlaybackMode.RECORDING && recording != null
    val hasBothModes = station != null && recording != null

    val isFavorite = uiState.isFavorite
    val trackHistory = uiState.trackHistory
    val stationRecordings = uiState.stationRecordings
    val retryCountdown = uiState.retryCountdown
    val lyricsUiState = uiState.lyricsUiState
    val isRecording = uiState.isRecording
    val recordingDuration = uiState.recordingDuration
    val amplitude = uiState.amplitude
    val connectedCastDevice = uiState.connectedCastDevice
    val volume = if (uiState.connectedCastDevice != null) uiState.castVolume else uiState.volume
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val clipboardManager = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val castConnectedMessage = stringResource(R.string.player_cast_connected)
    val trackCopiedMessage = stringResource(R.string.player_copied_track_to_clipboard)

    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var searchDialogTrack by remember { mutableStateOf<String?>(null) }
    var showCastDialog by remember { mutableStateOf(false) }
    var showCoverArt by remember { mutableStateOf(false) }
    var showVolumeDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(connectedCastDevice) {
        if (connectedCastDevice != null) {
            android.widget.Toast.makeText(context, castConnectedMessage, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    val bottomPagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 4 })

    val activeSleepTimerEndTime = if (isRecordingMode) recordingUiState.sleepTimerEndTimeMs else playbackState.sleepTimerEndTime
    val activeSleepTimerTotalDuration = if (isRecordingMode) recordingUiState.sleepTimerTotalDurationMs else playbackState.sleepTimerTotalDuration

    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(activeSleepTimerEndTime, isRecordingMode) {
        val endTime = activeSleepTimerEndTime
        if (endTime != null) {
            while (true) {
                currentTime = System.currentTimeMillis()
                if (currentTime >= endTime) {
                    if (isRecordingMode) onCancelRecordingSleepTimer() else onCancelSleepTimer()
                    break
                }
                delay(1000)
            }
        }
    }

    val displayCodec = if (station != null) {
        (if (!isFavorite || station.codec.isBlank() || station.codec.equals("UNKNOWN", ignoreCase = true)) playbackState.streamCodec else null) ?: station.codec
    } else ""
    val displayBitrate = if (station != null) {
        (if (!isFavorite || station.bitrate <= 0) playbackState.streamBitrate else null) ?: station.bitrate
    } else 0

    val hasBitrate = displayBitrate > 0
    val hasCodec = displayCodec.isNotBlank() && displayCodec.uppercase() != "UNKNOWN"
    val hasInfo = hasBitrate || hasCodec
    var userToggledTimer by rememberSaveable(station?.stationUuid ?: "none") { mutableStateOf(false) }
    val showTimer = if (hasInfo) userToggledTimer else true

    // Tell the sheet when a player dialog is open so dragging can't collapse it underneath.
    val isAnyDialogOpen = showCastDialog || showVolumeDialog || showSleepTimerDialog || searchDialogTrack != null || showSpeedDialog || showNoteDialog
    BackHandler(enabled = showSpeedDialog) { showSpeedDialog = false }
    val currentOnDialogOpenChange by rememberUpdatedState(onDialogOpenChange)
    LaunchedEffect(isAnyDialogOpen) { currentOnDialogOpenChange(isAnyDialogOpen) }
    DisposableEffect(Unit) { onDispose { currentOnDialogOpenChange(false) } }
    val volumeLevel = if (isRecordingMode) recordingUiState.volume else if (connectedCastDevice != null) volume else playbackState.volume
    val appPreferences = LocalAppPreferences.current

    val remainingTime = activeSleepTimerEndTime?.let { (it - currentTime).coerceAtLeast(0L) } ?: 0L
    val sleepTimerProgress = if (activeSleepTimerTotalDuration > 0) {
        (remainingTime.toFloat() / activeSleepTimerTotalDuration).coerceIn(0f, 1f)
    } else 0f

    val coroutineScope = rememberCoroutineScope()
    val historyProgressAnim = remember { Animatable(0f) }
    val historyProgress = historyProgressAnim.value

    // Close history when collapsing the outer sheet back to the mini-player
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

    val maxDragDistance = with(density) { 600.dp.toPx() }
    val nestedScrollConnection = rememberBottomDrawerNestedScroll(
        currentListState = currentListState,
        historyProgressAnim = historyProgressAnim,
        maxDragDistance = maxDragDistance,
        coroutineScope = coroutineScope
    )

    SharedTransitionLayout {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(enabled = progress < 0.1f, onClick = onExpand)
        ) {
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
        val historyExpandedX = if (isWidescreen) baseExpandedX else miniX
        val actualExpandedX = lerp(baseExpandedX, historyExpandedX, historyProgress)
        
        val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val baseExpandedY = if (isWidescreen) {
            val availableHeight = screenHeight - statusBarPadding
            statusBarPadding + (availableHeight - baseExpandedSize) / 2
        } else {
            68.dp + statusBarPadding
        }
        val historyExpandedY = if (isWidescreen) baseExpandedY else miniY + statusBarPadding
        val actualExpandedY = lerp(baseExpandedY, historyExpandedY, historyProgress)
        
        val currentX = lerp(miniX, actualExpandedX, progress)
        val currentY = lerp(miniY, actualExpandedY, progress)

        // --- The Moving Thumbnail ---
        val playlist = if (playbackState.currentPlaylist.isEmpty()) listOfNotNull(station) else playbackState.currentPlaylist
        val playlistSize = playlist.size
        
        val isInfinite = playlistSize > 1
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
                val currentIndex = if (isInfinite) {
                    ((pagerState.currentPage % playlistSize) + playlistSize) % playlistSize
                } else {
                    pagerState.currentPage
                }
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
                val currentIndex = if (isInfinite) {
                    ((pagerState.currentPage % playlistSize) + playlistSize) % playlistSize
                } else {
                    pagerState.currentPage
                }
                if (currentIndex != playbackState.currentPlaylistIndex) {
                    onPlayIndex(currentIndex)
                }
            }
        }

        val currentScale = if (baseExpandedSize.value > 0f) currentSize.value / baseExpandedSize.value else 1f

        val effectiveRecordingDuration = when {
            recordingUiState.durationMs > 0L -> recordingUiState.durationMs
            recording != null && recording.durationMs > 0L -> recording.durationMs
            else -> 1L
        }
        val activeRecTrack = recordingUiState.activeTrack
        val activeRecTrackText = activeRecTrack?.let {
            if (!it.artist.isNullOrBlank()) "${it.title} - ${it.artist}" else it.title
        }
        val recElapsedText = DateUtils.formatElapsedTime((recordingUiState.currentPositionMs / 1000L).coerceAtLeast(0L))
        val recTotalText = if (effectiveRecordingDuration > 1L) DateUtils.formatElapsedTime(effectiveRecordingDuration / 1000L) else "00:00"

        val activeTitle = if (isRecordingMode) recording!!.fileName else station!!.name
        val recordingProgress = if (isRecordingMode) {
            if (effectiveRecordingDuration > 1L) {
                (recordingUiState.currentPositionMs.toFloat() / effectiveRecordingDuration).coerceIn(0f, 1f)
            } else {
                0f
            }
        } else {
            null
        }
        val activeTrackSubtitle = if (isRecordingMode) {
            buildString {
                val label = activeRecTrackText ?: recording!!.stationName
                if (label.isNotBlank()) {
                    append(label)
                    append(" • ")
                }
                append(recElapsedText)
                if (effectiveRecordingDuration > 1L) {
                    append(" / ")
                    append(recTotalText)
                }
            }
        } else if (retryCountdown != null) {
            stringResource(R.string.player_retrying_in, retryCountdown)
        } else if (playbackState.isLoading) {
            stringResource(R.string.player_buffering)
        } else {
            playbackState.currentTrack ?: stringResource(R.string.player_no_track_data)
        }
        val activeCopyableTrack = if (isRecordingMode) activeRecTrackText else playbackState.currentTrack
        val activeIsPlaying = if (isRecordingMode) recordingUiState.isPlaying else playbackState.isPlaying
        val activeIsLoading = if (isRecordingMode) false else playbackState.isLoading
        val activeHasPrevious = if (isRecordingMode) {
            recordingUiState.hasPrevious && recordingUiState.playlist.size > 1
        } else {
            playbackState.hasPrevious && playbackState.currentPlaylist.size > 1
        }
        val activeHasNext = if (isRecordingMode) {
            recordingUiState.hasNext && recordingUiState.playlist.size > 1
        } else {
            playbackState.hasNext && playbackState.currentPlaylist.size > 1
        }
        val onActiveTogglePlayPause = if (isRecordingMode) onToggleRecordingPlayPause else onTogglePlayPause
        val onActivePrevious = if (isRecordingMode) onRecordingPrevious else onPrevious
        val onActiveNext = if (isRecordingMode) onRecordingNext else onNext

        Box(
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
                }
        ) {
            AnimatedContent(
                targetState = isRecordingMode,
                transitionSpec = {
                    fadeIn(androidx.compose.animation.core.tween(220)) togetherWith
                        fadeOut(androidx.compose.animation.core.tween(180))
                },
                label = "ArtworkModeTransition"
            ) { recMode ->
                if (recMode && recording != null) {
                    RecordingArtworkContent(
                        folderArtworkUri = recordingUiState.folderArtworkUri,
                        trackCoverArtUrl = activeRecTrack?.coverArtUrl,
                        showStationThumbnails = appPreferences.showStationThumbnails,
                        showCoverArt = showCoverArt,
                        onToggleCoverArt = { showCoverArt = !showCoverArt },
                        baseExpandedSize = baseExpandedSize,
                        progress = progress,
                        historyProgress = historyProgress,
                        currentScale = currentScale
                    )
                } else if (station != null) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.foundation.LocalOverscrollFactory provides null
                    ) {
                        androidx.compose.foundation.pager.HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = progress > 0.5f // Only allow swiping when expanded
                        ) { page ->
                            val pageIndex = if (isInfinite && playlistSize > 0) {
                                ((page % playlistSize) + playlistSize) % playlistSize
                            } else {
                                page
                            }
                            val pageStation = playlist.getOrNull(pageIndex) ?: station
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
                }
            }
        }

        // --- Mini Content (Fades out as we expand) ---
        if (progress < 0.9f) {
            MiniPlayerContent(
                activeTitle = activeTitle,
                activeTrackSubtitle = activeTrackSubtitle,
                activeCopyableTrack = activeCopyableTrack,
                activeHasPrevious = activeHasPrevious,
                activeHasNext = activeHasNext,
                activeIsPlaying = activeIsPlaying,
                activeIsLoading = activeIsLoading,
                recordingProgress = recordingProgress,
                hasBothModes = hasBothModes,
                activeMode = activeMode,
                stationName = station?.name,
                recordingName = recording?.fileName,
                isStationPlaying = playbackState.isPlaying,
                isRecordingPlaying = recordingUiState.isPlaying,
                includeThumbnailSpace = true,
                thumbnailSpacerWidth = miniSize + 12.dp + cutoutLeftPadding,
                cutoutLeftPadding = cutoutLeftPadding,
                cutoutRightPadding = cutoutRightPadding,
                interactionEnabled = progress < 0.1f,
                onTitleClick = onExpand,
                onCopyTrack = { track ->
                    clipboardManager.setText(AnnotatedString(track))
                    Toast.makeText(context, trackCopiedMessage, Toast.LENGTH_SHORT).show()
                },
                onPrevious = onActivePrevious,
                onTogglePlayPause = onActiveTogglePlayPause,
                onNext = onActiveNext,
                onSelectMode = onSelectMode,
                modifier = Modifier.alpha(1f - (progress * 5f).coerceIn(0f, 1f))
            )
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
                if (historyProgress < 1f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .collapseHeight(historyProgress)
                            .alpha(1f - historyProgress)
                    ) {
                        FilledTonalIconButton(
                            onClick = onCollapse,
                            enabled = historyProgress == 0f,
                            shapes = androidx.compose.material3.IconButtonShapes(
                                shape = RoundedCornerShape(12.dp),
                                pressedShape = RoundedCornerShape(6.dp)
                            ),
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
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val leftPairShape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp, topEnd = 4.dp, bottomEnd = 4.dp)
                            val rightPairShape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp)
                            val isCasting = connectedCastDevice != null

                            Box(modifier = Modifier.size(width = 48.dp, height = 40.dp)) {
                                if (isRecordingMode) {
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = !showSpeedDialog,
                                        enter = fadeIn(androidx.compose.animation.core.tween(300)),
                                        exit = fadeOut(androidx.compose.animation.core.tween(300))
                                    ) {
                                        HeaderPairButton(
                                            checked = recordingUiState.playbackSpeed != 1.0f,
                                            shape = leftPairShape,
                                            enabled = historyProgress == 0f,
                                            onClick = { showSpeedDialog = true },
                                            modifier = Modifier.sharedBounds(
                                                sharedContentState = rememberSharedContentState(key = SPEED_CONTAINER_KEY),
                                                animatedVisibilityScope = this@AnimatedVisibility,
                                                enter = fadeIn(androidx.compose.animation.core.tween(300)),
                                                exit = fadeOut(androidx.compose.animation.core.tween(300)),
                                                boundsTransform = { _, _ -> androidx.compose.animation.core.tween(durationMillis = 350) },
                                                clipInOverlayDuringTransition = OverlayClip(leftPairShape)
                                            )
                                        ) {
                                            Text(
                                                text = formatPlaybackSpeed(recordingUiState.playbackSpeed),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                modifier = Modifier.sharedElement(
                                                    sharedContentState = rememberSharedContentState(key = SPEED_VALUE_KEY),
                                                    animatedVisibilityScope = this@AnimatedVisibility,
                                                    boundsTransform = { _, _ -> androidx.compose.animation.core.tween(durationMillis = 350) }
                                                )
                                            )
                                        }
                                    }
                                } else {
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = !showCastDialog,
                                        enter = fadeIn(androidx.compose.animation.core.tween(300)),
                                        exit = fadeOut(androidx.compose.animation.core.tween(300))
                                    ) {
                                        HeaderPairButton(
                                            checked = isCasting,
                                            shape = leftPairShape,
                                            enabled = historyProgress == 0f,
                                            onClick = { showCastDialog = true },
                                            modifier = Modifier.sharedBounds(
                                                sharedContentState = rememberSharedContentState(
                                                    key = com.armanmaurya.internetradio.ui.shared.components.CAST_CONTAINER_KEY
                                                ),
                                                animatedVisibilityScope = this@AnimatedVisibility,
                                                enter = fadeIn(androidx.compose.animation.core.tween(300)),
                                                exit = fadeOut(androidx.compose.animation.core.tween(300)),
                                                boundsTransform = { _, _ -> androidx.compose.animation.core.tween(durationMillis = 350) },
                                                clipInOverlayDuringTransition = OverlayClip(leftPairShape)
                                            )
                                        ) {
                                            Icon(
                                                imageVector = if (isCasting) Icons.Default.CastConnected else Icons.Default.Cast,
                                                contentDescription = stringResource(R.string.player_cd_cast),
                                                modifier = Modifier
                                                    .sharedElement(
                                                        sharedContentState = rememberSharedContentState(
                                                            key = com.armanmaurya.internetradio.ui.shared.components.CAST_ICON_KEY
                                                        ),
                                                        animatedVisibilityScope = this@AnimatedVisibility,
                                                        boundsTransform = { _, _ -> androidx.compose.animation.core.tween(durationMillis = 350) }
                                                    )
                                                    .size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            HeaderPairButton(
                                checked = !isRecordingMode && isFavorite,
                                shape = rightPairShape,
                                enabled = historyProgress == 0f,
                                onClick = {
                                    if (isRecordingMode && recording != null) {
                                        onCollapse()
                                        onNavigateToRecording(recording)
                                    } else {
                                        onToggleFavorite()
                                    }
                                },
                                modifier = Modifier.size(width = 48.dp, height = 40.dp)
                            ) {
                                AnimatedContent(
                                    targetState = isRecordingMode,
                                    label = "rightPairIcon"
                                ) { recMode ->
                                    if (recMode) {
                                        Icon(
                                            imageVector = Icons.Default.NearMe,
                                            contentDescription = stringResource(R.string.recording_cd_go_to_recording),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                            contentDescription = stringResource(R.string.player_cd_toggle_favorite),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
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

                        if (hasBothModes) {
                            PlayerModeSegmentedPill(
                                activeMode = activeMode,
                                isStationPlaying = playbackState.isPlaying,
                                isRecordingPlaying = recordingUiState.isPlaying,
                                enabled = historyProgress == 0f,
                                onSelectMode = onSelectMode,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else if (isRecordingMode) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (effectiveRecordingDuration > 1L) "$recElapsedText / $recTotalText" else recElapsedText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(enabled = hasInfo && historyProgress == 0f) { userToggledTimer = !userToggledTimer }
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
                    val extraNestedMiniHeight by animateDpAsState(
                        targetValue = if (hasBothModes) MINI_MODE_BAR_HEIGHT else 0.dp,
                        label = "nestedMiniModeHeight"
                    )
                    // Thumbnail Placeholder and Mini Controls
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        // Placeholder for the moving thumbnail
                        if (!isWidescreen) {
                            Spacer(modifier = Modifier
                                .fillMaxWidth()
                                .height(
                                    lerp(
                                        28.dp + baseExpandedSize, // 68dp (Thumbnail Y) + size + 16dp (gap) - 56dp (Header Box) = 28dp + size
                                        72.dp + extraNestedMiniHeight,
                                        historyProgress
                                    )
                                )
                            )
                        } else {
                            Spacer(modifier = Modifier.height(lerp(16.dp, 72.dp + extraNestedMiniHeight, historyProgress)))
                        }

                        // Mini controls when history is expanded
                        if (historyProgress > 0f) {
                            MiniPlayerContent(
                                activeTitle = activeTitle,
                                activeTrackSubtitle = activeTrackSubtitle,
                                activeCopyableTrack = activeCopyableTrack,
                                activeHasPrevious = activeHasPrevious,
                                activeHasNext = activeHasNext,
                                activeIsPlaying = activeIsPlaying,
                                activeIsLoading = activeIsLoading,
                                recordingProgress = recordingProgress,
                                hasBothModes = hasBothModes,
                                activeMode = activeMode,
                                stationName = station?.name,
                                recordingName = recording?.fileName,
                                isStationPlaying = playbackState.isPlaying,
                                isRecordingPlaying = recordingUiState.isPlaying,
                                includeThumbnailSpace = !isWidescreen,
                                thumbnailSpacerWidth = miniSize + 12.dp + cutoutLeftPadding,
                                cutoutLeftPadding = if (isWidescreen) 0.dp else cutoutLeftPadding,
                                cutoutRightPadding = cutoutRightPadding,
                                interactionEnabled = historyProgress > 0.9f,
                                onTitleClick = { coroutineScope.launch { historyProgressAnim.animateTo(0f) } },
                                onCopyTrack = { track ->
                                    clipboardManager.setText(AnnotatedString(track))
                                    Toast.makeText(context, trackCopiedMessage, Toast.LENGTH_SHORT).show()
                                },
                                onPrevious = onActivePrevious,
                                onTogglePlayPause = onActiveTogglePlayPause,
                                onNext = onActiveNext,
                                onSelectMode = onSelectMode,
                                modifier = Modifier.alpha(historyProgress)
                            )
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
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .clip(RoundedCornerShape(20.dp))
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = activeTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Start,
                                    maxLines = 1,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .basicMarquee(iterations = Int.MAX_VALUE)
                                )
                            }

                            val titleActionShapes = androidx.compose.material3.IconButtonShapes(
                                shape = RoundedCornerShape(20.dp),
                                pressedShape = RoundedCornerShape(12.dp)
                            )
                            if (isRecordingMode && recording != null) {
                                val hasNote = !recording.note.isNullOrBlank()
                                FilledTonalIconButton(
                                    onClick = { showNoteDialog = true },
                                    modifier = Modifier
                                        .width(48.dp)
                                        .height(52.dp),
                                    shapes = titleActionShapes,
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = if (hasNote) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = if (hasNote) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Notes,
                                        contentDescription = stringResource(
                                            if (hasNote) R.string.recording_edit_note
                                            else R.string.recording_add_note
                                        ),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else if (isFavorite && station != null) {
                                FilledTonalIconButton(
                                    onClick = {
                                        onCollapse()
                                        onEditStation(station)
                                    },
                                    modifier = Modifier
                                        .width(48.dp)
                                        .height(52.dp),
                                    shapes = titleActionShapes,
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = stringResource(R.string.edit_station_title),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val bufferingText = stringResource(R.string.player_buffering)
                        val noTrackDataText = stringResource(R.string.player_no_track_data)

                        val displayTrack = if (isRecordingMode) {
                            activeRecTrackText ?: recording!!.stationName.ifBlank { noTrackDataText }
                        } else if (retryCountdown != null) {
                            stringResource(R.string.player_retrying_in, retryCountdown)
                        } else if (playbackState.currentTrack != null) {
                            playbackState.currentTrack
                        } else if (playbackState.isLoading) {
                            bufferingText
                        } else {
                            noTrackDataText
                        }

                        val isSearchExpanded = searchDialogTrack != null
                        val canSearch = if (isRecordingMode) {
                            activeRecTrackText != null
                        } else {
                            displayTrack.isNotBlank() && displayTrack != bufferingText && displayTrack != noTrackDataText
                        }

                        TrackPill(
                            displayTrack = displayTrack.ifBlank { noTrackDataText },
                            trackCoverArtUri = if (isRecordingMode) activeRecTrack?.coverArtUrl else if (canSearch) playbackState.trackCoverArtUri else null,
                            isFetchingArtwork = if (isRecordingMode) false else if (canSearch) playbackState.isFetchingArtwork else false,
                            canSearch = canSearch,
                            isSearchExpanded = isSearchExpanded,
                            onOpenSearch = { track -> searchDialogTrack = track }
                        )

                        // Recording seekbar expands/collapses smoothly between TrackPill and Waveform
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isRecordingMode,
                            enter = androidx.compose.animation.expandVertically(
                                animationSpec = androidx.compose.animation.core.tween(240),
                                expandFrom = Alignment.Top
                            ) + fadeIn(androidx.compose.animation.core.tween(220)),
                            exit = androidx.compose.animation.shrinkVertically(
                                animationSpec = androidx.compose.animation.core.tween(220),
                                shrinkTowards = Alignment.Top
                            ) + fadeOut(androidx.compose.animation.core.tween(160))
                        ) {
                            if (recording != null) {
                                RecordingSeekBarSection(
                                    recordingUri = recording.uri,
                                    currentPositionMs = recordingUiState.currentPositionMs,
                                    durationMs = effectiveRecordingDuration,
                                    isPlaying = recordingUiState.isPlaying,
                                    onSeek = onSeekRecording
                                )
                            }
                        }

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
                            val showLiveRecBanner = !isRecordingMode && isRecording
                            val activeAmplitude = if (isRecordingMode) {
                                if (recordingUiState.isPlaying) recordingUiState.amplitude else 0f
                            } else {
                                if (playbackState.isPlaying) amplitude else 0f
                            }
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp)
                                    .background(
                                        color = if (showLiveRecBanner) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .padding(
                                        start = 0.dp,
                                        end = if (showLiveRecBanner) 16.dp else 0.dp,
                                        top = 8.dp,
                                        bottom = 8.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Waveform
                                ScrollingWaveform(
                                    amplitude = activeAmplitude,
                                    modifier = Modifier.weight(1f),
                                    barColor = if (showLiveRecBanner) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    emptyColor = if (showLiveRecBanner) MaterialTheme.colorScheme.error.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
                                AnimatedVisibility(
                                    visible = showLiveRecBanner,
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
                                val isBoosted = !isRecordingMode && appPreferences.enableVolumeBoost && volumeLevel > 1.0f
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

                // Controls Row (stays mounted and anchored across Station ↔ Recording switches)
                Controls(
                    historyProgress = historyProgress,
                    showSleepTimerDialog = showSleepTimerDialog,
                    sleepTimerEndTime = activeSleepTimerEndTime,
                    remainingTime = remainingTime,
                    sleepTimerProgress = sleepTimerProgress,
                    hasPrevious = activeHasPrevious,
                    hasNext = activeHasNext,
                    isPlaying = activeIsPlaying,
                    isLoading = activeIsLoading,
                    isRecording = if (isRecordingMode) recordingUiState.isLooping else isRecording,
                    onOpenSleepTimer = { showSleepTimerDialog = true },
                    onPrevious = onActivePrevious,
                    onTogglePlayPause = onActiveTogglePlayPause,
                    onNext = onActiveNext,
                    onToggleRecording = if (isRecordingMode) onToggleRecordingLoop else onToggleRecording,
                    trailingIcon = if (isRecordingMode) {
                        if (recordingUiState.isLooping) Icons.Default.RepeatOne else Icons.Default.Repeat
                    } else {
                        Icons.Default.Mic
                    },
                    trailingContentDescription = if (isRecordingMode) {
                        stringResource(
                            if (recordingUiState.isLooping) R.string.recording_cd_loop_enabled
                            else R.string.recording_cd_loop_disabled
                        )
                    } else {
                        stringResource(R.string.player_cd_record)
                    },
                    trailingCheckedContainerColor = if (isRecordingMode) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    trailingCheckedContentColor = if (isRecordingMode) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onError
                    }
                )
                } // End of Player UI wrapper

                // Bottom Tabs (sits naturally below everything else, moves up as above content collapses)
                PlayerBottomTabsBar(
                    tabTitles = listOf(
                        stringResource(R.string.player_tab_tracks),
                        stringResource(R.string.home_tab_recordings),
                        stringResource(R.string.player_tab_lyrics),
                        stringResource(R.string.player_tab_about)
                    ),
                    bottomPagerState = bottomPagerState,
                    historyProgressAnim = historyProgressAnim,
                    maxDragDistance = maxDragDistance,
                    coroutineScope = coroutineScope
                )

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
                            when (page) {
                                0 -> {
                                    if (isRecordingMode && recording != null) {
                                        RecordingTracksTabPage(
                                            recording = recording,
                                            currentPositionMs = recordingUiState.currentPositionMs,
                                            listState = historyListState,
                                            nestedScrollConnection = nestedScrollConnection,
                                            onSeek = onSeekRecording
                                        )
                                    } else {
                                        HistoryTab(
                                            trackHistory = trackHistory,
                                            savedTracks = uiState.savedTracks,
                                            listState = historyListState,
                                            nestedScrollConnection = nestedScrollConnection,
                                            onToggleStar = onToggleTrackStarred,
                                            onLoadMore = onLoadMoreHistory,
                                            onDownloadLyrics = onDownloadHistoryLyrics
                                        )
                                    }
                                }
                                1 -> {
                                    if (isRecordingMode && recording != null) {
                                        val folderRecordings = recordingUiState.folderRecordings.ifEmpty { recordingUiState.playlist }
                                        RecordingFolderTabPage(
                                            currentRecording = recording,
                                            folderRecordings = folderRecordings,
                                            isPlaying = recordingUiState.isPlaying,
                                            listState = recordingsListState,
                                            nestedScrollConnection = nestedScrollConnection,
                                            onTogglePlayPause = onToggleRecordingPlayPause,
                                            onPlayRecording = onPlayRecordingItem,
                                            onDeleteRecording = onDeleteRecordingItem,
                                            onSaveNote = onSaveRecordingNote
                                        )
                                    } else {
                                        RecordingsTab(
                                            stationRecordings = stationRecordings,
                                            listState = recordingsListState,
                                            nestedScrollConnection = nestedScrollConnection,
                                            onDeleteRecording = onDeleteRecording
                                        )
                                    }
                                }
                                2 -> {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        if (isRecordingMode) {
                                            LyricsTab(
                                                listState = lyricsListState,
                                                nestedScrollConnection = nestedScrollConnection,
                                                lyricsUiState = recordingUiState.lyricsUiState,
                                                trackStartTime = activeRecTrack?.offsetMs,
                                                syncOffsetMs = recordingUiState.lyricsSyncOffsetMs,
                                                isPlaying = recordingUiState.isPlaying,
                                                getCurrentPosition = getRecordingPositionRealtime,
                                                onSyncOffsetChange = onRecordingSyncOffsetChange,
                                                playbackSpeed = recordingUiState.playbackSpeed,
                                                currentTrack = activeRecTrack?.title,
                                                currentArtist = activeRecTrack?.artist,
                                                customQueryTitle = recordingUiState.customLyricsQueryTitle,
                                                customQueryArtist = recordingUiState.customLyricsQueryArtist,
                                                selectedProviderId = recordingUiState.selectedLyricsProviderId,
                                                onSearchLyrics = onSearchRecordingLyrics,
                                                onSelectProvider = onSelectRecordingLyricsProvider,
                                                onResetQuery = onResetRecordingLyricsQuery
                                            )
                                        } else {
                                            LyricsTab(
                                                listState = lyricsListState,
                                                nestedScrollConnection = nestedScrollConnection,
                                                lyricsUiState = lyricsUiState,
                                                trackStartTime = playbackState.trackStartTime,
                                                syncOffsetMs = playbackState.lyricsSyncOffsetMs,
                                                isPlaying = playbackState.isPlaying,
                                                getCurrentPosition = getCurrentPosition,
                                                onSyncOffsetChange = onSyncOffsetChange,
                                                currentTrack = playbackState.cleanTrackName ?: playbackState.currentTrack,
                                                currentArtist = playbackState.cleanArtistName,
                                                customQueryTitle = playbackState.customLyricsQueryTitle,
                                                customQueryArtist = playbackState.customLyricsQueryArtist,
                                                selectedProviderId = playbackState.selectedLyricsProviderId,
                                                onSearchLyrics = onSearchLyrics,
                                                onSelectProvider = onSelectLyricsProvider,
                                                onResetQuery = onResetLyricsQuery
                                            )
                                        }
                                    }
                                }
                                else -> {
                                    if (isRecordingMode && recording != null) {
                                        RecordingAboutTab(
                                            recording = recording,
                                            effectiveDurationMs = effectiveRecordingDuration,
                                            listState = aboutListState,
                                            nestedScrollConnection = nestedScrollConnection,
                                            onSaveNote = { newNote -> onSaveRecordingNote(recording, newNote) },
                                            onGoToRecording = {
                                                onCollapse()
                                                onNavigateToRecording(recording)
                                            }
                                        )
                                    } else if (station != null) {
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
        }
    }

        // DIALOG: visible when expanded — same sharedBounds key as pill = true container transform
        TrackDialog(
            searchDialogTrack = searchDialogTrack,
            rawTrackName = if (isRecordingMode) activeRecTrack?.rawTitle else playbackState.rawTrackName,
            trackCoverArtUri = if (isRecordingMode) activeRecTrack?.coverArtUrl else playbackState.trackCoverArtUri,
            isFetchingArtwork = if (isRecordingMode) false else playbackState.isFetchingArtwork,
            isStarred = if (isRecordingMode) false else uiState.isCurrentTrackStarred,
            onToggleStar = if (isRecordingMode) null else onToggleCurrentTrackStarred,
            onDismissRequest = { searchDialogTrack = null }
        )

        CastDeviceDialog(
            showDialog = showCastDialog,
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

        VolumeDialog(
            showDialog = showVolumeDialog,
            onDismissRequest = { showVolumeDialog = false },
            volume = volumeLevel,
            onVolumeChange = {
                if (isRecordingMode) onRecordingVolumeChange(it) else onVolumeChange(it)
            },
            isCastActive = !isRecordingMode && connectedCastDevice != null,
            isVolumeBoostEnabled = !isRecordingMode && appPreferences.enableVolumeBoost
        )

        SleepTimerDialog(
            showDialog = showSleepTimerDialog,
            activeTimerEndTime = activeSleepTimerEndTime,
            timerProgress = sleepTimerProgress,
            onDismissRequest = { showSleepTimerDialog = false },
            onSetTimer = {
                if (isRecordingMode) onSetRecordingSleepTimer(it) else onSetSleepTimer(it)
            },
            onCancelTimer = {
                if (isRecordingMode) onCancelRecordingSleepTimer() else onCancelSleepTimer()
            }
        )

        RecordingSpeedDialog(
            showDialog = showSpeedDialog,
            currentSpeed = recordingUiState.playbackSpeed,
            onDismissRequest = { showSpeedDialog = false },
            onSelectSpeed = onRecordingSpeedChange
        )

        if (showNoteDialog && recording != null) {
            RecordingNoteEditDialog(
                recording = recording,
                onDismiss = { showNoteDialog = false },
                onSave = { newNote -> onSaveRecordingNote(recording, newNote) }
            )
        }
    }
}
}

/** Shared mini-player layout used identically for both the collapsed sheet and the nested drawer header. */
@Composable
private fun MiniPlayerContent(
    activeTitle: String,
    activeTrackSubtitle: String,
    activeCopyableTrack: String?,
    activeHasPrevious: Boolean,
    activeHasNext: Boolean,
    activeIsPlaying: Boolean,
    activeIsLoading: Boolean,
    recordingProgress: Float?,
    hasBothModes: Boolean,
    activeMode: ActivePlaybackMode,
    stationName: String?,
    recordingName: String?,
    isStationPlaying: Boolean,
    isRecordingPlaying: Boolean,
    includeThumbnailSpace: Boolean,
    thumbnailSpacerWidth: Dp,
    cutoutLeftPadding: Dp,
    cutoutRightPadding: Dp,
    interactionEnabled: Boolean,
    onTitleClick: () -> Unit,
    onCopyTrack: (String) -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onSelectMode: (ActivePlaybackMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(start = 16.dp, end = 16.dp + cutoutRightPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (includeThumbnailSpace) {
                    Spacer(modifier = Modifier.width(thumbnailSpacerWidth))
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = interactionEnabled,
                            onClick = onTitleClick
                        ),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = activeTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    )
                    Text(
                        text = activeTrackSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .basicMarquee(iterations = Int.MAX_VALUE)
                            .pointerInput(activeCopyableTrack) {
                                detectTapGestures(
                                    onTap = {
                                        if (interactionEnabled) onTitleClick()
                                    },
                                    onLongPress = {
                                        if (!activeCopyableTrack.isNullOrBlank()) {
                                            onCopyTrack(activeCopyableTrack)
                                        }
                                    }
                                )
                            }
                    )
                }

                if (activeHasPrevious) {
                    IconButton(onClick = onPrevious) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = stringResource(R.string.player_cd_previous)
                        )
                    }
                }

                IconButton(onClick = onTogglePlayPause) {
                    if (activeIsLoading) {
                        LoadingIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Icon(
                            imageVector = if (activeIsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                    }
                }

                if (activeHasNext) {
                    IconButton(onClick = onNext) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = stringResource(R.string.player_cd_next)
                        )
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = recordingProgress != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp + cutoutLeftPadding,
                        end = 16.dp + cutoutRightPadding
                    )
            ) {
                LinearProgressIndicator(
                    progress = { recordingProgress ?: 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(1.25.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    drawStopIndicator = {}
                )
            }
        }

        AnimatedMiniModeSwitcherBar(
            visible = hasBothModes,
            activeMode = activeMode,
            stationName = stationName,
            recordingName = recordingName,
            isStationPlaying = isStationPlaying,
            isRecordingPlaying = isRecordingPlaying,
            enabled = interactionEnabled,
            onSelectMode = onSelectMode,
            modifier = Modifier.padding(
                start = 16.dp + cutoutLeftPadding,
                end = 16.dp + cutoutRightPadding,
                bottom = 10.dp
            )
        )
    }
}
