package com.armanmaurya.internetradio.ui.tv.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import coil3.compose.AsyncImage
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.armanmaurya.internetradio.R
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.ButtonDefaults
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import android.text.format.DateUtils
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.armanmaurya.internetradio.ui.shared.viewmodels.PlayerViewModel
import com.armanmaurya.internetradio.ui.shared.viewmodels.LibraryViewModel
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    playerViewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel,
    onEditStation: (String) -> Unit
) {
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()
    val station = playbackState.currentStation
    val isInLibraryFlow = remember(station?.stationUuid) {
        station?.let { libraryViewModel.isStationInLibrary(it.stationUuid) }
            ?: kotlinx.coroutines.flow.MutableStateFlow(false)
    }
    val isInLibrary by isInLibraryFlow.collectAsStateWithLifecycle()
    val trackHistory by playerViewModel.trackHistory.collectAsStateWithLifecycle()
    var isHistoryOpen by remember { mutableStateOf(false) }
    var sidebarHadFocus by remember { mutableStateOf(false) }
    val sidebarFocusRequester = remember { FocusRequester() }
    val recentTracksButtonFocusRequester = remember { FocusRequester() }

    // Optimistic play state: flips to true immediately on click so the
    // icon shows Pause before the media3 callback fires.
    var isPlayingOptimistic by remember { mutableStateOf(playbackState.isPlaying) }
    LaunchedEffect(playbackState.isPlaying, playbackState.isLoading) {
        isPlayingOptimistic = playbackState.isPlaying || playbackState.isLoading
    }

    LaunchedEffect(isHistoryOpen) {
        if (isHistoryOpen) {
            try {
                sidebarFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    BackHandler(enabled = isHistoryOpen) {
        isHistoryOpen = false
        try {
            recentTracksButtonFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (station == null) {
            Text("Nothing is currently playing", style = MaterialTheme.typography.bodyLarge)
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                val playButtonFocusRequester = remember { FocusRequester() }
                val fallbackPainter = painterResource(id = R.drawable.ic_launcher_foreground)

                AsyncImage(
                    model = station.favicon.ifEmpty { null },
                    contentDescription = null,
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
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.3f),
                                    Color.Black.copy(alpha = 0.9f)
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 96.dp, bottom = 32.dp, end = 48.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.Start
                ) {
                    Box(modifier = Modifier.size(192.dp)) {
                        AsyncImage(
                            model = station.favicon.ifEmpty { null },
                            contentDescription = "Station Thumbnail",
                            placeholder = fallbackPainter,
                            error = fallbackPainter,
                            fallback = fallbackPainter,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                        
                        if (station.bitrate > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(
                                        color = Color.Black.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                val badgeText = buildString {
                                    append("${station.bitrate} kbps")
                                    if (station.codec.isNotEmpty()) {
                                        append(" • ${station.codec.uppercase()}")
                                    }
                                }
                                Text(
                                    text = badgeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = station.name,
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    
                    if (playbackState.isLoading) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Buffering…",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1
                        )
                    } else if (!playbackState.currentTrack.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = playbackState.currentTrack!!,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.padding(top = 32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                isPlayingOptimistic = true
                                playerViewModel.togglePlayPause()
                            },
                            modifier = Modifier.focusRequester(playButtonFocusRequester),
                            scale = ButtonDefaults.scale(focusedScale = 1f),
                            colors = ButtonDefaults.colors(
                                containerColor = Color.White,
                                contentColor = Color.Black,
                                focusedContainerColor = Color.LightGray,
                                focusedContentColor = Color.Black
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(50))
                        ) {
                            Icon(
                                imageVector = if (isPlayingOptimistic) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlayingOptimistic) "Pause" else "Play",
                                modifier = Modifier.size(32.dp).padding(4.dp)
                            )
                        }

                        Button(
                            onClick = { isHistoryOpen = !isHistoryOpen },
                            modifier = Modifier.focusRequester(recentTracksButtonFocusRequester),
                            scale = ButtonDefaults.scale(focusedScale = 1f),
                            colors = ButtonDefaults.colors(
                                containerColor = Color.Black.copy(alpha = 0.5f),
                                contentColor = Color.White,
                                focusedContainerColor = Color.White.copy(alpha = 0.8f),
                                focusedContentColor = Color.Black
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(50)),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "Recent Tracks",
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Button(
                            onClick = {
                                if (isInLibrary) {
                                    libraryViewModel.removeStation(station.stationUuid)
                                } else {
                                    libraryViewModel.addStationToLibrary(station)
                                }
                            },
                            scale = ButtonDefaults.scale(focusedScale = 1f),
                            colors = ButtonDefaults.colors(
                                containerColor = Color.Black.copy(alpha = 0.5f),
                                contentColor = Color.White,
                                focusedContainerColor = Color.White.copy(alpha = 0.8f),
                                focusedContentColor = Color.Black
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(50))
                        ) {
                            Icon(
                                imageVector = if (isInLibrary) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (isInLibrary) "Remove from Library" else "Add to Library",
                                modifier = Modifier.size(28.dp).padding(2.dp)
                            )
                        }

                        if (isInLibrary && station != null) {
                            Button(
                                onClick = { onEditStation(station.stationUuid) },
                                scale = ButtonDefaults.scale(focusedScale = 1f),
                                colors = ButtonDefaults.colors(
                                    containerColor = Color.Black.copy(alpha = 0.5f),
                                    contentColor = Color.White,
                                    focusedContainerColor = Color.White.copy(alpha = 0.8f),
                                    focusedContentColor = Color.Black
                                ),
                                shape = ButtonDefaults.shape(shape = RoundedCornerShape(50))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Station",
                                    modifier = Modifier.size(28.dp).padding(2.dp)
                                )
                            }
                        }
                    }
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
                            .width(360.dp)
                            .background(Color.Black.copy(alpha = 0.85f))
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
                                    recentTracksButtonFocusRequester.requestFocus()
                                    true
                                } else {
                                    false
                                }
                            }
                            .padding(24.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = "Recent Tracks",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            if (trackHistory.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .focusRequester(sidebarFocusRequester)
                                        .focusable(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No tracks played yet",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    itemsIndexed(trackHistory) { index, track ->
                                        val time = DateUtils.getRelativeTimeSpanString(
                                            track.timestamp,
                                            System.currentTimeMillis(),
                                            DateUtils.MINUTE_IN_MILLIS,
                                            DateUtils.FORMAT_ABBREV_RELATIVE
                                        ).toString()
                                        ListItem(
                                            modifier = if (index == 0) Modifier.focusRequester(sidebarFocusRequester) else Modifier,
                                            selected = false,
                                            onClick = { },
                                            headlineContent = {
                                                Text(
                                                    text = track.trackTitle,
                                                    maxLines = 1,
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                            },
                                            supportingContent = {
                                                Text(
                                                    text = time,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            },
                                            colors = ListItemDefaults.colors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                            ),
                                            shape = ListItemDefaults.shape(shape = RoundedCornerShape(12.dp))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Removed focus trapping spacer
            }
        }
    }
}
