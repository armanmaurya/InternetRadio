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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
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

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Mic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    widthSizeClass: WindowWidthSizeClass,
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
        stringResource(R.string.home_tab_browse),
        stringResource(R.string.home_tab_recent),
        stringResource(R.string.home_tab_library),
        stringResource(R.string.home_tab_recordings)
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
            if (widthSizeClass != WindowWidthSizeClass.Expanded) {
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
                onSearch = { if (uiState.autoRouteToBrowseOnSearch) viewModel.onTabSelected(0) },
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
                                    if (uiState.autoRouteToBrowseOnSearch) {
                                        viewModel.onTabSelected(0)
                                    }
                                }
                        )
                    }
                }
                }
            }
        },

        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        val pagerContent = @Composable {
            // Tab Container with horizontal pager
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = if (widthSizeClass == WindowWidthSizeClass.Expanded) {
                    RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 28.dp)
                } else {
                    RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                },
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

        if (widthSizeClass == WindowWidthSizeClass.Expanded) {
            val isPureBlack = MaterialTheme.colorScheme.surfaceContainerHigh == androidx.compose.ui.graphics.Color.Black
            androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .width(200.dp)
                        .padding(start = 12.dp, end = 12.dp)
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 0.dp, bottom = 8.dp)
                                .clip(RoundedCornerShape(100))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .then(
                                    if (isPureBlack) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(100))
                                    else Modifier
                                )
                                .clickable { isSearchExpanded = true }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = uiState.searchQuery.ifEmpty { stringResource(R.string.general_search) },
                                color = if (uiState.searchQuery.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly
                        ) {
                            androidx.compose.material3.IconButton(
                                onClick = onTagClick,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.LocalOffer,
                                    contentDescription = stringResource(R.string.home_cd_tags),
                                    modifier = Modifier.size(20.dp),
                                    tint = if (uiState.selectedTags.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            androidx.compose.material3.IconButton(
                                onClick = onLanguageClick,
                                modifier = Modifier.size(36.dp)
                            ) {
                                androidx.compose.foundation.layout.Box {
                                    Icon(
                                        Icons.Default.Translate,
                                        contentDescription = stringResource(R.string.edit_station_language_field),
                                        modifier = Modifier.size(20.dp),
                                        tint = if (!uiState.selectedLanguage.isNullOrBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!uiState.selectedLanguage.isNullOrBlank()) {
                                        Text(
                                            text = uiState.selectedLanguage!!.take(2).uppercase(),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp),
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 6.dp, y = (-4).dp)
                                        )
                                    }
                                }
                            }
                            androidx.compose.material3.IconButton(
                                onClick = onCountryClick,
                                modifier = Modifier.size(36.dp)
                            ) {
                                androidx.compose.foundation.layout.Box {
                                    Icon(
                                        Icons.Default.Public,
                                        contentDescription = stringResource(R.string.edit_station_country_field),
                                        modifier = Modifier.size(20.dp),
                                        tint = if (!uiState.selectedCountryCode.isNullOrBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!uiState.selectedCountryCode.isNullOrBlank()) {
                                        Text(
                                            text = uiState.selectedCountryCode!!,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp),
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 6.dp, y = (-4).dp)
                                        )
                                    }
                                }
                            }
                            androidx.compose.material3.IconButton(
                                onClick = onSettingsClick,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.home_cd_settings), modifier = Modifier.size(20.dp))
                            }
                        }
                        val icons = listOf(
                            androidx.compose.material.icons.Icons.Rounded.Explore,
                            androidx.compose.material.icons.Icons.Rounded.History,
                            androidx.compose.material.icons.Icons.Rounded.LibraryMusic,
                            androidx.compose.material.icons.Icons.Rounded.Mic
                        )
                        tabs.forEachIndexed { index, title ->
                            androidx.compose.material3.NavigationDrawerItem(
                                icon = { Icon(icons.getOrElse(index) { androidx.compose.material.icons.Icons.Rounded.Explore }, contentDescription = title) },
                                label = { Text(title) },
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                                colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = if (isPureBlack) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.secondaryContainer
                                ),
                                modifier = Modifier
                                    .padding(vertical = 0.dp)
                                    .height(40.dp)
                                    .then(
                                        if (isPureBlack && pagerState.currentPage == index)
                                            Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(100))
                                        else Modifier
                                    )
                            )
                        }
                    }

                }
                Box(modifier = Modifier.weight(1f)) {
                    pagerContent()
                }
            } // End Row
                
                if (isSearchExpanded) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
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
                            selectedLanguage = uiState.selectedLanguage,
                            selectedTags = uiState.selectedTags,
                            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
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
                                                if (uiState.autoRouteToBrowseOnSearch) {
                                                    viewModel.onTabSelected(0)
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            } // End Box
        } else {
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

            pagerContent()
        }
    }
    }
}
