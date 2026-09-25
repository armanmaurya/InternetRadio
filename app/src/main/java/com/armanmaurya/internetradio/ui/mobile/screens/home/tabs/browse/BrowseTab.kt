@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.model.PlaybackSource
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.StationCard
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.StationListCard
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.automirrored.filled.ViewList
import com.armanmaurya.internetradio.domain.model.RecordingSession
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.ToggleChip
import com.armanmaurya.internetradio.ui.shared.viewmodels.BrowseViewModel

@Composable
fun BrowseContent(
    onStationClick: (List<RadioStation>, Int, PlaybackSource) -> Unit,
    onEditStation: (String) -> Unit,
    onExportStation: ((RadioStation) -> Unit)? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: BrowseViewModel = hiltViewModel(),
    playingStationUuid: String? = null,
    isPlaybackActive: Boolean = false,
    activeSessions: Map<String, RecordingSession> = emptyMap(),
    onToggleRecording: (RadioStation) -> Unit = {}
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
                isVerified = uiState.isVerified,
                onVerifiedChange = viewModel::onVerifiedChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp)
            )
        }

        when {
            uiState.isLoading -> { /* handled below */ }
            uiState.error != null -> { /* handled below */ }
            uiState.stations.isEmpty() && uiState.isSearchActive -> { /* handled below */ }
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
                    
                    val session = activeSessions[station.stationUuid]
                    val duration by (session?.durationSeconds ?: kotlinx.coroutines.flow.flowOf(0L)).collectAsStateWithLifecycle(initialValue = 0L)
                    
                    if (uiState.isGridView) {
                        StationCard(
                            station = station,
                            onClick = { onStationClick(uiState.stations, index, source) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(),
                            onExportClick = { onExportStation?.invoke(station) },
                            onToggleFavoriteClick = { viewModel.toggleLibrary(station) },
                            onEditClick = if (libraryStationUuids.contains(station.stationUuid)) { { onEditStation(station.stationUuid) } } else null,
                            isCurrentlyPlaying = playingStationUuid == station.stationUuid,
                            isPlaybackActive = isPlaybackActive,
                            isFavorite = libraryStationUuids.contains(station.stationUuid),
                            isRecording = session != null,
                            recordingDuration = duration,
                            onRecordClick = { onToggleRecording(station) },
                            onStopRecordingClick = if (session != null) { { onToggleRecording(station) } } else null
                        )
                    } else {
                        StationListCard(
                            station = station,
                            onClick = { onStationClick(uiState.stations, index, source) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(),
                            onExportClick = { onExportStation?.invoke(station) },
                            onToggleFavoriteClick = { viewModel.toggleLibrary(station) },
                            onEditClick = if (libraryStationUuids.contains(station.stationUuid)) { { onEditStation(station.stationUuid) } } else null,
                            isCurrentlyPlaying = playingStationUuid == station.stationUuid,
                            isPlaybackActive = isPlaybackActive,
                            isFavorite = libraryStationUuids.contains(station.stationUuid),
                            isRecording = session != null,
                            recordingDuration = duration,
                            onRecordClick = { onToggleRecording(station) },
                            onStopRecordingClick = if (session != null) { { onToggleRecording(station) } } else null
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
                            LoadingIndicator(modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }

        // Overlay: centered loading / error / empty states
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        } else if (uiState.error != null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.error_something_went_wrong),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.retry() }) {
                    Text(text = stringResource(R.string.action_retry))
                }
            }
        } else if (uiState.stations.isEmpty() && uiState.isSearchActive) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.home_no_stations_found_for, uiState.searchQuery),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = showScrollToTop,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp + contentPadding.calculateBottomPadding(), end = 16.dp),
            enter = scaleIn() + fadeIn() + androidx.compose.animation.slideIn(initialOffset = { androidx.compose.ui.unit.IntOffset(it.width, it.height) }),
            exit = scaleOut() + fadeOut() + androidx.compose.animation.slideOut(targetOffset = { androidx.compose.ui.unit.IntOffset(it.width, it.height) })
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
    isVerified: Boolean,
    onVerifiedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val orderOptions = listOf(
        "votes" to stringResource(R.string.home_votes),
        "clickcount" to stringResource(R.string.home_clicks),
        "clicktrend" to stringResource(R.string.home_trend),
        "name" to stringResource(R.string.general_name)
    )
    var orderExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onGridViewChange(!isGridView) }
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
                text = stringResource(R.string.home_verified),
                onClick = { onVerifiedChange(!isVerified) },
                isActive = isVerified,
                leadingIcon = Icons.Default.FilterList,
                trailingIcon = if (isVerified) Icons.Default.Check else null
            )

            Box {
                ToggleChip(
                    text = orderOptions.find { it.first == order }?.second ?: order,
                    onClick = { orderExpanded = !orderExpanded },
                    leadingContent = {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(
                                id = if (reverse) R.drawable.ic_sort_down else R.drawable.ic_sort_up
                            ),
                            contentDescription = if (reverse) stringResource(R.string.home_descending) else stringResource(R.string.home_ascending),
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    trailingIcon = if (orderExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    trailingIconContentDescription = if (orderExpanded) "Close sort menu" else "Open sort menu"
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
                                        painter = androidx.compose.ui.res.painterResource(
                                            id = if (reverse) R.drawable.ic_sort_down else R.drawable.ic_sort_up
                                        ),
                                        contentDescription = if (reverse) stringResource(R.string.home_cd_descending) else stringResource(R.string.home_cd_ascending),
                                        modifier = Modifier.size(20.dp),
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

