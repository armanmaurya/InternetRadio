package com.armanmaurya.internetradio.ui.tv.screens.recent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.ui.tv.components.StationCard
import com.armanmaurya.internetradio.ui.shared.viewmodels.RecentViewModel
import com.armanmaurya.internetradio.ui.shared.viewmodels.LibraryViewModel

@Composable
fun RecentScreen(
    viewModel: RecentViewModel,
    playingStationUuid: String?,
    isPlaybackActive: Boolean,
    onStationClick: (List<RadioStation>, Int, String) -> Unit,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val stations by viewModel.recentStations.collectAsStateWithLifecycle()
    val libraryUuids by libraryViewModel.stationUuids.collectAsStateWithLifecycle()
    val useFilter by viewModel.useFilter.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Button(
                        onClick = { viewModel.toggleFilter() },
                        colors = ButtonDefaults.colors(
                            containerColor = if (useFilter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (useFilter) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = if (useFilter) Icons.Default.Close else Icons.Default.FilterList,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(if (useFilter) "Filters Active" else "Use Filters")
                    }
                }
            }

            if (stations.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                        Text("No recent stations", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                itemsIndexed(
                    items = stations,
                    key = { _, station -> station.stationUuid }
                ) { index, station ->
                    StationCard(
                        station = station,
                        onClick = { onStationClick(stations, index, "tv_recent") },
                        isCurrentlyPlaying = station.stationUuid == playingStationUuid,
                        isPlaybackActive = isPlaybackActive && station.stationUuid == playingStationUuid,
                        isFavorite = station.stationUuid in libraryUuids
                    )
                }
            }
        }
    }
}
