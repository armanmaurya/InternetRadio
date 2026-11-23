package com.armanmaurya.internetradio.ui.tv.screens.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.LrcLine
import com.armanmaurya.internetradio.domain.model.startedWordCount
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.armanmaurya.internetradio.ui.shared.viewmodels.PlayerViewModel.LyricsUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val OFFSET_STEP_MS = 500L

/**
 * Lyrics area for the TV player. Synced lyrics follow playback and keep the current line centred;
 * both kinds can be focused and scrolled with Up/Down (synced ones go back to following shortly after).
 */
@Composable
fun TvLyricsPanel(
    lyricsUiState: LyricsUiState,
    trackStartTime: Long?,
    syncOffsetMs: Long,
    getCurrentPosition: () -> Long,
    onSyncOffsetChange: (Long) -> Unit,
    onOpenLyricsOptions: () -> Unit = {},
    lyricsOptionsFocusRequester: FocusRequester? = null,
    controlBarFocusRequester: FocusRequester? = null,
    onLyricsFocusChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val success = lyricsUiState as? LyricsUiState.Success
    val hasSynced = success?.syncedLyrics?.isNotEmpty() == true && trackStartTime != null
    val hasAny = lyricsUiState is LyricsUiState.Success &&
        (hasSynced || !lyricsUiState.plainLyrics.isNullOrBlank())

    val providerName = success?.lyrics?.providerInfo?.displayName ?: stringResource(R.string.lyrics_provider_lrclib)
    val isWordSync = success?.lyrics?.hasWordSync == true ||
        success?.syncedLyrics?.any { !it.words.isNullOrEmpty() } == true
    val syncTypeStr = if (hasSynced) {
        if (isWordSync) " • " + stringResource(R.string.lyrics_word_synced) else " • " + stringResource(R.string.lyrics_line_synced)
    } else ""
    val providerCredit = if (hasAny) stringResource(R.string.lyrics_powered_by) + providerName + syncTypeStr else null

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .fadingEdges(),
            contentAlignment = Alignment.CenterStart
        ) {
            val synced = success?.syncedLyrics?.takeIf { it.isNotEmpty() && trackStartTime != null }
            val plain = success?.plainLyrics?.takeIf { it.isNotBlank() }
                ?: success?.syncedLyrics?.joinToString("\n") { it.text }?.takeIf { it.isNotBlank() }
            when {
                lyricsUiState is LyricsUiState.Loading ->
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                synced != null -> SyncedLyrics(
                    lines = synced,
                    trackStartTime = trackStartTime!!,
                    syncOffsetMs = syncOffsetMs,
                    getCurrentPosition = getCurrentPosition,
                    providerCredit = providerCredit,
                    controlBarFocusRequester = controlBarFocusRequester,
                    onFocusChange = onLyricsFocusChange
                )
                plain != null -> PlainLyrics(
                    lyrics = plain,
                    providerCredit = providerCredit,
                    controlBarFocusRequester = controlBarFocusRequester,
                    onFocusChange = onLyricsFocusChange
                )
                else -> Text(
                    text = stringResource(
                        if (lyricsUiState is LyricsUiState.Error) R.string.error_something_went_wrong
                        else R.string.player_lyrics_not_available
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        if (lyricsUiState !is LyricsUiState.Loading) {
            val iconColors = IconButtonDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.12f),
                contentColor = Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onOpenLyricsOptions,
                    colors = iconColors,
                    modifier = Modifier
                        .size(36.dp)
                        .then(if (lyricsOptionsFocusRequester != null) Modifier.focusRequester(lyricsOptionsFocusRequester) else Modifier)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = stringResource(R.string.lyrics_options_title),
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (hasSynced) {
                    SyncOffsetControl(syncOffsetMs, onSyncOffsetChange)
                }
            }
        }
    }
}

