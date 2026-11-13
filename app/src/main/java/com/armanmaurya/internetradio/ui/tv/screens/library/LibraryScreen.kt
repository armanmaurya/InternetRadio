package com.armanmaurya.internetradio.ui.tv.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import android.view.ViewConfiguration
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.LibrarySortOption
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.ui.tv.components.StationCard
import com.armanmaurya.internetradio.ui.tv.components.TvSelectionDialog
import com.armanmaurya.internetradio.ui.tv.components.TvSelectionOption
import com.armanmaurya.internetradio.ui.shared.viewmodels.LibraryViewModel

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    playingStationUuid: String?,
    isPlaybackActive: Boolean,
    onStationClick: (List<RadioStation>, Int, String) -> Unit,
    onAddStation: () -> Unit,
    onEditStation: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val stations by viewModel.stations.collectAsStateWithLifecycle()
    val useFilter by viewModel.useFilter.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    var sortExpanded by remember { mutableStateOf(false) }

    var isDragLocked by remember { mutableStateOf(true) }
    var movingStationUuid by remember { mutableStateOf<String?>(null) }
    var currentStations by remember { mutableStateOf(stations ?: emptyList()) }
    val confirmGate = remember { ConfirmKeyGate() }
    val isReorderEnabled = sortOption == LibrarySortOption.CUSTOM && !isDragLocked

    LaunchedEffect(stations) {
        if (movingStationUuid == null) {
            currentStations = stations ?: emptyList()
        }
    }

    LaunchedEffect(sortOption) {
        if (sortOption != LibrarySortOption.CUSTOM) {
            if (movingStationUuid != null) {
                movingStationUuid = null
                viewModel.updateStationsOrder(currentStations)
            }
            isDragLocked = true
        }
    }

    BackHandler(enabled = movingStationUuid != null) {
        movingStationUuid = null
        viewModel.updateStationsOrder(currentStations)
    }

    DisposableEffect(Unit) {
        onDispose {
            if (movingStationUuid != null) {
                viewModel.updateStationsOrder(currentStations)
            }
        }
    }

    // Leaving for another app (or recent apps) mid-move: save now, since the app may be killed
    // in the background without this screen ever being disposed.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (movingStationUuid != null) {
            viewModel.updateStationsOrder(currentStations)
        }
    }

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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
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
                            Text(if (useFilter) stringResource(R.string.home_filters_active) else stringResource(R.string.home_use_filters))
                        }

                        val sortText = when (sortOption) {
                            LibrarySortOption.NAME_A_Z, LibrarySortOption.NAME_Z_A -> stringResource(R.string.home_sort_name)
                            LibrarySortOption.RECENTLY_ADDED, LibrarySortOption.OLDEST_ADDED -> stringResource(R.string.home_sort_added)
                            LibrarySortOption.RECENTLY_PLAYED, LibrarySortOption.LEAST_RECENTLY_PLAYED -> stringResource(R.string.home_sort_played)
                            LibrarySortOption.CUSTOM -> stringResource(R.string.home_sort_custom)
                        }
                        val hasDirection = sortOption != LibrarySortOption.CUSTOM
                        val isDescending = when (sortOption) {
                            LibrarySortOption.NAME_A_Z, LibrarySortOption.RECENTLY_ADDED, LibrarySortOption.RECENTLY_PLAYED -> true
                            else -> false
                        }

                        Box {
                            Button(
                                onClick = { sortExpanded = true },
                                colors = ButtonDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(text = sortText)
                                if (hasDirection) {
                                    Icon(
                                        imageVector = if (isDescending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                        contentDescription = if (isDescending) stringResource(R.string.home_descending) else stringResource(R.string.home_ascending),
                                        modifier = Modifier.padding(start = 6.dp).size(18.dp)
                                    )
                                }
                            }
                            if (sortExpanded) {
                                // Each pair is (descending, ascending); picking the current one again flips it.
                                val directional = listOf(
                                    Triple(R.string.home_sort_played, LibrarySortOption.RECENTLY_PLAYED, LibrarySortOption.LEAST_RECENTLY_PLAYED),
                                    Triple(R.string.home_sort_added, LibrarySortOption.RECENTLY_ADDED, LibrarySortOption.OLDEST_ADDED),
                                    Triple(R.string.home_sort_name, LibrarySortOption.NAME_A_Z, LibrarySortOption.NAME_Z_A)
                                )
                                TvSelectionDialog(
                                    title = stringResource(R.string.library_cd_sort_options),
                                    options = directional.map { (labelRes, first, second) ->
                                        val isCurrent = sortOption == first || sortOption == second
                                        TvSelectionOption(
                                            label = stringResource(labelRes),
                                            selected = isCurrent,
                                            onClick = {
                                                viewModel.setSortOption(if (sortOption == first) second else first)
                                            },
                                            trailing = if (isCurrent) {
                                                {
                                                    val isFirst = sortOption == first
                                                    Icon(
                                                        imageVector = if (isFirst) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                                        contentDescription = when {
                                                            first == LibrarySortOption.NAME_A_Z ->
                                                                stringResource(if (isFirst) R.string.library_cd_sort_a_z else R.string.library_cd_sort_z_a)
                                                            isFirst -> stringResource(R.string.home_descending)
                                                            else -> stringResource(R.string.home_ascending)
                                                        }
                                                    )
                                                }
                                            } else null
                                        )
                                    } + TvSelectionOption(
                                        label = stringResource(R.string.home_sort_custom),
                                        selected = sortOption == LibrarySortOption.CUSTOM,
                                        onClick = { viewModel.setSortOption(LibrarySortOption.CUSTOM) }
                                    ),
                                    onDismiss = { sortExpanded = false }
                                )
                            }
                        }

                        AnimatedVisibility(visible = sortOption == LibrarySortOption.CUSTOM) {
                            Button(
                                onClick = {
                                    if (!isDragLocked && movingStationUuid != null) {
                                        movingStationUuid = null
                                        viewModel.updateStationsOrder(currentStations)
                                    }
                                    isDragLocked = !isDragLocked
                                },
                                colors = ButtonDefaults.colors(
                                    containerColor = if (isDragLocked) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                                    contentColor = if (isDragLocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = if (isDragLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = stringResource(if (isDragLocked) R.string.library_cd_unlock_reordering else R.string.library_cd_lock_reordering),
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(if (isDragLocked) "Locked" else "Reorder")
                            }
                        }
                    }

                    Button(
                        onClick = onAddStation,
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.edit_station_add_station))
                    }
                }
            }

            if (currentStations.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (useFilter) stringResource(R.string.home_no_library_stations_filter) else stringResource(R.string.home_no_library_stations_yet),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = currentStations,
                    key = { _, station -> station.stationUuid }
                ) { index, station ->
                    val isMoving = movingStationUuid == station.stationUuid
                    val focusRequester = remember { FocusRequester() }

                    LaunchedEffect(index, isMoving) {
                        if (isMoving) {
                            try {
                                focusRequester.requestFocus()
                            } catch (_: Exception) {}
                        }
                    }

                    val moveModifier = if (isMoving) {
                        Modifier.onPreviewKeyEvent { event ->
                            if (event.isConfirmKey()) {
                                // Only a fresh press, made after OK was let go, drops the item.
                                // Everything belonging to the hold that started the move is passed
                                // to the Card (its onClick is a no-op while moving), so it can
                                // reset its long-click state.
                                return@onPreviewKeyEvent when (event.type) {
                                    KeyEventType.KeyDown -> confirmGate.onKeyDown(event)
                                    KeyEventType.KeyUp -> {
                                        if (confirmGate.onKeyUp(event)) {
                                            movingStationUuid = null
                                            viewModel.updateStationsOrder(currentStations)
                                            true
                                        } else false
                                    }
                                    else -> false
                                }
                            }
                            if (event.type == KeyEventType.KeyDown) {
                                val currentIndex = currentStations.indexOfFirst { it.stationUuid == station.stationUuid }
                                if (currentIndex != -1) {
                                    val targetIndex = when (event.key) {
                                        Key.DirectionLeft -> (currentIndex - 1).coerceAtLeast(0)
                                        Key.DirectionRight -> (currentIndex + 1).coerceAtMost(currentStations.lastIndex)
                                        Key.DirectionUp -> (currentIndex - 4).coerceAtLeast(0)
                                        Key.DirectionDown -> (currentIndex + 4).coerceAtMost(currentStations.lastIndex)
                                        Key.Back, Key.Escape -> {
                                            movingStationUuid = null
                                            viewModel.updateStationsOrder(currentStations)
                                            return@onPreviewKeyEvent true
                                        }
                                        else -> null
                                    }
                                    if (targetIndex != null) {
                                        if (targetIndex != currentIndex) {
                                            currentStations = currentStations.toMutableList().apply {
                                                add(targetIndex, removeAt(currentIndex))
                                            }
                                        }
                                        return@onPreviewKeyEvent true
                                    }
                                }
                            } else if (event.type == KeyEventType.KeyUp) {
                                val isNavOrConfirm = event.key == Key.Back ||
                                        event.key == Key.Escape ||
                                        event.key == Key.DirectionLeft ||
                                        event.key == Key.DirectionRight ||
                                        event.key == Key.DirectionUp ||
                                        event.key == Key.DirectionDown
                                if (isNavOrConfirm) {
                                    return@onPreviewKeyEvent true
                                }
                            }
                            false
                        }
                    } else if (isReorderEnabled) {
                        // Not moving yet: just note OK timings so the hold that picks the item
                        // up is recognised once move mode starts.
                        Modifier.onPreviewKeyEvent { event ->
                            if (event.isConfirmKey()) confirmGate.record(event)
                            false
                        }
                    } else Modifier

                    // Starts a move. Dropping is done only by the key handler above, so a held
                    // OK (repeats, or a stream of down/up pairs on some remotes) can't toggle it.
                    val startMove = {
                        if (movingStationUuid == null) {
                            confirmGate.reset()
                            movingStationUuid = station.stationUuid
                        }
                    }

                    StationCard(
                        station = station,
                        onClick = {
                            if (isReorderEnabled) {
                                startMove()
                            } else {
                                onStationClick(currentStations, index, "tv_library")
                            }
                        },
                        onLongClick = if (isReorderEnabled) startMove else null,
                        modifier = Modifier
                            .animateItem()
                            .focusRequester(focusRequester)
                            .then(moveModifier),
                        isCurrentlyPlaying = station.stationUuid == playingStationUuid,
                        isPlaybackActive = isPlaybackActive && station.stationUuid == playingStationUuid,
                        isMoving = isMoving
                    )
                }
            }
        }
    }
}

