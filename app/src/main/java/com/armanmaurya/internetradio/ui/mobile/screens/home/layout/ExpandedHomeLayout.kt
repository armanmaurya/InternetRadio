package com.armanmaurya.internetradio.ui.mobile.screens.home.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.StationListCard
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.model.RecordingSession
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.RadioSearchBar
import kotlinx.coroutines.launch

@Composable
internal fun ExpandedHomeLayout(
    innerPadding: PaddingValues,
    contentPadding: PaddingValues,
    pagerContent: @Composable () -> Unit,
    searchQuery: String,
    isSearchExpanded: Boolean,
    onSearchExpandedChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchCleared: () -> Unit,
    onCountryClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onTagClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearch: (String) -> Unit,
    selectedCountryCode: String?,
    selectedStateCode: String?,
    selectedLanguage: String?,
    selectedTags: Set<String>,
    browseStations: List<RadioStation>,
    libraryStations: List<RadioStation>? = null,
    libraryUuids: Set<String>,
    onLibraryStationClick: (RadioStation) -> Unit,
    onBrowseStationClick: (RadioStation) -> Unit,
    onLibraryHeaderClick: () -> Unit = {},
    onBrowseHeaderClick: () -> Unit = {},
    tabs: List<String>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    activeSessions: Map<String, RecordingSession> = emptyMap(),
    onToggleRecording: (RadioStation) -> Unit = {},
    isPlayingRecording: Boolean = false
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Sidebar(
                searchQuery = searchQuery,
                onSearchClick = { onSearchExpandedChange(true) },
                onTagClick = onTagClick,
                onLanguageClick = onLanguageClick,
                onCountryClick = onCountryClick,
                onSettingsClick = onSettingsClick,
                selectedTags = selectedTags,
                selectedLanguage = selectedLanguage,
                selectedCountryCode = selectedCountryCode,
                selectedStateCode = selectedStateCode,
                tabs = tabs,
                pagerState = pagerState,
                coroutineScope = coroutineScope,
                isPlayingRecording = isPlayingRecording
            )

            Box(modifier = Modifier.weight(1f)) {
                pagerContent()
            }
        }

        if (isSearchExpanded) {
            ExpandedSearchOverlay(
                contentPadding = contentPadding,
                searchQuery = searchQuery,
                onQueryChange = onSearchQueryChange,
                onExpandedChange = onSearchExpandedChange,
                onSearchCleared = onSearchCleared,
                onCountryClick = onCountryClick,
                onLanguageClick = onLanguageClick,
                onTagClick = onTagClick,
                onSettingsClick = onSettingsClick,
                onSearch = onSearch,
                selectedCountryCode = selectedCountryCode,
                selectedStateCode = selectedStateCode,
                selectedLanguage = selectedLanguage,
                selectedTags = selectedTags,
                browseStations = browseStations,
                libraryStations = libraryStations,
                libraryUuids = libraryUuids,
                onLibraryStationClick = onLibraryStationClick,
                onBrowseStationClick = onBrowseStationClick,
                onLibraryHeaderClick = onLibraryHeaderClick,
                onBrowseHeaderClick = onBrowseHeaderClick,
                activeSessions = activeSessions,
                onToggleRecording = onToggleRecording
            )
        }
    }
}

@Composable
private fun Sidebar(
    searchQuery: String,
    onSearchClick: () -> Unit,
    onTagClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onCountryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    selectedTags: Set<String>,
    selectedLanguage: String?,
    selectedCountryCode: String?,
    selectedStateCode: String?,
    tabs: List<String>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    isPlayingRecording: Boolean = false
) {
    Column(
        modifier = Modifier
            .width(200.dp)
            .padding(start = 12.dp, end = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            DummySearchBox(
                searchQuery = searchQuery,
                onSearchClick = onSearchClick,
            )
            
            SidebarFilters(
                onTagClick = onTagClick,
                onLanguageClick = onLanguageClick,
                onCountryClick = onCountryClick,
                onSettingsClick = onSettingsClick,
                selectedTags = selectedTags,
                selectedLanguage = selectedLanguage,
                selectedCountryCode = selectedCountryCode,
                selectedStateCode = selectedStateCode
            )
            
            SidebarNavigation(
                tabs = tabs,
                pagerState = pagerState,
                coroutineScope = coroutineScope,
                isPlayingRecording = isPlayingRecording
            )
        }
    }
}

