package com.armanmaurya.internetradio.ui.mobile.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
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
import com.armanmaurya.internetradio.ui.shared.viewmodels.ImportPart
import com.armanmaurya.internetradio.ui.shared.viewmodels.ImportStationItem
import com.armanmaurya.internetradio.ui.shared.viewmodels.ImportUiState
import com.armanmaurya.internetradio.ui.shared.viewmodels.ImportViewModel
import com.armanmaurya.internetradio.ui.shared.viewmodels.StationParts

/**
 * Review of the picked backup file(s) before anything is saved: what to bring in for each
 * station, with shortcuts to include or leave out track history and starred tracks everywhere.
 */
@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    onClose: () -> Unit,
    onPlayStation: (RadioStation) -> Unit,
    playingStationUuid: String?,
    isPlaybackActive: Boolean,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val state by viewModel.uiState.collectAsState()

    // Both the finished event and the idle state can ask to close; only leave once.
    var closed by remember { mutableStateOf(false) }
    fun close() {
        if (!closed) {
            closed = true
            onClose()
        }
    }

    // Back to idle means the import finished, failed, found nothing or was cancelled; Settings
    // shows how it went. Also covers the app being restarted on this screen.
    LaunchedEffect(state) {
        if (state == ImportUiState.Idle) close()
    }

    val review = state as? ImportUiState.Review
    val isImporting = review?.isImporting == true
    fun cancel() {
        if (!isImporting) {
            viewModel.cancel()
            close()
        }
    }
    BackHandler { cancel() }

    StationPickerScaffold(
        title = stringResource(R.string.import_title),
        onClose = ::cancel,
        closeEnabled = !isImporting,
        stationCount = review?.stations?.size ?: 0,
        action = review?.let {
            StationPickerAction(
                text = pluralStringResource(R.plurals.import_button, it.selectedCount, it.selectedCount),
                enabled = it.selectedCount > 0,
                isWorking = it.isImporting,
                onClick = viewModel::import
            )
        },
        contentPadding = contentPadding
    ) { innerPadding, searchQuery ->
        if (review != null) {
            ReviewList(
                review = review,
                viewModel = viewModel,
                playback = StationPlayback(onPlayStation, playingStationUuid, isPlaybackActive),
                searchQuery = searchQuery,
                contentPadding = innerPadding
            )
        }
    }
}

