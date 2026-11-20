package com.armanmaurya.internetradio.ui.tv.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Text
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.ui.shared.viewmodels.ExportUiState
import com.armanmaurya.internetradio.ui.shared.viewmodels.ExportViewModel
import com.armanmaurya.internetradio.ui.shared.viewmodels.ImportPart
import com.armanmaurya.internetradio.ui.shared.viewmodels.STATION_SEARCH_THRESHOLD

/**
 * TV version of the export screen: the Export action at the top, then history and starred
 * tracks for every station, then the library. OK on a station opens its page, where it can be
 * played and its parts chosen, like on the import page.
 * [onPickFile] opens the system file picker; the ViewModel writes once a file is chosen.
 */
@Composable
internal fun TvExportPage(
    viewModel: ExportViewModel,
    onPickFile: () -> Unit,
    onClose: () -> Unit,
    onPlayStation: (RadioStation) -> Unit,
    playingStationUuid: String?,
    isPlaybackActive: Boolean
) {
    val state by viewModel.uiState.collectAsState()

    var closed by remember { mutableStateOf(false) }
    fun close() {
        if (!closed) {
            closed = true
            onClose()
        }
    }
    LaunchedEffect(state) {
        if (state == ExportUiState.Idle) close()
    }

    val choosing = state as? ExportUiState.Choosing
    if (choosing == null) {
        SettingsColumn(title = stringResource(R.string.export_title)) {}
        return
    }

    // Swallow Back while writing so the file isn't left half done.
    BackHandler(enabled = choosing.isExporting) {}
    val enabled = !choosing.isExporting

    var openStationUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val isSearching = searchQuery.isNotEmpty()
    val stations = if (isSearching) {
        choosing.stations.filter { it.name.contains(searchQuery, ignoreCase = true) }
    } else choosing.stations
    var returnFocusTo by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    val openStation = choosing.stations.firstOrNull { it.stationUuid == openStationUuid }
    if (openStation != null) {
        BackHandler {
            returnFocusTo = openStation.stationUuid
            openStationUuid = null
        }
        StationPartsPage(
            station = openStation,
            stationDetail = null,
            enabled = enabled,
            isPlaying = isPlaybackActive && playingStationUuid == openStation.stationUuid,
            onPlay = { onPlayStation(openStation.station) },
            onSetPart = { part, checked ->
                if (part == ImportPart.STATION) {
                    if (checked != openStation.isPicked) viewModel.toggleStation(openStation.stationUuid)
                } else {
                    viewModel.setPart(openStation.stationUuid, part, checked)
                }
            }
        )
        return
    }

    val exportFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (returnFocusTo == null) {
            try {
                exportFocus.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    SettingsColumn(title = stringResource(R.string.export_title)) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "export") {
                ListItem(
                    selected = false,
                    enabled = choosing.selectedCount > 0 && enabled,
                    onClick = onPickFile,
                    headlineContent = {
                        Text(pluralStringResource(R.plurals.export_button, choosing.selectedCount, choosing.selectedCount))
                    },
                    leadingContent = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                    scale = ListItemDefaults.scale(focusedScale = 1.02f),
                    modifier = Modifier.fillMaxWidth().focusRequester(exportFocus)
                )
            }
            // The "for every station" switches act on stations a search hides, so they wait.
            if (choosing.hasHistory && !isSearching) {
                item(key = "history") {
                    SettingsToggleRow(
                        title = stringResource(R.string.import_part_history),
                        icon = Icons.Default.History,
                        checked = choosing.partState(ImportPart.HISTORY) == ToggleableState.On,
                        onCheckedChange = { viewModel.togglePartForAll(ImportPart.HISTORY) }
                    )
                }
            }
            if (choosing.hasStarred && !isSearching) {
                item(key = "starred") {
                    SettingsToggleRow(
                        title = stringResource(R.string.import_part_starred),
                        icon = Icons.Default.Star,
                        checked = choosing.partState(ImportPart.STARRED) == ToggleableState.On,
                        onCheckedChange = { viewModel.togglePartForAll(ImportPart.STARRED) }
                    )
                }
            }

            if (choosing.stations.size > STATION_SEARCH_THRESHOLD) {
                item(key = "search") {
                    TvSearchRow(query = searchQuery, onQueryChange = { searchQuery = it })
                }
            }

            item(key = "header") {
                SettingsSectionHeader("${stringResource(R.string.export_section_library)} · ${stations.size}")
            }
            if (!isSearching) item(key = "select_all") {
                ListItem(
                    selected = false,
                    enabled = enabled,
                    onClick = viewModel::toggleAll,
                    headlineContent = { Text(stringResource(R.string.import_select_all)) },
                    trailingContent = { CheckIcon(choosing.allState) },
                    scale = ListItemDefaults.scale(focusedScale = 1.02f),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            items(stations, key = { it.stationUuid }) { station ->
                val focusRequester = remember { FocusRequester() }
                if (station.stationUuid == returnFocusTo) {
                    LaunchedEffect(Unit) {
                        try {
                            focusRequester.requestFocus()
                        } catch (_: Exception) {
                        }
                        returnFocusTo = null
                    }
                }
                ListItem(
                    selected = false,
                    onClick = { openStationUuid = station.stationUuid },
                    headlineContent = { Text(station.name, maxLines = 1) },
                    leadingContent = if (isPlaybackActive && station.stationUuid == playingStationUuid) {
                        { Icon(Icons.Default.GraphicEq, contentDescription = stringResource(R.string.import_cd_playing)) }
                    } else null,
                    supportingContent = { Text(selectionSummary(station), maxLines = 1) },
                    trailingContent = { CheckIcon(station.state) },
                    scale = ListItemDefaults.scale(focusedScale = 1.02f),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                )
            }
            if (isSearching && stations.isEmpty()) {
                item(key = "no_matches") { NoMatches() }
            }
        }
    }
}