@Composable
private fun DummySearchBox(
    searchQuery: String,
    onSearchClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(100))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            
            .clickable { onSearchClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = searchQuery.ifEmpty { stringResource(R.string.general_search) },
            color = if (searchQuery.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SidebarFilters(
    onTagClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onCountryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    selectedTags: Set<String>,
    selectedLanguage: String?,
    selectedCountryCode: String?,
    selectedStateCode: String?
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly
    ) {
        IconButton(
            onClick = onTagClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Default.LocalOffer,
                contentDescription = stringResource(R.string.home_cd_tags),
                modifier = Modifier.size(20.dp),
                tint = if (selectedTags.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = onLanguageClick,
            modifier = Modifier.size(36.dp)
        ) {
            Box {
                Icon(
                    Icons.Default.Translate,
                    contentDescription = stringResource(R.string.edit_station_language_field),
                    modifier = Modifier.size(20.dp),
                    tint = if (!selectedLanguage.isNullOrBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!selectedLanguage.isNullOrBlank()) {
                    Text(
                        text = selectedLanguage.take(3).uppercase(),
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
        IconButton(
            onClick = onCountryClick,
            modifier = Modifier.size(36.dp)
        ) {
            Box {
                Icon(
                    Icons.Default.Public,
                    contentDescription = stringResource(R.string.edit_station_country_field),
                    modifier = Modifier.size(20.dp),
                    tint = if (!selectedCountryCode.isNullOrBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!selectedCountryCode.isNullOrBlank()) {
                    val displayCode = if (!selectedStateCode.isNullOrBlank()) selectedStateCode else selectedCountryCode
                    Text(
                        text = displayCode ?: "",
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
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.home_cd_settings), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SidebarNavigation(
    tabs: List<String>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    isPlayingRecording: Boolean = false
) {
    val icons = listOf(
        Icons.Rounded.Explore,
        Icons.Rounded.History,
        Icons.Rounded.LibraryMusic,
        Icons.Rounded.Mic,
        Icons.Rounded.Schedule
    )
    tabs.forEachIndexed { index, title ->
        NavigationDrawerItem(
            icon = { Icon(icons.getOrElse(index) { Icons.Rounded.Explore }, contentDescription = title) },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedVisibility(
                        visible = index == 3 && isPlayingRecording,
                        enter = fadeIn(animationSpec = tween(200)) +
                                expandHorizontally(animationSpec = tween(250), expandFrom = Alignment.Start) +
                                scaleIn(animationSpec = tween(250), initialScale = 0.8f),
                        exit = fadeOut(animationSpec = tween(150)) +
                               shrinkHorizontally(animationSpec = tween(250), shrinkTowards = Alignment.Start) +
                               scaleOut(animationSpec = tween(200), targetScale = 0.8f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (pagerState.currentPage == index)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                    }
                    Text(title)
                }
            },
            selected = pagerState.currentPage == index,
            onClick = {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(index)
                }
            },
            colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            modifier = Modifier
                .padding(vertical = 0.dp)
                .height(40.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpandedSearchOverlay(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onSearchCleared: () -> Unit,
    onCountryClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onTagClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearch: (String) -> Unit,
    selectedCountryCode: String?,
    selectedStateCode: String?,
    selectedLanguage: String?,
    selectedTags: Set<String>,
    browseStations: List<RadioStation>,
    libraryStations: List<RadioStation>? = null,
    libraryUuids: Set<String>,
    onLibraryStationClick: (RadioStation) -> Unit,
    onBrowseStationClick: (RadioStation) -> Unit,
    onLibraryHeaderClick: () -> Unit = {},
    onBrowseHeaderClick: () -> Unit = {},
    activeSessions: Map<String, RecordingSession> = emptyMap(),
    onToggleRecording: (RadioStation) -> Unit = {},
    modifier: Modifier = Modifier.fillMaxSize()
) {
    Box(
        modifier = modifier
    ) {
        RadioSearchBar(
            query = searchQuery,
            onQueryChange = onQueryChange,
            isSearchExpanded = true,
            onExpandedChange = onExpandedChange,
            onSearchCleared = onSearchCleared,
            onCountryClick = onCountryClick,
            onLanguageClick = onLanguageClick,
            onTagClick = onTagClick,
            onSettingsClick = onSettingsClick,
            onSearch = onSearch,
            selectedCountryCode = selectedCountryCode,
            selectedStateCode = selectedStateCode,
            selectedLanguage = selectedLanguage,
            selectedTags = selectedTags,
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
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
                if (!libraryStations.isNullOrEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLibraryHeaderClick() }
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
                        items = libraryStations.take(5),
                        key = { "lib_${it.stationUuid}" }
                    ) { station ->
                        val session = activeSessions[station.stationUuid]
                        val duration by (session?.durationSeconds ?: kotlinx.coroutines.flow.flowOf(0L)).collectAsStateWithLifecycle(initialValue = 0L)
                        val isReconnecting by (session?.isReconnecting ?: kotlinx.coroutines.flow.flowOf(false)).collectAsStateWithLifecycle(initialValue = false)
                        StationListCard(
                            station = station,
                            onClick = { onLibraryStationClick(station) },
                            isFavorite = true,
                            isRecording = session != null,
                            recordingDuration = duration,
                            isReconnecting = isReconnecting,
                            onRecordClick = { onToggleRecording(station) },
                            onStopRecordingClick = if (session != null) { { onToggleRecording(station) } } else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .animateItem()
                        )
                    }
                }
                if (browseStations.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onBrowseHeaderClick() }
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
                        items = browseStations.take(10),
                        key = { "browse_${it.stationUuid}" }
                    ) { station ->
                        val session = activeSessions[station.stationUuid]
                        val duration by (session?.durationSeconds ?: kotlinx.coroutines.flow.flowOf(0L)).collectAsStateWithLifecycle(initialValue = 0L)
                        val isReconnecting by (session?.isReconnecting ?: kotlinx.coroutines.flow.flowOf(false)).collectAsStateWithLifecycle(initialValue = false)
                        StationListCard(
                            station = station,
                            onClick = { onBrowseStationClick(station) },
                            isFavorite = libraryUuids.contains(station.stationUuid),
                            isRecording = session != null,
                            recordingDuration = duration,
                            isReconnecting = isReconnecting,
                            onRecordClick = { onToggleRecording(station) },
                            onStopRecordingClick = if (session != null) { { onToggleRecording(station) } } else null,
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
}
