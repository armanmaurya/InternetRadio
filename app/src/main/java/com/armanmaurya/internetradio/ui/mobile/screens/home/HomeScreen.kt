package com.armanmaurya.internetradio.ui.mobile.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TabRow
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.RadioSearchBar
import com.armanmaurya.internetradio.ui.shared.viewmodels.PlayerViewModel
import com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.library.LibraryContent
import com.armanmaurya.internetradio.ui.shared.viewmodels.LibraryViewModel
import com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.browse.BrowseContent
import com.armanmaurya.internetradio.ui.shared.viewmodels.BrowseViewModel
import com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.recent.RecentContent
import com.armanmaurya.internetradio.ui.shared.viewmodels.RecentViewModel
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSettingsClick: () -> Unit,
    onCountryClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onTagClick: () -> Unit,
    onEditStation: (String?) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: HomeViewModel = hiltViewModel(),
    browseViewModel: BrowseViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val browseUiState by browseViewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()
    val playingStationUuid = playbackState.currentStation?.stationUuid
    val isPlaybackActive = playbackState.isPlaying

    if (!uiState.isPreferencesLoaded) {
        return // Wait for preferences to load before rendering
    }

    val tabs = listOf(
        stringResource(R.string.tab_browse),
        stringResource(R.string.tab_recent),
        "Library",
        "Recordings"
    )
    val pagerState = rememberPagerState(
        initialPage = uiState.selectedTab,
        pageCount = { tabs.size }
    )
    
    // Defer beyondViewportPageCount to avoid lag during navigation transitions
    var beyondBounds by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(400)
        beyondBounds = 1
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

    // Forward search query from HomeViewModel → ViewModels
    LaunchedEffect(uiState.searchQuery) {
        browseViewModel.onSearchQueryChange(uiState.searchQuery)
    }

    // Keep pager in sync with tab state from HomeViewModel (tab click)
    LaunchedEffect(uiState.selectedTab) {
        if (pagerState.currentPage != uiState.selectedTab) {
            pagerState.animateScrollToPage(uiState.selectedTab)
        }
    }

    // Keep HomeViewModel's selectedTab in sync when user swipes pager
    LaunchedEffect(pagerState.currentPage) {
        viewModel.onTabSelected(pagerState.currentPage)
    }

    Scaffold(
        topBar = {
            val isPureBlack = MaterialTheme.colorScheme.surfaceContainerHigh == Color.Black
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
                selectedCountryCode = uiState.selectedCountryCode,
                selectedLanguage = uiState.selectedLanguage,
                selectedTags = uiState.selectedTags
            ) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(
                        items = browseUiState.stations,
                        key = { it.stationUuid }
                    ) { station ->
                        ListItem(
                            headlineContent = { Text(station.name) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .clickable {
                                    viewModel.onSearchQueryChange(station.name)
                                    isSearchExpanded = false
                                }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (pagerState.currentPage == 2) {
                FloatingActionButton(
                    onClick = { onEditStation(null) },
                    modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding())
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_station)
                    )
                }
            }
        },
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        // Tabs
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 8.dp,
                modifier = Modifier.padding(horizontal = 4.dp),
                indicator = { tabPositions ->
                    if (pagerState.currentPage < tabPositions.size) {
                        val pagerPage = pagerState.currentPage
                        val fraction = pagerState.currentPageOffsetFraction
                        val targetPage = when {
                            fraction > 0 && pagerPage < tabs.size - 1 -> pagerPage + 1
                            fraction < 0 && pagerPage > 0 -> pagerPage - 1
                            else -> pagerPage
                        }

                        val currentTabPosition = tabPositions[pagerPage]
                        val targetTabPosition = tabPositions[targetPage]

                        val currentContentWidth = tabWidths.getOrElse(pagerPage) { 0.dp }
                        val targetContentWidth = tabWidths.getOrElse(targetPage) { 0.dp }

                        val indicatorWidth = lerp(currentContentWidth, targetContentWidth, fraction.absoluteValue) + 32.dp
                        val indicatorOffset = lerp(currentTabPosition.left, targetTabPosition.left, fraction.absoluteValue)
                        val tabWidth = lerp(currentTabPosition.width, targetTabPosition.width, fraction.absoluteValue)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .wrapContentSize(Alignment.BottomStart)
                                .offset(x = indicatorOffset + (tabWidth - indicatorWidth) / 2)
                                .width(indicatorWidth)
                                .fillMaxHeight()
                                .padding(vertical = 8.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(100)
                                )
                                .then(
                                    if (MaterialTheme.colorScheme.surfaceContainerHigh == Color.Black) {
                                        Modifier.border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                            RoundedCornerShape(100)
                                        )
                                    } else Modifier
                                )
                                .zIndex(-1f)
                        )
                    }
                },
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    CompositionLocalProvider(LocalRippleConfiguration provides null) {
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = {
                                Text(
                                    text = title,
                                    style = if (pagerState.currentPage == index)
                                        MaterialTheme.typography.titleSmall
                                    else
                                        MaterialTheme.typography.bodyMedium,
                                    color = if (pagerState.currentPage == index)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(horizontal = 18.dp)
                                        .onGloballyPositioned { coords ->
                                            if (index < tabWidths.size) {
                                                tabWidths[index] = with(density) { coords.size.width.toDp() }
                                            }
                                        }
                                )
                            }
                        )
                    }
                }
            }

            // Tab Container with horizontal pager
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                border = if (MaterialTheme.colorScheme.surfaceContainerHigh == Color.Black) {
                    androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                } else null
            ) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = beyondBounds,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> BrowseContent(
                            onStationClick = { stations, index, source -> playerViewModel.play(stations, index, source) },
                            contentPadding = contentPadding,
                            viewModel = browseViewModel,
                            playingStationUuid = playingStationUuid,
                            isPlaybackActive = isPlaybackActive
                        )
                        1 -> RecentContent(
                            onStationClick = { stations, index, source -> playerViewModel.play(stations, index, source) },
                            contentPadding = contentPadding,
                            playingStationUuid = playingStationUuid,
                            isPlaybackActive = isPlaybackActive,
                            searchQuery = uiState.searchQuery
                        )
                        2 -> LibraryContent(
                            onStationClick = { stations, index, source -> playerViewModel.play(stations, index, source) },
                            onEditStation = { stationUuid -> onEditStation(stationUuid) },
                            contentPadding = contentPadding,
                            playingStationUuid = playingStationUuid,
                            isPlaybackActive = isPlaybackActive,
                            searchQuery = uiState.searchQuery
                        )
                        3 -> com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.recordings.RecordingsContent(
                            contentPadding = contentPadding
                        )
                    }
                }
            }
        }
    }
}
