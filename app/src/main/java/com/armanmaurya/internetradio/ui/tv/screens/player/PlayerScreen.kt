package com.armanmaurya.internetradio.ui.tv.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.ui.shared.components.LoadMoreWhenNearEnd
import com.armanmaurya.internetradio.ui.shared.components.shimmerEffect
import com.armanmaurya.internetradio.ui.shared.viewmodels.LibraryViewModel
import com.armanmaurya.internetradio.ui.shared.viewmodels.PlayerViewModel
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay

/** Controls fade out after this long without a key press while something is playing. */
private const val AMBIENT_DELAY_MS = 10_000L

/** How often the ambient layout shifts slightly, to avoid burn-in on TVs left playing for hours. */
private const val AMBIENT_DRIFT_MS = 60_000L

private val ambientDriftOffsets = listOf(0 to 0, 14 to -10, -10 to -18, 8 to 12, -14 to 6)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    playerViewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel,
    isVolumeBoostEnabled: Boolean,
    isLyricsOpen: Boolean,
    onLyricsOpenChange: (Boolean) -> Unit,
    onEditStation: (String) -> Unit
) {
    val playbackState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val station = playbackState.currentStation
    val isInLibraryFlow = remember(station?.stationUuid) {
        station?.let { libraryViewModel.isStationInLibrary(it.stationUuid) }
            ?: kotlinx.coroutines.flow.MutableStateFlow(false)
    }
    val isInLibrary by isInLibraryFlow.collectAsStateWithLifecycle()
    val trackHistory by playerViewModel.trackHistory.collectAsStateWithLifecycle()
    val savedTracks by playerViewModel.savedTracks.collectAsStateWithLifecycle()

    var isHistoryOpen by remember { mutableStateOf(false) }
    var sidebarHadFocus by remember { mutableStateOf(false) }
    var isSleepTimerOpen by remember { mutableStateOf(false) }
    var isVolumeOpen by remember { mutableStateOf(false) }
    var isAboutOpen by remember { mutableStateOf(false) }
    val sleepTimerMinutesLeft = rememberSleepTimerMinutesLeft(playbackState.sleepTimerEndTime)

    val sidebarFocusRequester = remember { FocusRequester() }
    val tracksButtonFocusRequester = remember { FocusRequester() }
    val playButtonFocusRequester = remember { FocusRequester() }

    // Optimistic play state: flips to true immediately on click so the
    // icon shows Pause before the media3 callback fires.
    var isPlayingOptimistic by remember { mutableStateOf(playbackState.isPlaying) }
    LaunchedEffect(playbackState.isPlaying, playbackState.isLoading) {
        isPlayingOptimistic = playbackState.isPlaying || playbackState.isLoading
    }

    // ── Ambient mode ────────────────────────────────────────────────────
    var isAmbient by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var swallowKeyUp by remember { mutableStateOf<Key?>(null) }
    val canGoAmbient = playbackState.isPlaying && !isHistoryOpen && !isSleepTimerOpen && !isVolumeOpen && !isAboutOpen
    LaunchedEffect(lastInteraction, canGoAmbient) {
        if (canGoAmbient) {
            delay(AMBIENT_DELAY_MS)
            isAmbient = true
        } else {
            isAmbient = false
        }
    }
    var driftIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(isAmbient) {
        if (!isAmbient) {
            driftIndex = 0
            return@LaunchedEffect
        }
        while (true) {
            delay(AMBIENT_DRIFT_MS)
            driftIndex = (driftIndex + 1) % ambientDriftOffsets.size
        }
    }
    val driftX by animateDpAsState(ambientDriftOffsets[driftIndex].first.dp, tween(3000), label = "driftX")
    val driftY by animateDpAsState(ambientDriftOffsets[driftIndex].second.dp, tween(3000), label = "driftY")
    val controlsAlpha by animateFloatAsState(if (isAmbient) 0f else 1f, tween(600), label = "controlsAlpha")
    // Share of the controls' height that stays reserved; collapsing it lets the info slide down.
    val controlsHeightFraction by animateFloatAsState(
        if (isAmbient) 0f else 1f,
        tween(durationMillis = 900, delayMillis = if (isAmbient) 300 else 0),
        label = "controlsHeight"
    )

    // Play/Pause gets focus when the player opens.
    LaunchedEffect(station != null) {
        if (station != null) {
            awaitFrame()
            try {
                playButtonFocusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(isHistoryOpen) {
        if (isHistoryOpen) {
            try {
                sidebarFocusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    BackHandler(enabled = isLyricsOpen && !isHistoryOpen) {
        onLyricsOpenChange(false)
    }

    val aboutStation = station
    if (isAboutOpen && aboutStation != null) {
        TvStationAboutDialog(
            station = aboutStation,
            streamCodec = playbackState.streamCodec,
            streamBitrate = playbackState.streamBitrate,
            onDismiss = {
                isAboutOpen = false
                lastInteraction = System.currentTimeMillis()
            }
        )
    }

    if (isVolumeOpen) {
        TvVolumeDialog(
            volume = playbackState.volume,
            maxVolume = if (isVolumeBoostEnabled) 2f else 1f,
            onVolumeChange = { playerViewModel.setVolume(it) },
            onDismiss = {
                isVolumeOpen = false
                lastInteraction = System.currentTimeMillis()
            }
        )
    }

    if (isSleepTimerOpen) {
        TvSleepTimerDialog(
            endTime = playbackState.sleepTimerEndTime,
            onSetTimer = { playerViewModel.setSleepTimer(it) },
            onCancelTimer = { playerViewModel.cancelSleepTimer() },
            onDismiss = {
                isSleepTimerOpen = false
                lastInteraction = System.currentTimeMillis()
            }
        )
    }

    BackHandler(enabled = isHistoryOpen) {
        isHistoryOpen = false
        try {
            tracksButtonFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                // Remote channel / skip keys switch stations from anywhere in the player.
                val isStationKey = event.key == Key.ChannelUp || event.key == Key.ChannelDown ||
                    event.key == Key.MediaNext || event.key == Key.MediaPrevious
                val isMediaKey = event.key == Key.MediaPlayPause || event.key == Key.MediaPlay ||
                    event.key == Key.MediaPause || event.key == Key.MediaStop
                if (event.type == KeyEventType.KeyDown) {
                    lastInteraction = System.currentTimeMillis()
                    if (isStationKey) {
                        isAmbient = false
                        if (event.key == Key.ChannelUp || event.key == Key.MediaNext) {
                            playerViewModel.next()
                        } else {
                            playerViewModel.previous()
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (isAmbient) {
                        // The first press only wakes the controls; it shouldn't also act.
                        isAmbient = false
                        if (!isMediaKey) {
                            swallowKeyUp = event.key
                            return@onPreviewKeyEvent true
                        }
                    }
                    false
                } else {
                    if (isStationKey) return@onPreviewKeyEvent true
                    if (swallowKeyUp == event.key) {
                        swallowKeyUp = null
                        return@onPreviewKeyEvent true
                    }
                    false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (station == null) {
            Text(stringResource(R.string.widget_nothing_playing), style = MaterialTheme.typography.bodyLarge)
            return@Box
        }

        val fallbackPainter = painterResource(id = R.drawable.ic_launcher_foreground)
        val hasCoverArt = !playbackState.trackCoverArtUri.isNullOrBlank()
        val artModel = if (hasCoverArt) playbackState.trackCoverArtUri else station.favicon.ifEmpty { null }
        val background = MaterialTheme.colorScheme.background

        // Blurred artwork, tinted towards the theme's background colour.
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(artModel)
                .size(coil3.size.Size.ORIGINAL)
                .crossfade(true)
                .build(),
            contentDescription = null,
            filterQuality = FilterQuality.High,
            placeholder = fallbackPainter,
            error = fallbackPainter,
            fallback = fallbackPainter,
            modifier = Modifier
                .fillMaxSize()
                .blur(100.dp),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(background.copy(alpha = 0.35f), background.copy(alpha = 0.92f))
                    )
                )
        )

        // Keeps the titles clear of the lyrics area on the right.
        val titleWidthFraction by animateFloatAsState(
            targetValue = if (isLyricsOpen) 0.4f else 1f,
            label = "titleWidth"
        )
        val artSize = 220.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = driftX, y = driftY)
                .padding(start = 96.dp, bottom = 28.dp, end = 48.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.Start
        ) {
            Box(modifier = Modifier.size(artSize)) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(artModel)
                        .size(coil3.size.Size.ORIGINAL)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    filterQuality = FilterQuality.High,
                    placeholder = fallbackPainter,
                    error = fallbackPainter,
                    fallback = fallbackPainter,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )

                if (playbackState.isFetchingArtwork && !hasCoverArt) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                            .shimmerEffect()
                    )
                }

                if (hasCoverArt && station.favicon.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(station.favicon)
                                .crossfade(true)
                                .build(),
                            contentDescription = stringResource(R.string.schedule_station_logo_cd),
                            filterQuality = FilterQuality.High,
                            placeholder = fallbackPainter,
                            error = fallbackPainter,
                            fallback = fallbackPainter,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = station.name,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth(titleWidthFraction)
                    .basicMarquee(iterations = Int.MAX_VALUE)
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (playbackState.isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.player_buffering),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            } else if (!playbackState.currentTrack.isNullOrEmpty()) {
                Text(
                    text = playbackState.currentTrack!!,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth(titleWidthFraction)
                        .basicMarquee(iterations = Int.MAX_VALUE)
                )
            }

            val displayCodec = (if (!isInLibrary) playbackState.streamCodec else null) ?: station.codec
            val displayBitrate = (if (!isInLibrary) playbackState.streamBitrate else null) ?: station.bitrate
            val infoLine = stationInfoLine(displayBitrate, displayCodec, station.countryCode)
            if (infoLine.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = infoLine,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            // ── Controls (fade out in ambient mode) ──────────────────────
            // Stays composed (so focus is kept) but takes no space once collapsed.
            Column(
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val height = (placeable.height * controlsHeightFraction).toInt()
                        layout(placeable.width, height) { placeable.place(0, 0) }
                    }
                    .graphicsLayer { alpha = controlsAlpha }
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvPlayerIconButton(
                        icon = Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.player_cd_previous),
                        onClick = { playerViewModel.previous() }
                    )
                    TvPlayerPrimaryButton(
                        icon = if (isPlayingOptimistic) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(
                            if (isPlayingOptimistic) R.string.player_cd_pause else R.string.player_cd_play
                        ),
                        onClick = {
                            isPlayingOptimistic = !isPlayingOptimistic
                            playerViewModel.togglePlayPause()
                        },
                        modifier = Modifier.focusRequester(playButtonFocusRequester)
                    )
                    TvPlayerIconButton(
                        icon = Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.player_cd_next),
                        onClick = { playerViewModel.next() }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    TvPlayerIconButton(
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = stringResource(R.string.player_tab_tracks),
                        onClick = { isHistoryOpen = !isHistoryOpen },
                        modifier = Modifier.focusRequester(tracksButtonFocusRequester),
                        isActive = isHistoryOpen
                    )
                    TvPlayerIconButton(
                        icon = Icons.Default.Lyrics,
                        contentDescription = stringResource(R.string.player_tab_lyrics),
                        onClick = { onLyricsOpenChange(!isLyricsOpen) },
                        isActive = isLyricsOpen
                    )
                    TvPlayerIconButton(
                        icon = Icons.Default.Info,
                        contentDescription = stringResource(R.string.player_tab_about),
                        onClick = { isAboutOpen = true }
                    )
                    TvPlayerIconButton(
                        icon = Icons.Default.Timer,
                        contentDescription = sleepTimerMinutesLeft?.let { stringResource(R.string.player_timer_min, it) }
                            ?: stringResource(R.string.player_sleep_timer_title),
                        onClick = { isSleepTimerOpen = true },
                        isActive = sleepTimerMinutesLeft != null
                    )
                    TvPlayerIconButton(
                        icon = if (playbackState.volume <= 0f) Icons.AutoMirrored.Filled.VolumeOff
                        else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = volumePercentLabel(playbackState.volume),
                        onClick = { isVolumeOpen = true },
                        isActive = playbackState.volume > 1f
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    TvPlayerIconButton(
                        icon = if (isInLibrary) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = stringResource(
                            if (isInLibrary) R.string.home_remove_from_library else R.string.home_add_to_library
                        ),
                        onClick = {
                            if (isInLibrary) {
                                libraryViewModel.removeStationWithoutConfirmation(station.stationUuid)
                            } else {
                                libraryViewModel.addStationToLibrary(station)
                            }
                        },
                        isActive = isInLibrary
                    )
                    if (isInLibrary) {
                        TvPlayerIconButton(
                            icon = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.edit_station_title),
                            onClick = { onEditStation(station.stationUuid) }
                        )
                    }
                }
            }
        }

        // Lyrics, on the right above the controls
        val lyricsBottomPadding by animateDpAsState(
            if (isAmbient) 40.dp else 130.dp,
            tween(900),
            label = "lyricsBottom"
        )
        AnimatedVisibility(
            visible = isLyricsOpen,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(0.55f)
                .fillMaxHeight()
                .offset(x = driftX, y = driftY)
                .padding(top = 40.dp, end = 64.dp, bottom = lyricsBottomPadding),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            TvLyricsPanel(
                lyricsUiState = playbackState.lyricsUiState,
                trackStartTime = playbackState.trackStartTime,
                syncOffsetMs = playbackState.lyricsSyncOffsetMs,
                getCurrentPosition = { playerViewModel.currentPosition },
                onSyncOffsetChange = playerViewModel::setLyricsSyncOffset,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Track History Right Sidebar
        AnimatedVisibility(
            visible = isHistoryOpen,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it })
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(440.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                    .onFocusChanged { state ->
                        if (state.hasFocus) {
                            sidebarHadFocus = true
                        } else if (sidebarHadFocus) {
                            isHistoryOpen = false
                            sidebarHadFocus = false
                        }
                    }
                    .onKeyEvent { event ->
                        if (event.key == Key.DirectionLeft && event.type == KeyEventType.KeyDown) {
                            tracksButtonFocusRequester.requestFocus()
                            true
                        } else {
                            false
                        }
                    }
                    .padding(24.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = stringResource(R.string.player_tab_tracks),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    val historyListState = rememberLazyListState()
                    LoadMoreWhenNearEnd(
                        listState = historyListState,
                        onLoadMore = playerViewModel::loadMoreHistory
                    )
                    if (trackHistory.isEmpty() && savedTracks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .focusRequester(sidebarFocusRequester)
                                .focusable(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.player_no_tracks_played),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        TvTrackHistoryList(
                            trackHistory = trackHistory,
                            savedTracks = savedTracks,
                            listState = historyListState,
                            firstItemModifier = Modifier.focusRequester(sidebarFocusRequester),
                            onToggleStar = playerViewModel::toggleTrackStarred
                        )
                    }
                }
            }
        }
    }
}
