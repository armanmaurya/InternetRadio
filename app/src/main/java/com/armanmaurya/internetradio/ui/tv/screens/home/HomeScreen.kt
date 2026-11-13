package com.armanmaurya.internetradio.ui.tv.screens.home

import android.view.KeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import coil3.compose.AsyncImage
import coil3.request.crossfade
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.basicMarquee
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.res.painterResource
import com.armanmaurya.internetradio.R
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.tv.material3.DrawerValue
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.delay
import com.armanmaurya.internetradio.ui.tv.screensaver.NowPlayingScreensaver
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.android.awaitFrame
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.input.ImeAction
import androidx.tv.material3.Border
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.SelectableSurfaceDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import com.armanmaurya.internetradio.ui.shared.viewmodels.PlayerViewModel
import com.armanmaurya.internetradio.ui.mobile.screens.home.HomeViewModel

import com.armanmaurya.internetradio.ui.shared.viewmodels.BrowseViewModel
import com.armanmaurya.internetradio.ui.shared.viewmodels.LibraryViewModel
import com.armanmaurya.internetradio.ui.shared.viewmodels.RecentViewModel
import com.armanmaurya.internetradio.ui.shared.viewmodels.SettingsViewModel
import com.armanmaurya.internetradio.ui.shared.theme.TvInternetRadioTheme
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.armanmaurya.internetradio.ui.tv.navigation.AppDestination
import com.armanmaurya.internetradio.ui.tv.navigation.AppNavHost

