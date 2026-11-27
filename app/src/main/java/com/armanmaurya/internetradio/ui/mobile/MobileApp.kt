package com.armanmaurya.internetradio.ui.mobile

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.AppPreferences
import com.armanmaurya.internetradio.ui.mobile.components.AppOverlays
import com.armanmaurya.internetradio.ui.mobile.components.LocalShortcutController
import com.armanmaurya.internetradio.ui.mobile.components.rememberManualUpdateChecker
import com.armanmaurya.internetradio.ui.mobile.navigation.AppDestination
import com.armanmaurya.internetradio.ui.mobile.navigation.AppIntentHandler
import com.armanmaurya.internetradio.ui.mobile.navigation.AppNavHost
import com.armanmaurya.internetradio.ui.mobile.screens.home.HomeViewModel
import com.armanmaurya.internetradio.ui.mobile.screens.player.PlayerBottomSheet
import com.armanmaurya.internetradio.ui.mobile.screens.player.components.MINI_MODE_BAR_HEIGHT
import com.armanmaurya.internetradio.ui.mobile.screens.player.rememberPlayerSheetProgress
import com.armanmaurya.internetradio.ui.shared.theme.InternetRadioTheme
import com.armanmaurya.internetradio.ui.shared.theme.LocalAppPreferences
import androidx.compose.runtime.CompositionLocalProvider
import com.armanmaurya.internetradio.ui.shared.viewmodels.MainViewModel
import com.armanmaurya.internetradio.ui.shared.viewmodels.PlayerViewModel
import com.armanmaurya.internetradio.ui.shared.viewmodels.RecordingPlayerViewModel
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileApp(
    intentFlow: Flow<Intent>,
    widthSizeClass: WindowWidthSizeClass,
    mainViewModel: MainViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
    recordingPlayerViewModel: RecordingPlayerViewModel = hiltViewModel()
) {
    val appPreferences by mainViewModel.appPreferences.collectAsStateWithLifecycle(initialValue = AppPreferences())
    val uiState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val recordingUiState by recordingPlayerViewModel.uiState.collectAsStateWithLifecycle()

    CompositionLocalProvider(
        LocalAppPreferences provides appPreferences,
        LocalShortcutController provides mainViewModel.shortcutController
    ) {
        InternetRadioTheme(appPreferences = appPreferences) {
        AppOverlays(
            mainViewModel = mainViewModel
        )

        val navController = rememberNavController()
        val scaffoldState = rememberBottomSheetScaffoldState(
            bottomSheetState = rememberStandardBottomSheetState(
                initialValue = SheetValue.Hidden,
                skipHiddenState = false
            )
        )

        AppIntentHandler(
            intentFlow = intentFlow,
            navController = navController,
            scaffoldState = scaffoldState,
            playerViewModel = playerViewModel,
            recordingPlayerViewModel = recordingPlayerViewModel,
            homeViewModel = homeViewModel
        )

        val localContext = LocalContext.current
        val recordingSavedMessage = stringResource(R.string.player_recording_saved)
        LaunchedEffect(Unit) {
            playerViewModel.recordingSavedEvent.collect {
                Toast.makeText(localContext, recordingSavedMessage, Toast.LENGTH_SHORT).show()
            }
        }

        val shortcutStationMissingMessage = stringResource(R.string.shortcut_station_not_in_library)
        LaunchedEffect(Unit) {
            playerViewModel.shortcutStationMissingEvent.collect {
                Toast.makeText(localContext, shortcutStationMissingMessage, Toast.LENGTH_SHORT).show()
            }
        }

        LaunchedEffect(Unit) {
            playerViewModel.autoPlayRecentStationIfEnabled()
        }

        val onCheckUpdates = rememberManualUpdateChecker(mainViewModel)

        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val hasActiveMedia = uiState.currentStation != null || recordingUiState.currentRecording != null
        val hasBothModes = uiState.currentStation != null && recordingUiState.currentRecording != null
        val extraMiniHeight by animateDpAsState(
            targetValue = if (hasBothModes) MINI_MODE_BAR_HEIGHT else 0.dp,
            label = "miniModeBarPeekHeight"
        )
        val sheetPeekHeight = if (hasActiveMedia) 72.dp + extraMiniHeight + bottomInset else 0.dp
        val isExpanded = widthSizeClass == WindowWidthSizeClass.Expanded

        val progress by rememberPlayerSheetProgress(scaffoldState, bottomInset, extraMiniHeight)
        // While a player dialog (cast, volume, sleep timer, track) is open, the sheet can't be dragged.
        var isPlayerDialogOpen by remember { mutableStateOf(false) }
        val cornerRadius = lerp(28.dp, 0.dp, progress)

        BottomSheetScaffold(
            modifier = Modifier.imePadding(),
            scaffoldState = scaffoldState,
            sheetPeekHeight = sheetPeekHeight,
            sheetMaxWidth = Dp.Unspecified,
            sheetDragHandle = null,
            sheetSwipeEnabled = !isPlayerDialogOpen,
            sheetShape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius),
            sheetContent = {
                PlayerBottomSheet(
                    scaffoldState = scaffoldState,
                    progress = progress,
                    isWidescreen = isExpanded,
                    bottomInset = bottomInset,
                    keepScreenOn = appPreferences.keepScreenOn,
                    onDialogOpenChange = { isPlayerDialogOpen = it },
                    onEditStation = { station ->
                        navController.navigate(AppDestination.EditStation.createRoute(station.stationUuid))
                    },
                    onNavigateToRecording = { recording ->
                        navController.popBackStack(AppDestination.Discover.route, inclusive = false)
                        homeViewModel.onTabSelected(3)
                        homeViewModel.routeToRecording(recording.stationName, recording.uri.toString())
                    },
                    playerViewModel = playerViewModel,
                    recordingPlayerViewModel = recordingPlayerViewModel
                )
            }
        ) { innerPadding ->
            AppNavHost(
                navController = navController,
                widthSizeClass = widthSizeClass,
                contentPadding = innerPadding,
                modifier = Modifier.fillMaxSize(),
                onCheckUpdates = onCheckUpdates
            )
        }
    }
}
}
