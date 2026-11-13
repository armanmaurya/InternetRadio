package com.armanmaurya.internetradio.ui.tv.screens.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.tv.material3.Switch
import com.armanmaurya.internetradio.domain.model.AppPreferences
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Translate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Text
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.ConflictStrategy
import com.armanmaurya.internetradio.ui.shared.theme.AppColor
import com.armanmaurya.internetradio.ui.shared.viewmodels.SettingsViewModel

/** Sub-pages opened from the main settings list, Google TV style (drill in, Back returns). */
private enum class SettingsPage {
    COLOR, SCREENSAVER, DEFAULT_TAB, TRACK_HISTORY_LIMIT, RECENT_LIMIT, MAX_RETRY, VOLUME_BOOST_WARNING, CONFLICT
}

private val TrackHistoryPresets = listOf(25, 50, 100, 200, 500, 1000)
private val RecentLimitPresets = listOf(10, 25, 50, 100, 200, 500)
private val ScreensaverMinutesPresets = listOf(0, 2, 5, 10, 15, 30)

/** Rows wider than this are hard to scan on a TV; Google TV settings uses a narrow column too. */
private val SettingsColumnMaxWidth = 720.dp

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onAboutClick: () -> Unit,
    onAppLanguageClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var openPage by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    // Row to put focus back on after closing a sub-page.
    var returnFocusTo by remember { mutableStateOf<SettingsPage?>(null) }
    val mainListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.backupResult.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportLibrary(context, it) }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importLibraries(context, uris)
    }

    // Many TV builds ship without a document picker, so launching it can throw.
    val exportUnavailable = stringResource(R.string.settings_exported_failed)
    val importUnavailable = pluralStringResource(R.plurals.settings_imported_failed, 1, 1)
    fun launchPicker(fallbackMessage: String, launch: () -> Unit) {
        try {
            launch()
        } catch (_: Exception) {
            // ActivityNotFoundException when the TV has no file picker; anything else is treated the same.
            Toast.makeText(context, fallbackMessage, Toast.LENGTH_LONG).show()
        }
    }

    fun closePage() {
        returnFocusTo = openPage
        openPage = null
    }

    BackHandler(enabled = openPage != null) { closePage() }

    when (val page = openPage) {
        null -> SettingsMainList(
            listState = mainListState,
            returnFocusTo = returnFocusTo,
            prefs = uiState,
            onOpenPage = { openPage = it },
            onSetAutoPlay = viewModel::setAutoPlayOnStart,
            onSetPauseOnVolumeZero = viewModel::setPauseOnVolumeZero,
            onSetVolumeBoost = { enable ->
                // Turning boost on needs the same warning as the phone.
                if (enable) openPage = SettingsPage.VOLUME_BOOST_WARNING
                else viewModel.setEnableVolumeBoost(false)
            },
            onAppLanguageClick = onAppLanguageClick,
            onExport = { launchPicker(exportUnavailable) { exportLauncher.launch("stations.json") } },
            onImport = { launchPicker(importUnavailable) { importLauncher.launch(arrayOf("application/json")) } },
            onAboutClick = onAboutClick
        )

        SettingsPage.COLOR -> {
            val colors = AppColor.entries.filter { it != AppColor.CUSTOM }
            SettingsOptionsPage(
                title = stringResource(R.string.settings_theme_title),
                options = colors.map { color ->
                    SettingsOption(
                        label = stringResource(color.titleRes),
                        selected = uiState.appColor == color,
                        trailing = { ColorSwatch(color) },
                        onSelect = { viewModel.setAppColor(color) }
                    )
                },
                onDone = ::closePage
            )
        }

        SettingsPage.SCREENSAVER -> SettingsOptionsPage(
            title = stringResource(R.string.settings_tv_screensaver),
            options = (ScreensaverMinutesPresets + uiState.tvScreensaverMinutes).distinct().sorted().map { minutes ->
                SettingsOption(
                    label = screensaverLabel(minutes),
                    selected = uiState.tvScreensaverMinutes == minutes,
                    onSelect = { viewModel.setTvScreensaverMinutes(minutes) }
                )
            },
            onDone = ::closePage
        )

        SettingsPage.DEFAULT_TAB -> SettingsOptionsPage(
            title = stringResource(R.string.settings_default_tab),
            options = defaultTabLabels().mapIndexed { index, label ->
                SettingsOption(
                    label = label,
                    selected = uiState.defaultTab == index,
                    onSelect = { viewModel.setDefaultTab(index) }
                )
            },
            onDone = ::closePage
        )

        SettingsPage.TRACK_HISTORY_LIMIT -> SettingsOptionsPage(
            title = stringResource(R.string.settings_track_history_limit),
            options = (TrackHistoryPresets + uiState.trackHistoryLimit).distinct().sorted().map { limit ->
                SettingsOption(
                    label = pluralStringResource(R.plurals.tracks_count, limit, limit),
                    selected = uiState.trackHistoryLimit == limit,
                    onSelect = { viewModel.setTrackHistoryLimit(limit) }
                )
            },
            onDone = ::closePage
        )

        SettingsPage.RECENT_LIMIT -> SettingsOptionsPage(
            title = stringResource(R.string.settings_recent_limit),
            options = (RecentLimitPresets + uiState.recentLimit).distinct().sorted().map { limit ->
                SettingsOption(
                    label = pluralStringResource(R.plurals.stations_count, limit, limit),
                    selected = uiState.recentLimit == limit,
                    onSelect = { viewModel.setRecentLimit(limit) }
                )
            },
            onDone = ::closePage
        )

        SettingsPage.MAX_RETRY -> SettingsOptionsPage(
            title = stringResource(R.string.settings_max_retry_duration),
            options = retryOptions().map { (duration, label) ->
                SettingsOption(
                    label = label,
                    selected = uiState.maxRetryDuration == duration,
                    onSelect = { viewModel.setMaxRetryDuration(duration) }
                )
            },
            onDone = ::closePage
        )

        SettingsPage.VOLUME_BOOST_WARNING -> SettingsConfirmPage(
            title = stringResource(R.string.settings_volume_boost_warning_title),
            message = stringResource(R.string.settings_volume_boost_warning_desc),
            confirmLabel = stringResource(R.string.settings_volume_boost_warning_confirm),
            dismissLabel = stringResource(R.string.general_cancel),
            onConfirm = {
                viewModel.setEnableVolumeBoost(true)
                closePage()
            },
            onDismiss = ::closePage
        )

        SettingsPage.CONFLICT -> SettingsOptionsPage(
            title = stringResource(R.string.settings_conflict_title),
            options = conflictOptions().map { (strategy, label, description) ->
                SettingsOption(
                    label = label,
                    description = description,
                    selected = uiState.conflictStrategy == strategy,
                    onSelect = { viewModel.setConflictStrategy(strategy) }
                )
            },
            onDone = ::closePage
        )
    }
}

