package com.armanmaurya.internetradio

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.data.repository.SettingsRepository
import com.armanmaurya.internetradio.ui.mobile.navigation.AppNavHost
import com.armanmaurya.internetradio.ui.mobile.navigation.AppDestination
import com.armanmaurya.internetradio.ui.mobile.screens.player.PlayerSheetContent
import com.armanmaurya.internetradio.ui.shared.viewmodels.PlayerViewModel
import com.armanmaurya.internetradio.ui.shared.theme.InternetRadioTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import javax.inject.Inject

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import com.armanmaurya.internetradio.ui.shared.viewmodels.MainViewModel
import com.armanmaurya.internetradio.ui.shared.components.UpdateBottomSheet

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            startActivity(Intent(this, TvActivity::class.java))
            finish()
            return
        }

        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Sync whatever locale is currently active (set by our settings or system App Info)
        // back to DataStore so our UI always reflects the real current language.
        val currentLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        val activeTag = if (currentLocales.isEmpty) "System" else currentLocales[0]?.toLanguageTag() ?: "System"
        lifecycleScope.launch {
            settingsRepository.setAppLanguage(activeTag)
        }
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val updateAvailable by mainViewModel.updateAvailable.collectAsStateWithLifecycle()
            
            LaunchedEffect(Unit) {
                val versionName = try {
                    packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0"
                } catch (e: Exception) {
                    "0.0.0"
                }
                mainViewModel.checkForUpdates(versionName)
            }
            
            val appPreferences by settingsRepository.appPreferencesFlow
                .collectAsStateWithLifecycle(initialValue = com.armanmaurya.internetradio.data.model.AppPreferences())

            InternetRadioTheme(appPreferences = appPreferences) {
                updateAvailable?.let { release ->
                    UpdateBottomSheet(
                        release = release,
                        onDismiss = { mainViewModel.dismissUpdate() },
                        onConfirm = { 
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.html_url)))
                        }
                    )
                }
                
                val navController = rememberNavController()
                val playerViewModel: PlayerViewModel = hiltViewModel()
                val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()

                val scope = rememberCoroutineScope()
                val scaffoldState = rememberBottomSheetScaffoldState(
                    bottomSheetState = rememberStandardBottomSheetState(
                        initialValue = SheetValue.PartiallyExpanded,
                        skipHiddenState = false
                    )
                )

                // Handle Swipe to Dismiss (Stop playback when swiped away)
                LaunchedEffect(scaffoldState.bottomSheetState.currentValue) {
                    if (scaffoldState.bottomSheetState.currentValue == SheetValue.Hidden && playbackState.currentStation != null) {
                        playerViewModel.stop()
                    }
                }

                // Handle Re-appearing (Show player when a station starts playing) and Hiding (when playback stops)
                LaunchedEffect(playbackState.currentStation) {
                    if (playbackState.currentStation != null && scaffoldState.bottomSheetState.currentValue == SheetValue.Hidden) {
                        scaffoldState.bottomSheetState.partialExpand()
                    } else if (playbackState.currentStation == null && scaffoldState.bottomSheetState.currentValue != SheetValue.Hidden) {
                        scaffoldState.bottomSheetState.hide()
                    }
                }

                val density = LocalDensity.current
                val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val sheetPeekHeight = if (playbackState.currentStation != null) 72.dp + bottomInset else 0.dp

                val onCheckUpdates: () -> Unit = {
                    runOnUiThread {
                        android.widget.Toast.makeText(this@MainActivity, getString(R.string.settings_checking_for_updates), android.widget.Toast.LENGTH_SHORT).show()
                    }
                    val vName = try {
                        packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0"
                    } catch (e: Exception) {
                        "0.0.0"
                    }
                    mainViewModel.checkForUpdates(vName, force = true) { hasUpdate ->
                        if (!hasUpdate) {
                            runOnUiThread {
                                android.widget.Toast.makeText(this@MainActivity, getString(R.string.settings_no_update_available), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                val windowSizeClass = calculateWindowSizeClass(this@MainActivity)
                val widthSizeClass = windowSizeClass.widthSizeClass
                val isExpanded = widthSizeClass == WindowWidthSizeClass.Expanded


                BottomSheetScaffold(
                    scaffoldState = scaffoldState,
                    sheetPeekHeight = sheetPeekHeight,
                    sheetMaxWidth = androidx.compose.ui.unit.Dp.Unspecified,
                    sheetDragHandle = null,
                    sheetContent = {
                        val configuration = LocalConfiguration.current
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .heightIn(min = 72.dp + bottomInset)
                        ) {
                            val fullHeight = with(density) { configuration.screenHeightDp.dp.toPx() }
                            val peekHeightPx = with(density) { (72.dp + bottomInset).toPx() }
                            
                            val progress by remember(fullHeight, peekHeightPx) {
                                derivedStateOf {
                                    val currentOffset = try {
                                        scaffoldState.bottomSheetState.requireOffset()
                                    } catch (e: Exception) {
                                        fullHeight - peekHeightPx
                                    }
                                    
                                    val totalRange = fullHeight - peekHeightPx
                                    if (totalRange > 0) {
                                        (1f - (currentOffset / totalRange)).coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }
                                }
                            }

                            val isFavorite by playerViewModel.isFavorite.collectAsStateWithLifecycle()
                            val trackHistory by playerViewModel.trackHistory.collectAsStateWithLifecycle()
                            val stationRecordings by playerViewModel.stationRecordings.collectAsStateWithLifecycle()
                            val isRecording by playerViewModel.isRecording.collectAsStateWithLifecycle()
                            val recordingDuration by playerViewModel.recordingDuration.collectAsStateWithLifecycle()
                            val amplitude by playerViewModel.amplitude.collectAsStateWithLifecycle()

                            PlayerSheetContent(
                                isWidescreen = isExpanded,
                                playbackState = playbackState,
                                isFavorite = isFavorite,
                                trackHistory = trackHistory,
                                stationRecordings = stationRecordings,
                                progress = progress,
                                onTogglePlayPause = playerViewModel::togglePlayPause,
                                onToggleFavorite = playerViewModel::toggleFavorite,
                                onSetSleepTimer = playerViewModel::setSleepTimer,
                                onCancelSleepTimer = playerViewModel::cancelSleepTimer,
                                onCollapse = {
                                    scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                                },
                                onExpand = {
                                    scope.launch { scaffoldState.bottomSheetState.expand() }
                                },
                                onNext = playerViewModel::next,
                                onPrevious = playerViewModel::previous,
                                onEditStation = { station ->
                                    scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                                    navController.navigate(AppDestination.EditStation.createRoute(station.stationUuid))
                                },
                                isRecording = isRecording,
                                recordingDuration = recordingDuration,
                                amplitude = amplitude,
                                onToggleRecording = playerViewModel::toggleRecording
                            )
                        }
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
}