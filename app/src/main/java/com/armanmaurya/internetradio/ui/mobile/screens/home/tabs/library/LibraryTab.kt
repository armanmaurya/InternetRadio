@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.library


import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.model.PlaybackSource
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.StationCard
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.StationListCard
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.ToggleChip
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.ui.shared.viewmodels.LibraryViewModel
import com.armanmaurya.internetradio.domain.model.LibrarySortOption
import sh.calvin.reorderable.*


import com.armanmaurya.internetradio.domain.model.RecordingSession

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LibraryContent(
    onStationClick: (List<RadioStation>, Int, PlaybackSource) -> Unit,
    onEditStation: (String?) -> Unit,
    onExportStation: ((RadioStation) -> Unit)? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: LibraryViewModel = hiltViewModel(),
    playingStationUuid: String? = null,
    isPlaybackActive: Boolean = false,
    searchQuery: String = "",
    activeSessions: Map<String, RecordingSession> = emptyMap(),
    onToggleRecording: (RadioStation) -> Unit = {},
    onDragStateChange: (Boolean) -> Unit = {}
) {
    LaunchedEffect(searchQuery) {
        viewModel.onSearchQueryChange(searchQuery)
    }
    val stations by viewModel.stations.collectAsStateWithLifecycle()
    val useFilter by viewModel.useFilter.collectAsStateWithLifecycle()
    val isGridView by viewModel.isGridView.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    var showSortMenu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var isDragLocked by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }

    val isLoading = stations == null
    Crossfade(
        targetState = isLoading,
        label = "LibraryContentTransition",
        modifier = modifier.fillMaxSize()
    ) { loading ->
        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
        } else {
            var currentStations by androidx.compose.runtime.remember(stations) { 
                androidx.compose.runtime.mutableStateOf(stations ?: emptyList()) 
            }
            val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            val showScrollToTop by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { gridState.firstVisibleItemIndex > 0 } }
            

            val coroutineScope = rememberCoroutineScope()
            val reorderableState = rememberReorderableLazyGridState(gridState) { from, to ->
                if (sortOption == LibrarySortOption.CUSTOM) {
                    currentStations = currentStations.toMutableList().apply {
                        val fromIndex = indexOfFirst { it.stationUuid == from.key }
                        val toIndex = indexOfFirst { it.stationUuid == to.key }
                        if (fromIndex != -1 && toIndex != -1) {
                            add(toIndex, removeAt(fromIndex))
                        }
                    }
                }
            }
            
            // Wait for drag completion to update database
            LaunchedEffect(reorderableState.isAnyItemDragging) {
                onDragStateChange(reorderableState.isAnyItemDragging)
                if (!reorderableState.isAnyItemDragging && sortOption == LibrarySortOption.CUSTOM) {
                    // Update database only when dragging is completely done
                    // Since moveStation takes indices, we'd need to update based on the full list.
                    // Instead, let's add a new function updateStationsOrder to ViewModel.
                    viewModel.updateStationsOrder(currentStations)
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = if (isGridView) GridCells.Adaptive(150.dp) else GridCells.Fixed(1),
                    modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp + contentPadding.calculateBottomPadding() + 120.dp // Extra padding for stacked FABs
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    ToggleChip(
                        text = if (useFilter) stringResource(R.string.home_filters_active) else stringResource(R.string.home_use_filters),
                        onClick = { viewModel.toggleFilter() },
                        isActive = useFilter,
                        leadingIcon = Icons.Default.FilterList,
                        trailingIcon = if (useFilter) Icons.Default.Close else null,
                        trailingIconContentDescription = if (useFilter) stringResource(R.string.general_clear) else null
                    )

                    androidx.compose.animation.AnimatedVisibility(visible = sortOption == LibrarySortOption.CUSTOM) {
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(if (isDragLocked) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer)
                                .clickable { isDragLocked = !isDragLocked }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isDragLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = if (isDragLocked) "Unlock dragging" else "Lock dragging",
                                tint = if (isDragLocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Box {
                        val sortText = when (sortOption) {
                            LibrarySortOption.NAME_A_Z, LibrarySortOption.NAME_Z_A -> stringResource(R.string.home_sort_name)
                            LibrarySortOption.RECENTLY_ADDED, LibrarySortOption.OLDEST_ADDED -> stringResource(R.string.home_sort_added)
                            LibrarySortOption.RECENTLY_PLAYED, LibrarySortOption.LEAST_RECENTLY_PLAYED -> stringResource(R.string.home_sort_played)
                            LibrarySortOption.CUSTOM -> stringResource(R.string.home_sort_custom)
                        }
                        
                        val sortIconRes = when (sortOption) {
                            LibrarySortOption.NAME_A_Z, LibrarySortOption.RECENTLY_ADDED, LibrarySortOption.RECENTLY_PLAYED -> R.drawable.ic_sort_down
                            LibrarySortOption.NAME_Z_A, LibrarySortOption.OLDEST_ADDED, LibrarySortOption.LEAST_RECENTLY_PLAYED -> R.drawable.ic_sort_up
                            else -> null
                        }
                        ToggleChip(
                            text = sortText,
                            onClick = { showSortMenu = !showSortMenu },
                            leadingContent = {
                                if (sortIconRes != null) {
                                    Icon(
                                        painter = androidx.compose.ui.res.painterResource(id = sortIconRes),
                                        contentDescription = stringResource(R.string.library_cd_sort_options),
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Sort,
                                        contentDescription = stringResource(R.string.library_cd_sort_options),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            },
                            trailingIcon = if (showSortMenu) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown
                        )
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home_sort_played)) },
                                onClick = { 
                                    if (sortOption == LibrarySortOption.RECENTLY_PLAYED) {
                                        viewModel.setSortOption(LibrarySortOption.LEAST_RECENTLY_PLAYED)
                                    } else {
                                        viewModel.setSortOption(LibrarySortOption.RECENTLY_PLAYED)
                                    }
                                    showSortMenu = false
                                },
                                trailingIcon = {
                                    if (sortOption == LibrarySortOption.RECENTLY_PLAYED) Icon(painter = androidx.compose.ui.res.painterResource(R.drawable.ic_sort_down), contentDescription = stringResource(R.string.home_cd_descending), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                    else if (sortOption == LibrarySortOption.LEAST_RECENTLY_PLAYED) Icon(painter = androidx.compose.ui.res.painterResource(R.drawable.ic_sort_up), contentDescription = stringResource(R.string.home_cd_ascending), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home_sort_added)) },
                                onClick = { 
                                    if (sortOption == LibrarySortOption.RECENTLY_ADDED) {
                                        viewModel.setSortOption(LibrarySortOption.OLDEST_ADDED)
                                    } else {
                                        viewModel.setSortOption(LibrarySortOption.RECENTLY_ADDED)
                                    }
                                    showSortMenu = false
                                },
                                trailingIcon = {
                                    if (sortOption == LibrarySortOption.RECENTLY_ADDED) Icon(painter = androidx.compose.ui.res.painterResource(R.drawable.ic_sort_down), contentDescription = stringResource(R.string.home_cd_descending), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                    else if (sortOption == LibrarySortOption.OLDEST_ADDED) Icon(painter = androidx.compose.ui.res.painterResource(R.drawable.ic_sort_up), contentDescription = stringResource(R.string.home_cd_ascending), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home_sort_name)) },
                                onClick = { 
                                    if (sortOption == LibrarySortOption.NAME_A_Z) {
                                        viewModel.setSortOption(LibrarySortOption.NAME_Z_A)
                                    } else {
                                        viewModel.setSortOption(LibrarySortOption.NAME_A_Z)
                                    }
                                    showSortMenu = false
                                },
                                trailingIcon = {
                                    if (sortOption == LibrarySortOption.NAME_A_Z) Icon(painter = androidx.compose.ui.res.painterResource(R.drawable.ic_sort_down), contentDescription = stringResource(R.string.library_cd_sort_a_z), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                    else if (sortOption == LibrarySortOption.NAME_Z_A) Icon(painter = androidx.compose.ui.res.painterResource(R.drawable.ic_sort_up), contentDescription = stringResource(R.string.library_cd_sort_z_a), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home_sort_custom)) },
                                onClick = { 
                                    viewModel.setSortOption(LibrarySortOption.CUSTOM)
                                    showSortMenu = false
                                },
                                trailingIcon = if (sortOption == LibrarySortOption.CUSTOM) { { Icon(Icons.Default.Check, "Active") } } else null
                            )
                        }
                    }
                }
            }
        }

        if (currentStations.isEmpty()) { /* handled below as overlay */ }
        else {
            itemsIndexed(
                items = currentStations,
                key = { _, it -> it.stationUuid }
            ) { index, station ->
                ReorderableItem(reorderableState, key = station.stationUuid) { isDragging ->
                    val dragModifier = if (sortOption == LibrarySortOption.CUSTOM && !isDragLocked) Modifier.longPressDraggableHandle() else Modifier
                    
                    val session = activeSessions[station.stationUuid]
                    val duration by (session?.durationSeconds ?: kotlinx.coroutines.flow.flowOf(0L)).collectAsStateWithLifecycle(initialValue = 0L)
                    
                    Box(modifier = Modifier.animateItem()) {
                        if (isGridView) {
                            StationCard(
                                station = station,
                                onClick = { onStationClick(currentStations, index, PlaybackSource.Library) },
                                onToggleFavoriteClick = { viewModel.removeStation(station.stationUuid) },
                                onEditClick = { onEditStation(station.stationUuid) },
                                onExportClick = { onExportStation?.invoke(station) },
                                modifier = Modifier.fillMaxWidth().then(dragModifier),
                                isCurrentlyPlaying = playingStationUuid == station.stationUuid,
                                isPlaybackActive = isPlaybackActive,
                                isFavorite = true,
                                isRecording = session != null,
                                recordingDuration = duration,
                                onRecordClick = { onToggleRecording(station) },
                                onStopRecordingClick = if (session != null) { { onToggleRecording(station) } } else null
                            )
                        } else {
                            StationListCard(
                                station = station,
                                onClick = { onStationClick(currentStations, index, PlaybackSource.Library) },
                                onToggleFavoriteClick = { viewModel.removeStation(station.stationUuid) },
                                onEditClick = { onEditStation(station.stationUuid) },
                                onExportClick = { onExportStation?.invoke(station) },
                                modifier = Modifier.fillMaxWidth().then(dragModifier),
                                isCurrentlyPlaying = playingStationUuid == station.stationUuid,
                                isPlaybackActive = isPlaybackActive,
                                isFavorite = true,
                                isRecording = session != null,
                                recordingDuration = duration,
                                onRecordClick = { onToggleRecording(station) },
                                onStopRecordingClick = if (session != null) { { onToggleRecording(station) } } else null
                            )
                        }
                    }
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
                            text = if (useFilter)
                                stringResource(R.string.home_no_library_stations_filter)
                            else
                                stringResource(R.string.home_no_library_stations_yet),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp + contentPadding.calculateBottomPadding(), end = 16.dp)
        ) {
            FloatingActionButton(
                onClick = { onEditStation(null) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.edit_station_add_station)
                )
            }
        }

        AnimatedVisibility(
            visible = showScrollToTop,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp + contentPadding.calculateBottomPadding() + 72.dp, end = 16.dp),
            enter = scaleIn() + fadeIn() + slideIn(initialOffset = { androidx.compose.ui.unit.IntOffset(it.width, it.height) }),
            exit = scaleOut() + fadeOut() + slideOut(targetOffset = { androidx.compose.ui.unit.IntOffset(it.width, it.height) })
        ) {
            SmallFloatingActionButton(
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
        } // AnimatedVisibility closes
    } // Box closes
    } // else closes
} // Crossfade closes
} // LibraryContent closes