// ── Main list ────────────────────────────────────────────────────────────────

@Composable
private fun SettingsMainList(
    listState: LazyListState,
    returnFocusTo: SettingsPage?,
    prefs: AppPreferences,
    onOpenPage: (SettingsPage) -> Unit,
    onSetAutoPlay: (Boolean) -> Unit,
    onSetPauseOnVolumeZero: (Boolean) -> Unit,
    onSetVolumeBoost: (Boolean) -> Unit,
    onAppLanguageClick: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onAboutClick: () -> Unit
) {
    val rowFocus = remember { SettingsPage.entries.associateWith { FocusRequester() } }

    LaunchedEffect(Unit) {
        val target = returnFocusTo ?: return@LaunchedEffect
        try {
            rowFocus.getValue(target).requestFocus()
        } catch (_: Exception) {
        }
    }

    val tabLabels = defaultTabLabels()
    val conflictLabel = conflictOptions().firstOrNull { it.first == prefs.conflictStrategy }?.second
        ?: stringResource(R.string.settings_conflict_skip)
    val retryLabel = retryOptions().firstOrNull { it.first == prefs.maxRetryDuration }?.second
        ?: stringResource(R.string.settings_retry_5_min)

    SettingsColumn(title = stringResource(R.string.settings_title)) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item { SettingsSectionHeader(stringResource(R.string.settings_appearance_section)) }
            item {
                SettingsRow(
                    title = stringResource(R.string.settings_theme_title),
                    value = stringResource(prefs.appColor.titleRes),
                    icon = Icons.Default.Palette,
                    onClick = { onOpenPage(SettingsPage.COLOR) },
                    modifier = Modifier.focusRequester(rowFocus.getValue(SettingsPage.COLOR))
                )
            }
            item {
                SettingsRow(
                    title = stringResource(R.string.settings_tv_screensaver),
                    value = screensaverLabel(prefs.tvScreensaverMinutes),
                    icon = Icons.Default.Bedtime,
                    onClick = { onOpenPage(SettingsPage.SCREENSAVER) },
                    modifier = Modifier.focusRequester(rowFocus.getValue(SettingsPage.SCREENSAVER))
                )
            }

            item { SettingsSectionHeader(stringResource(R.string.settings_general_section)) }
            item {
                SettingsRow(
                    title = stringResource(R.string.settings_language_title),
                    value = rememberCurrentAppLanguageName(),
                    icon = Icons.Default.Translate,
                    onClick = onAppLanguageClick
                )
            }
            item {
                SettingsRow(
                    title = stringResource(R.string.settings_default_tab),
                    value = tabLabels.getOrNull(prefs.defaultTab) ?: tabLabels.first(),
                    icon = Icons.Default.Home,
                    onClick = { onOpenPage(SettingsPage.DEFAULT_TAB) },
                    modifier = Modifier.focusRequester(rowFocus.getValue(SettingsPage.DEFAULT_TAB))
                )
            }
            item {
                SettingsRow(
                    title = stringResource(R.string.settings_track_history_limit),
                    value = pluralStringResource(R.plurals.tracks_count, prefs.trackHistoryLimit, prefs.trackHistoryLimit),
                    icon = Icons.Default.History,
                    onClick = { onOpenPage(SettingsPage.TRACK_HISTORY_LIMIT) },
                    modifier = Modifier.focusRequester(rowFocus.getValue(SettingsPage.TRACK_HISTORY_LIMIT))
                )
            }
            item {
                SettingsRow(
                    title = stringResource(R.string.settings_recent_limit),
                    value = pluralStringResource(R.plurals.stations_count, prefs.recentLimit, prefs.recentLimit),
                    icon = Icons.Default.Restore,
                    onClick = { onOpenPage(SettingsPage.RECENT_LIMIT) },
                    modifier = Modifier.focusRequester(rowFocus.getValue(SettingsPage.RECENT_LIMIT))
                )
            }

            item { SettingsSectionHeader(stringResource(R.string.settings_player_section)) }
            item {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_auto_play),
                    description = stringResource(R.string.settings_auto_play_desc),
                    icon = Icons.Default.PlayCircle,
                    checked = prefs.autoPlayOnStart,
                    onCheckedChange = onSetAutoPlay
                )
            }
            item {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_pause_on_volume_zero),
                    description = stringResource(R.string.settings_pause_on_volume_zero_desc),
                    icon = Icons.AutoMirrored.Filled.VolumeOff,
                    checked = prefs.pauseOnVolumeZero,
                    onCheckedChange = onSetPauseOnVolumeZero
                )
            }
            item {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_enable_volume_boost),
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    checked = prefs.enableVolumeBoost,
                    onCheckedChange = onSetVolumeBoost,
                    modifier = Modifier.focusRequester(rowFocus.getValue(SettingsPage.VOLUME_BOOST_WARNING))
                )
            }
            item {
                SettingsRow(
                    title = stringResource(R.string.settings_max_retry_duration),
                    value = retryLabel,
                    icon = Icons.Default.Refresh,
                    onClick = { onOpenPage(SettingsPage.MAX_RETRY) },
                    modifier = Modifier.focusRequester(rowFocus.getValue(SettingsPage.MAX_RETRY))
                )
            }

            item { SettingsSectionHeader(stringResource(R.string.settings_backup_section)) }
            item {
                SettingsRow(
                    title = stringResource(R.string.settings_export_title),
                    value = stringResource(R.string.settings_export_subtitle),
                    icon = Icons.Default.FileUpload,
                    onClick = onExport,
                    showChevron = false
                )
            }
            item {
                SettingsRow(
                    title = stringResource(R.string.settings_import_title),
                    value = stringResource(R.string.settings_import_subtitle),
                    icon = Icons.Default.FileDownload,
                    onClick = onImport,
                    showChevron = false
                )
            }
            item {
                SettingsRow(
                    title = stringResource(R.string.settings_conflict_title),
                    value = conflictLabel,
                    icon = Icons.Default.SyncProblem,
                    onClick = { onOpenPage(SettingsPage.CONFLICT) },
                    modifier = Modifier.focusRequester(rowFocus.getValue(SettingsPage.CONFLICT))
                )
            }

            item { SettingsSectionHeader(stringResource(R.string.about_title)) }
            item {
                SettingsRow(
                    title = stringResource(R.string.about_us),
                    icon = Icons.Default.Info,
                    onClick = onAboutClick
                )
            }
        }
    }
}