@Composable
private fun ReviewList(
    review: ImportUiState.Review,
    viewModel: ImportViewModel,
    playback: StationPlayback,
    searchQuery: String,
    contentPadding: PaddingValues
) {
    val enabled = !review.isImporting
    val isSearching = searchQuery.isNotEmpty()
    fun List<ImportStationItem>.matching() =
        if (isSearching) filter { it.name.contains(searchQuery, ignoreCase = true) } else this
    val newStations = review.newStations.matching()
    val existingStations = review.existingStations.matching()

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
        if (review.failedFiles > 0) {
            item(key = "failed") {
                Text(
                    text = pluralStringResource(R.plurals.settings_imported_failed, review.failedFiles, review.failedFiles),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        // Shortcuts for every station at once, styled like the Settings switches.
        val options = buildList<@Composable (Shape) -> Unit> {
            if (review.hasHistory) add { shape ->
                ToggleItem(
                    title = stringResource(R.string.import_part_history),
                    icon = Icons.Default.History,
                    isEnabled = review.partState(ImportPart.HISTORY) == ToggleableState.On,
                    onToggle = { viewModel.togglePartForAll(ImportPart.HISTORY) },
                    shape = shape,
                    enabled = enabled
                )
            }
            if (review.hasStarred) add { shape ->
                ToggleItem(
                    title = stringResource(R.string.import_part_starred),
                    icon = Icons.Default.Star,
                    isEnabled = review.partState(ImportPart.STARRED) == ToggleableState.On,
                    onToggle = { viewModel.togglePartForAll(ImportPart.STARRED) },
                    shape = shape,
                    enabled = enabled
                )
            }
            if (review.exceedsHistoryLimit) add { shape ->
                ToggleItem(
                    title = stringResource(R.string.import_keep_all_plays),
                    subtitle = stringResource(R.string.import_keep_all_plays_desc, review.historyLimit, review.playsNeeded),
                    icon = Icons.Default.Storage,
                    isEnabled = review.keepAllPlays,
                    onToggle = viewModel::setKeepAllPlays,
                    shape = shape,
                    enabled = enabled
                )
            }
        }
        // While searching, only the matching stations are shown.
        if (options.isNotEmpty() && !isSearching) {
            item(key = "options") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    options.forEachIndexed { index, option -> option(groupShape(index, options.size)) }
                }
            }
        }

        stationSection(
            title = R.string.import_section_new,
            stations = newStations,
            sectionState = review.sectionState(existing = false),
            showSelectAll = !isSearching,
            enabled = enabled,
            onToggleSection = { viewModel.toggleSection(existing = false) },
            viewModel = viewModel,
            playback = playback
        )
        stationSection(
            title = R.string.import_section_existing,
            stations = existingStations,
            sectionState = review.sectionState(existing = true),
            showSelectAll = !isSearching,
            enabled = enabled,
            onToggleSection = { viewModel.toggleSection(existing = true) },
            viewModel = viewModel,
            playback = playback
        )
        if (isSearching && newStations.isEmpty() && existingStations.isEmpty()) {
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

private fun LazyListScope.stationSection(
    title: Int,
    stations: List<ImportStationItem>,
    sectionState: ToggleableState,
    /** Select all acts on the whole section, so it is hidden while a search hides some of it. */
    showSelectAll: Boolean,
    enabled: Boolean,
    onToggleSection: () -> Unit,
    viewModel: ImportViewModel,
    playback: StationPlayback
) {
    if (stations.isEmpty()) return

    // Header only; the cards are separate items so long lists stay lazy.
    item(key = "header_$title") {
        Section(
            title = "${stringResource(title)} · ${stations.size}",
            trailingContent = if (showSelectAll) {
                { TriStateCheckbox(state = sectionState, onClick = onToggleSection, enabled = enabled) }
            } else null
        ) {}
    }

    items(stations, key = { it.stationUuid }) { item ->
        StationListCard(
            station = item.station,
            // Like everywhere else in the app, tapping a station plays it, so it can be tried
            // before importing; the checkbox and chips do the selecting.
            onClick = { playback.onPlay(item.station) },
            isCurrentlyPlaying = playback.playingStationUuid == item.stationUuid,
            isPlaybackActive = playback.isPlaybackActive,
            trailingContent = {
                TriStateCheckbox(
                    state = item.state,
                    onClick = { viewModel.toggleStation(item.stationUuid) },
                    enabled = enabled,
                    // The card plays the station; name the checkbox so screen readers say which one it picks.
                    modifier = Modifier.semantics { contentDescription = item.name }
                )
            },
            footerContent = if (item.availableParts.size > 1 || item.isInLibrary) {
                {
                    PartChips(
                        item = item,
                        stationLabel = stringResource(
                            if (item.isInLibrary) R.string.import_part_station_existing else R.string.import_part_station
                        ),
                        enabled = enabled,
                        onSetPart = { part, checked -> viewModel.setPart(item.stationUuid, part, checked) }
                    )
                }
            } else null
        )
    }
}

/** One chip per part in [parts]; tapping one changes only that part. Shared with the export screen. */
@Composable
internal fun PartChips(
    item: StationParts,
    enabled: Boolean,
    parts: List<ImportPart> = item.availableParts,
    stationLabel: String = stringResource(R.string.import_part_station),
    onSetPart: (ImportPart, Boolean) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, bottom = 4.dp)
    ) {
        parts.forEach { part ->
            val selected = part in item.selected
            FilterChip(
                selected = selected,
                onClick = { onSetPart(part, !selected) },
                enabled = enabled && item.canChoose(part),
                label = {
                    Text(
                        when (part) {
                            ImportPart.STATION -> stationLabel
                            ImportPart.HISTORY -> pluralStringResource(R.plurals.import_plays, item.playCount, item.playCount)
                            ImportPart.STARRED -> pluralStringResource(R.plurals.import_starred, item.starredCount, item.starredCount)
                        }
                    )
                },
                leadingIcon = if (selected) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else null
            )
        }
    }
}
