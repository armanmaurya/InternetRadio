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
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.filled.ArrowUpward
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.player.PlaybackSource
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.StationCard
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.StationListCard
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.ui.shared.viewmodels.RecentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentContent(
    onStationClick: (List<RadioStation>, Int, PlaybackSource) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: RecentViewModel = hiltViewModel(),
    playingStationUuid: String? = null,
    isPlaybackActive: Boolean = false,
    searchQuery: String = ""
) {
    val recentStations by viewModel.recentStations.collectAsStateWithLifecycle()
    val libraryStationUuids by viewModel.libraryStationUuids.collectAsStateWithLifecycle()

    LaunchedEffect(searchQuery) {
        viewModel.onSearchQueryChange(searchQuery)
    }
    val useFilter by viewModel.useFilter.collectAsStateWithLifecycle()
    val isGridView by viewModel.isGridView.collectAsStateWithLifecycle()

    val isLoading = recentStations == null
    Crossfade(
        targetState = isLoading,
        label = "RecentContentTransition",
        modifier = modifier.fillMaxSize()
    ) { loading ->
        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
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
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { viewModel.onGridViewChange(!isGridView) }) {
                    Icon(
                        imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = stringResource(R.string.home_toggle_view)
                    )
                }

                FilterChip(
                    selected = useFilter,
                    onClick = { viewModel.toggleFilter() },
                    label = { 
                        Text(
                            text = if (useFilter) stringResource(R.string.home_filters_active) else stringResource(R.string.home_use_filters),
                            style = MaterialTheme.typography.labelMedium
                        ) 
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = if (useFilter) {
                        {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.general_clear),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Transparent,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    border = null
                )
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
                        text = if (useFilter) stringResource(R.string.home_no_recent_stations_filtered) else stringResource(R.string.home_no_recent_stations),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            itemsIndexed(
                items = currentStations,
                key = { _, it -> it.stationUuid }
            ) { index, station ->
                if (isGridView) {
                    StationCard(
                        station = station,
                        onClick = { onStationClick(currentStations, index, PlaybackSource.Recent) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(),
                        isCurrentlyPlaying = playingStationUuid == station.stationUuid,
                        isPlaybackActive = isPlaybackActive,
                        isFavorite = libraryStationUuids.contains(station.stationUuid)
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
                        isFavorite = libraryStationUuids.contains(station.stationUuid)
                    )
                }
            }
        }
    }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showScrollToTop,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 16.dp + contentPadding.calculateBottomPadding(), end = 16.dp),
                    enter = androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut()
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
            }
        }
    }
}