@Composable
fun HomeScreen(
    browseViewModel: BrowseViewModel = hiltViewModel(),
    recentViewModel: RecentViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    openPlayerRequests: Flow<Unit> = emptyFlow()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination?.route ?: AppDestination.Browse.route

    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    // Read once from stored settings. Building the graph from the not-yet-loaded defaults and then
    // changing it would rebuild navigation and drop the back stack (including one restored after
    // the app was killed in the background). A new default tab applies on the next launch.
    val startDestination by produceState<String?>(initialValue = null) {
        // Unreadable settings fall back to Browse rather than leaving the app without a screen.
        val defaultTab = try {
            settingsViewModel.loadPreferences().defaultTab
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            0
        }
        value = when (defaultTab) {
            1 -> AppDestination.Recent.route
            2 -> AppDestination.Library.route
            else -> AppDestination.Browse.route
        }
    }

    // Same as the phone: resume the last station on launch when "Auto play on start" is on.
    LaunchedEffect(Unit) {
        playerViewModel.autoPlayRecentStationIfEnabled()
    }

    // Open the player when asked from outside (media notification, the TV's now-playing card).
    // Waits for the navigation graph; navigation errors are ignored rather than crashing.
    val hasGraph = startDestination != null
    LaunchedEffect(hasGraph) {
        if (!hasGraph) return@LaunchedEffect
        awaitFrame()
        openPlayerRequests.collect {
            try {
                navController.navigate(AppDestination.Player.route) { launchSingleTop = true }
            } catch (_: Exception) {
            }
        }
    }

    // Forward search query from HomeViewModel -> BrowseViewModel, RecentViewModel, and LibraryViewModel
    LaunchedEffect(homeUiState.searchQuery) {
        browseViewModel.onSearchQueryChange(homeUiState.searchQuery)
        recentViewModel.onSearchQueryChange(homeUiState.searchQuery)
        libraryViewModel.onSearchQueryChange(homeUiState.searchQuery)
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Open)
    val coroutineScope = rememberCoroutineScope()
    val contentFocusRequester = remember { FocusRequester() }
    
    val playbackState by playerViewModel.uiState.collectAsStateWithLifecycle()
    
    // Helper for drawer navigation
    val navigateToDrawerItem: (String) -> Unit = navigate@{ route ->
        // The graph is set only once the start screen is known; navigating before that would throw.
        if (startDestination == null) return@navigate
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Choosing a tab moves focus into the new screen, which also closes the drawer
    // (it only closes once focus leaves it).
    val openFromDrawer: (String) -> Unit = { route ->
        navigateToDrawerItem(route)
        coroutineScope.launch {
            // Wait for the screen-change animation to finish. Until then the old screen is still
            // there, and focus handed over early lands in it and is lost when it goes away.
            withTimeoutOrNull(2_000L) {
                navController.visibleEntries.first { entries ->
                    entries.size == 1 && entries.single().destination.route == route
                }
            }
            awaitFrame()
            try {
                contentFocusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    // ── In-app screensaver ──────────────────────────────────────────────
    // Shows after the chosen minutes without input while something plays. The key that wakes it
    // (and its release) is swallowed so it doesn't also act on the screen underneath.
    var lastInputAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isScreensaverOn by remember { mutableStateOf(false) }
    var swallowKeyUp by remember { mutableStateOf<Key?>(null) }
    val screensaverMinutes = settingsUiState.tvScreensaverMinutes
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val isResumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(lastInputAt, playbackState.isPlaying, screensaverMinutes, isResumed) {
        // In the background (e.g. sitting in recent apps) the timer stops, and coming back
        // starts a fresh wait instead of opening straight into the screensaver.
        if (!isResumed) {
            isScreensaverOn = false
            return@LaunchedEffect
        }
        if (screensaverMinutes > 0 && playbackState.isPlaying) {
            delay(screensaverMinutes * 60_000L)
            isScreensaverOn = true
        }
    }

    TvInternetRadioTheme(appPreferences = settingsUiState) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    lastInputAt = System.currentTimeMillis()
                    when {
                        isScreensaverOn && event.type == KeyEventType.KeyDown -> {
                            isScreensaverOn = false
                            swallowKeyUp = event.key
                            true
                        }
                        event.type == KeyEventType.KeyUp && swallowKeyUp == event.key -> {
                            swallowKeyUp = null
                            true
                        }
                        else -> false
                    }
                }
        ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = { drawerValue ->
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(max = 280.dp)
                            .background(
                                if (currentDestination == AppDestination.Player.route && drawerValue == DrawerValue.Closed) {
                                    Color.Transparent
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
                            .padding(12.dp)
                    ) {
                        if (playbackState.currentStation != null) {
                            val miniplayerWidth by androidx.compose.animation.core.animateDpAsState(
                                targetValue = if (drawerValue == DrawerValue.Open) 256.dp else 56.dp,
                                label = "miniplayerWidth"
                            )
                            Surface(
                                onClick = { openFromDrawer(AppDestination.Player.route) },
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.Transparent,
                                    focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                                    focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
                                    pressedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                                    pressedContentColor = MaterialTheme.colorScheme.inverseOnSurface
                                ),
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                modifier = Modifier
                                    .padding(bottom = 16.dp)
                                    .width(miniplayerWidth)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(4.dp)
                                ) {
                                    val fallbackPainter = painterResource(id = R.drawable.ic_launcher_foreground)
                                    val hasCoverArt = !playbackState.trackCoverArtUri.isNullOrBlank()
                                    val miniplayerArt = if (hasCoverArt) {
                                        playbackState.trackCoverArtUri
                                    } else {
                                        playbackState.currentStation!!.favicon.ifEmpty { null }
                                    }
                                    Box {
                                        AsyncImage(
                                            model = coil3.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                                .data(miniplayerArt)
                                                .size(coil3.size.Size.ORIGINAL)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = null,
                                            filterQuality = FilterQuality.High,
                                            placeholder = fallbackPainter,
                                            error = fallbackPainter,
                                            fallback = fallbackPainter,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentScale = ContentScale.Crop
                                        )
                                        
                                        if (playbackState.isPlaying || playbackState.isLoading) {
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.Black.copy(alpha = 0.4f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                com.armanmaurya.internetradio.ui.mobile.screens.home.components.PlayingVisualizer(
                                                    isPlaybackActive = playbackState.isPlaying,
                                                    barCount = 3
                                                )
                                            }
                                        }

                                        if (hasCoverArt && playbackState.currentStation!!.favicon.isNotBlank()) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(2.dp)
                                                    .size(18.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(MaterialTheme.colorScheme.surface)
                                                    .border(
                                                        1.dp,
                                                        Color.White.copy(alpha = 0.5f),
                                                        RoundedCornerShape(4.dp)
                                                    )
                                            ) {
                                                AsyncImage(
                                                    model = coil3.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                                        .data(playbackState.currentStation!!.favicon)
                                                        .size(coil3.size.Size.ORIGINAL)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = stringResource(R.string.schedule_station_logo_cd),
                                                    filterQuality = FilterQuality.High,
                                                    placeholder = fallbackPainter,
                                                    error = fallbackPainter,
                                                    fallback = fallbackPainter,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                        }
                                    }

                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = drawerValue == DrawerValue.Open
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = playbackState.currentStation!!.name,
                                                    maxLines = 1,
                                                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                                if (!playbackState.currentTrack.isNullOrEmpty()) {
                                                    Text(
                                                        text = playbackState.currentTrack!!,
                                                        maxLines = 1,
                                                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.7f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }


                        var isSearchEditing by remember { mutableStateOf(false) }
                        val searchFocusRequester = remember { FocusRequester() }
                        val searchBarWidth by androidx.compose.animation.core.animateDpAsState(
                            targetValue = if (drawerValue == DrawerValue.Open) 256.dp else 56.dp,
                            label = "searchBarWidth"
                        )

                        if (isSearchEditing && drawerValue == DrawerValue.Open) {
                            var hasGainedFocus by remember { mutableStateOf(false) }
                            OutlinedTextField(
                                value = homeUiState.searchQuery,
                                onValueChange = { homeViewModel.onSearchQueryChange(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                                    .focusRequester(searchFocusRequester)
                                    .onFocusChanged { state ->
                                        if (state.isFocused) {
                                            hasGainedFocus = true
                                        } else if (hasGainedFocus) {
                                            isSearchEditing = false
                                        }
                                    },
                                placeholder = { Text(stringResource(R.string.general_search)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = stringResource(R.string.cd_search)
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Search
                                ),
                                keyboardActions = KeyboardActions(
                                    onSearch = { isSearchEditing = false }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.border,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedPlaceholderColor = Color.Gray,
                                    unfocusedPlaceholderColor = Color.Gray
                                )
                            )
                            LaunchedEffect(Unit) {
                                searchFocusRequester.requestFocus()
                            }
                            androidx.activity.compose.BackHandler {
                                isSearchEditing = false
                            }
                        } else {
                            Surface(
                                onClick = { 
                                    isSearchEditing = true
                                    coroutineScope.launch { drawerState.setValue(DrawerValue.Open) }
                                },
                                modifier = Modifier
                                    .padding(bottom = 12.dp)
                                    .width(searchBarWidth),
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.Transparent,
                                    focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                                    focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
                                ),
                                border = ClickableSurfaceDefaults.border(
                                    border = Border(
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.border),
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                    focusedBorder = Border(
                                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = if (drawerValue == DrawerValue.Open) Arrangement.Start else Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = stringResource(R.string.cd_search)
                                    )
                                    if (drawerValue == DrawerValue.Open) {
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = if (homeUiState.searchQuery.isNotEmpty()) homeUiState.searchQuery else stringResource(R.string.general_search),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        if (drawerValue == DrawerValue.Open) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                            ) {
                                Surface(
                                    selected = currentDestination == AppDestination.Tags.route,
                                    onClick = { navigateToDrawerItem(AppDestination.Tags.route) },
                                    modifier = Modifier.weight(1f).height(56.dp),
                                    scale = SelectableSurfaceDefaults.scale(focusedScale = 1f),
                                    shape = SelectableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                    colors = SelectableSurfaceDefaults.colors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        focusedSelectedContainerColor = MaterialTheme.colorScheme.inverseSurface
                                    )
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Sell,
                                                contentDescription = stringResource(R.string.home_cd_select_tags)
                                            )
                                            if (homeUiState.selectedTags.isNotEmpty()) {
                                                Text(
                                                    text = homeUiState.selectedTags.size.toString(),
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = androidx.tv.material3.LocalContentColor.current,
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .offset(x = 6.dp, y = (-4).dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                Surface(
                                    selected = currentDestination == AppDestination.Language.route,
                                    onClick = { navigateToDrawerItem(AppDestination.Language.route) },
                                    modifier = Modifier.weight(1f).height(56.dp),
                                    scale = SelectableSurfaceDefaults.scale(focusedScale = 1f),
                                    shape = SelectableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                    colors = SelectableSurfaceDefaults.colors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        focusedSelectedContainerColor = MaterialTheme.colorScheme.inverseSurface
                                    )
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Translate,
                                                contentDescription = stringResource(R.string.home_cd_select_language)
                                            )
                                            if (!homeUiState.selectedLanguage.isNullOrBlank()) {
                                                Text(
                                                    text = homeUiState.selectedLanguage!!.take(3).uppercase(),
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = androidx.tv.material3.LocalContentColor.current,
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .offset(x = 6.dp, y = (-4).dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                Surface(
                                    selected = currentDestination == AppDestination.Country.route,
                                    onClick = { navigateToDrawerItem(AppDestination.Country.route) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .focusProperties { right = contentFocusRequester },
                                    scale = SelectableSurfaceDefaults.scale(focusedScale = 1f),
                                    shape = SelectableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                    colors = SelectableSurfaceDefaults.colors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        focusedSelectedContainerColor = MaterialTheme.colorScheme.inverseSurface
                                    )
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Public,
                                                contentDescription = stringResource(R.string.home_cd_select_country)
                                            )
                                            if (!homeUiState.selectedCountryCode.isNullOrBlank()) {
                                                Text(
                                                    text = homeUiState.selectedCountryCode!!,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = androidx.tv.material3.LocalContentColor.current,
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .offset(x = 6.dp, y = (-4).dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            NavigationDrawerItem(
                                selected = currentDestination == AppDestination.Tags.route || 
                                           currentDestination == AppDestination.Language.route || 
                                           currentDestination == AppDestination.Country.route,
                                onClick = { coroutineScope.launch { drawerState.setValue(DrawerValue.Open) } },
                                shape = NavigationDrawerItemDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = stringResource(R.string.home_use_filters),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                modifier = Modifier
                                    .padding(bottom = 16.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                            ) {}
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        NavigationDrawerItem(
                            selected = currentDestination == AppDestination.Browse.route,
                            onClick = { openFromDrawer(AppDestination.Browse.route) },
                            shape = NavigationDrawerItemDefaults.shape(shape = RoundedCornerShape(8.dp)),
                            leadingContent = {
                                Icon(imageVector = Icons.Default.Explore, contentDescription = stringResource(R.string.home_tab_browse))
                            }
                        ) {
                            Text(stringResource(R.string.home_tab_browse))
                        }
                        
                        NavigationDrawerItem(
                            selected = currentDestination == AppDestination.Recent.route,
                            onClick = { openFromDrawer(AppDestination.Recent.route) },
                            shape = NavigationDrawerItemDefaults.shape(shape = RoundedCornerShape(8.dp)),
                            leadingContent = {
                                Icon(imageVector = Icons.Default.History, contentDescription = stringResource(R.string.home_tab_recent))
                            }
                        ) {
                            Text(stringResource(R.string.home_tab_recent))
                        }

                        NavigationDrawerItem(
                            selected = currentDestination == AppDestination.Library.route,
                            onClick = { openFromDrawer(AppDestination.Library.route) },
                            shape = NavigationDrawerItemDefaults.shape(shape = RoundedCornerShape(8.dp)),
                            leadingContent = {
                                Icon(imageVector = Icons.Default.LibraryMusic, contentDescription = stringResource(R.string.home_tab_library))
                            }
                        ) {
                            Text(stringResource(R.string.home_tab_library))
                        }



                        Spacer(modifier = Modifier.weight(1f))

                        NavigationDrawerItem(
                            selected = currentDestination == AppDestination.Settings.route,
                            onClick = { openFromDrawer(AppDestination.Settings.route) },
                            shape = NavigationDrawerItemDefaults.shape(shape = RoundedCornerShape(8.dp)),
                            leadingContent = {
                                Icon(imageVector = Icons.Default.Settings, contentDescription = stringResource(R.string.home_cd_settings))
                            }
                        ) {
                            Text(stringResource(R.string.settings_title))
                        }
                    }
                }
            ) {
                // Content area
                val contentPadding = if (currentDestination == AppDestination.Player.route) 0.dp else 96.dp
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = contentPadding)
                        .focusRequester(contentFocusRequester)
                        .focusRestorer()
                ) {
                    val start = startDestination
                    if (start != null) AppNavHost(
                        navController = navController,
                        browseViewModel = browseViewModel,
                        recentViewModel = recentViewModel,
                        libraryViewModel = libraryViewModel,
                        playerViewModel = playerViewModel,
                        homeViewModel = homeViewModel,
                        startDestination = start,
                        isVolumeBoostEnabled = settingsUiState.enableVolumeBoost,
                        isLyricsOpen = settingsUiState.tvLyricsOpen,
                        onLyricsOpenChange = settingsViewModel::setTvLyricsOpen
                    )
                }
            }
        }

        if (isScreensaverOn) {
            NowPlayingScreensaver(
                station = playbackState.currentStation,
                trackTitle = playbackState.cleanTrackName ?: playbackState.currentTrack,
                artist = playbackState.cleanArtistName,
                coverArtUri = playbackState.trackCoverArtUri
            )
        }
        }
    }
}
