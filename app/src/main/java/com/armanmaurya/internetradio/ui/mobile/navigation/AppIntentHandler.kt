package com.armanmaurya.internetradio.ui.mobile.navigation

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import com.armanmaurya.internetradio.domain.controller.ActivePlaybackMode
import com.armanmaurya.internetradio.domain.controller.ShortcutController
import com.armanmaurya.internetradio.service.RecordingPlaybackService
import com.armanmaurya.internetradio.ui.mobile.screens.home.HomeViewModel
import com.armanmaurya.internetradio.ui.shared.viewmodels.PlayerViewModel
import com.armanmaurya.internetradio.ui.shared.viewmodels.RecordingPlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppIntentHandler(
    intentFlow: Flow<Intent>,
    navController: NavHostController,
    scaffoldState: BottomSheetScaffoldState,
    playerViewModel: PlayerViewModel,
    recordingPlayerViewModel: RecordingPlayerViewModel,
    homeViewModel: HomeViewModel
) {
    val scope = rememberCoroutineScope()

    fun expandPlayerSheet(targetMode: ActivePlaybackMode = ActivePlaybackMode.STATION) {
        scope.launch {
            if (targetMode == ActivePlaybackMode.RECORDING) {
                if (recordingPlayerViewModel.uiState.value.currentRecording == null) {
                    withTimeoutOrNull(2500L) {
                        recordingPlayerViewModel.uiState.first { it.currentRecording != null }
                    }
                }
                if (recordingPlayerViewModel.uiState.value.currentRecording != null) {
                    recordingPlayerViewModel.setActiveMode(ActivePlaybackMode.RECORDING)
                    delay(100)
                    runCatching { scaffoldState.bottomSheetState.expand() }.onFailure {
                        delay(150)
                        runCatching { scaffoldState.bottomSheetState.expand() }
                    }
                }
            } else {
                if (playerViewModel.uiState.value.currentStation == null) {
                    withTimeoutOrNull(2500L) {
                        playerViewModel.uiState.first { it.currentStation != null }
                    }
                }
                if (playerViewModel.uiState.value.currentStation != null) {
                    recordingPlayerViewModel.setActiveMode(ActivePlaybackMode.STATION)
                    delay(100)
                    try {
                        scaffoldState.bottomSheetState.expand()
                    } catch (e: Exception) {
                        delay(150)
                        try {
                            scaffoldState.bottomSheetState.expand()
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        intentFlow.collect { intent ->
            if (intent.action == ShortcutController.ACTION_PLAY_STATION) {
                intent.action = null
                playerViewModel.markShortcutPlaybackInitiated()
                // Older shortcuts also carry a station JSON copy; shortcuts only play library stations, so it is ignored
                intent.getStringExtra(ShortcutController.EXTRA_STATION_UUID)?.let { stationUuid ->
                    playerViewModel.playFromShortcut(stationUuid)
                }
            } else if (intent.action == RecordingPlaybackService.ACTION_OPEN_RECORDING_PLAYER ||
                intent.getBooleanExtra("open_recording_player", false)
            ) {
                intent.action = null
                intent.removeExtra("open_recording_player")
                expandPlayerSheet(ActivePlaybackMode.RECORDING)
            } else if (intent.getStringExtra("open_tab") == "recordings") {
                intent.removeExtra("open_tab")
                val stationName = intent.getStringExtra("route_recording_station")
                val uriString = intent.getStringExtra("route_recording_uri")
                intent.removeExtra("route_recording_station")
                intent.removeExtra("route_recording_uri")
                navController.popBackStack(AppDestination.Discover.route, inclusive = false)
                homeViewModel.onTabSelected(3)
                homeViewModel.routeToRecording(stationName, uriString)
            } else if (intent.action == "com.armanmaurya.internetradio.ACTION_OPEN_PLAYER" || intent.getBooleanExtra("open_player_sheet", false)) {
                intent.action = null
                intent.removeExtra("open_player_sheet")
                expandPlayerSheet(ActivePlaybackMode.STATION)
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            playerViewModel.proceedWithRecording()
        }
    }

    LaunchedEffect(Unit) {
        playerViewModel.permissionRequestEvent.collect { _ ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                playerViewModel.proceedWithRecording()
            }
        }
    }
}
