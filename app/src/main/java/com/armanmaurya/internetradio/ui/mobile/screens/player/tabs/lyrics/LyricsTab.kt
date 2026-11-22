@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.lyrics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.armanmaurya.internetradio.R
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import com.armanmaurya.internetradio.domain.model.sungFraction
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.drawscope.clipPath
import com.armanmaurya.internetradio.domain.model.LrcLine
import com.armanmaurya.internetradio.ui.shared.viewmodels.PlayerViewModel.LyricsUiState
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.ui.platform.LocalContext
import com.armanmaurya.internetradio.core.utils.LyricsExporter
import com.armanmaurya.internetradio.domain.lyrics.LyricsProviderInfo
import com.armanmaurya.internetradio.domain.model.Lyrics
import kotlinx.coroutines.launch

private sealed interface LyricsDisplayState {
    data object Loading : LyricsDisplayState
    data object Error : LyricsDisplayState
    data object NotAvailable : LyricsDisplayState
    data class Synced(val lines: List<LrcLine>, val lyrics: Lyrics?, val isWordSynced: Boolean) : LyricsDisplayState
    data class Plain(val plain: String, val lyrics: Lyrics?) : LyricsDisplayState
}

@Composable
fun LyricsTab(
    listState: LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    lyricsUiState: LyricsUiState,
    trackStartTime: Long?,
    syncOffsetMs: Long,
    isPlaying: Boolean,
    getCurrentPosition: () -> Long,
    onSyncOffsetChange: (Long) -> Unit,
    onDownloadLyrics: ((Lyrics) -> Unit)? = null,
    currentTrack: String? = null,
    currentArtist: String? = null,
    customQueryTitle: String? = null,
    customQueryArtist: String? = null,
    selectedProviderId: String? = null,
    onSearchLyrics: ((String, String?) -> Unit)? = null,
    onSelectProvider: ((String?) -> Unit)? = null,
    onResetQuery: (() -> Unit)? = null
) {
    var isSyncEnabled by rememberSaveable { mutableStateOf(true) }
    var showLyricsOptionsSheet by rememberSaveable { mutableStateOf(false) }
    var lastKnownCandidates by remember { mutableStateOf<List<Lyrics>>(emptyList()) }
    var lastKnownLyrics by remember { mutableStateOf<Lyrics?>(null) }

    LaunchedEffect(currentTrack) {
        lastKnownCandidates = emptyList()
        lastKnownLyrics = null
    }

    LaunchedEffect(lyricsUiState) {
        if (lyricsUiState is LyricsUiState.Success) {
            lastKnownCandidates = lyricsUiState.allCandidates
            lastKnownLyrics = lyricsUiState.lyrics
        }
    }

    val context = LocalContext.current
    var pendingExportPayload by remember { mutableStateOf<LyricsExporter.ExportPayload?>(null) }
    val downloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && pendingExportPayload != null) {
            LyricsExporter.writeToUri(context, uri, pendingExportPayload!!.content)
        }
        pendingExportPayload = null
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Inner box with the gradient blur mask
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .graphicsLayer { alpha = 0.99f }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.15f to Color.Black,
                            0.85f to Color.Black,
                            1f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            val displayState = remember(lyricsUiState, trackStartTime, isSyncEnabled) {
                when (lyricsUiState) {
                    is LyricsUiState.Loading -> LyricsDisplayState.Loading
                    is LyricsUiState.Error -> LyricsDisplayState.Error
                    is LyricsUiState.NotAvailable -> LyricsDisplayState.NotAvailable
                    is LyricsUiState.Success -> {
                        val synced = lyricsUiState.syncedLyrics
                        val plain = lyricsUiState.plainLyrics?.takeIf { it.isNotBlank() }
                            ?: synced?.joinToString("\n") { it.text }?.takeIf { it.isNotBlank() }
                        val isWordSynced = lyricsUiState.lyrics?.hasWordSync == true ||
                            synced?.any { !it.words.isNullOrEmpty() } == true

                        if (synced != null && trackStartTime != null && isSyncEnabled) {
                            LyricsDisplayState.Synced(synced, lyricsUiState.lyrics, isWordSynced)
                        } else if (!plain.isNullOrEmpty()) {
                            LyricsDisplayState.Plain(plain, lyricsUiState.lyrics)
                        } else {
                            LyricsDisplayState.NotAvailable
                        }
                    }
                }
            }

            AnimatedContent(
                targetState = displayState,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(350)) + slideInVertically(animationSpec = tween(350)) { height -> height / 16 })
                        .togetherWith(fadeOut(animationSpec = tween(200)))
                },
                label = "lyricsDisplayTransition",
                modifier = Modifier.fillMaxSize()
            ) { state ->
                when (state) {
                    is LyricsDisplayState.Loading -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            item {
                                LoadingIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    is LyricsDisplayState.Error -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            item {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.error_something_went_wrong),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FilledTonalButton(
                                        onClick = { showLyricsOptionsSheet = true }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = stringResource(R.string.lyrics_search_query_button))
                                    }
                                }
                            }
                        }
                    }
                    is LyricsDisplayState.NotAvailable -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            item {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.player_lyrics_not_available),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FilledTonalButton(
                                        onClick = { showLyricsOptionsSheet = true }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = stringResource(R.string.lyrics_search_query_button))
                                    }
                                }
                            }
                        }
                    }
                    is LyricsDisplayState.Synced -> {
                        SyncedLyricsView(
                            lines = state.lines,
                            listState = listState,
                            trackStartTime = trackStartTime ?: 0L,
                            syncOffsetMs = syncOffsetMs,
                            getCurrentPosition = getCurrentPosition,
                            providerInfo = state.lyrics?.providerInfo,
                            hasWordSync = state.isWordSynced,
                            onOpenOptions = { showLyricsOptionsSheet = true }
                        )
                    }
                    is LyricsDisplayState.Plain -> {
                        PlainLyricsView(
                            lyrics = state.plain,
                            listState = listState,
                            providerInfo = state.lyrics?.providerInfo,
                            onOpenOptions = { showLyricsOptionsSheet = true }
                        )
                    }
                }
            }
        }

        // Overlay Action Bar / Buttons
        val successState = lyricsUiState as? LyricsUiState.Success
        val currentLyrics = successState?.lyrics
        val synced = successState?.syncedLyrics
        val plain = successState?.plainLyrics?.takeIf { it.isNotBlank() }
            ?: synced?.joinToString("\n") { it.text }?.takeIf { it.isNotBlank() }
        val canSync = !synced.isNullOrEmpty() && trackStartTime != null
        val isCurrentlySynced = canSync && isSyncEnabled
        val isWordSynced = currentLyrics?.hasWordSync == true ||
            synced?.any { !it.words.isNullOrEmpty() } == true
        val hasLyrics = successState != null && (!synced.isNullOrEmpty() || !plain.isNullOrEmpty())

        val cornerRadius by androidx.compose.animation.core.animateDpAsState(
            targetValue = if (isCurrentlySynced) 12.dp else 50.dp,
            animationSpec = androidx.compose.animation.core.tween(300),
            label = "lyricsCornerRadius"
        )
        val sideRotation by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (isCurrentlySynced) 0f else 90f,
            animationSpec = androidx.compose.animation.core.tween(300),
            label = "lyricsSideRotation"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Left: Options & Search (always accessible, including while searching/loading)
            androidx.compose.material3.FilledTonalIconButton(
                onClick = { showLyricsOptionsSheet = true },
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = stringResource(R.string.lyrics_cd_options)
                )
            }

            // Center: Sync Offset (-) + Sync/Plain Toggle + Sync Offset (+)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.align(Alignment.Center)
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isCurrentlySynced,
                    enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { it * 2 }),
                    exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { it * 2 })
                ) {
                    androidx.compose.material3.FilledTonalIconButton(
                        onClick = { onSyncOffsetChange(syncOffsetMs - 500L) },
                    ) {
                        Icon(
                            Icons.Default.Remove, 
                            contentDescription = stringResource(R.string.player_cd_delay_lyrics),
                            modifier = Modifier.graphicsLayer { rotationZ = -sideRotation }
                        )
                    }
                }

                FilledTonalButton(
                    onClick = {
                        if (canSync) {
                            isSyncEnabled = !isSyncEnabled
                        }
                    },
                    enabled = canSync && hasLyrics,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius),
                    modifier = Modifier.height(48.dp).zIndex(1f)
                ) {
                    val buttonText = when {
                        !hasLyrics || !isCurrentlySynced -> stringResource(R.string.lyrics_plain)
                        syncOffsetMs != 0L -> "${if (syncOffsetMs > 0) "+" else ""}${syncOffsetMs / 1000f}s"
                        isWordSynced -> stringResource(R.string.lyrics_word_synced)
                        else -> stringResource(R.string.lyrics_line_synced)
                    }
                    Text(
                        text = buttonText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.animateContentSize()
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = isCurrentlySynced,
                    enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { -it * 2 }),
                    exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { -it * 2 })
                ) {
                    androidx.compose.material3.FilledTonalIconButton(
                        onClick = { onSyncOffsetChange(syncOffsetMs + 500L) },
                    ) {
                        Icon(
                            Icons.Default.Add, 
                            contentDescription = stringResource(R.string.player_cd_fast_forward_lyrics),
                            modifier = Modifier.graphicsLayer { rotationZ = sideRotation }
                        )
                    }
                }
            }

            // Right: Download Button
            androidx.compose.material3.FilledTonalIconButton(
                onClick = {
                    if (currentLyrics != null) {
                        if (onDownloadLyrics != null) {
                            onDownloadLyrics(currentLyrics)
                        } else {
                            val payload = LyricsExporter.prepareExport(currentLyrics)
                            pendingExportPayload = payload
                            downloadLauncher.launch(payload.defaultFileName)
                        }
                    }
                },
                enabled = hasLyrics && currentLyrics != null,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.CenterEnd)
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = stringResource(R.string.lyrics_download)
                )
            }
        }

        if (showLyricsOptionsSheet) {
            val allCandidates = (lyricsUiState as? LyricsUiState.Success)?.allCandidates?.takeIf { it.isNotEmpty() } ?: lastKnownCandidates
            val activeLyrics = (lyricsUiState as? LyricsUiState.Success)?.lyrics ?: lastKnownLyrics
            val initialTitle = customQueryTitle ?: currentTrack ?: activeLyrics?.trackTitle ?: ""
            val initialArtist = customQueryArtist ?: currentArtist ?: activeLyrics?.artistName ?: ""

            LyricsOptionsBottomSheet(
                onDismissRequest = { showLyricsOptionsSheet = false },
                currentTrackTitle = initialTitle,
                currentArtistName = initialArtist,
                streamTrackTitle = currentTrack,
                streamArtistName = currentArtist,
                allCandidates = allCandidates,
                selectedProviderId = selectedProviderId,
                activeLyrics = activeLyrics,
                onSearch = { title, artist ->
                    onSearchLyrics?.invoke(title, artist)
                },
                onSelectProvider = { providerId ->
                    onSelectProvider?.invoke(providerId)
                },
                onResetToTrack = {
                    onResetQuery?.invoke()
                }
            )
        }
    }
}

