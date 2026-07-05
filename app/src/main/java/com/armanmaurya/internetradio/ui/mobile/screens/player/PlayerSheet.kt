package com.armanmaurya.internetradio.ui.mobile.screens.player

import android.text.format.DateUtils
import android.widget.NumberPicker
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity
import com.armanmaurya.internetradio.player.PlaybackState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt



fun Modifier.collapseHeight(progress: Float) = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val height = (placeable.height * (1f - progress)).toInt().coerceAtLeast(0)
    layout(placeable.width, height) {
        placeable.placeRelative(0, (height - placeable.height) / 2) // center vertically while collapsing
    }
}

@Composable
fun PlayerSheetContent(
    playbackState: PlaybackState,
    isFavorite: Boolean,
    trackHistory: List<TrackHistoryEntity> = emptyList(),
    progress: Float, // 0.0 (collapsed) to 1.0 (expanded)
    onTogglePlayPause: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSetSleepTimer: (Long) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onCollapse: () -> Unit,
    onExpand: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
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
                    val delta = -available.y / maxDragDistance
                    coroutineScope.launch {
                        historyProgressAnim.snapTo((historyProgressAnim.value + delta).coerceIn(0f, 1f))
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(enabled = progress < 0.1f, onClick = onExpand)
    ) {
        // --- Thumbnail Calculation ---
        val miniSize = 48.dp
        val baseExpandedSize = (screenWidth - 48.dp).coerceAtMost(400.dp)
        val historySize = 48.dp // Match exact collapsed size
        val actualExpandedSize = lerp(baseExpandedSize, historySize, historyProgress)
        
        val currentSize = lerp(miniSize, actualExpandedSize, progress)
        
        // Mini position (relative to sheet)
        val miniX = 16.dp
        val miniY = 12.dp // (72 - 48) / 2 - perfectly centered in 72.dp row
        
        // Expanded position
        val baseExpandedX = (screenWidth - baseExpandedSize) / 2
        val historyExpandedX = 24.dp // Move to left edge of column
        val actualExpandedX = lerp(baseExpandedX, historyExpandedX, historyProgress)
        
        val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val baseExpandedY = 100.dp + statusBarPadding
        val historyExpandedY = 22.dp + statusBarPadding // Shifted further down
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
                    .padding(horizontal = 16.dp)
                    .alpha(1f - (progress * 5f).coerceIn(0f, 1f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Space for the moving thumbnail
                Spacer(modifier = Modifier.width(miniSize + 12.dp))

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
                    val currentTrackText = if (playbackState.isLoading) "Buffering..." else playbackState.currentTrack ?: "No track data"
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

                IconButton(
                    onClick = onPrevious,
                    enabled = playbackState.hasPrevious
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous"
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
                        contentDescription = "Next"
                    )
                }

                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Toggle Favorite",
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
                    .padding(horizontal = 24.dp)
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.navigationBars)
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
                    IconButton(
                        onClick = onCollapse,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Collapse",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showSleepTimerDialog = true }) {
                            if (playbackState.sleepTimerEndTime != null) {
                                Box(contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(
                                        progress = { sleepTimerProgress },
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 2.dp,
                                        strokeCap = StrokeCap.Round
                                    )
                                    val mins = (remainingTime / 60000).toInt() + 1
                                    Text(
                                        text = mins.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = "Sleep Timer",
                                    tint = LocalContentColor.current
                                )
                            }
                        }

                        if (station.homepage.isNotBlank()) {
                            IconButton(onClick = {
                                try {
                                    var url = station.homepage
                                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                        url = "http://$url"
                                    }
                                    uriHandler.openUri(url)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Invalid URL", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = "Homepage",
                                    modifier = Modifier.size(28.dp),
                                    tint = LocalContentColor.current
                                )
                            }
                        }

                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Toggle Favorite",
                                modifier = Modifier.size(32.dp),
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                    }
                }

                // Thumbnail Placeholder and Mini Controls
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    // Placeholder for the moving thumbnail
                    Spacer(modifier = Modifier
                        .fillMaxWidth()
                        .height(
                            lerp(
                                100.dp + baseExpandedSize - 20.dp,
                                72.dp, // Matches Row height
                                historyProgress
                            )
                        )
                    )

                    // Mini controls when history is expanded
                    if (historyProgress > 0f) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp) // perfectly match the collapsed player height
                                .alpha(historyProgress),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Space for the moving thumbnail: 48 size + 12 gap = 60.dp
                            Spacer(modifier = Modifier.width(60.dp))

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
                                val currentTrackText = if (playbackState.isLoading) "Buffering..." else playbackState.currentTrack ?: "No track data"
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
                                    contentDescription = "Previous"
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
                                    contentDescription = "Next"
                                )
                            }

                            IconButton(onClick = onToggleFavorite) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Toggle Favorite",
                                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                )
                            }
                        }
                    }
                }

                // Main controls that fade out and collapse
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .collapseHeight(historyProgress)
                        .alpha(1f - historyProgress),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = station.name,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.basicMarquee()
                    )

                    AnimatedContent(
                        targetState = if (playbackState.isLoading) "Buffering..." else playbackState.currentTrack ?: "No track data",
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut() using SizeTransform(clip = false)
                        },
                        label = "TrackAnimation"
                    ) { displayTrack ->
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = displayTrack,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Start,
                                maxLines = 1,
                                modifier = Modifier
                                    .basicMarquee()
                                    .pointerInput(displayTrack) {
                                        detectTapGestures(
                                            onLongPress = {
                                                if (displayTrack != "Buffering..." && displayTrack != "No track data") {
                                                    clipboardManager.setText(AnnotatedString(displayTrack))
                                                    Toast.makeText(context, "Copied track to clipboard", Toast.LENGTH_SHORT).show()
                                                }
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
                        FilledIconButton(
                            onClick = onPrevious,
                            enabled = playbackState.hasPrevious,
                            modifier = Modifier.size(64.dp),
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        FilledIconButton(
                            onClick = onTogglePlayPause,
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = if (playbackState.isPlaying || playbackState.isLoading) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        FilledIconButton(
                            onClick = onNext,
                            enabled = playbackState.hasNext,
                            modifier = Modifier.size(64.dp),
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    val countryLanguageText = buildString {
                        if (station.country.isNotBlank()) append(station.country)
                        if (station.country.isNotBlank() && station.language.isNotBlank()) append(" • ")
                        if (station.language.isNotBlank()) append(station.language)
                    }

                    if (station.tags.isNotEmpty() || countryLanguageText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        if (countryLanguageText.isNotBlank()) {
                            Text(
                                text = countryLanguageText,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                            if (station.tags.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                        
                        if (station.tags.isNotEmpty()) {
                            Text(
                                text = station.tags.joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }

                val topSpacerWeight = 1f - historyProgress
                if (topSpacerWeight > 0.01f) {
                    Spacer(modifier = Modifier.weight(topSpacerWeight))
                } else {
                    Spacer(modifier = Modifier.height(0.dp))
                }

                // Recent Tracks Button (sits naturally below everything else, moves up as above content collapses)
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
                    val interactionSource = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .background(
                                if (isHistoryExpanded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.Transparent,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = {
                                    coroutineScope.launch {
                                        historyProgressAnim.animateTo(if (isHistoryExpanded) 0f else 1f)
                                    }
                                }
                            )
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Recent Tracks",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Track History Panel Content
                if (historyProgress > 0f) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(historyProgress.coerceAtLeast(0.01f)) // Takes remaining space in the expanded view
                            .alpha(historyProgress)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(nestedScrollConnection)
                        ) {
                            if (trackHistory.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No tracks played yet",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                items(trackHistory.size) { index ->
                                    val track = trackHistory[index]
                                    val time = DateUtils.getRelativeTimeSpanString(
                                        track.timestamp,
                                        System.currentTimeMillis(),
                                        DateUtils.MINUTE_IN_MILLIS,
                                        DateUtils.FORMAT_ABBREV_RELATIVE
                                    ).toString()
                                    
                                    var isExpanded by remember { mutableStateOf(false) }
                                    
                                    @OptIn(ExperimentalFoundationApi::class)
                                    ListItem(
                                        modifier = Modifier
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .combinedClickable(
                                                onClick = { isExpanded = !isExpanded },
                                                onLongClick = {
                                                    clipboardManager.setText(AnnotatedString(track.trackTitle))
                                                    Toast.makeText(context, "Copied track to clipboard", Toast.LENGTH_SHORT).show()
                                                }
                                            ),
                                        headlineContent = {
                                            Text(
                                                text = track.trackTitle,
                                                style = MaterialTheme.typography.bodyLarge,
                                                maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                                                overflow = if (isExpanded) TextOverflow.Visible else TextOverflow.Ellipsis
                                            )
                                        },
                                        trailingContent = {
                                            Text(
                                                text = time,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        leadingContent = {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        colors = ListItemDefaults.colors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
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
            title = { Text("Sleep Timer") },
            text = {
                val mins = (remaining / 60000).toInt() + 1
                Text("Timer ends in $mins minutes.")
            },
            confirmButton = {
                TextButton(onClick = onDismissRequest) {
                    Text("OK")
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
                    Text("Turn Off Timer")
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
            title = { Text("Set Sleep Timer") },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Hours", style = MaterialTheme.typography.labelMedium)
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
                        Text("Minutes", style = MaterialTheme.typography.labelMedium)
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
                    Text("Start")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text("Cancel")
                }
            }
        )
    }
}