@Composable
private fun SyncOffsetControl(syncOffsetMs: Long, onSyncOffsetChange: (Long) -> Unit) {
    val iconColors = IconButtonDefaults.colors(
        containerColor = Color.White.copy(alpha = 0.12f),
        contentColor = Color.White,
        focusedContainerColor = Color.White,
        focusedContentColor = Color.Black
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(
            onClick = { onSyncOffsetChange(syncOffsetMs - OFFSET_STEP_MS) },
            colors = iconColors,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = stringResource(R.string.player_cd_delay_lyrics),
                modifier = Modifier.size(18.dp)
            )
        }
        val seconds = syncOffsetMs / 1000f
        Text(
            text = (if (syncOffsetMs > 0) "+" else "") + "${seconds}s",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.widthIn(min = 44.dp)
        )
        IconButton(
            onClick = { onSyncOffsetChange(syncOffsetMs + OFFSET_STEP_MS) },
            colors = iconColors,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.player_cd_fast_forward_lyrics),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SyncedLyrics(
    lines: List<LrcLine>,
    trackStartTime: Long,
    syncOffsetMs: Long,
    getCurrentPosition: () -> Long,
    providerCredit: String?,
    controlBarFocusRequester: FocusRequester?,
    onFocusChange: (Boolean) -> Unit
) {
    val listState = rememberLazyListState()
    var currentTimeMs by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()
    val stepPx = with(LocalDensity.current) { 120.dp.toPx() }
    var isFocused by remember { mutableStateOf(false) }
    // Up while focused lets you peek at past lyrics; following pauses until reset or focus leaves.
    var lastManualScrollAt by remember { mutableLongStateOf(0L) }
    var isFollowPaused by remember { mutableStateOf(false) }
    LaunchedEffect(lastManualScrollAt, isFocused) {
        if (!isFocused || lastManualScrollAt == 0L) {
            isFollowPaused = false
            return@LaunchedEffect
        }
        isFollowPaused = true
        delay(MANUAL_SCROLL_HOLD_MS)
        isFollowPaused = false
    }

    LaunchedEffect(lines, trackStartTime) {
        listState.scrollToItem(0)
        while (true) {
            currentTimeMs = getCurrentPosition() - trackStartTime
            delay(100)
        }
    }

    val activeIndexWithOffset = remember(lines, syncOffsetMs, currentTimeMs) {
        val t = currentTimeMs + syncOffsetMs
        lines.indexOfLast { t >= it.timestampMs }.coerceAtLeast(0)
    }

    LaunchedEffect(activeIndexWithOffset, isFollowPaused) {
        if (isFollowPaused) return@LaunchedEffect
        val info = listState.layoutInfo
        val viewport = info.viewportEndOffset - info.viewportStartOffset
        val item = info.visibleItemsInfo.firstOrNull { it.index == activeIndexWithOffset }
        if (item != null) {
            val distance = (item.offset + item.size / 2) - (info.viewportStartOffset + viewport / 2)
            listState.animateScrollBy(
                distance.toFloat(),
                tween(durationMillis = 700, easing = LinearOutSlowInEasing)
            )
        } else {
            listState.animateScrollToItem(activeIndexWithOffset, scrollOffset = -(viewport / 2))
        }
    }

    LazyColumn(
        state = listState,
        userScrollEnabled = false,
        modifier = Modifier
            .fillMaxSize()
            .onFocusChanged {
                isFocused = it.isFocused
                onFocusChange(it.isFocused)
                if (!it.isFocused) {
                    lastManualScrollAt = 0L
                    isFollowPaused = false
                }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (event.key == Key.DirectionLeft) {
                    try {
                        controlBarFocusRequester?.requestFocus()
                    } catch (_: Exception) {
                    }
                    return@onKeyEvent true
                }
                if (event.key == Key.DirectionDown) {
                    if (!isFollowPaused) {
                        // Following the song normally: move focus directly to footer controls!
                        return@onKeyEvent false
                    }
                    // User scrolled up into past lyrics. Scroll back down toward active line.
                    val info = listState.layoutInfo
                    val item = info.visibleItemsInfo.firstOrNull { it.index == activeIndexWithOffset }
                    if (item != null) {
                        isFollowPaused = false
                        lastManualScrollAt = 0L
                        return@onKeyEvent false
                    }
                    lastManualScrollAt = System.currentTimeMillis()
                    scope.launch { listState.animateScrollBy(stepPx) }
                    return@onKeyEvent true
                }
                if (event.key == Key.DirectionUp) {
                    if (!listState.canScrollBackward) return@onKeyEvent false
                    lastManualScrollAt = System.currentTimeMillis()
                    isFollowPaused = true
                    scope.launch { listState.animateScrollBy(-stepPx) }
                    return@onKeyEvent true
                }
                false
            }
            .focusable(),
        contentPadding = PaddingValues(vertical = 160.dp)
    ) {
        itemsIndexed(lines) { index, line ->
            val isActive = index == activeIndexWithOffset
            val color by animateColorAsState(
                targetValue = when {
                    isActive -> Color.White
                    index < activeIndexWithOffset -> Color.White.copy(alpha = if (isFocused) 0.55f else 0.35f)
                    else -> Color.White.copy(alpha = if (isFocused) 0.7f else 0.5f)
                },
                animationSpec = tween(400),
                label = "lyricColor"
            )
            val words = line.words
            if (isActive && !words.isNullOrEmpty()) {
                // Word-synced: words light up as they're sung.
                val sungCount = line.startedWordCount(currentTimeMs + syncOffsetMs)
                val upcoming = Color.White.copy(alpha = 0.5f)
                Text(
                    text = buildAnnotatedString {
                        words.forEachIndexed { i, word ->
                            withStyle(SpanStyle(color = if (i < sungCount) Color.White else upcoming)) {
                                append(word.text)
                            }
                        }
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                )
            } else {
                Text(
                    text = line.text.ifBlank { "♪" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                )
            }
        }
        if (providerCredit != null) {
            item(key = "lyrics_credit") {
                Text(
                    text = providerCredit,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.padding(top = 28.dp, bottom = 48.dp)
                )
            }
        }
    }
}

@Composable
private fun PlainLyrics(
    lyrics: String,
    providerCredit: String?,
    controlBarFocusRequester: FocusRequester?,
    onFocusChange: (Boolean) -> Unit
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val stepPx = with(LocalDensity.current) { 120.dp.toPx() }
    var isFocused by remember { mutableStateOf(false) }
    // Focus is shown by brightening the text and revealing a scroll indicator, not a frame.
    val textAlpha by animateFloatAsState(if (isFocused) 1f else 0.75f, tween(250), label = "lyricsAlpha")
    val indicatorAlpha by animateFloatAsState(
        if (isFocused && scrollState.maxValue > 0) 1f else 0f, tween(250), label = "indicatorAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onFocusChanged {
                isFocused = it.isFocused
                onFocusChange(it.isFocused)
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (event.key == Key.DirectionLeft) {
                    try {
                        controlBarFocusRequester?.requestFocus()
                    } catch (_: Exception) {
                    }
                    return@onKeyEvent true
                }
                when (event.key) {
                    Key.DirectionDown -> {
                        if (!scrollState.canScrollForward) return@onKeyEvent false
                        scope.launch { scrollState.animateScrollBy(stepPx) }
                        true
                    }
                    Key.DirectionUp -> {
                        if (!scrollState.canScrollBackward) return@onKeyEvent false
                        scope.launch { scrollState.animateScrollBy(-stepPx) }
                        true
                    }
                    else -> false
                }
            }
            .focusable()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState, enabled = false)
                .padding(end = 24.dp, top = 48.dp, bottom = 48.dp)
        ) {
            Text(
                text = lyrics,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = textAlpha)
            )
            if (providerCredit != null) {
                Spacer(Modifier.height(32.dp))
                Text(
                    text = providerCredit,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.45f)
                )
            }
        }

        // Thin scroll indicator on the right edge, shown only while focused.
        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(0.7f)
                .width(4.dp)
                .graphicsLayer { alpha = indicatorAlpha }
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.15f))
        ) {
            val viewport = scrollState.viewportSize.toFloat()
            val total = viewport + scrollState.maxValue
            val thumbFraction = if (total > 0f) (viewport / total).coerceIn(0.1f, 1f) else 1f
            val progress = if (scrollState.maxValue > 0) scrollState.value.toFloat() / scrollState.maxValue else 0f
            val thumbHeight = maxHeight * thumbFraction
            Box(
                modifier = Modifier
                    .offset(y = (maxHeight - thumbHeight) * progress)
                    .fillMaxWidth()
                    .height(thumbHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.8f))
            )
        }
    }
}

private const val MANUAL_SCROLL_HOLD_MS = 4_000L

/** Fades the top and bottom of the lyrics so lines slide in and out softly. */
private fun Modifier.fadingEdges(): Modifier = this
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
    }
