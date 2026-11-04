package com.armanmaurya.internetradio.ui.mobile.screens.home


import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.RadioSearchBar
import com.armanmaurya.internetradio.ui.shared.viewmodels.PlayerViewModel
import com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.library.LibraryContent
import com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.browse.BrowseContent
import com.armanmaurya.internetradio.ui.shared.viewmodels.BrowseViewModel
import com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.recent.RecentContent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.platform.LocalContext
import com.armanmaurya.internetradio.domain.model.PlaybackSource
import com.armanmaurya.internetradio.domain.model.RadioStation

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.rememberUpdatedState
import com.armanmaurya.internetradio.ui.shared.viewmodels.LibraryViewModel
import com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.recordings.RecordingsContent
import com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.schedules.SchedulesTabContent
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.StationListCard
import com.armanmaurya.internetradio.ui.mobile.screens.home.layout.ExpandedHomeLayout
import com.armanmaurya.internetradio.ui.mobile.screens.home.layout.CompactHomeLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    widthSizeClass: WindowWidthSizeClass,
    onSettingsClick: () -> Unit,
    onCountryClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onTagClick: () -> Unit,
    onEditSchedule: (Int?) -> Unit,
    onEditStation: (String?) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: HomeViewModel = hiltViewModel(),
    browseViewModel: BrowseViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val browseUiState by browseViewModel.uiState.collectAsStateWithLifecycle()
    val libraryStations by libraryViewModel.stations.collectAsStateWithLifecycle(initialValue = emptyList())
    val libraryUuids by libraryViewModel.stationUuids.collectAsStateWithLifecycle(initialValue = emptySet())
    val searchLibraryStations by libraryViewModel.searchStations.collectAsStateWithLifecycle(initialValue = emptyList())
    val filteredLibraryStations = searchLibraryStations ?: emptyList()
    val playerUiState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val playingStationUuid = playerUiState.currentStation?.stationUuid
    val isPlaybackActive = playerUiState.isPlaying
    val activeSessions by playerViewModel.activeSessions.collectAsStateWithLifecycle()
    val isPlayingRecording by viewModel.isPlayingRecording.collectAsStateWithLifecycle()

    if (!uiState.isPreferencesLoaded) {
        return // Wait for preferences to load before rendering
    }

    val tabs = listOf(
        stringResource(R.string.home_tab_browse),
        stringResource(R.string.home_tab_recent),
        stringResource(R.string.home_tab_library),
        stringResource(R.string.home_tab_recordings),
        stringResource(R.string.home_tab_schedules)
    )
    val pagerState = rememberPagerState(
        initialPage = uiState.selectedTab,
        pageCount = { tabs.size }
    )
    
    val context = LocalContext.current
    val exportSuccessTemplate = stringResource(R.string.export_success)
    val exportFailedTemplate = stringResource(R.string.export_failed)
    var stationToExport by remember { mutableStateOf<RadioStation?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && stationToExport != null) {
            libraryViewModel.exportStation(context, uri, stationToExport!!) { result ->
                val message = if (result.isSuccess) {
                    java.lang.String.format(exportSuccessTemplate, stationToExport!!.name)
                } else {
                    java.lang.String.format(exportFailedTemplate, result.exceptionOrNull()?.localizedMessage)
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                stationToExport = null
            }
        }
    }
    val onExportStation: (RadioStation) -> Unit = { station ->
        stationToExport = station
        exportLauncher.launch("${station.name}.json")
    }

    val coroutineScope = rememberCoroutineScope()

    val density = LocalDensity.current
    val tabWidths = remember {
        mutableStateListOf<Dp>().apply {
            repeat(tabs.size) { add(0.dp) }
        }
    }

    val sheetState = rememberModalBottomSheetState()
    var isSearchExpanded by remember { mutableStateOf(false) }
    var isLibraryDragging by remember { mutableStateOf(false) }
    val searchBarCollapseProgress = remember { Animatable(if (uiState.isSearchBarHidden) 1f else 0f) }
    val searchBarCollapsibleHeightPx = remember { mutableFloatStateOf(0f) }
    val statusBarsInsets = WindowInsets.statusBars

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded && searchBarCollapseProgress.value > 0f) {
            searchBarCollapseProgress.snapTo(0f)
        }
    }

    // Forward search query from HomeViewModel → ViewModels
    LaunchedEffect(uiState.searchQuery) {
        browseViewModel.onSearchQueryChange(uiState.searchQuery)
        libraryViewModel.onSearchQueryChange(uiState.searchQuery)
    }

    // Keep pager in sync with tab state from HomeViewModel (tab click)
    LaunchedEffect(uiState.selectedTab) {
        if (pagerState.currentPage != uiState.selectedTab) {
            pagerState.animateScrollToPage(uiState.selectedTab)
        }
    }

    // Keep HomeViewModel's selectedTab in sync when user swipes pager
    LaunchedEffect(pagerState.settledPage) {
        viewModel.onTabSelected(pagerState.settledPage)
    }

    Scaffold(
        topBar = {
            if (widthSizeClass != WindowWidthSizeClass.Expanded) {
                val isPureBlack = MaterialTheme.colorScheme.surfaceContainerHigh == Color.Black
                val collapseModifier = if (!isSearchExpanded) {
                    Modifier
                        .clipToBounds()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val statusBarPx = statusBarsInsets.getTop(this)
                            val collapsiblePx = (placeable.height - statusBarPx).coerceAtLeast(0)
                            searchBarCollapsibleHeightPx.floatValue = collapsiblePx.toFloat()
                            val progress = searchBarCollapseProgress.value.coerceIn(0f, 1f)
                            val offsetPx = (collapsiblePx * progress).roundToInt()
                            val layoutHeight = (placeable.height - offsetPx).coerceAtLeast(statusBarPx)
                            layout(placeable.width, layoutHeight) {
                                placeable.placeRelativeWithLayer(0, -offsetPx) {
                                    alpha = 1f - progress
                                }
                            }
                        }
                } else {
                    Modifier
                }
            RadioSearchBar(
                query = uiState.searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                isSearchExpanded = isSearchExpanded,
                onExpandedChange = { isSearchExpanded = it },
                onSearchCleared = viewModel::onSearchCleared,
                onCountryClick = onCountryClick,
                onLanguageClick = onLanguageClick,
                onTagClick = onTagClick,
                onSettingsClick = onSettingsClick,
                onSearch = { if (uiState.autoRouteToBrowseOnSearch) viewModel.onTabSelected(0) },
                selectedCountryCode = uiState.selectedCountryCode,
                selectedStateCode = uiState.selectedStateCode,
                selectedLanguage = uiState.selectedLanguage,
                selectedTags = uiState.selectedTags,
                selectAllTextOnFocus = uiState.selectAllTextOnFocus,
                modifier = collapseModifier
            ) {
                val safeDrawingBottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
                val bottomPadding = androidx.compose.ui.unit.max(contentPadding.calculateBottomPadding(), safeDrawingBottom)
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = 8.dp + bottomPadding
                    )
                ) {
                    if (filteredLibraryStations.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        isSearchExpanded = false
                                        viewModel.onTabSelected(2)
                                        libraryViewModel.setFilterEnabled(true)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.home_tab_library),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        items(
                            items = filteredLibraryStations.take(5),
                            key = { "lib_${it.stationUuid}" }
                        ) { station ->
                            val session = activeSessions[station.stationUuid]
                            val duration by (session?.durationSeconds ?: kotlinx.coroutines.flow.flowOf(0L)).collectAsStateWithLifecycle(initialValue = 0L)
                            val isReconnecting by (session?.isReconnecting ?: kotlinx.coroutines.flow.flowOf(false)).collectAsStateWithLifecycle(initialValue = false)
                            StationListCard(
                                station = station,
                                isCurrentlyPlaying = playingStationUuid == station.stationUuid,
                                isPlaybackActive = isPlaybackActive,
                                isFavorite = true,
                                onClick = {
                                    val index = filteredLibraryStations.indexOf(station).coerceAtLeast(0)
                                    playerViewModel.play(filteredLibraryStations, index, PlaybackSource.None)
                                },
                                onToggleFavoriteClick = { browseViewModel.toggleLibrary(station) },
                                isRecording = session != null,
                                recordingDuration = duration,
                                isReconnecting = isReconnecting,
                                onRecordClick = { playerViewModel.toggleRecording(station) },
                                onStopRecordingClick = if (session != null) { { playerViewModel.toggleRecording(station) } } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .animateItem()
                            )
                        }
                    }

                    if (browseUiState.stations.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        isSearchExpanded = false
                                        viewModel.onTabSelected(0)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.home_tab_browse),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        items(
                            items = browseUiState.stations.take(10),
                            key = { "browse_${it.stationUuid}" }
                        ) { station ->
                            val session = activeSessions[station.stationUuid]
                            val duration by (session?.durationSeconds ?: kotlinx.coroutines.flow.flowOf(0L)).collectAsStateWithLifecycle(initialValue = 0L)
                            val isReconnecting by (session?.isReconnecting ?: kotlinx.coroutines.flow.flowOf(false)).collectAsStateWithLifecycle(initialValue = false)
                            StationListCard(
                                station = station,
                                isCurrentlyPlaying = playingStationUuid == station.stationUuid,
                                isPlaybackActive = isPlaybackActive,
                                isFavorite = libraryUuids.contains(station.stationUuid),
                                onClick = {
                                    val index = browseUiState.stations.indexOf(station).coerceAtLeast(0)
                                    playerViewModel.play(browseUiState.stations, index, PlaybackSource.None)
                                },
                                onToggleFavoriteClick = { browseViewModel.toggleLibrary(station) },
                                isRecording = session != null,
                                recordingDuration = duration,
                                isReconnecting = isReconnecting,
                                onRecordClick = { playerViewModel.toggleRecording(station) },
                                onStopRecordingClick = if (session != null) { { playerViewModel.toggleRecording(station) } } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .animateItem()
                            )
                        }
                    }
                }
                }
            }
        },

        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        val isPureBlack = MaterialTheme.colorScheme.surfaceContainerHigh == Color.Black
        val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        val currentWidthSizeClass by rememberUpdatedState(widthSizeClass)
        val currentContentPadding by rememberUpdatedState(contentPadding)
        val currentPlayingStationUuid by rememberUpdatedState(playingStationUuid)
        val currentIsPlaybackActive by rememberUpdatedState(isPlaybackActive)
        val currentActiveSessions by rememberUpdatedState(activeSessions)
        val currentIsLibraryDragging by rememberUpdatedState(isLibraryDragging)
        val currentOnEditStation by rememberUpdatedState(onEditStation)
        val currentOnExportStation by rememberUpdatedState(onExportStation)
        val currentOnEditSchedule by rememberUpdatedState(onEditSchedule)

        val pagerContent = remember {
            movableContentOf {
                // Tab Container with horizontal pager
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (isPureBlack) {
                                Modifier.drawWithContent {
                                    drawContent()
                                    // Draw a 3-sided border (left + top + right) for the pure black theme.
                                    // We skip the bottom edge intentionally so there is no divider line
                                    // between the surface and the navigation bar.
                                    val strokePx = 1.dp.toPx()
                                    val r = 28.dp.toPx()
                                    val path = androidx.compose.ui.graphics.Path().apply {
                                        moveTo(0f, size.height)
                                        lineTo(0f, r)
                                        arcTo(
                                            rect = androidx.compose.ui.geometry.Rect(0f, 0f, r * 2, r * 2),
                                            startAngleDegrees = 180f,
                                            sweepAngleDegrees = 90f,
                                            forceMoveTo = false
                                        )
                                        lineTo(size.width - r, 0f)
                                        arcTo(
                                            rect = androidx.compose.ui.geometry.Rect(size.width - r * 2, 0f, size.width, r * 2),
                                            startAngleDegrees = 270f,
                                            sweepAngleDegrees = 90f,
                                            forceMoveTo = false
                                        )
                                        lineTo(size.width, size.height)
                                    }
                                    drawPath(
                                        path = path,
                                        color = outlineColor,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePx)
                                    )
                                }
                            } else Modifier
                        ),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = if (currentWidthSizeClass == WindowWidthSizeClass.Expanded) {
                        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 28.dp)
                    } else {
                        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                    },
                ) {
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = !currentIsLibraryDragging,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (page) {
                            0 -> BrowseContent(
                                onStationClick = { stations, index, source -> playerViewModel.play(stations, index, source) },
                                onEditStation = currentOnEditStation,
                                onExportStation = currentOnExportStation,
                                contentPadding = currentContentPadding,
                                viewModel = browseViewModel,
                                playingStationUuid = currentPlayingStationUuid,
                                isPlaybackActive = currentIsPlaybackActive,
                                activeSessions = currentActiveSessions,
                                onToggleRecording = { playerViewModel.toggleRecording(it) }
                            )
                            1 -> RecentContent(
                                onStationClick = { stations, index, source -> playerViewModel.play(stations, index, source) },
                                onEditStation = currentOnEditStation,
                                onExportStation = currentOnExportStation,
                                contentPadding = currentContentPadding,
                                playingStationUuid = currentPlayingStationUuid,
                                isPlaybackActive = currentIsPlaybackActive,
                                searchQuery = uiState.searchQuery,
                                activeSessions = currentActiveSessions,
                                onToggleRecording = { playerViewModel.toggleRecording(it) }
                            )
                            2 -> LibraryContent(
                                onStationClick = { stations, index, source -> playerViewModel.play(stations, index, source) },
                                onEditStation = { stationUuid -> currentOnEditStation(stationUuid) },
                                onExportStation = currentOnExportStation,
                                contentPadding = currentContentPadding,
                                playingStationUuid = currentPlayingStationUuid,
                                isPlaybackActive = currentIsPlaybackActive,
                                searchQuery = uiState.searchQuery,
                                activeSessions = currentActiveSessions,
                                onToggleRecording = { playerViewModel.toggleRecording(it) },
                                onDragStateChange = { isLibraryDragging = it }
                            )
                            3 -> RecordingsContent(
                                onStationClick = { stations, index, source -> playerViewModel.play(stations, index, source) },
                                onEditStation = currentOnEditStation,
                                onExportStation = currentOnExportStation,
                                playingStationUuid = currentPlayingStationUuid,
                                isPlaybackActive = currentIsPlaybackActive,
                                contentPadding = currentContentPadding
                            )
                            4 -> SchedulesTabContent(
                                onEditSchedule = currentOnEditSchedule,
                                contentPadding = currentContentPadding
                            )
                        }
                    }
                }
            }
        }

        if (widthSizeClass == WindowWidthSizeClass.Expanded) {
            ExpandedHomeLayout(
                innerPadding = innerPadding,
                contentPadding = contentPadding,
                pagerContent = pagerContent,
                searchQuery = uiState.searchQuery,
                isSearchExpanded = isSearchExpanded,
                onSearchExpandedChange = { isSearchExpanded = it },
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onSearchCleared = viewModel::onSearchCleared,
                onCountryClick = onCountryClick,
                onLanguageClick = onLanguageClick,
                onTagClick = onTagClick,
                onSettingsClick = onSettingsClick,
                onSearch = { _ -> if (uiState.autoRouteToBrowseOnSearch) viewModel.onTabSelected(0) },
                selectedCountryCode = uiState.selectedCountryCode,
                selectedStateCode = uiState.selectedStateCode,
                selectedLanguage = uiState.selectedLanguage,
                selectedTags = uiState.selectedTags,
                selectAllTextOnFocus = uiState.selectAllTextOnFocus,
                browseStations = browseUiState.stations,
                libraryStations = filteredLibraryStations,
                libraryUuids = libraryUuids,
                onLibraryStationClick = { station ->
                    val index = filteredLibraryStations.indexOf(station).coerceAtLeast(0)
                    playerViewModel.play(filteredLibraryStations, index, PlaybackSource.None)
                },
                onBrowseStationClick = { station ->
                    val index = browseUiState.stations.indexOf(station).coerceAtLeast(0)
                    playerViewModel.play(browseUiState.stations, index, PlaybackSource.None)
                },
                onLibraryHeaderClick = {
                    isSearchExpanded = false
                    viewModel.onTabSelected(2)
                    libraryViewModel.setFilterEnabled(true)
                },
                onBrowseHeaderClick = {
                    isSearchExpanded = false
                    viewModel.onTabSelected(0)
                },
                tabs = tabs,
                pagerState = pagerState,
                coroutineScope = coroutineScope,
                activeSessions = activeSessions,
                onToggleRecording = { playerViewModel.toggleRecording(it) },
                isPlayingRecording = isPlayingRecording
            )
        } else {
            val tabRowDragModifier = Modifier.draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    val maxCollapse = searchBarCollapsibleHeightPx.floatValue
                    if (maxCollapse > 0f && !isSearchExpanded) {
                        val newProgress = (searchBarCollapseProgress.value - delta / maxCollapse).coerceIn(0f, 1f)
                        coroutineScope.launch {
                            searchBarCollapseProgress.snapTo(newProgress)
                        }
                    }
                },
                onDragStopped = { velocity ->
                    val target = when {
                        velocity < -300f -> 1f
                        velocity > 300f -> 0f
                        searchBarCollapseProgress.value > 0.5f -> 1f
                        else -> 0f
                    }
                    coroutineScope.launch {
                        searchBarCollapseProgress.animateTo(target)
                        viewModel.setSearchBarHidden(target == 1f)
                    }
                }
            )
            CompactHomeLayout(
                innerPadding = innerPadding,
                pagerState = pagerState,
                tabs = tabs,
                tabWidths = tabWidths,
                coroutineScope = coroutineScope,
                isPlayingRecording = isPlayingRecording,
                tabRowModifier = tabRowDragModifier,
                pagerContent = pagerContent
            )
        }
    }
}