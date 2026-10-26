package com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryTab(
    trackHistory: List<TrackHistoryEntity>,
    savedTracks: List<TrackHistoryEntity> = emptyList(),
    listState: LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    onToggleStar: (TrackHistoryEntity) -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    var isSavedExpanded by remember { mutableStateOf(true) }

    val savedArrowRotation by animateFloatAsState(
        targetValue = if (isSavedExpanded) 0f else 180f,
        animationSpec = tween(durationMillis = 250),
        label = "saved_arrow_rotation"
    )

    val isPureBlack = MaterialTheme.colorScheme.surfaceContainerLow == Color.Black
    val headerBg = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .nestedScroll(nestedScrollConnection)
    ) {
        if (savedTracks.isEmpty() && trackHistory.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.player_no_tracks_played),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            if (savedTracks.isNotEmpty()) {
                item(key = "header_saved") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(headerBg)
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (isSavedExpanded) {
                                        isSavedExpanded = false
                                    } else {
                                        isSavedExpanded = true
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(0)
                                        }
                                    }
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "${stringResource(R.string.player_saved_songs)} (${savedTracks.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer { rotationZ = savedArrowRotation }
                            )
                        }
                    }
                }

                item(key = "saved_tracks_content") {
                    AnimatedVisibility(
                        visible = isSavedExpanded,
                        enter = expandVertically(
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                            expandFrom = Alignment.Top
                        ) + fadeIn(animationSpec = tween(durationMillis = 300)) + slideInVertically(
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                            initialOffsetY = { -it / 2 }
                        ),
                        exit = shrinkVertically(
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                            shrinkTowards = Alignment.Top
                        ) + fadeOut(animationSpec = tween(durationMillis = 250)) + slideOutVertically(
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                            targetOffsetY = { -it / 2 }
                        )
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            savedTracks.forEach { track ->
                                TrackHistoryItem(
                                    track = track,
                                    onToggleStar = { onToggleStar(track) }
                                )
                            }
                        }
                    }
                }
            }

            stickyHeader(key = "header_history") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerBg)
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (trackHistory.isNotEmpty()) {
                                "${stringResource(R.string.player_station_history)} (${trackHistory.size})"
                            } else {
                                stringResource(R.string.player_station_history)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (trackHistory.isEmpty()) {
                item(key = "empty_history") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.player_no_tracks_played),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(
                    items = trackHistory,
                    key = { "history_${it.id}" }
                ) { track ->
                    TrackHistoryItem(
                        track = track,
                        onToggleStar = { onToggleStar(track) }
                    )
                }
            }
        }
    }
}
