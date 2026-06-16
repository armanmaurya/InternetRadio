package com.armanmaurya.internetradio.ui.screens.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.ui.components.RadioSearchBar
import com.armanmaurya.internetradio.ui.components.StationCard
import com.armanmaurya.internetradio.ui.player.PlayerViewModel
import com.armanmaurya.internetradio.ui.screens.added.AddStationBottomSheet
import com.armanmaurya.internetradio.ui.screens.added.AddedContent
import com.armanmaurya.internetradio.ui.screens.added.AddedViewModel
import com.armanmaurya.internetradio.ui.screens.favorites.FavoritesContent
import com.armanmaurya.internetradio.ui.screens.recent.RecentContent
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onSettingsClick: () -> Unit,
    onCountryClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onTagClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: DiscoverViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
    addedViewModel: AddedViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tabs = listOf(
        stringResource(R.string.tab_browse),
        stringResource(R.string.tab_recent),
        stringResource(R.string.tab_favourites),
        stringResource(R.string.tab_added)
    )
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()
    
    val density = LocalDensity.current
    val tabWidths = remember { 
        mutableStateListOf<Dp>().apply { 
            repeat(tabs.size) { add(0.dp) }
        }
    }

    var showAddBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        topBar = {
            RadioSearchBar(
                query = uiState.searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                isSearchActive = uiState.isSearchActive,
                isSearchExpanded = uiState.isSearchExpanded,
                onSearchExpandedChange = viewModel::onSearchExpandedChange,
                onSearchCleared = viewModel::onSearchCleared,
                onCountryClick = onCountryClick,
                onLanguageClick = onLanguageClick,
                onTagClick = onTagClick,
                onSettingsClick = onSettingsClick,
                selectedCountryCode = uiState.selectedCountryCode,
                selectedLanguage = uiState.selectedLanguage,
                selectedTags = uiState.selectedTags
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(
                        items = uiState.stations,
                        key = { it.stationUuid }
                    ) { station ->
                        ListItem(
                            headlineContent = { Text(station.name) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .clickable {
                                    viewModel.onSearchQueryChange(station.name)
                                    viewModel.onSearchExpandedChange(false)
                                }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (pagerState.currentPage == 3) {
                FloatingActionButton(
                    onClick = { showAddBottomSheet = true },
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
        if (showAddBottomSheet) {
            AddStationBottomSheet(
                onDismiss = { showAddBottomSheet = false },
                onConfirm = { name, url, favicon, tags, country, language ->
                    addedViewModel.addStation(name, url, favicon, tags, country, "", language)
                    coroutineScope.launch {
                        sheetState.hide()
                    }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showAddBottomSheet = false
                        }
                    }
                },
                sheetState = sheetState
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.padding(horizontal = 4.dp),
                edgePadding = 24.dp,
                indicator = { tabPositions ->
                    if (pagerState.currentPage < tabPositions.size) {
                        val pagerPage = pagerState.currentPage
                        val fraction = pagerState.currentPageOffsetFraction
                        val targetPage = if (fraction > 0 && pagerPage < tabs.size - 1) pagerPage + 1 
                                        else if (fraction < 0 && pagerPage > 0) pagerPage - 1 
                                        else pagerPage
                        
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
                                        .padding(horizontal = 12.dp)
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

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> StationsList(
                            uiState = uiState,
                            onStationClick = { playerViewModel.play(it) },
                            onOrderChange = viewModel::onOrderChange,
                            onReverseChange = viewModel::onReverseChange,
                            onLoadMore = viewModel::loadMoreStations,
                            contentPadding = contentPadding
                        )
                        1 -> RecentContent(
                            onStationClick = { playerViewModel.play(it) },
                            contentPadding = contentPadding
                        )
                        2 -> FavoritesContent(
                            onStationClick = { playerViewModel.play(it) },
                            contentPadding = contentPadding
                        )
                        3 -> AddedContent(
                            onStationClick = { playerViewModel.play(it) },
                            contentPadding = contentPadding,
                            viewModel = addedViewModel
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchFilters(
    order: String,
    reverse: Boolean,
    onOrderChange: (String) -> Unit,
    onReverseChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val orderOptions = listOf(
        "votes" to "Votes",
        "clickcount" to "Clicks",
        "clicktrend" to "Trend",
        "name" to "Name"
    )
    var orderExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                FilterChip(
                    selected = false,
                    onClick = { orderExpanded = true },
                    label = {
                        Text(
                            text = orderOptions.find { it.first == order }?.second ?: order,
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = orderExpanded)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Transparent,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    border = null
                )
                DropdownMenu(
                    expanded = orderExpanded,
                    onDismissRequest = { orderExpanded = false }
                ) {
                    orderOptions.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onOrderChange(value)
                                orderExpanded = false
                            }
                        )
                    }
                }
            }

            IconButton(
                onClick = { onReverseChange(!reverse) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (reverse) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = if (reverse) "Descending" else "Ascending",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun StationsList(
    uiState: DiscoverUiState,
    onStationClick: (RadioStation) -> Unit,
    onOrderChange: (String) -> Unit,
    onReverseChange: (Boolean) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf false
            
            lastVisibleItem.index >= gridState.layoutInfo.totalItemsCount - 9 // 3 rows early
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && !uiState.isLoading && !uiState.isNextPageLoading && uiState.canLoadMore) {
            onLoadMore()
        }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 16.dp + contentPadding.calculateBottomPadding()
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            SearchFilters(
                order = uiState.order,
                reverse = uiState.reverse,
                onOrderChange = onOrderChange,
                onReverseChange = onReverseChange,
                modifier = Modifier
                    .fillMaxWidth()
            )
        }

        when {
            uiState.isLoading -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            uiState.error != null -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "Something went wrong. Please try again.")
                    }
                }
            }

            uiState.stations.isEmpty() && uiState.isSearchActive -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "No stations found for \"${uiState.searchQuery}\"")
                    }
                }
            }

            else -> {
                items(
                    items = uiState.stations,
                    key = { it.stationUuid },
                ) { station ->
                    StationCard(
                        station = station,
                        onClick = { onStationClick(station) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(),
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