// ── Option sub-page ──────────────────────────────────────────────────────────

private class SettingsOption(
    val label: String,
    val selected: Boolean,
    val onSelect: () -> Unit,
    val description: String? = null,
    val trailing: (@Composable () -> Unit)? = null
)

/** Single-choice list. Picking an option applies it and returns to the main list. */
@Composable
private fun SettingsOptionsPage(
    title: String,
    options: List<SettingsOption>,
    onDone: () -> Unit
) {
    val selectedIndex = options.indexOfFirst { it.selected }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val selectedFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            selectedFocus.requestFocus()
        } catch (_: Exception) {
        }
    }

    SettingsColumn(title = title) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(options) { index, option ->
                ListItem(
                    selected = false,
                    onClick = {
                        option.onSelect()
                        onDone()
                    },
                    headlineContent = { Text(option.label) },
                    supportingContent = option.description?.let { { Text(it) } },
                    leadingContent = { RadioButton(selected = option.selected, onClick = null) },
                    trailingContent = option.trailing,
                    scale = ListItemDefaults.scale(focusedScale = 1.02f),
                    modifier = if (index == selectedIndex) Modifier.focusRequester(selectedFocus) else Modifier
                )
            }
        }
    }
}

// ── Building blocks ──────────────────────────────────────────────────────────