@Composable
fun PlainLyricsView(
    lyrics: String,
    listState: LazyListState,
    providerInfo: LyricsProviderInfo? = null,
    onOpenOptions: (() -> Unit)? = null
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentPadding = PaddingValues(top = 84.dp, bottom = 120.dp)
    ) {
        item {
            Text(
                text = lyrics,
                style = MaterialTheme.typography.headlineMedium, // match synced lyrics
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start, // left aligned
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            val uriHandler = LocalUriHandler.current
            val providerName = providerInfo?.displayName ?: stringResource(R.string.lyrics_provider_lrclib)
            val providerUrl = providerInfo?.websiteUrl ?: "https://lrclib.net/"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.lyrics_powered_by) + " ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$providerName ▾",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        if (onOpenOptions != null) onOpenOptions()
                        else uriHandler.openUri(providerUrl)
                    }
                )
                Text(
                    text = " • " + stringResource(R.string.lyrics_plain),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Builds an AnnotatedString highlighting words word-by-word according to song playback time.
 */
fun buildWordSyncedAnnotatedString(
    line: LrcLine,
    currentSongTime: Long,
    primaryColor: Color,
    dimColor: Color
): androidx.compose.ui.text.AnnotatedString {
    val words = line.words
    if (words.isNullOrEmpty()) {
        return androidx.compose.ui.text.AnnotatedString(line.text)
    }

    val directConcat = words.joinToString("") { it.text }
    if (directConcat == line.text) {
        return buildAnnotatedString {
            words.forEach { word ->
                val isSung = currentSongTime >= word.startMs
                val color = if (isSung) primaryColor else dimColor
                withStyle(
                    SpanStyle(
                        color = color
                    )
                ) {
                    append(word.text)
                }
            }
        }
    }

    val spaceConcat = words.joinToString(" ") { it.text.trim() }
    if (spaceConcat == line.text.trim()) {
        return buildAnnotatedString {
            words.forEachIndexed { i, word ->
                val isSung = currentSongTime >= word.startMs
                val color = if (isSung) primaryColor else dimColor
                withStyle(
                    SpanStyle(
                        color = color
                    )
                ) {
                    append(word.text.trim())
                    if (i < words.lastIndex) {
                        append(" ")
                    }
                }
            }
        }
    }

    return buildAnnotatedString {
        var textCursor = 0
        words.forEach { word ->
            val isSung = currentSongTime >= word.startMs
            val color = if (isSung) primaryColor else dimColor
            val trimmedWord = word.text.trim()
            val matchIdx = line.text.indexOf(trimmedWord, startIndex = textCursor, ignoreCase = true)
            if (matchIdx >= 0) {
                if (matchIdx > textCursor) {
                    append(line.text.substring(textCursor, matchIdx))
                }
                withStyle(
                    SpanStyle(
                        color = color
                    )
                ) {
                    append(line.text.substring(matchIdx, matchIdx + trimmedWord.length))
                }
                textCursor = matchIdx + trimmedWord.length
            } else {
                withStyle(
                    SpanStyle(
                        color = color
                    )
                ) {
                    append(word.text)
                }
            }
        }
        if (textCursor < line.text.length) {
            append(line.text.substring(textCursor))
        }
    }
}

@Composable
fun SyncedLyricsView(
    lines: List<LrcLine>,
    listState: LazyListState,
    trackStartTime: Long,
    syncOffsetMs: Long,
    getCurrentPosition: () -> Long,
    providerInfo: LyricsProviderInfo? = null,
    hasWordSync: Boolean = false,
    onOpenOptions: (() -> Unit)? = null
) {
    var currentTimeMs by remember { mutableLongStateOf(0L) }
    var activeIndex by remember(trackStartTime) { mutableIntStateOf(0) }

    LaunchedEffect(lines, trackStartTime) {
        listState.scrollToItem(0)
        while (true) {
            currentTimeMs = getCurrentPosition() - trackStartTime
            delay(50) // 20 updates a second for snappy word-by-word response
        }
    }

    // Calculate new index smoothly without launching effects every 50ms
    val derivedIndex = remember(currentTimeMs, syncOffsetMs) {
        val adjustedTime = currentTimeMs + syncOffsetMs
        val idx = lines.indexOfLast { adjustedTime >= it.timestampMs }
        if (idx >= 0) idx else 0
    }

    LaunchedEffect(derivedIndex) {
        activeIndex = derivedIndex
        if (!listState.isScrollInProgress) {
            val layoutInfo = listState.layoutInfo
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == activeIndex }
            
            if (itemInfo != null) {
                val center = layoutInfo.viewportStartOffset + (viewportHeight / 2)
                val itemCenter = itemInfo.offset + (itemInfo.size / 2)
                val distance = itemCenter - center
                listState.animateScrollBy(
                    value = distance.toFloat(),
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 800,
                        easing = androidx.compose.animation.core.LinearOutSlowInEasing
                    )
                )
            } else {
                listState.animateScrollToItem(
                    index = activeIndex,
                    scrollOffset = -(viewportHeight / 2)
                )
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentPadding = PaddingValues(top = 72.dp, bottom = 160.dp)
    ) {
        items(count = lines.size, key = { index -> "${lines[index].timestampMs}_$index" }) { index ->
            val isActive = index == activeIndex
            val line = lines[index]
            
            val primaryColor = MaterialTheme.colorScheme.primary
            val dimColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)

            if (isActive && !line.words.isNullOrEmpty()) {
                val currentSongTime = currentTimeMs + syncOffsetMs
                val annotatedString = buildWordSyncedAnnotatedString(
                    line = line,
                    currentSongTime = currentSongTime,
                    primaryColor = primaryColor,
                    dimColor = dimColor
                )
                Text(
                    text = annotatedString,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )
            } else if (isActive) {
                var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                val progress = line.sungFraction(currentTimeMs + syncOffsetMs, lines.getOrNull(index + 1)?.timestampMs)

                Text(
                    text = line.text.ifBlank { "♪" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black, // Fully opaque mask so SrcIn preserves the primary color's alpha!
                    textAlign = TextAlign.Start,
                    onTextLayout = { textLayoutResult = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent() // Draw the opaque black text mask
                            
                            if (progress <= 0f) {
                                drawRect(color = dimColor, blendMode = BlendMode.SrcIn)
                            } else {
                                val layoutResult = textLayoutResult ?: return@drawWithContent
                                val lineCount = layoutResult.lineCount
                                
                                var totalWidth = 0f
                                val lineWidths = FloatArray(lineCount)
                                for (i in 0 until lineCount) {
                                    val w = java.lang.Math.abs(layoutResult.getLineRight(i) - layoutResult.getLineLeft(i))
                                    lineWidths[i] = w
                                    totalWidth += w
                                }
                                
                                val targetWidth = progress * totalWidth
                                var accumulatedWidth = 0f
                                var currentLine = lineCount - 1
                                var lineProgress = 1f
                                
                                for (i in 0 until lineCount) {
                                    val w = lineWidths[i]
                                    if (accumulatedWidth + w >= targetWidth || i == lineCount - 1) {
                                        currentLine = i
                                        lineProgress = if (w > 0f) (targetWidth - accumulatedWidth) / w else 1f
                                        lineProgress = lineProgress.coerceIn(0f, 1f)
                                        break
                                    }
                                    accumulatedWidth += w
                                }
                                
                                // Path for the FILLED portion (Primary Color)
                                val filledPath = Path().apply {
                                    for (i in 0 until currentLine) {
                                        addRect(Rect(
                                            layoutResult.getLineLeft(i), layoutResult.getLineTop(i),
                                            layoutResult.getLineRight(i), layoutResult.getLineBottom(i)
                                        ))
                                    }
                                    
                                    val currentLeft = layoutResult.getLineLeft(currentLine)
                                    val currentRight = layoutResult.getLineRight(currentLine)
                                    val currentWidth = currentRight - currentLeft
                                    val splitX = currentLeft + (currentWidth * lineProgress)
                                    
                                    addRect(Rect(
                                        currentLeft, layoutResult.getLineTop(currentLine),
                                        splitX, layoutResult.getLineBottom(currentLine)
                                    ))
                                }

                                // Path for the UNFILLED portion (Dim Color)
                                val unfilledPath = Path().apply {
                                    val currentLeft = layoutResult.getLineLeft(currentLine)
                                    val currentRight = layoutResult.getLineRight(currentLine)
                                    val currentWidth = currentRight - currentLeft
                                    val splitX = currentLeft + (currentWidth * lineProgress)
                                    
                                    addRect(Rect(
                                        splitX, layoutResult.getLineTop(currentLine),
                                        currentRight, layoutResult.getLineBottom(currentLine)
                                    ))
                                    
                                    for (i in (currentLine + 1) until lineCount) {
                                        addRect(Rect(
                                            layoutResult.getLineLeft(i), layoutResult.getLineTop(i),
                                            layoutResult.getLineRight(i), layoutResult.getLineBottom(i)
                                        ))
                                    }
                                }
                                
                                // Color the filled part (100% vibrant primary)
                                clipPath(filledPath) {
                                    drawRect(color = primaryColor, blendMode = BlendMode.SrcIn)
                                }
                                
                                // Color the unfilled part (dim)
                                clipPath(unfilledPath) {
                                    drawRect(color = dimColor, blendMode = BlendMode.SrcIn)
                                }
                            }
                        }
                )
            } else {
                Text(
                    text = line.text.ifBlank { "♪" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = dimColor,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )
            }
        }
        item {
            val uriHandler = LocalUriHandler.current
            val providerName = providerInfo?.displayName ?: stringResource(R.string.lyrics_provider_lrclib)
            val providerUrl = providerInfo?.websiteUrl ?: "https://lrclib.net/"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.lyrics_powered_by) + " ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$providerName ▾",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        if (onOpenOptions != null) onOpenOptions()
                        else uriHandler.openUri(providerUrl)
                    }
                )
                val typeStr = if (hasWordSync) stringResource(R.string.lyrics_word_synced) else stringResource(R.string.lyrics_line_synced)
                Text(
                    text = " • $typeStr",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

val ALL_AVAILABLE_PROVIDERS = listOf(
    LyricsProviderInfo("lyricsplus", "LyricsPlus"),
    LyricsProviderInfo("lrclib", "LRCLIB"),
    LyricsProviderInfo("kugou", "KuGou"),
    LyricsProviderInfo("betterlyrics", "Better Lyrics"),
    LyricsProviderInfo("unison", "Unison")
)

private val groupTopShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
private val groupMiddleShape = RoundedCornerShape(4.dp)
private val groupBottomShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
private val groupSingleShape = RoundedCornerShape(20.dp)

private fun lyricsGroupShape(index: Int, count: Int): Shape = when {
    count == 1 -> groupSingleShape
    index == 0 -> groupTopShape
    index == count - 1 -> groupBottomShape
    else -> groupMiddleShape
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsOptionsBottomSheet(
    onDismissRequest: () -> Unit,
    currentTrackTitle: String,
    currentArtistName: String?,
    streamTrackTitle: String?,
    streamArtistName: String?,
    allCandidates: List<Lyrics>,
    selectedProviderId: String?,
    activeLyrics: Lyrics?,
    onSearch: (String, String?) -> Unit,
    onSelectProvider: (String?) -> Unit,
    onResetToTrack: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var trackTitleInput by rememberSaveable(streamTrackTitle) { mutableStateOf(currentTrackTitle) }
    var artistInput by rememberSaveable(streamTrackTitle) { mutableStateOf(currentArtistName.orEmpty()) }
    val coroutineScope = rememberCoroutineScope()

    fun dismissWithAction(action: () -> Unit) {
        action()
        coroutineScope.launch {
            sheetState.hide()
            onDismissRequest()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.lyrics_options_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_close)
                    )
                }
            }

            TextField(
                value = trackTitleInput,
                onValueChange = { trackTitleInput = it },
                label = { Text(stringResource(R.string.lyrics_track_title)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Next
                ),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            TextField(
                value = artistInput,
                onValueChange = { artistInput = it },
                label = { Text(stringResource(R.string.lyrics_artist_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = {
                        if (trackTitleInput.isNotBlank()) {
                            dismissWithAction {
                                onSearch(trackTitleInput, artistInput.takeIf { it.isNotBlank() })
                            }
                        }
                    }
                ),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        trackTitleInput = streamTrackTitle.orEmpty()
                        artistInput = streamArtistName.orEmpty()
                        dismissWithAction {
                            onResetToTrack()
                        }
                    }
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.lyrics_reset_action))
                }

                FilledTonalButton(
                    onClick = {
                        if (trackTitleInput.isNotBlank()) {
                            dismissWithAction {
                                onSearch(trackTitleInput, artistInput.takeIf { it.isNotBlank() })
                            }
                        }
                    },
                    enabled = trackTitleInput.isNotBlank()
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.lyrics_search_action))
                }
            }

            val isAutoSelected = selectedProviderId == null || selectedProviderId.equals("auto", ignoreCase = true)

            val autoChosenLyrics = remember(allCandidates, activeLyrics) {
                allCandidates
                    .filter { it.hasWordSync || !it.syncedLyrics.isNullOrEmpty() || !it.plainLyrics.isNullOrBlank() }
                    .minWithOrNull(
                        compareBy(
                            { candidate ->
                                when {
                                    candidate.hasWordSync -> 0
                                    !candidate.syncedLyrics.isNullOrEmpty() -> 1
                                    else -> 2
                                }
                            },
                            { candidate ->
                                val idx = ALL_AVAILABLE_PROVIDERS.indexOfFirst {
                                    it.id.equals(candidate.providerInfo.id, ignoreCase = true)
                                }
                                if (idx >= 0) idx else Int.MAX_VALUE
                            }
                        )
                    ) ?: allCandidates.firstOrNull() ?: activeLyrics
            }

            val autoProviderName = autoChosenLyrics?.providerInfo?.displayName
            val autoSyncType = when {
                autoChosenLyrics?.hasWordSync == true -> stringResource(R.string.lyrics_word_synced)
                autoChosenLyrics?.syncedLyrics?.isNotEmpty() == true -> stringResource(R.string.lyrics_line_synced)
                autoChosenLyrics != null -> stringResource(R.string.lyrics_plain)
                else -> null
            }
            val autoSubtitle = if (autoProviderName != null && autoSyncType != null) {
                "$autoProviderName • $autoSyncType"
            } else {
                stringResource(R.string.lyrics_provider_auto_subtitle)
            }

            // Auto Option (Standalone Card - NOT a list item)
            Surface(
                onClick = {
                    onSelectProvider(null)
                },
                shape = groupSingleShape,
                color = if (isAutoSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                contentColor = if (isAutoSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.lyrics_provider_auto),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = autoSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isAutoSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Providers Grouped List
            val providerCount = ALL_AVAILABLE_PROVIDERS.size
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                ALL_AVAILABLE_PROVIDERS.forEachIndexed { idx, provider ->
                    val itemShape = lyricsGroupShape(idx, providerCount)
                    val matching = allCandidates.firstOrNull { it.providerInfo.id.equals(provider.id, ignoreCase = true) }
                    val isSelected = if (isAutoSelected) {
                        provider.id.equals(autoChosenLyrics?.providerInfo?.id, ignoreCase = true)
                    } else {
                        selectedProviderId.equals(provider.id, ignoreCase = true)
                    }
                    val isAvailable = matching != null

                    Surface(
                        onClick = {
                            if (isAvailable) {
                                if (!isAutoSelected && selectedProviderId.equals(provider.id, ignoreCase = true)) {
                                    onSelectProvider(null)
                                } else {
                                    onSelectProvider(provider.id)
                                }
                            }
                        },
                        enabled = isAvailable,
                        shape = itemShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (!isAvailable) Modifier.alpha(0.5f) else Modifier)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = provider.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )

                            if (matching != null) {
                                val badgeText = when {
                                    matching.hasWordSync -> stringResource(R.string.lyrics_word_synced)
                                    !matching.syncedLyrics.isNullOrEmpty() -> stringResource(R.string.lyrics_line_synced)
                                    else -> stringResource(R.string.lyrics_plain)
                                }
                                val badgeColor = when {
                                    isSelected -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                                    matching.hasWordSync -> MaterialTheme.colorScheme.primaryContainer
                                    !matching.syncedLyrics.isNullOrEmpty() -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = badgeColor
                                ) {
                                    Text(
                                        text = badgeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.lyrics_provider_not_found),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
