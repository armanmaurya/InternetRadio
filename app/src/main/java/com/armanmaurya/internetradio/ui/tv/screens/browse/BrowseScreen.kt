package com.armanmaurya.internetradio.ui.tv.screens.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.ui.tv.components.StationCard
import com.armanmaurya.internetradio.ui.shared.viewmodels.BrowseViewModel
import com.armanmaurya.internetradio.ui.shared.viewmodels.LibraryViewModel

@Composable
fun BrowseScreen(
    viewModel: BrowseViewModel,
    playingStationUuid: String?,
    isPlaybackActive: Boolean,
    onStationClick: (List<RadioStation>, Int, String) -> Unit,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val libraryUuids by libraryViewModel.stationUuids.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
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

        if (uiState.stations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (uiState.isLoading) "Loading..." else "No stations found")
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp, end = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val orderOptions = listOf(
                        "votes" to stringResource(R.string.home_votes),
                        "clickcount" to stringResource(R.string.home_clicks),
                        "clicktrend" to stringResource(R.string.home_trend),
                        "name" to stringResource(R.string.general_name)
                    )
                    var orderExpanded by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Box {
                            Button(
                                onClick = { orderExpanded = true },
                                colors = ButtonDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = orderOptions.find { it.first == uiState.order }?.second ?: uiState.order,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (uiState.reverse) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                    contentDescription = if (uiState.reverse) stringResource(R.string.home_descending) else stringResource(R.string.home_ascending),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = orderExpanded,
                                onDismissRequest = { orderExpanded = false }
                            ) {
                                orderOptions.forEach { (value, label) ->
                                    DropdownMenuItem(
                                        text = { androidx.compose.material3.Text(label) },
                                        onClick = {
                                            if (uiState.order == value) {
                                                viewModel.onReverseChange(!uiState.reverse)
                                            } else {
                                                viewModel.onOrderChange(value)
                                            }
                                            orderExpanded = false
                                        },
                                        trailingIcon = {
                                            if (uiState.order == value) {
                                                androidx.compose.material3.Icon(
                                                    imageVector = if (uiState.reverse) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                                    contentDescription = if (uiState.reverse) "Descending" else "Ascending",
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

                itemsIndexed(
                    items = uiState.stations,
                    key = { _, station -> station.stationUuid }
                ) { index, station ->
                    StationCard(
                        station = station,
                        onClick = { onStationClick(uiState.stations, index, "tv_browse") },
                        isCurrentlyPlaying = station.stationUuid == playingStationUuid,
                        isPlaybackActive = isPlaybackActive && station.stationUuid == playingStationUuid,
                        isFavorite = station.stationUuid in libraryUuids
                    )
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
}
