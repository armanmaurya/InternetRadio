package com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.player.PlaybackSource
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.StationCard
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.StationListCard
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.IconButton
import com.armanmaurya.internetradio.ui.shared.viewmodels.BrowseViewModel

@Composable
fun BrowseContent(
    onStationClick: (List<RadioStation>, Int, PlaybackSource) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: BrowseViewModel = hiltViewModel(),
    playingStationUuid: String? = null,
    isPlaybackActive: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val libraryStationUuids by viewModel.libraryStationUuids.collectAsStateWithLifecycle()
    
    val gridState = rememberLazyGridState()
    
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf false
            
            lastVisibleItem.index >= gridState.layoutInfo.totalItemsCount - 9 // 3 rows early
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && !uiState.isLoading && !uiState.isNextPageLoading && uiState.canLoadMore) {
            viewModel.loadMoreStations()
        }
    }

    val showScrollToTop by remember { derivedStateOf { gridState.firstVisibleItemIndex > 0 } }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = if (uiState.isGridView) GridCells.Adaptive(150.dp) else GridCells.Fixed(1),
            modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 16.dp + contentPadding.calculateBottomPadding()
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            SearchFilters(
                order = uiState.order,
                reverse = uiState.reverse,
                onOrderChange = viewModel::onOrderChange,
                onReverseChange = viewModel::onReverseChange,
                isGridView = uiState.isGridView,
                onGridViewChange = viewModel::onGridViewChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        }

        when {
            uiState.isLoading -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            uiState.error != null -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "Something went wrong. Please try again.")
                    }
                }
            }

            uiState.stations.isEmpty() && uiState.isSearchActive -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "No stations found for \"${uiState.searchQuery}\"")
                    }
                }
            }

            else -> {
                itemsIndexed(
                    items = uiState.stations,
                    key = { _, it -> it.stationUuid },
                ) { index, station ->
                    val source = PlaybackSource.Browse(
                        name = uiState.searchQuery,
                        countryCode = uiState.selectedCountryCode,
                        language = uiState.selectedLanguage,
                        tagList = uiState.selectedTags.joinToString(","),
                        order = uiState.order,
                        reverse = uiState.reverse
                    )
                    if (uiState.isGridView) {
                        StationCard(
                            station = station,
                            onClick = { onStationClick(uiState.stations, index, source) },
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
                            onClick = { onStationClick(uiState.stations, index, source) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(),
                            isCurrentlyPlaying = playingStationUuid == station.stationUuid,
                            isPlaybackActive = isPlaybackActive,
                            isFavorite = libraryStationUuids.contains(station.stationUuid)
                        )
                    }
                }

                if (uiState.isNextPageLoading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }

        AnimatedVisibility(
            visible = showScrollToTop,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp + contentPadding.calculateBottomPadding(), end = 16.dp),
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
                    contentDescription = "Scroll to top"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchFilters(
    order: String,
    reverse: Boolean,
    onOrderChange: (String) -> Unit,
    onReverseChange: (Boolean) -> Unit,
    isGridView: Boolean,
    onGridViewChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val orderOptions = listOf(
        "votes" to stringResource(R.string.votes),
        "clickcount" to stringResource(R.string.clicks),
        "clicktrend" to stringResource(R.string.trend),
        "name" to stringResource(R.string.name)
    )
    var orderExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = { onGridViewChange(!isGridView) }) {
            Icon(
                imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                contentDescription = stringResource(R.string.toggle_view)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                FilterChip(
                    selected = false,
                    onClick = { orderExpanded = true },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = orderOptions.find { it.first == order }?.second ?: order,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (reverse) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                contentDescription = if (reverse) stringResource(R.string.descending) else stringResource(R.string.ascending),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Transparent,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    border = null
                )
                DropdownMenu(
                    expanded = orderExpanded,
                    onDismissRequest = { orderExpanded = false }
                ) {
                    orderOptions.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                if (order == value) {
                                    onReverseChange(!reverse)
                                } else {
                                    onOrderChange(value)
                                }
                                orderExpanded = false
                            },
                            trailingIcon = {
                                if (order == value) {
                                    Icon(
                                        imageVector = if (reverse) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                        contentDescription = if (reverse) "Descending" else "Ascending",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
