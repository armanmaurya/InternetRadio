package com.armanmaurya.internetradio.ui.mobile.screens.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.StationListCard
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.Section
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.StationPickerAction
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.StationPickerScaffold
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.StationPlayback
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.ToggleItem
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.groupShape
import com.armanmaurya.internetradio.ui.shared.viewmodels.ExportUiState
import com.armanmaurya.internetradio.ui.shared.viewmodels.ExportViewModel
import com.armanmaurya.internetradio.ui.shared.viewmodels.ImportPart

/**
 * Choose which library stations go into a backup file, and whether their track history and
 * starred tracks go along. Laid out like the import screen.
 */
@Composable
fun ExportScreen(
    viewModel: ExportViewModel,
    onClose: () -> Unit,
    onPlayStation: (RadioStation) -> Unit,
    playingStationUuid: String?,
    isPlaybackActive: Boolean,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val state by viewModel.uiState.collectAsState()

    var closed by remember { mutableStateOf(false) }
    fun close() {
        if (!closed) {
            closed = true
            onClose()
        }
    }
    // Back to idle means the export finished or was cancelled; Settings shows how it went.
    LaunchedEffect(state) {
        if (state == ExportUiState.Idle) close()
    }

    val choosing = state as? ExportUiState.Choosing
    val isExporting = choosing?.isExporting == true
    fun cancel() {
        if (!isExporting) {
            viewModel.cancel()
            close()
        }
    }
    BackHandler { cancel() }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let(viewModel::export)
    }

    StationPickerScaffold(
        title = stringResource(R.string.export_title),
        onClose = ::cancel,
        closeEnabled = !isExporting,
        stationCount = choosing?.stations?.size ?: 0,
        action = choosing?.let {
            StationPickerAction(
                text = pluralStringResource(R.plurals.export_button, it.selectedCount, it.selectedCount),
                enabled = it.selectedCount > 0,
                isWorking = it.isExporting,
                onClick = { fileLauncher.launch("stations.json") }
            )
        },
        contentPadding = contentPadding
    ) { innerPadding, searchQuery ->
        if (choosing != null) {
            ExportList(
                state = choosing,
                viewModel = viewModel,
                playback = StationPlayback(onPlayStation, playingStationUuid, isPlaybackActive),
                searchQuery = searchQuery,
                contentPadding = innerPadding
            )
        }
    }
}

@Composable
private fun ExportList(
    state: ExportUiState.Choosing,
    viewModel: ExportViewModel,
    playback: StationPlayback,
    searchQuery: String,
    contentPadding: PaddingValues
) {
    val enabled = !state.isExporting
    val isSearching = searchQuery.isNotEmpty()
    val stations = if (isSearching) state.stations.filter { it.name.contains(searchQuery, ignoreCase = true) } else state.stations

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp
        )
    ) {
        val options = buildList<@Composable (Shape) -> Unit> {
            if (state.hasHistory) add { shape ->
                ToggleItem(
                    title = stringResource(R.string.import_part_history),
                    icon = Icons.Default.History,
                    isEnabled = state.partState(ImportPart.HISTORY) == ToggleableState.On,
                    onToggle = { viewModel.togglePartForAll(ImportPart.HISTORY) },
                    shape = shape,
                    enabled = enabled
                )
            }
            if (state.hasStarred) add { shape ->
                ToggleItem(
                    title = stringResource(R.string.import_part_starred),
                    icon = Icons.Default.Star,
                    isEnabled = state.partState(ImportPart.STARRED) == ToggleableState.On,
                    onToggle = { viewModel.togglePartForAll(ImportPart.STARRED) },
                    shape = shape,
                    enabled = enabled
                )
            }
        }
        if (options.isNotEmpty() && !isSearching) {
            item(key = "options") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    options.forEachIndexed { index, option -> option(groupShape(index, options.size)) }
                }
            }
        }

        item(key = "header") {
            Section(
                title = "${stringResource(R.string.export_section_library)} · ${stations.size}",
                // Select all acts on the whole library, so it is hidden while a search hides some of it.
                trailingContent = if (!isSearching) {
                    { TriStateCheckbox(state = state.allState, onClick = viewModel::toggleAll, enabled = enabled) }
                } else null
            ) {}
        }

        items(stations, key = { it.stationUuid }) { item ->
            StationListCard(
                station = item.station,
                onClick = { playback.onPlay(item.station) },
                isCurrentlyPlaying = playback.playingStationUuid == item.stationUuid,
                isPlaybackActive = playback.isPlaybackActive,
                trailingContent = {
                    Checkbox(
                        checked = item.isPicked,
                        onCheckedChange = { viewModel.toggleStation(item.stationUuid) },
                        enabled = enabled,
                        // The card plays the station; name the checkbox so screen readers say which one it picks.
                        modifier = Modifier.semantics { contentDescription = item.name }
                    )
                },
                // The checkbox picks the station; chips choose what goes along with it.
                footerContent = if (item.availableParts.size > 1) {
                    {
                        PartChips(
                            item = item,
                            parts = item.availableParts - ImportPart.STATION,
                            enabled = enabled,
                            onSetPart = { part, checked -> viewModel.setPart(item.stationUuid, part, checked) }
                        )
                    }
                } else null
            )
        }

        if (isSearching && stations.isEmpty()) {
            item(key = "no_matches") {
                Text(
                    text = stringResource(R.string.import_no_matches),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp)
                )
            }
        }
    }
}
