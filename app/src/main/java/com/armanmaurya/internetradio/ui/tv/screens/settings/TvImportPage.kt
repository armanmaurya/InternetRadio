package com.armanmaurya.internetradio.ui.tv.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.IndeterminateCheckBox
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Storage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.ui.shared.viewmodels.ImportPart
import com.armanmaurya.internetradio.ui.shared.viewmodels.ImportStationItem
import com.armanmaurya.internetradio.ui.shared.viewmodels.ImportUiState
import com.armanmaurya.internetradio.ui.shared.viewmodels.ImportViewModel
import com.armanmaurya.internetradio.ui.shared.viewmodels.STATION_SEARCH_THRESHOLD
import com.armanmaurya.internetradio.ui.shared.viewmodels.StationParts

/**
 * TV version of the import review. The Import action sits at the top so it is reachable without
 * scrolling past every station; OK on a station opens its page, where it can be played and its
 * parts chosen.
 */
@Composable
internal fun TvImportPage(
    viewModel: ImportViewModel,
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
        if (state == ImportUiState.Idle) close()
    }

    val review = state as? ImportUiState.Review
    if (review == null) {
        SettingsColumn(title = stringResource(R.string.import_title)) {}
        return
    }

    // Swallow Back while saving so the import isn't cut short.
    BackHandler(enabled = review.isImporting) {}

    var openStationUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val isSearching = searchQuery.isNotEmpty()
    fun matching(stations: List<ImportStationItem>) =
        if (isSearching) stations.filter { it.name.contains(searchQuery, ignoreCase = true) } else stations
    var returnFocusTo by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    val openStation = review.stations.firstOrNull { it.stationUuid == openStationUuid }
    if (openStation != null) {
        BackHandler {
            returnFocusTo = openStation.stationUuid
            openStationUuid = null
        }
        StationPartsPage(
            station = openStation,
            stationDetail = stringResource(
                if (openStation.isInLibrary) R.string.import_part_station_existing else R.string.import_part_station_new
            ),
            enabled = !review.isImporting,
            isPlaying = isPlaybackActive && playingStationUuid == openStation.stationUuid,
            onPlay = { onPlayStation(openStation.station) },
            onSetPart = { part, checked -> viewModel.setPart(openStation.stationUuid, part, checked) }
        )
        return
    }

    val importFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (returnFocusTo == null) {
            try {
                importFocus.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    SettingsColumn(title = stringResource(R.string.import_title)) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (review.failedFiles > 0) {
                item(key = "failed") {
                    Text(
                        text = pluralStringResource(R.plurals.settings_imported_failed, review.failedFiles, review.failedFiles),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )
                }
            }

            item(key = "import") {
                ListItem(
                    selected = false,
                    enabled = review.selectedCount > 0 && !review.isImporting,
                    onClick = viewModel::import,
                    headlineContent = {
                        Text(pluralStringResource(R.plurals.import_button, review.selectedCount, review.selectedCount))
                    },
                    leadingContent = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                    scale = ListItemDefaults.scale(focusedScale = 1.02f),
                    modifier = Modifier.fillMaxWidth().focusRequester(importFocus)
                )
            }

            // The "for every station" switches act on stations a search hides, so they wait.
            if (review.hasHistory && !isSearching) {
                item(key = "all_history") {
                    SettingsToggleRow(
                        title = stringResource(R.string.import_part_history),
                        icon = Icons.Default.History,
                        checked = review.partState(ImportPart.HISTORY) == ToggleableState.On,
                        onCheckedChange = { viewModel.togglePartForAll(ImportPart.HISTORY) }
                    )
                }
            }
            if (review.hasStarred && !isSearching) {
                item(key = "all_starred") {
                    SettingsToggleRow(
                        title = stringResource(R.string.import_part_starred),
                        icon = Icons.Default.Star,
                        checked = review.partState(ImportPart.STARRED) == ToggleableState.On,
                        onCheckedChange = { viewModel.togglePartForAll(ImportPart.STARRED) }
                    )
                }
            }
            if (review.exceedsHistoryLimit && !isSearching) {
                item(key = "limit") {
                    SettingsToggleRow(
                        title = stringResource(R.string.import_keep_all_plays),
                        description = stringResource(R.string.import_keep_all_plays_desc, review.historyLimit, review.playsNeeded),
                        icon = Icons.Default.Storage,
                        checked = review.keepAllPlays,
                        onCheckedChange = viewModel::setKeepAllPlays
                    )
                }
            }

            if (review.stations.size > STATION_SEARCH_THRESHOLD) {
                item(key = "search") {
                    TvSearchRow(query = searchQuery, onQueryChange = { searchQuery = it })
                }
            }

            val newStations = matching(review.newStations)
            val existingStations = matching(review.existingStations)
            tvStationSection(
                title = R.string.import_section_new,
                stations = newStations,
                showSelectAll = !isSearching,
                sectionState = review.sectionState(existing = false),
                returnFocusTo = returnFocusTo,
                playingUuid = playingStationUuid.takeIf { isPlaybackActive },
                onFocusRestored = { returnFocusTo = null },
                onToggleSection = { viewModel.toggleSection(existing = false) },
                onStationClick = { station -> openStationUuid = station.stationUuid }
            )
            tvStationSection(
                title = R.string.import_section_existing,
                stations = existingStations,
                showSelectAll = !isSearching,
                sectionState = review.sectionState(existing = true),
                returnFocusTo = returnFocusTo,
                playingUuid = playingStationUuid.takeIf { isPlaybackActive },
                onFocusRestored = { returnFocusTo = null },
                onToggleSection = { viewModel.toggleSection(existing = true) },
                onStationClick = { station -> openStationUuid = station.stationUuid }
            )
            if (isSearching && newStations.isEmpty() && existingStations.isEmpty()) {
                item(key = "no_matches") { NoMatches() }
            }
        }
    }
}

