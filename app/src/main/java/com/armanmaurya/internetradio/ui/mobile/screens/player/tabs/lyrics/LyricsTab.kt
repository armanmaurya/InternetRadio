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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import kotlinx.coroutines.launch

@Composable
fun LyricsTab(
    listState: LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    lyricsUiState: LyricsUiState,
    trackStartTime: Long?,
    syncOffsetMs: Long,
    isPlaying: Boolean,
    getCurrentPosition: () -> Long,
    onSyncOffsetChange: (Long) -> Unit
) {
    var isSyncEnabled by rememberSaveable { mutableStateOf(true) }

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
            when (lyricsUiState) {
                is LyricsUiState.Loading -> {
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
                is LyricsUiState.NotAvailable -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item {
                            Text(
                                text = stringResource(R.string.player_lyrics_not_available),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is LyricsUiState.Success -> {
                    val canSync = !lyricsUiState.syncedLyrics.isNullOrEmpty() && trackStartTime != null
                    val actuallySync = canSync && isSyncEnabled
                    
                    if (actuallySync) {
                        SyncedLyricsView(
                            lines = lyricsUiState.syncedLyrics!!,
                            listState = listState,
                            trackStartTime = trackStartTime!!,
                            syncOffsetMs = syncOffsetMs,
                            getCurrentPosition = getCurrentPosition
                        )
                    } else if (!lyricsUiState.plainLyrics.isNullOrEmpty()) {
                        PlainLyricsView(
                            lyrics = lyricsUiState.plainLyrics!!,
                            listState = listState
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            item {
                                Text(
                                    text = stringResource(R.string.player_lyrics_not_available),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Overlay Toggle Button
        if (lyricsUiState is LyricsUiState.Success) {
            val canSync = !lyricsUiState.syncedLyrics.isNullOrEmpty() && trackStartTime != null
            val isCurrentlySynced = canSync && isSyncEnabled
            
            // Only show button if we have SOME lyrics
            if (!lyricsUiState.syncedLyrics.isNullOrEmpty() || !lyricsUiState.plainLyrics.isNullOrEmpty()) {
                val cornerRadius by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (isCurrentlySynced) 12.dp else 50.dp,
                    animationSpec = androidx.compose.animation.core.tween(300)
                )
                val sideRotation by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isCurrentlySynced) 0f else 90f,
                    animationSpec = androidx.compose.animation.core.tween(300)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
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
                        enabled = canSync,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius),
                        modifier = Modifier.height(48.dp).zIndex(1f)
                    ) {
                        val offsetText = if (syncOffsetMs != 0L) " (${if (syncOffsetMs > 0) "+" else ""}${syncOffsetMs / 1000f}s)" else ""
                        Text(
                            text = if (isCurrentlySynced) "Synced$offsetText" else "Plain",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
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
            }
        }
    }
}

@Composable
fun PlainLyricsView(
    lyrics: String,
    listState: LazyListState
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentPadding = PaddingValues(top = 72.dp, bottom = 120.dp)
    ) {
        item {
            Text(
                text = lyrics,
                style = MaterialTheme.typography.headlineMedium, // match synced lyrics
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start, // left aligned
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            val uriHandler = LocalUriHandler.current
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
                    text = stringResource(R.string.lyrics_provider_lrclib),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://lrclib.net/")
                    }
                )
            }
        }
    }
}

@Composable
fun SyncedLyricsView(
    lines: List<LrcLine>,
    listState: LazyListState,
    trackStartTime: Long,
    syncOffsetMs: Long,
    getCurrentPosition: () -> Long
) {
    var currentTimeMs by remember { mutableLongStateOf(0L) }
    var activeIndex by remember(trackStartTime) { mutableIntStateOf(0) }

    LaunchedEffect(lines, trackStartTime) {
        listState.scrollToItem(0)
        while (true) {
            currentTimeMs = getCurrentPosition() - trackStartTime
            delay(100) // update 10 times a second
        }
    }

    // Calculate new index smoothly without launching effects every 100ms
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
        items(lines.size) { index ->
            val isActive = index == activeIndex
            val line = lines[index]
            
            val primaryColor = MaterialTheme.colorScheme.primary
            val dimColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            
            var textLayoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }

            val progress = if (isActive) {
                val adjustedTime = currentTimeMs + syncOffsetMs
                val lineDuration = if (index + 1 < lines.size) {
                    lines[index + 1].timestampMs - line.timestampMs
                } else {
                    5000L // Fallback duration for the last line
                }
                
                ((adjustedTime - line.timestampMs).toFloat() / java.lang.Math.max(1L, lineDuration).toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            
            Text(
                text = line.text,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = Color.Black, // Fully opaque mask so SrcIn preserves the primary color's alpha!
                textAlign = TextAlign.Start,
                onTextLayout = { textLayoutResult = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .graphicsLayer {
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent() // Draw the opaque black text mask
                        
                        if (!isActive || progress <= 0f) {
                            // If not active, the whole line is dim
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
                            val filledPath = androidx.compose.ui.graphics.Path().apply {
                                for (i in 0 until currentLine) {
                                    addRect(androidx.compose.ui.geometry.Rect(
                                        layoutResult.getLineLeft(i), layoutResult.getLineTop(i),
                                        layoutResult.getLineRight(i), layoutResult.getLineBottom(i)
                                    ))
                                }
                                
                                val currentLeft = layoutResult.getLineLeft(currentLine)
                                val currentRight = layoutResult.getLineRight(currentLine)
                                val currentWidth = currentRight - currentLeft
                                val splitX = currentLeft + (currentWidth * lineProgress)
                                
                                addRect(androidx.compose.ui.geometry.Rect(
                                    currentLeft, layoutResult.getLineTop(currentLine),
                                    splitX, layoutResult.getLineBottom(currentLine)
                                ))
                            }

                            // Path for the UNFILLED portion (Dim Color)
                            val unfilledPath = androidx.compose.ui.graphics.Path().apply {
                                val currentLeft = layoutResult.getLineLeft(currentLine)
                                val currentRight = layoutResult.getLineRight(currentLine)
                                val currentWidth = currentRight - currentLeft
                                val splitX = currentLeft + (currentWidth * lineProgress)
                                
                                addRect(androidx.compose.ui.geometry.Rect(
                                    splitX, layoutResult.getLineTop(currentLine),
                                    currentRight, layoutResult.getLineBottom(currentLine)
                                ))
                                
                                for (i in (currentLine + 1) until lineCount) {
                                    addRect(androidx.compose.ui.geometry.Rect(
                                        layoutResult.getLineLeft(i), layoutResult.getLineTop(i),
                                        layoutResult.getLineRight(i), layoutResult.getLineBottom(i)
                                    ))
                                }
                            }
                            
                            // Color the filled part (100% vibrant primary)
                            clipPath(filledPath) {
                                drawRect(color = primaryColor, blendMode = BlendMode.SrcIn)
                            }
                            
                            // Color the unfilled part (60% translucent dim)
                            clipPath(unfilledPath) {
                                drawRect(color = dimColor, blendMode = BlendMode.SrcIn)
                            }
                        }
                    }
            )
        }
        item {
            val uriHandler = LocalUriHandler.current
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
                    text = stringResource(R.string.lyrics_provider_lrclib),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://lrclib.net/")
                    }
                )
            }
        }
    }
}