@Composable
private fun SettingsColumn(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(max = SettingsColumnMaxWidth)
            .padding(start = 24.dp, top = 32.dp, end = 24.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, bottom = 16.dp)
        )
        content()
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    showChevron: Boolean = true
) {
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = { Text(title) },
        supportingContent = value?.let { { Text(it, maxLines = 1) } },
        leadingContent = { Icon(imageVector = icon, contentDescription = null) },
        trailingContent = if (showChevron) {
            { Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
        } else null,
        scale = ListItemDefaults.scale(focusedScale = 1.02f),
        modifier = modifier.fillMaxWidth()
    )
}

/** Quartered circle: primary, secondary, tertiary, primary container. */
@Composable
private fun ColorSwatch(color: AppColor) {
    val swatch = remember(color) { color.getPreviewColors(darkTheme = true) }
    Canvas(modifier = Modifier.size(28.dp)) {
        swatch.forEachIndexed { i, c ->
            drawArc(color = c, startAngle = -90f + i * 90f, sweepAngle = 90f, useCenter = true)
        }
    }
}

/** Tabs the TV app actually has, in the same index order as the phone (Recordings/Schedules are phone-only). */
@Composable
private fun defaultTabLabels(): List<String> = listOf(
    stringResource(R.string.home_tab_browse),
    stringResource(R.string.home_tab_recent),
    stringResource(R.string.home_tab_library)
)

@Composable
private fun conflictOptions(): List<Triple<ConflictStrategy, String, String>> = listOf(
    Triple(
        ConflictStrategy.SKIP,
        stringResource(R.string.settings_conflict_skip),
        stringResource(R.string.settings_conflict_skip_desc)
    ),
    Triple(
        ConflictStrategy.OVERWRITE,
        stringResource(R.string.settings_conflict_overwrite),
        stringResource(R.string.settings_conflict_overwrite_desc)
    ),
    Triple(
        ConflictStrategy.KEEP_NEWER,
        stringResource(R.string.settings_conflict_keep_newer),
        stringResource(R.string.settings_conflict_keep_newer_desc)
    )
)

@Composable
private fun SettingsToggleRow(
    title: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null
) {
    ListItem(
        selected = false,
        onClick = { onCheckedChange(!checked) },
        headlineContent = { Text(title) },
        supportingContent = description?.let { { Text(it, maxLines = 2) } },
        leadingContent = { Icon(imageVector = icon, contentDescription = null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        scale = ListItemDefaults.scale(focusedScale = 1.02f),
        modifier = modifier.fillMaxWidth()
    )
}

/** Full-page confirmation, Google TV style: message on top, the two choices as list rows. */
@Composable
private fun SettingsConfirmPage(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // Focus starts on the safe choice.
    val dismissFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            dismissFocus.requestFocus()
        } catch (_: Exception) {
        }
    }

    SettingsColumn(title = title) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            )
            ListItem(
                selected = false,
                onClick = onDismiss,
                headlineContent = { Text(dismissLabel) },
                scale = ListItemDefaults.scale(focusedScale = 1.02f),
                modifier = Modifier.fillMaxWidth().focusRequester(dismissFocus)
            )
            ListItem(
                selected = false,
                onClick = onConfirm,
                headlineContent = { Text(confirmLabel) },
                scale = ListItemDefaults.scale(focusedScale = 1.02f),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun retryOptions(): List<Pair<Long, String>> = listOf(
    60_000L to stringResource(R.string.settings_retry_1_min),
    300_000L to stringResource(R.string.settings_retry_5_min),
    900_000L to stringResource(R.string.settings_retry_15_min),
    1_800_000L to stringResource(R.string.settings_retry_30_min),
    -1L to stringResource(R.string.settings_retry_indefinitely)
)

@Composable
private fun screensaverLabel(minutes: Int): String =
    if (minutes <= 0) stringResource(R.string.settings_gradual_volume_disabled)
    else stringResource(R.string.player_timer_min, minutes)
