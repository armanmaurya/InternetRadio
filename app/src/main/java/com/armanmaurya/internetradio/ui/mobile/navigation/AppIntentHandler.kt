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
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.ui.mobile.screens.home.HomeViewModel
import com.armanmaurya.internetradio.ui.shared.utils.ShortcutHelper
import com.armanmaurya.internetradio.ui.shared.viewmodels.PlayerViewModel
import com.google.gson.Gson
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
    homeViewModel: HomeViewModel
) {
    val scope = rememberCoroutineScope()

    fun expandPlayerSheet() {
        scope.launch {
            if (playerViewModel.uiState.value.currentStation == null) {
                withTimeoutOrNull(2500L) {
                    playerViewModel.uiState.first { it.currentStation != null }
                }
            }
            if (playerViewModel.uiState.value.currentStation != null) {
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

    LaunchedEffect(Unit) {
        intentFlow.collect { intent ->
            if (intent.action == ShortcutHelper.ACTION_PLAY_STATION) {
                intent.action = null
                val json = intent.getStringExtra(ShortcutHelper.EXTRA_STATION_JSON)
                if (json != null) {
                    try {
                        val station = Gson().fromJson(json, RadioStation::class.java)
                        playerViewModel.play(listOf(station), 0)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else if (intent.getStringExtra("open_tab") == "recordings") {
                intent.removeExtra("open_tab")
                navController.popBackStack(AppDestination.Discover.route, inclusive = false)
                homeViewModel.onTabSelected(3)
            } else if (intent.action == "com.armanmaurya.internetradio.ACTION_OPEN_PLAYER" || intent.getBooleanExtra("open_player_sheet", false)) {
                intent.action = null
                intent.removeExtra("open_player_sheet")
                expandPlayerSheet()
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