@Composable
internal fun NoMatches() {
    Text(
        text = stringResource(R.string.import_no_matches),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp)
    )
}

private fun LazyListScope.tvStationSection(
    title: Int,
    stations: List<ImportStationItem>,
    /** Select all acts on the whole section, so it is hidden while a search hides some of it. */
    showSelectAll: Boolean,
    sectionState: ToggleableState,
    returnFocusTo: String?,
    /** The station playing right now, marked in the list. */
    playingUuid: String?,
    onFocusRestored: () -> Unit,
    onToggleSection: () -> Unit,
    onStationClick: (ImportStationItem) -> Unit
) {
    if (stations.isEmpty()) return

    item(key = "header_$title") {
        SettingsSectionHeader("${stringResource(title)} · ${stations.size}")
    }
    if (showSelectAll) item(key = "select_all_$title") {
        ListItem(
            selected = false,
            onClick = onToggleSection,
            headlineContent = { Text(stringResource(R.string.import_select_all)) },
            trailingContent = { CheckIcon(sectionState) },
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
                onFocusRestored()
            }
        }
        ListItem(
            selected = false,
            onClick = { onStationClick(station) },
            headlineContent = { Text(station.name, maxLines = 1) },
            leadingContent = if (station.stationUuid == playingUuid) {
                { Icon(Icons.Default.GraphicEq, contentDescription = stringResource(R.string.import_cd_playing)) }
            } else null,
            supportingContent = { Text(selectionSummary(station), maxLines = 1) },
            trailingContent = { CheckIcon(station.state) },
            scale = ListItemDefaults.scale(focusedScale = 1.02f),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
        )
    }
}

/**
 * One station: try it with Play, then toggle each part with OK. Back returns to the list.
 * Shared with the export page; [stationDetail] is shown under the Station row.
 */
@Composable
internal fun StationPartsPage(
    station: StationParts,
    stationDetail: String?,
    enabled: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onSetPart: (ImportPart, Boolean) -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            firstFocus.requestFocus()
        } catch (_: Exception) {
        }
    }

    SettingsColumn(title = station.name) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "play") {
                ListItem(
                    selected = false,
                    onClick = onPlay,
                    headlineContent = {
                        Text(stringResource(if (isPlaying) R.string.player_cd_pause else R.string.player_cd_play))
                    },
                    leadingContent = {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                    },
                    scale = ListItemDefaults.scale(focusedScale = 1.02f),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            items(station.availableParts, key = { it.name }) { part ->
                val checked = part in station.selected
                ListItem(
                    selected = false,
                    enabled = enabled && station.canChoose(part),
                    onClick = { onSetPart(part, !checked) },
                    headlineContent = { Text(partTitle(part)) },
                    supportingContent = (if (part == ImportPart.STATION) stationDetail else partDetail(part, station))
                        ?.let { detail -> { Text(detail) } },
                    leadingContent = { Icon(partIcon(part), contentDescription = null) },
                    trailingContent = { CheckIcon(if (checked) ToggleableState.On else ToggleableState.Off) },
                    scale = ListItemDefaults.scale(focusedScale = 1.02f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (part == station.availableParts.first()) Modifier.focusRequester(firstFocus) else Modifier)
                )
            }
        }
    }
}

/** Checkbox look for TV rows, which have no checkbox of their own. */
@Composable
internal fun CheckIcon(state: ToggleableState) {
    Box(contentAlignment = Alignment.Center) {
        Icon(
            imageVector = when (state) {
                ToggleableState.On -> Icons.Default.CheckBox
                ToggleableState.Indeterminate -> Icons.Default.IndeterminateCheckBox
                ToggleableState.Off -> Icons.Default.CheckBoxOutlineBlank
            },
            contentDescription = null
        )
    }
}

/** What will be imported for this station, e.g. "Station · 40 plays · 3 starred". */
@Composable
internal fun selectionSummary(station: StationParts): String {
    if (station.state == ToggleableState.Off) return stringResource(R.string.import_not_selected)
    return station.availableParts.filter { it in station.selected }.map { part ->
        when (part) {
            ImportPart.STATION -> stringResource(R.string.import_part_station)
            ImportPart.HISTORY -> pluralStringResource(R.plurals.import_plays, station.playCount, station.playCount)
            ImportPart.STARRED -> pluralStringResource(R.plurals.import_starred, station.starredCount, station.starredCount)
        }
    }.joinToString(" · ")
}

@Composable
private fun partTitle(part: ImportPart): String = stringResource(
    when (part) {
        ImportPart.STATION -> R.string.import_part_station
        ImportPart.HISTORY -> R.string.import_part_history
        ImportPart.STARRED -> R.string.import_part_starred
    }
)

@Composable
private fun partDetail(part: ImportPart, station: StationParts): String? = when (part) {
    ImportPart.STATION -> null
    ImportPart.HISTORY -> pluralStringResource(R.plurals.import_plays, station.playCount, station.playCount)
    ImportPart.STARRED -> pluralStringResource(R.plurals.import_starred, station.starredCount, station.starredCount)
}

private fun partIcon(part: ImportPart): ImageVector = when (part) {
    ImportPart.STATION -> Icons.Default.Radio
    ImportPart.HISTORY -> Icons.Default.History
    ImportPart.STARRED -> Icons.Default.Star
}
