package com.armanmaurya.internetradio.ui.mobile

import android.content.Intent
import android.widget.Toast
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
import com.armanmaurya.internetradio.ui.mobile.components.rememberManualUpdateChecker
import com.armanmaurya.internetradio.ui.mobile.navigation.AppDestination
import com.armanmaurya.internetradio.ui.mobile.navigation.AppIntentHandler
import com.armanmaurya.internetradio.ui.mobile.navigation.AppNavHost
import com.armanmaurya.internetradio.ui.mobile.screens.home.HomeViewModel
import com.armanmaurya.internetradio.ui.mobile.screens.player.PlayerBottomSheet
import com.armanmaurya.internetradio.ui.mobile.screens.player.rememberPlayerSheetProgress
import com.armanmaurya.internetradio.ui.shared.theme.InternetRadioTheme
import com.armanmaurya.internetradio.ui.shared.theme.LocalAppPreferences
import androidx.compose.runtime.CompositionLocalProvider
import com.armanmaurya.internetradio.ui.shared.viewmodels.MainViewModel
import com.armanmaurya.internetradio.ui.shared.viewmodels.PlayerViewModel
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileApp(
    intentFlow: Flow<Intent>,
    widthSizeClass: WindowWidthSizeClass,
    mainViewModel: MainViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val appPreferences by mainViewModel.appPreferences.collectAsStateWithLifecycle(initialValue = AppPreferences())
    val uiState by playerViewModel.uiState.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalAppPreferences provides appPreferences) {
        InternetRadioTheme(appPreferences = appPreferences) {
        AppOverlays(
            mainViewModel = mainViewModel
        )

        val navController = rememberNavController()
        val scaffoldState = rememberBottomSheetScaffoldState(
            bottomSheetState = rememberStandardBottomSheetState(
                initialValue = SheetValue.PartiallyExpanded,
                skipHiddenState = false
            )
        )

        AppIntentHandler(
            intentFlow = intentFlow,
            navController = navController,
            scaffoldState = scaffoldState,
            playerViewModel = playerViewModel,
            homeViewModel = homeViewModel
        )

        val localContext = LocalContext.current
        val recordingSavedMessage = stringResource(R.string.player_recording_saved)
        LaunchedEffect(Unit) {
            playerViewModel.recordingSavedEvent.collect {
                Toast.makeText(localContext, recordingSavedMessage, Toast.LENGTH_SHORT).show()
            }
        }

        LaunchedEffect(Unit) {
            playerViewModel.autoPlayRecentStationIfEnabled()
        }

        val onCheckUpdates = rememberManualUpdateChecker(mainViewModel)

        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val sheetPeekHeight = if (uiState.currentStation != null) 72.dp + bottomInset else 0.dp
        val isExpanded = widthSizeClass == WindowWidthSizeClass.Expanded

        val progress by rememberPlayerSheetProgress(scaffoldState, bottomInset)
        val cornerRadius = lerp(28.dp, 0.dp, progress)

        BottomSheetScaffold(
            modifier = Modifier.imePadding(),
            scaffoldState = scaffoldState,
            sheetPeekHeight = sheetPeekHeight,
            sheetMaxWidth = Dp.Unspecified,
            sheetDragHandle = null,
            sheetShape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius),
            sheetContent = {
                PlayerBottomSheet(
                    scaffoldState = scaffoldState,
                    progress = progress,
                    isWidescreen = isExpanded,
                    bottomInset = bottomInset,
                    keepScreenOn = appPreferences.keepScreenOn,
                    onEditStation = { station ->
                        navController.navigate(AppDestination.EditStation.createRoute(station.stationUuid))
                    },
                    playerViewModel = playerViewModel
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