private fun KeyEvent.isConfirmKey(): Boolean =
    key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter || key == Key.Spacebar

/**
 * Tells a fresh OK press apart from a held one. Depending on the remote, a held OK arrives as
 * repeat events or as a fast stream of separate down/up pairs, so a press only counts as fresh
 * when OK was idle for longer than the key-repeat delay.
 */
private class ConfirmKeyGate {
    private val holdGapMs = ViewConfiguration.getKeyRepeatTimeout().toLong() + 100L
    private var lastEventTime = 0L
    private var dropArmed = false

    fun record(event: KeyEvent) {
        lastEventTime = event.nativeKeyEvent.eventTime
    }

    fun reset() {
        dropArmed = false
    }

    /** Returns true when the event is consumed (a fresh press that will drop on release). */
    fun onKeyDown(event: KeyEvent): Boolean {
        val native = event.nativeKeyEvent
        if (native.repeatCount == 0 && native.eventTime - lastEventTime > holdGapMs) dropArmed = true
        lastEventTime = native.eventTime
        return dropArmed
    }

    /** Returns true when this release should drop the moving item. */
    fun onKeyUp(event: KeyEvent): Boolean {
        lastEventTime = event.nativeKeyEvent.eventTime
        return dropArmed.also { dropArmed = false }
    }
}
