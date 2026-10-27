package com.armanmaurya.internetradio.ui.mobile.screens.settings


import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import org.xmlpull.v1.XmlPullParser
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.material.icons.filled.ContentCopy
import coil3.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import kotlin.math.roundToInt
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.filled.Favorite
import com.armanmaurya.internetradio.core.config.StoreConfig
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Widgets
import com.armanmaurya.internetradio.ui.widget.NowPlayingWidgetConfigureActivity
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.MalformedBackupBottomSheet
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.VolumeBoostWarningBottomSheet
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.AppPreferences
import com.armanmaurya.internetradio.domain.model.ConflictStrategy
import com.armanmaurya.internetradio.domain.model.StartOfWeek
import com.armanmaurya.internetradio.ui.shared.viewmodels.SettingsViewModel
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.ExpandableItem
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.Item
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.OptionItem
import androidx.compose.foundation.isSystemInDarkTheme
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.PalettePickerItem
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.CustomColorPickerBottomSheet
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.Section
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.ToggleItem
import com.armanmaurya.internetradio.ui.shared.theme.AppColor
import com.armanmaurya.internetradio.ui.shared.theme.AppTheme
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onCheckUpdatesClick: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val uiState by viewModel.uiState.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val showMalformedBackupBottomSheet by viewModel.showMalformedBackupBottomSheet.collectAsState()
    val localStationsCount by viewModel.localStationsCount.collectAsState()

    // UI-only state for expand/collapse
    var expandedItem by remember { mutableStateOf<String?>(null) }
    var hasInitializedGradualVolume by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isAlarmVolumeTransitionEnabled) {
        if (!hasInitializedGradualVolume && uiState.isAlarmVolumeTransitionEnabled) {
            expandedItem = "GradualVolume"
            hasInitializedGradualVolume = true
        }
    }

    var showHistoryLimitDialog by remember { mutableStateOf(false) }

    // Toast feedback for backup/restore operations
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.backupResult.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Activity result launchers for file picker
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportLibrary(context, it) }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importLibraries(context, uris)
        }
    }
    val autoBackupFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.setAutoBackupFolder(context, it) }
    }

    val topShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
    val middleShape = RoundedCornerShape(4.dp)
    val bottomShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
    val singleShape = RoundedCornerShape(24.dp)

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .padding(bottom = contentPadding.calculateBottomPadding()),
        topBar = { SettingsTopBar(onBackClick = onBackClick, scrollBehavior = scrollBehavior) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            AppearanceSection(
                uiState = uiState,
                availableThemes = listOf(AppTheme.LIGHT, AppTheme.DARK, AppTheme.SYSTEM),
                expandedItem = expandedItem,
                onExpandedItemChange = { expandedItem = it },
                onSetDynamicTheme = viewModel::setDynamicTheme,
                onSetAppColor = viewModel::setAppColor,
                onSetCustomColor = viewModel::setCustomColor,
                onSetTheme = viewModel::setAppTheme,
                onSetPureBlack = viewModel::setPureBlack,
                onSetShowStationThumbnails = viewModel::setShowStationThumbnails,
                onOpenWidgetConfig = {
                    context.startActivity(Intent(context, NowPlayingWidgetConfigureActivity::class.java))
                },
                topShape = topShape,
                middleShape = middleShape,
                bottomShape = bottomShape
            )
            GeneralSection(
                uiState = uiState,
                languages = rememberAvailableLanguages(),
                expandedItem = expandedItem,
                onExpandedItemChange = { expandedItem = it },
                onSetLanguage = viewModel::setAppLanguage,
                onSetDefaultTab = viewModel::setDefaultTab,
                onSetAutoRouteToBrowseOnSearch = viewModel::setAutoRouteToBrowseOnSearch,
                onSetSelectAllTextOnFocus = viewModel::setSelectAllTextOnFocus,
                topShape = topShape,
                middleShape = middleShape,
                bottomShape = bottomShape
            )
            PlayerSection(
                uiState = uiState,
                expandedItem = expandedItem,
                onExpandedItemChange = { expandedItem = it },
                onSetAutoPlayOnStart = viewModel::setAutoPlayOnStart,
                onSetStopOnAudioBecomingNoisy = viewModel::setStopOnAudioBecomingNoisy,
                onSetPauseOnVolumeZero = viewModel::setPauseOnVolumeZero,
                onSetEnableVolumeBoost = viewModel::setEnableVolumeBoost,
                onSetKeepScreenOn = viewModel::setKeepScreenOn,
                onSetShowCoverArtInNotification = viewModel::setShowCoverArtInNotification,
                showHistoryLimitDialog = showHistoryLimitDialog,
                onToggleHistoryLimitDialog = { showHistoryLimitDialog = !showHistoryLimitDialog },
                onSetHistoryLimit = viewModel::setTrackHistoryLimit,
                onSetMaxRetryDuration = viewModel::setMaxRetryDuration,
                topShape = topShape,
                middleShape = middleShape,
                bottomShape = bottomShape,
                singleShape = singleShape
            )
            ScheduleSection(
                uiState = uiState,
                expandedItem = expandedItem,
                onExpandedItemChange = { expandedItem = it },
                onSetStartOfWeek = viewModel::setStartOfWeek,
                onSetAlarmVolumeTransitionEnabled = viewModel::setAlarmVolumeTransitionEnabled,
                onSetAlarmVolumeTransitionSeconds = viewModel::setAlarmVolumeTransitionSeconds,
                topShape = topShape,
                bottomShape = bottomShape
            )
            BackupSection(
                uiState = uiState,
                isSyncing = isSyncing,
                expandedItem = expandedItem,
                onExpandedItemChange = { expandedItem = it },
                onSetConflictStrategy = viewModel::setConflictStrategy,
                onExport = { exportLauncher.launch("stations.json") },
                onImport = { importLauncher.launch(arrayOf("application/json")) },
                onSelectAutoBackupFolder = { autoBackupFolderLauncher.launch(null) },
                onToggleAutoBackup = viewModel::setAutoBackupEnabled,
                onClearAutoBackupFolder = { viewModel.clearAutoBackupFolder(context) },
                onSyncNow = { viewModel.syncNow(context) },
                topShape = topShape,
                middleShape = middleShape,
                bottomShape = bottomShape
            )
            UpdateSection(
                uiState = uiState,
                onCheckUpdatesClick = onCheckUpdatesClick,
                onSetDisableUpdateCheck = viewModel::setDisableUpdateCheck,
                topShape = topShape,
                bottomShape = bottomShape
            )
            CommunitySection()
            val mainViewModel: com.armanmaurya.internetradio.ui.shared.viewmodels.MainViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            val versionName = mainViewModel.systemFacade.getAppVersionName()
            val versionCode = mainViewModel.systemFacade.getAppVersionCode().toString()

            AboutSection(
                shape = singleShape,
                topShape = topShape,
                bottomShape = bottomShape,
                versionName = versionName,
                versionCode = versionCode
            )

            DonateSection()

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showMalformedBackupBottomSheet) {
        MalformedBackupBottomSheet(
            localStationCount = localStationsCount,
            onDismissRequest = viewModel::dismissMalformedBackupBottomSheet,
            onOverwriteWithCurrentLibrary = { viewModel.overwriteMalformedBackup(context) },
            onChooseAnotherFolder = {
                viewModel.dismissMalformedBackupBottomSheet()
                autoBackupFolderLauncher.launch(null)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(
    onBackClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    CenterAlignedTopAppBar(
        title = { Text(stringResource(R.string.settings_title)) },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back)
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun AppearanceSection(
    uiState: AppPreferences,
    availableThemes: List<AppTheme>,
    expandedItem: String?,
    onExpandedItemChange: (String?) -> Unit,
    onSetDynamicTheme: (Boolean) -> Unit,
    onSetAppColor: (AppColor) -> Unit,
    onSetCustomColor: (Int) -> Unit,
    onSetTheme: (AppTheme) -> Unit,
    onSetPureBlack: (Boolean) -> Unit,
    onSetShowStationThumbnails: (Boolean) -> Unit,
    onOpenWidgetConfig: () -> Unit,
    topShape: RoundedCornerShape,
    middleShape: RoundedCornerShape,
    bottomShape: RoundedCornerShape
) {
    val isDynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val isDynamicActive = isDynamicColorSupported && uiState.useDynamicColor
    val darkTheme = when (uiState.themeMode) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    var showColorPickerSheet by remember { mutableStateOf(false) }

    if (showColorPickerSheet) {
        CustomColorPickerBottomSheet(
            initialColorArgb = uiState.customColorArgb,
            darkTheme = darkTheme,
            onColorApplied = { colorArgb ->
                onSetCustomColor(colorArgb)
                onSetAppColor(AppColor.CUSTOM)
                if (uiState.useDynamicColor) {
                    onSetDynamicTheme(false)
                }
            },
            onDismissRequest = { showColorPickerSheet = false }
        )
    }

    Section(title = stringResource(R.string.settings_appearance_section)) {
        if (isDynamicColorSupported) {
            ToggleItem(
                title = stringResource(R.string.settings_dynamic_theme_title),
                subtitle = stringResource(R.string.settings_dynamic_theme_subtitle),
                isEnabled = uiState.useDynamicColor,
                onToggle = onSetDynamicTheme,
                icon = Icons.Default.AutoAwesome,
                shape = topShape
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))
        }

        PalettePickerItem(
            selectedColor = uiState.appColor,
            isDynamicActive = isDynamicActive,
            onColorSelected = { color ->
                onSetAppColor(color)
                if (uiState.useDynamicColor) {
                    onSetDynamicTheme(false)
                }
            },
            darkTheme = darkTheme,
            customColorArgb = uiState.customColorArgb,
            onOpenCustomColorPicker = { showColorPickerSheet = true },
            shape = if (isDynamicColorSupported) middleShape else topShape
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

        ExpandableItem(
            title = stringResource(R.string.settings_theme_title),
            subtitle = uiState.themeMode.toDisplayString(),
            isExpanded = expandedItem == "Theme",
            onToggle = { onExpandedItemChange(if (expandedItem == "Theme") null else "Theme") },
            icon = Icons.Default.Brightness4,
            shape = middleShape
        ) {
            availableThemes.forEach { theme ->
                OptionItem(
                    label = theme.toDisplayString(),
                    isSelected = uiState.themeMode == theme,
                    onClick = { onSetTheme(theme) }
                )
            }
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

        ToggleItem(
            title = stringResource(R.string.settings_pure_black_title),
            subtitle = if (darkTheme) {
                stringResource(R.string.settings_pure_black_subtitle)
            } else {
                stringResource(R.string.settings_pure_black_disabled_subtitle)
            },
            isEnabled = uiState.pureBlack,
            onToggle = onSetPureBlack,
            icon = Icons.Default.Contrast,
            shape = middleShape,
            enabled = darkTheme
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

        ToggleItem(
            title = stringResource(R.string.settings_show_station_thumbnails),
            subtitle = stringResource(R.string.settings_show_station_thumbnails_desc),
            isEnabled = uiState.showStationThumbnails,
            onToggle = onSetShowStationThumbnails,
            icon = Icons.Default.Image,
            shape = middleShape
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

        Item(
            title = stringResource(R.string.settings_widget_appearance_title),
            subtitle = stringResource(R.string.settings_widget_appearance_subtitle),
            icon = Icons.Default.Widgets,
            onClick = onOpenWidgetConfig,
            shape = bottomShape
        )
    }
}

@Composable
private fun AppTheme.toDisplayString(): String = when (this) {
    AppTheme.LIGHT -> stringResource(R.string.settings_theme_light)
    AppTheme.DARK -> stringResource(R.string.settings_theme_dark)
    AppTheme.SYSTEM -> stringResource(R.string.settings_theme_system)
}

@Composable
private fun StartOfWeek.toDisplayString(): String = when (this) {
    StartOfWeek.SUNDAY -> stringResource(R.string.settings_start_week_sunday)
    StartOfWeek.MONDAY -> stringResource(R.string.settings_start_week_monday)
    StartOfWeek.FRIDAY -> stringResource(R.string.settings_start_week_friday)
    StartOfWeek.SATURDAY -> stringResource(R.string.settings_start_week_saturday)
}

@Composable
private fun GeneralSection(
    uiState: AppPreferences,
    languages: List<Pair<String, String>>,
    expandedItem: String?,
    onExpandedItemChange: (String?) -> Unit,
    onSetLanguage: (String) -> Unit,
    onSetDefaultTab: (Int) -> Unit,
    onSetAutoRouteToBrowseOnSearch: (Boolean) -> Unit,
    onSetSelectAllTextOnFocus: (Boolean) -> Unit,
    topShape: RoundedCornerShape,
    middleShape: RoundedCornerShape,
    bottomShape: RoundedCornerShape
) {
    val currentLocales = AppCompatDelegate.getApplicationLocales()
    val activeLanguageCode = if (currentLocales.isEmpty) {
        "System"
    } else {
        currentLocales[0]?.language ?: "System"
    }

    Section(title = stringResource(R.string.settings_general_section)) {
        ExpandableItem(
            title = stringResource(R.string.settings_language_title),
            subtitle = activeLanguageCode.getLanguageDisplayName(languages),
            isExpanded = expandedItem == "Language",
            onToggle = { onExpandedItemChange(if (expandedItem == "Language") null else "Language") },
            icon = Icons.Default.Translate,
            shape = topShape
        ) {
            languages.forEach { (code, name) ->
                OptionItem(
                    label = name,
                    isSelected = activeLanguageCode == code,
                    onClick = { onSetLanguage(code) }
                )
            }
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

        val tabs = listOf(
            stringResource(R.string.home_tab_browse),
            stringResource(R.string.home_tab_recent),
            stringResource(R.string.home_tab_library),
            stringResource(R.string.home_tab_recordings),
            stringResource(R.string.home_tab_schedules)
        )

        ExpandableItem(
            title = stringResource(R.string.settings_default_tab),
            subtitle = tabs.getOrNull(uiState.defaultTab) ?: stringResource(R.string.home_tab_browse),
            isExpanded = expandedItem == "DefaultTab",
            onToggle = { onExpandedItemChange(if (expandedItem == "DefaultTab") null else "DefaultTab") },
            icon = Icons.Default.StarRate,
            shape = middleShape
        ) {
            tabs.forEachIndexed { index, name ->
                OptionItem(
                    label = name,
                    isSelected = uiState.defaultTab == index,
                    onClick = { onSetDefaultTab(index) }
                )
            }
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

        ToggleItem(
            title = stringResource(R.string.settings_auto_route_search_title),
            subtitle = stringResource(R.string.settings_auto_route_search_subtitle),
            isEnabled = uiState.autoRouteToBrowseOnSearch,
            onToggle = onSetAutoRouteToBrowseOnSearch,
            icon = Icons.Default.Search,
            shape = middleShape
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

        ToggleItem(
            title = stringResource(R.string.settings_select_all_text_on_focus_title),
            subtitle = stringResource(R.string.settings_select_all_text_on_focus_subtitle),
            isEnabled = uiState.selectAllTextOnFocus,
            onToggle = onSetSelectAllTextOnFocus,
            icon = Icons.Default.Search,
            shape = bottomShape
        )
    }
}

@Composable
private fun PlayerSection(
    uiState: AppPreferences,
    expandedItem: String?,
    onExpandedItemChange: (String?) -> Unit,
    onSetAutoPlayOnStart: (Boolean) -> Unit,
    onSetStopOnAudioBecomingNoisy: (Boolean) -> Unit,
    onSetPauseOnVolumeZero: (Boolean) -> Unit,
    onSetEnableVolumeBoost: (Boolean) -> Unit,
    onSetKeepScreenOn: (Boolean) -> Unit,
    onSetShowCoverArtInNotification: (Boolean) -> Unit,
    showHistoryLimitDialog: Boolean,
    onToggleHistoryLimitDialog: () -> Unit,
    onSetHistoryLimit: (Int) -> Unit,
    onSetMaxRetryDuration: (Long) -> Unit,
    topShape: RoundedCornerShape,
    middleShape: RoundedCornerShape,
    bottomShape: RoundedCornerShape,
    singleShape: RoundedCornerShape
) {
    Section(title = stringResource(R.string.settings_player_section)) {
        ToggleItem(
            title = stringResource(R.string.settings_auto_play),
            subtitle = stringResource(R.string.settings_auto_play_desc),
            isEnabled = uiState.autoPlayOnStart,
            onToggle = onSetAutoPlayOnStart,
            icon = Icons.Default.PlayArrow,
            shape = topShape
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

        ToggleItem(
            title = stringResource(R.string.settings_stop_on_audio_noisy),
            subtitle = stringResource(R.string.settings_stop_on_audio_noisy_desc),
            isEnabled = uiState.stopOnAudioBecomingNoisy,
            onToggle = onSetStopOnAudioBecomingNoisy,
            icon = Icons.Default.Headphones,
            shape = middleShape
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

        ToggleItem(
            title = stringResource(R.string.settings_pause_on_volume_zero),
            subtitle = stringResource(R.string.settings_pause_on_volume_zero_desc),
            isEnabled = uiState.pauseOnVolumeZero,
            onToggle = onSetPauseOnVolumeZero,
            icon = Icons.AutoMirrored.Filled.VolumeOff,
            shape = middleShape
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

        var showVolumeBoostWarningSheet by remember { mutableStateOf(false) }

        ToggleItem(
            title = stringResource(R.string.settings_enable_volume_boost),
            subtitle = stringResource(R.string.settings_enable_volume_boost_desc),
            isEnabled = uiState.enableVolumeBoost,
            onToggle = { enable ->
                if (enable) {
                    showVolumeBoostWarningSheet = true
                } else {
                    onSetEnableVolumeBoost(false)
                }
            },
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            shape = middleShape
        )

        if (showVolumeBoostWarningSheet) {
            VolumeBoostWarningBottomSheet(
                onDismissRequest = { showVolumeBoostWarningSheet = false },
                onConfirm = {
                    onSetEnableVolumeBoost(true)
                }
            )
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

        ToggleItem(
            title = stringResource(R.string.settings_keep_screen_on_title),
            subtitle = stringResource(R.string.settings_keep_screen_on_desc),
            isEnabled = uiState.keepScreenOn,
            onToggle = onSetKeepScreenOn,
            icon = Icons.Default.Lightbulb,
            shape = middleShape
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

        ToggleItem(
            title = stringResource(R.string.settings_show_cover_art),
            subtitle = stringResource(R.string.settings_show_cover_art_desc),
            isEnabled = uiState.showCoverArtInNotification,
            onToggle = onSetShowCoverArtInNotification,
            icon = Icons.Default.Image,
            shape = middleShape
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

        val retryOptions = listOf(
            60_000L to stringResource(R.string.settings_retry_1_min),
            300_000L to stringResource(R.string.settings_retry_5_min),
            900_000L to stringResource(R.string.settings_retry_15_min),
            1_800_000L to stringResource(R.string.settings_retry_30_min),
            -1L to stringResource(R.string.settings_retry_indefinitely)
        )
        val currentRetryOption = retryOptions.find { it.first == uiState.maxRetryDuration }?.second ?: stringResource(R.string.settings_retry_5_min)

        ExpandableItem(
            title = stringResource(R.string.settings_max_retry_duration),
            subtitle = currentRetryOption,
            isExpanded = expandedItem == "RetryDuration",
            onToggle = { onExpandedItemChange(if (expandedItem == "RetryDuration") null else "RetryDuration") },
            icon = Icons.Default.Update,
            shape = middleShape
        ) {
            retryOptions.forEach { (duration, label) ->
                OptionItem(
                    label = label,
                    isSelected = uiState.maxRetryDuration == duration,
                    onClick = { onSetMaxRetryDuration(duration) }
                )
            }
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

        Item(
            title = stringResource(R.string.settings_track_history_limit),
            subtitle = androidx.compose.ui.res.pluralStringResource(R.plurals.tracks_count, uiState.trackHistoryLimit, uiState.trackHistoryLimit),
            onClick = onToggleHistoryLimitDialog,
            icon = Icons.Default.History,
            shape = bottomShape
        )

        if (showHistoryLimitDialog) {
            var inputLimit by remember { mutableStateOf(uiState.trackHistoryLimit.toString()) }
            AlertDialog(
                onDismissRequest = onToggleHistoryLimitDialog,
                title = { Text(stringResource(R.string.settings_track_history_limit)) },
                text = {
                    OutlinedTextField(
                        value = inputLimit,
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() }) {
                                inputLimit = newValue
                            }
                        },
                        label = { Text(stringResource(R.string.settings_number_of_tracks)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val limitInt = inputLimit.toIntOrNull() ?: 50
                            onSetHistoryLimit(limitInt.coerceIn(1, 500))
                            onToggleHistoryLimitDialog()
                        }
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onToggleHistoryLimitDialog) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun UpdateSection(
    uiState: AppPreferences,
    onCheckUpdatesClick: () -> Unit,
    onSetDisableUpdateCheck: (Boolean) -> Unit,
    topShape: RoundedCornerShape,
    bottomShape: RoundedCornerShape
) {
    Section(title = stringResource(R.string.settings_update_section)) {
        Item(
            title = stringResource(R.string.settings_check_updates),
            onClick = onCheckUpdatesClick,
            icon = Icons.Default.Update,
            shape = topShape
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))
        ToggleItem(
            title = stringResource(R.string.settings_disable_update_check),
            subtitle = stringResource(R.string.settings_disable_update_check_desc),
            isEnabled = uiState.disableUpdateCheck,
            onToggle = onSetDisableUpdateCheck,
            icon = Icons.Default.Update,
            shape = bottomShape
        )
    }
}

@Composable
private fun CommunitySection() {
    val context = LocalContext.current

    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (_: Exception) {
            // Handle error silently
        }
    }

    Section(title = stringResource(R.string.settings_community_section)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Telegram Button
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF2CA5E0))
                    .clickable { openUrl("https://t.me/internetradiocommunity") }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_telegram),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.about_cd_telegram),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            // Reddit Button
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFF4500))
                    .clickable { openUrl("https://www.reddit.com/r/InternetRadioFolks/") }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_reddit),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.about_cd_reddit),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun AboutSection(
    shape: RoundedCornerShape,
    topShape: RoundedCornerShape,
    bottomShape: RoundedCornerShape,
    versionName: String,
    versionCode: String
) {
    val context = LocalContext.current

    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (_: Exception) {
            // Handle error silently
        }
    }

    // Centered App Branding & Info Card
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Icon in center (big squircle)
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = stringResource(R.string.about_cd_app_icon),
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        // App Name & Version / Edition
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "v$versionName ($versionCode) • ${stringResource(StoreConfig.storeNameRes)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // App Description
        Text(
            text = stringResource(R.string.about_app_description),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        // Author Profile & Social Links Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
                // Author Profile & "Made with ❤️ by Arman Maurya"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { openUrl("https://github.com/armanmaurya") }
                ) {
                    AsyncImage(
                        model = "https://github.com/armanmaurya.png",
                        contentDescription = stringResource(R.string.about_author_name),
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        fallback = painterResource(id = R.drawable.ic_launcher_foreground),
                        error = painterResource(id = R.drawable.ic_launcher_foreground)
                    )

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Made with",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = "Love",
                                tint = androidx.compose.ui.graphics.Color(0xFFE53935),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "by",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = stringResource(R.string.about_author_name),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Social Icon Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .clickable { openUrl("https://github.com/armanmaurya/InternetRadio") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_github),
                            contentDescription = stringResource(R.string.about_cd_github),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .clickable { openUrl("https://www.linkedin.com/in/arman-maurya-2391aa263/") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_linkedin),
                            contentDescription = stringResource(R.string.about_cd_linkedin),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .clickable { openUrl("https://www.instagram.com/param.cs/") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_instagram),
                            contentDescription = stringResource(R.string.about_cd_instagram),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Rate & Review (or View on Play Store if FOSS)
    Item(
        title = if (StoreConfig.isPlayStoreBuild) {
            stringResource(R.string.settings_rate_review)
        } else {
            stringResource(R.string.settings_view_on_play_store)
        },
        onClick = {
            val packageName = context.packageName
            try {
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                context.startActivity(marketIntent)
            } catch (_: Exception) {
                openUrl("https://play.google.com/store/apps/details?id=$packageName")
            }
        },
        icon = if (StoreConfig.isPlayStoreBuild) Icons.Default.StarRate else null,
        iconPainter = if (!StoreConfig.isPlayStoreBuild) painterResource(id = R.drawable.ic_google) else null,
        shape = topShape
    )

    Spacer(modifier = Modifier.height(2.dp))

    // Star on GitHub (always there)
    Item(
        title = stringResource(R.string.review_star_github),
        onClick = {
            openUrl("https://github.com/armanmaurya/InternetRadio")
        },
        iconPainter = painterResource(id = R.drawable.ic_github),
        shape = bottomShape
    )
}

@Composable
private fun DonateSection() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val upiId = "arman.maurya@ptyes"
    val upiCopiedMessage = stringResource(R.string.about_upi_copied, upiId)

    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (_: Exception) {
            // Handle error silently
        }
    }

    Section(title = stringResource(R.string.settings_donate_section)) {
        // GitHub Sponsors Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF24292F))
                .clickable { openUrl("https://github.com/sponsors/armanmaurya") }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = Color(0xFFEA4AAA),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.about_github_sponsors),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = painterResource(id = R.drawable.ic_github),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Buy Me a Coffee Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFFFDD00))
                .clickable { openUrl("https://buymeacoffee.com/mauryaarman") }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_buymeacoffee),
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.about_buy_me_a_coffee),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // UPI Button (Click to copy UPI ID)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF005C8A))
                .clickable {
                    clipboardManager.setText(AnnotatedString(upiId))
                    Toast.makeText(context, upiCopiedMessage, Toast.LENGTH_SHORT).show()
                }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_upi),
                contentDescription = stringResource(R.string.about_upi),
                tint = Color.Unspecified,
                modifier = Modifier
                    .height(20.dp)
                    .width(70.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.about_donate_via_upi),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = upiId,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.about_cd_copy_upi),
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun BackupSection(
    uiState: AppPreferences,
    isSyncing: Boolean,
    expandedItem: String?,
    onExpandedItemChange: (String?) -> Unit,
    onSetConflictStrategy: (ConflictStrategy) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onSelectAutoBackupFolder: () -> Unit,
    onToggleAutoBackup: (Boolean) -> Unit,
    onClearAutoBackupFolder: () -> Unit,
    onSyncNow: () -> Unit,
    topShape: RoundedCornerShape,
    middleShape: RoundedCornerShape,
    bottomShape: RoundedCornerShape
) {
    val conflictOptions = listOf(
        ConflictStrategy.SKIP to stringResource(R.string.settings_conflict_skip),
        ConflictStrategy.OVERWRITE to stringResource(R.string.settings_conflict_overwrite),
        ConflictStrategy.KEEP_NEWER to stringResource(R.string.settings_conflict_keep_newer)
    )
    val currentLabel = conflictOptions.find { it.first == uiState.conflictStrategy }?.second
        ?: stringResource(R.string.settings_conflict_skip)

    val infiniteTransition = rememberInfiniteTransition(label = "syncRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "syncRotationAngle"
    )

    val autoSyncSubtitle = when {
        !uiState.isAutoBackupEnabled -> stringResource(R.string.settings_auto_sync_subtitle)
        isSyncing -> stringResource(R.string.settings_sync_status_syncing)
        uiState.autoBackupTreeUri.isNullOrBlank() -> stringResource(R.string.settings_sync_status_no_folder)
        uiState.lastAutoBackupStatus == com.armanmaurya.internetradio.domain.model.AutoBackupStatus.SUCCESS && uiState.lastAutoBackupTime > 0L -> {
            val formattedTime = java.text.SimpleDateFormat.getDateTimeInstance(
                java.text.SimpleDateFormat.MEDIUM,
                java.text.SimpleDateFormat.SHORT,
                java.util.Locale.getDefault()
            ).format(java.util.Date(uiState.lastAutoBackupTime))
            stringResource(R.string.settings_sync_status_success, formattedTime)
        }
        uiState.lastAutoBackupStatus == com.armanmaurya.internetradio.domain.model.AutoBackupStatus.FAILED -> {
            stringResource(
                R.string.settings_sync_status_failed,
                uiState.lastAutoBackupErrorMessage ?: stringResource(R.string.error_unknown)
            )
        }
        else -> stringResource(R.string.settings_sync_status_never)
    }

    Section(title = stringResource(R.string.settings_backup_section)) {
        ToggleItem(
            title = stringResource(R.string.settings_auto_sync_title),
            subtitle = autoSyncSubtitle,
            isEnabled = uiState.isAutoBackupEnabled,
            onToggle = { enabled ->
                if (enabled && uiState.autoBackupTreeUri.isNullOrBlank()) {
                    onSelectAutoBackupFolder()
                } else {
                    onToggleAutoBackup(enabled)
                }
            },
            icon = Icons.Default.CloudSync,
            shape = topShape,
            leadingTrailingContent = if (uiState.isAutoBackupEnabled && !uiState.autoBackupTreeUri.isNullOrBlank()) {
                {
                    IconButton(
                        onClick = onSyncNow,
                        enabled = !isSyncing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = stringResource(R.string.settings_sync_now),
                            modifier = if (isSyncing) Modifier.graphicsLayer { rotationZ = rotation } else Modifier,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else null
        )

        if (uiState.isAutoBackupEnabled) {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

            val displayFolderPath = remember(uiState.autoBackupTreeUri, uiState.autoBackupFolderName) {
                formatDisplayFolderPath(uiState.autoBackupTreeUri, uiState.autoBackupFolderName)
            }

            Item(
                title = stringResource(R.string.settings_folder_title),
                subtitle = displayFolderPath ?: stringResource(R.string.settings_folder_select),
                onClick = onSelectAutoBackupFolder,
                icon = Icons.Default.Folder,
                shape = middleShape,
                trailingContent = if (!uiState.autoBackupTreeUri.isNullOrBlank()) {
                    {
                        IconButton(onClick = onClearAutoBackupFolder) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.settings_clear_folder)
                            )
                        }
                    }
                } else null
            )
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

        ExpandableItem(
            title = stringResource(R.string.settings_conflict_title),
            subtitle = currentLabel,
            isExpanded = expandedItem == "Conflict",
            onToggle = { onExpandedItemChange(if (expandedItem == "Conflict") null else "Conflict") },
            icon = Icons.AutoMirrored.Filled.CallMerge,
            shape = middleShape
        ) {
            conflictOptions.forEach { (strategy, label) ->
                OptionItem(
                    label = label,
                    isSelected = uiState.conflictStrategy == strategy,
                    onClick = { onSetConflictStrategy(strategy) },
                    subtitle = when (strategy) {
                        ConflictStrategy.SKIP -> stringResource(R.string.settings_conflict_skip_desc)
                        ConflictStrategy.OVERWRITE -> stringResource(R.string.settings_conflict_overwrite_desc)
                        ConflictStrategy.KEEP_NEWER -> stringResource(R.string.settings_conflict_keep_newer_desc)
                    }
                )
            }
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

        Item(
            title = stringResource(R.string.settings_export_title),
            subtitle = stringResource(R.string.settings_export_subtitle),
            onClick = onExport,
            icon = Icons.Default.FileUpload,
            shape = middleShape
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

        Item(
            title = stringResource(R.string.settings_import_title),
            subtitle = stringResource(R.string.settings_import_subtitle),
            onClick = onImport,
            icon = Icons.Default.FileDownload,
            shape = bottomShape
        )
    }
}

private fun formatDisplayFolderPath(treeUriStr: String?, savedName: String?): String? {
    if (!savedName.isNullOrBlank() && (savedName.contains("/") || savedName.startsWith("Internal Storage") || savedName.startsWith("SD Card") || savedName.startsWith("Google Drive") || savedName.startsWith("Downloads"))) {
        return savedName
    }
    if (treeUriStr.isNullOrBlank()) return savedName
    return try {
        val decoded = android.net.Uri.decode(treeUriStr)
        val uri = android.net.Uri.parse(treeUriStr)
        when (uri.authority) {
            "com.android.externalstorage.documents" -> {
                val docId = decoded.substringAfter("/tree/").substringBefore("/document/")
                val split = docId.split(":", limit = 2)
                val storageType = split[0]
                val relPath = if (split.size > 1) split[1].trim('/') else ""
                val storageName = if (storageType.equals("primary", ignoreCase = true)) "Internal Storage" else "SD Card"
                if (relPath.isBlank()) storageName else "$storageName/$relPath"
            }
            "com.google.android.apps.docs.storage" -> {
                val clean = savedName?.removePrefix("Google Drive: ")?.removePrefix("Google Drive/")
                if (!clean.isNullOrBlank()) "Google Drive/$clean" else "Google Drive"
            }
            "com.android.providers.downloads.documents" -> {
                if (!savedName.isNullOrBlank()) "Downloads/$savedName" else "Downloads"
            }
            else -> savedName ?: decoded.substringAfterLast('/')
        }
    } catch (_: Exception) {
        savedName
    }
}

@Composable
private fun ScheduleSection(
    uiState: AppPreferences,
    expandedItem: String?,
    onExpandedItemChange: (String?) -> Unit,
    onSetStartOfWeek: (StartOfWeek) -> Unit,
    onSetAlarmVolumeTransitionEnabled: (Boolean) -> Unit,
    onSetAlarmVolumeTransitionSeconds: (Int) -> Unit,
    topShape: RoundedCornerShape,
    bottomShape: RoundedCornerShape
) {
    Section(title = stringResource(R.string.settings_schedule_section)) {
        ExpandableItem(
            title = stringResource(R.string.settings_start_week_title),
            subtitle = uiState.startOfWeek.toDisplayString(),
            isExpanded = expandedItem == "StartWeek",
            onToggle = { onExpandedItemChange(if (expandedItem == "StartWeek") null else "StartWeek") },
            icon = Icons.Default.CalendarMonth,
            shape = topShape
        ) {
            StartOfWeek.entries.forEach { startOfWeek ->
                OptionItem(
                    label = startOfWeek.toDisplayString(),
                    isSelected = uiState.startOfWeek == startOfWeek,
                    onClick = { onSetStartOfWeek(startOfWeek) }
                )
            }
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

        val isEnabled = uiState.isAlarmVolumeTransitionEnabled
        var lastSliderValue by remember { mutableStateOf(uiState.alarmVolumeTransitionSeconds.toFloat()) }
        
        LaunchedEffect(uiState.alarmVolumeTransitionSeconds) {
            lastSliderValue = uiState.alarmVolumeTransitionSeconds.toFloat()
        }

        ExpandableItem(
            title = stringResource(R.string.settings_gradual_volume),
            subtitle = if (isEnabled) {
                stringResource(
                    R.string.settings_gradual_volume_enabled,
                    pluralStringResource(
                        R.plurals.settings_gradual_volume_seconds,
                        lastSliderValue.toInt(),
                        lastSliderValue.toInt()
                    )
                )
            } else {
                stringResource(R.string.settings_gradual_volume_disabled)
            },
            isExpanded = expandedItem == "GradualVolume",
            hasSwitch = true,
            switchChecked = isEnabled,
            onSwitchChange = { checked ->
                onSetAlarmVolumeTransitionEnabled(checked)
                if (checked) {
                    onExpandedItemChange("GradualVolume")
                } else {
                    onExpandedItemChange(null)
                }
            },
            onToggle = { 
                val newState = !isEnabled
                onSetAlarmVolumeTransitionEnabled(newState)
                if (newState) {
                    onExpandedItemChange("GradualVolume")
                } else {
                    onExpandedItemChange(null)
                }
            },
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            shape = bottomShape
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Slider(
                    value = lastSliderValue,
                    onValueChange = { lastSliderValue = it },
                    onValueChangeFinished = { onSetAlarmVolumeTransitionSeconds(lastSliderValue.toInt()) },
                    valueRange = 1f..60f
                )
            }
        }
    }
}

@Composable
private fun rememberAvailableLanguages(): List<Pair<String, String>> {
    val context = LocalContext.current
    val systemDefaultStr = stringResource(R.string.settings_system_default)
    return remember(systemDefaultStr) {
        buildList {
            add("System" to systemDefaultStr)
            try {
                val parser = context.resources.getXml(R.xml.locales_config)
                var event = parser.next()
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name == "locale") {
                        val tag = parser.getAttributeValue(
                            "http://schemas.android.com/apk/res/android", "name"
                        )
                        if (!tag.isNullOrBlank()) {
                            val locale = Locale.forLanguageTag(tag)
                            add(tag to locale.getDisplayName(locale).replaceFirstChar { it.uppercaseChar() })
                        }
                    }
                    event = parser.next()
                }
                parser.close()
            } catch (_: Exception) { }
        }
    }
}

@Composable
private fun String.getLanguageDisplayName(availableLanguages: List<Pair<String, String>>): String {
    return availableLanguages.find { it.first == this }?.second ?: stringResource(R.string.settings_system_default)
}
