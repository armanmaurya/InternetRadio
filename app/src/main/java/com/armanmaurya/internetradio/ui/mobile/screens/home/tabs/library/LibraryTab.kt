package com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.library


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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.player.PlaybackSource
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.StationCard
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.StationListCard
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.ui.shared.viewmodels.LibraryViewModel
import com.armanmaurya.internetradio.data.model.LibrarySortOption
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.browse.SortPopupContent

import androidx.compose.material.icons.filled.DragIndicator

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LibraryContent(
    onStationClick: (List<RadioStation>, Int, PlaybackSource) -> Unit,
    onEditStation: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: LibraryViewModel = hiltViewModel(),
    playingStationUuid: String? = null,
    isPlaybackActive: Boolean = false,
    searchQuery: String = ""
) {
    LaunchedEffect(searchQuery) {
        viewModel.onSearchQueryChange(searchQuery)
    }
    val stations by viewModel.stations.collectAsStateWithLifecycle()
    val useFilter by viewModel.useFilter.collectAsStateWithLifecycle()
    val isGridView by viewModel.isGridView.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    var showSortMenu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    val isLoading = stations == null
    Crossfade(
        targetState = isLoading,
        label = "LibraryContentTransition",
        modifier = modifier.fillMaxSize()
    ) { loading ->
        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
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
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.onGridViewChange(!isGridView) }) {
                        androidx.compose.animation.AnimatedContent(
                            targetState = isGridView,
                            label = "view_toggle"
                        ) { isGrid ->
                            Icon(
                                imageVector = if (isGrid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.ViewModule,
                                contentDescription = stringResource(R.string.home_toggle_view),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically, 
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable { showSortMenu = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort Options",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            val sortText = when (sortOption) {
                                LibrarySortOption.NAME_A_Z, LibrarySortOption.NAME_Z_A -> "Name"
                                LibrarySortOption.RECENTLY_ADDED, LibrarySortOption.OLDEST_ADDED -> "Added"
                                LibrarySortOption.RECENTLY_PLAYED, LibrarySortOption.LEAST_RECENTLY_PLAYED -> "Played"
                                LibrarySortOption.CUSTOM -> "Custom"
                            }
                            Text(
                                text = sortText,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            val sortIcon = when (sortOption) {
                                LibrarySortOption.NAME_A_Z, LibrarySortOption.RECENTLY_ADDED, LibrarySortOption.RECENTLY_PLAYED -> Icons.Default.ArrowDownward
                                LibrarySortOption.NAME_Z_A, LibrarySortOption.OLDEST_ADDED, LibrarySortOption.LEAST_RECENTLY_PLAYED -> Icons.Default.ArrowUpward
                                else -> null
                            }
                            if (sortIcon != null) {
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = sortIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        val transitionState = remember { MutableTransitionState(false) }
                        transitionState.targetState = showSortMenu
                        
                        if (transitionState.currentState || transitionState.targetState) {
                            SortPopupContent(
                                transitionState = transitionState,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    tonalElevation = 3.dp,
                                    shadowElevation = 3.dp,
                                    modifier = Modifier.padding(top = 40.dp, end = 16.dp)
                                ) {
                                    Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                                        DropdownMenuItem(
                                            text = { Text("Played") },
                                            onClick = { 
                                                if (sortOption == LibrarySortOption.RECENTLY_PLAYED) {
                                                    viewModel.setSortOption(LibrarySortOption.LEAST_RECENTLY_PLAYED)
                                                } else {
                                                    viewModel.setSortOption(LibrarySortOption.RECENTLY_PLAYED)
                                                }
                                                showSortMenu = false
                                            },
                                            trailingIcon = {
                                                if (sortOption == LibrarySortOption.RECENTLY_PLAYED) Icon(Icons.Default.ArrowDownward, "Descending")
                                                else if (sortOption == LibrarySortOption.LEAST_RECENTLY_PLAYED) Icon(Icons.Default.ArrowUpward, "Ascending")
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Added") },
                                            onClick = { 
                                                if (sortOption == LibrarySortOption.RECENTLY_ADDED) {
                                                    viewModel.setSortOption(LibrarySortOption.OLDEST_ADDED)
                                                } else {
                                                    viewModel.setSortOption(LibrarySortOption.RECENTLY_ADDED)
                                                }
                                                showSortMenu = false
                                            },
                                            trailingIcon = {
                                                if (sortOption == LibrarySortOption.RECENTLY_ADDED) Icon(Icons.Default.ArrowDownward, "Descending")
                                                else if (sortOption == LibrarySortOption.OLDEST_ADDED) Icon(Icons.Default.ArrowUpward, "Ascending")
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Name") },
                                            onClick = { 
                                                if (sortOption == LibrarySortOption.NAME_A_Z) {
                                                    viewModel.setSortOption(LibrarySortOption.NAME_Z_A)
                                                } else {
                                                    viewModel.setSortOption(LibrarySortOption.NAME_A_Z)
                                                }
                                                showSortMenu = false
                                            },
                                            trailingIcon = {
                                                if (sortOption == LibrarySortOption.NAME_A_Z) Icon(Icons.Default.ArrowDownward, "A-Z")
                                                else if (sortOption == LibrarySortOption.NAME_Z_A) Icon(Icons.Default.ArrowUpward, "Z-A")
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Custom") },
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
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable { viewModel.toggleFilter() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (useFilter) stringResource(R.string.home_filters_active) else stringResource(R.string.home_use_filters),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (useFilter) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.general_clear),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        if (currentStations.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp),
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
        } else {
            itemsIndexed(
                items = currentStations,
                key = { _, it -> it.stationUuid }
            ) { index, station ->
                ReorderableItem(reorderableState, key = station.stationUuid) { isDragging ->
                    val dragModifier = if (sortOption == LibrarySortOption.CUSTOM) Modifier.longPressDraggableHandle() else Modifier
                    
                    Box(modifier = Modifier.animateItem()) {
                        if (isGridView) {
                            StationCard(
                                station = station,
                                onClick = { onStationClick(currentStations, index, PlaybackSource.Library) },
                                onDeleteClick = { viewModel.removeStation(station.stationUuid) },
                                onEditClick = { onEditStation(station.stationUuid) },
                                modifier = Modifier.fillMaxWidth().then(dragModifier),
                                isCurrentlyPlaying = playingStationUuid == station.stationUuid,
                                isPlaybackActive = isPlaybackActive
                            )
                        } else {
                            StationListCard(
                                station = station,
                                onClick = { onStationClick(currentStations, index, PlaybackSource.Library) },
                                onDeleteClick = { viewModel.removeStation(station.stationUuid) },
                                onEditClick = { onEditStation(station.stationUuid) },
                                modifier = Modifier.fillMaxWidth().then(dragModifier),
                                isCurrentlyPlaying = playingStationUuid == station.stationUuid,
                                isPlaybackActive = isPlaybackActive
                            )
                        }
                    }
                }
            }
        }
        } // LazyVerticalGrid closes
        
        AnimatedVisibility(
            visible = showScrollToTop,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp + contentPadding.calculateBottomPadding() + 72.dp, end = 16.dp),
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
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
