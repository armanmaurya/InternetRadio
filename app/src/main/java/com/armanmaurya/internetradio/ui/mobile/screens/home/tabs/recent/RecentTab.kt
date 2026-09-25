@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.recent


import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.ToggleChip
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteSweep
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.model.PlaybackSource
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.StationCard
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.StationListCard
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.RecordingSession
import com.armanmaurya.internetradio.ui.shared.viewmodels.RecentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentContent(
    onStationClick: (List<RadioStation>, Int, PlaybackSource) -> Unit,
    onEditStation: (String) -> Unit,
    onExportStation: ((RadioStation) -> Unit)? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: RecentViewModel = hiltViewModel(),
    playingStationUuid: String? = null,
    isPlaybackActive: Boolean = false,
    searchQuery: String = "",
    activeSessions: Map<String, RecordingSession> = emptyMap(),
    onToggleRecording: (RadioStation) -> Unit = {}
) {
    val recentStations by viewModel.recentStations.collectAsStateWithLifecycle()
    val libraryStationUuids by viewModel.libraryStationUuids.collectAsStateWithLifecycle()

    LaunchedEffect(searchQuery) {
        viewModel.onSearchQueryChange(searchQuery)
    }
    val useFilter by viewModel.useFilter.collectAsStateWithLifecycle()
    val isGridView by viewModel.isGridView.collectAsStateWithLifecycle()

    var showClearDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    val isLoading = recentStations == null
    Crossfade(
        targetState = isLoading,
        label = "RecentContentTransition",
        modifier = modifier.fillMaxSize()
    ) { loading ->
        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
        } else {
            val currentStations = recentStations ?: emptyList()
            val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            val showScrollToTop by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { gridState.firstVisibleItemIndex > 0 } }
            val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = if (isGridView) GridCells.Adaptive(150.dp) else GridCells.Fixed(1),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp + contentPadding.calculateBottomPadding()
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { viewModel.onGridViewChange(!isGridView) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.animation.AnimatedContent(
                                    targetState = isGridView,
                                    label = "view_toggle"
                                ) { isGrid ->
                                    Icon(
                                        imageVector = if (isGrid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.ViewModule,
                                        contentDescription = stringResource(R.string.home_toggle_view),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (currentStations.isNotEmpty()) {
                                    ToggleChip(
                                        text = stringResource(R.string.general_clear),
                                        onClick = { showClearDialog = true },
                                        leadingIcon = Icons.Default.DeleteSweep
                                    )
                                }

                                ToggleChip(
                                    text = if (useFilter) stringResource(R.string.home_filters_active) else stringResource(R.string.home_use_filters),
                                    onClick = { viewModel.toggleFilter() },
                                    isActive = useFilter,
                                    leadingIcon = Icons.Default.FilterList,
                                    trailingIcon = if (useFilter) Icons.Default.Close else null,
                                    trailingIconContentDescription = if (useFilter) stringResource(R.string.general_clear) else null
                                )
                            }
                        }
                    }
                    if (currentStations.isNotEmpty()) {
                        itemsIndexed(
                            items = currentStations,
                            key = { _, it -> it.stationUuid }
                        ) { index, station ->
                            val session = activeSessions[station.stationUuid]
                            val duration by (session?.durationSeconds ?: kotlinx.coroutines.flow.flowOf(0L)).collectAsStateWithLifecycle(initialValue = 0L)
                            
                            if (isGridView) {
                                StationCard(
                                    station = station,
                                    onClick = { onStationClick(currentStations, index, PlaybackSource.Recent) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem(),
                                    isCurrentlyPlaying = playingStationUuid == station.stationUuid,
                                    isPlaybackActive = isPlaybackActive,
                                    isFavorite = libraryStationUuids.contains(station.stationUuid),
                                    onToggleFavoriteClick = { viewModel.toggleLibrary(station) },
                                    onRemoveFromRecentClick = { viewModel.removeRecent(station.stationUuid) },
                                    onEditClick = if (libraryStationUuids.contains(station.stationUuid)) { { onEditStation(station.stationUuid) } } else null,
                                    onExportClick = { onExportStation?.invoke(station) },
                                    isRecording = session != null,
                                    recordingDuration = duration,
                                    onRecordClick = { onToggleRecording(station) },
                                    onStopRecordingClick = if (session != null) { { onToggleRecording(station) } } else null
                                )
                            } else {
                                StationListCard(
                                    station = station,
                                    onClick = { onStationClick(currentStations, index, PlaybackSource.Recent) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem(),
                                    isCurrentlyPlaying = playingStationUuid == station.stationUuid,
                                    isPlaybackActive = isPlaybackActive,
                                    isFavorite = libraryStationUuids.contains(station.stationUuid),
                                    onToggleFavoriteClick = { viewModel.toggleLibrary(station) },
                                    onRemoveFromRecentClick = { viewModel.removeRecent(station.stationUuid) },
                                    onEditClick = if (libraryStationUuids.contains(station.stationUuid)) { { onEditStation(station.stationUuid) } } else null,
                                    onExportClick = { onExportStation?.invoke(station) },
                                    isRecording = session != null,
                                    recordingDuration = duration,
                                    onRecordClick = { onToggleRecording(station) },
                                    onStopRecordingClick = if (session != null) { { onToggleRecording(station) } } else null
                                )
                            }
                        }
                    }
                } // LazyVerticalGrid

                // Overlay: centered empty state
                if (currentStations.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (useFilter) stringResource(R.string.home_no_recent_stations_filtered) else stringResource(R.string.home_no_recent_stations),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(32.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showScrollToTop,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 16.dp + contentPadding.calculateBottomPadding(), end = 16.dp),
                    enter = androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn() + androidx.compose.animation.slideIn(initialOffset = { androidx.compose.ui.unit.IntOffset(it.width, it.height) }),
                    exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOut(targetOffset = { androidx.compose.ui.unit.IntOffset(it.width, it.height) })
                ) {
                    androidx.compose.material3.SmallFloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                gridState.animateScrollToItem(0)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = stringResource(R.string.home_cd_scroll_to_top)
                        )
                    }
                }
            } // Box
        } // else (not loading)
    } // Crossfade

    if (showClearDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.home_clear_recent_title)) },
            text = { Text(stringResource(R.string.home_clear_recent_message)) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        viewModel.clearAllRecent()
                        showClearDialog = false
                    }
                ) {
                    Text(stringResource(R.string.general_clear))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.general_cancel))
                }
            }
        )
    }
}
