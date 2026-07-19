package com.armanmaurya.internetradio.ui.mobile.screens.settings


import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import org.xmlpull.v1.XmlPullParser
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.data.model.AppPreferences
import com.armanmaurya.internetradio.data.model.ConflictStrategy
import com.armanmaurya.internetradio.ui.shared.viewmodels.SettingsViewModel
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.ExpandableItem
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.Item
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.OptionItem
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.Section
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.ToggleItem
import com.armanmaurya.internetradio.ui.shared.theme.AppTheme
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onAboutClick: () -> Unit,
    onCheckUpdatesClick: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val uiState by viewModel.uiState.collectAsState()

    // UI-only state for expand/collapse
    var themeExpanded by remember { mutableStateOf(false) }
    var languageExpanded by remember { mutableStateOf(false) }
    var showHistoryLimitDialog by remember { mutableStateOf(false) }
    var defaultTabExpanded by remember { mutableStateOf(false) }
    var maxRetryDurationExpanded by remember { mutableStateOf(false) }
    var backupConflictExpanded by remember { mutableStateOf(false) }

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
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importLibrary(context, it) }
    }

    Scaffold(
        modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
        topBar = { SettingsTopBar(onBackClick) }
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
                themeExpanded = themeExpanded,
                onToggleThemeExpanded = { themeExpanded = !themeExpanded },
                onSetDynamicTheme = viewModel::setDynamicTheme,
                onSetTheme = viewModel::setAppTheme,
                onSetPureBlack = viewModel::setPureBlack
            )
            GeneralSection(
                uiState = uiState,
                languages = rememberAvailableLanguages(),
                languageExpanded = languageExpanded,
                onToggleLanguageExpanded = { languageExpanded = !languageExpanded },
                onSetLanguage = viewModel::setAppLanguage,
                defaultTabExpanded = defaultTabExpanded,
                onToggleDefaultTabExpanded = { defaultTabExpanded = !defaultTabExpanded },
                onSetDefaultTab = viewModel::setDefaultTab,
                onSetAutoRouteToBrowseOnSearch = viewModel::setAutoRouteToBrowseOnSearch
            )
            PlayerSection(
                uiState = uiState,
                onSetAutoPlayOnStart = viewModel::setAutoPlayOnStart,
                onSetStopOnAudioBecomingNoisy = viewModel::setStopOnAudioBecomingNoisy,
                showHistoryLimitDialog = showHistoryLimitDialog,
                onToggleHistoryLimitDialog = { showHistoryLimitDialog = !showHistoryLimitDialog },
                onSetHistoryLimit = viewModel::setTrackHistoryLimit,
                maxRetryDurationExpanded = maxRetryDurationExpanded,
                onToggleMaxRetryDurationExpanded = { maxRetryDurationExpanded = !maxRetryDurationExpanded },
                onSetMaxRetryDuration = viewModel::setMaxRetryDuration
            )
            BackupSection(
                uiState = uiState,
                conflictExpanded = backupConflictExpanded,
                onToggleConflictExpanded = { backupConflictExpanded = !backupConflictExpanded },
                onSetConflictStrategy = viewModel::setConflictStrategy,
                onExport = { exportLauncher.launch("stations.json") },
                onImport = { importLauncher.launch(arrayOf("application/json")) }
            )
            AboutSection(
                onAboutClick = onAboutClick,
                onCheckUpdatesClick = onCheckUpdatesClick
            )

            val context = LocalContext.current
            val packageInfo = remember {
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0)
                } catch (e: Exception) {
                    null
                }
            }
            val versionName = packageInfo?.versionName ?: stringResource(R.string.general_unknown)
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo?.longVersionCode?.toString() ?: "0"
            } else {
                @Suppress("DEPRECATION")
                packageInfo?.versionCode?.toString() ?: "0"
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "v$versionName ($versionCode)",
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(onBackClick: () -> Unit) {
    CenterAlignedTopAppBar(
        title = { Text(stringResource(R.string.settings_title)) },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back)
                )
            }
        }
    )
}

@Composable
private fun AppearanceSection(
    uiState: AppPreferences,
    availableThemes: List<AppTheme>,
    themeExpanded: Boolean,
    onToggleThemeExpanded: () -> Unit,
    onSetDynamicTheme: (Boolean) -> Unit,
    onSetTheme: (AppTheme) -> Unit,
    onSetPureBlack: (Boolean) -> Unit
) {
    Section(title = stringResource(R.string.settings_appearance_section)) {
        ToggleItem(
            title = stringResource(R.string.settings_dynamic_theme_title),
            subtitle = stringResource(R.string.settings_dynamic_theme_subtitle),
            isEnabled = uiState.useDynamicColor,
            onToggle = onSetDynamicTheme,
            icon = Icons.Default.AutoAwesome
        )

        ExpandableItem(
            title = stringResource(R.string.settings_theme_title),
            subtitle = uiState.themeMode.toDisplayString(),
            isExpanded = themeExpanded,
            onToggle = onToggleThemeExpanded,
            icon = Icons.Default.Brightness4
        ) {
            availableThemes.forEach { theme ->
                OptionItem(
                    label = theme.toDisplayString(),
                    isSelected = uiState.themeMode == theme,
                    onClick = { onSetTheme(theme) }
                )
            }
        }

        ToggleItem(
            title = stringResource(R.string.settings_pure_black_title),
            subtitle = stringResource(R.string.settings_pure_black_subtitle),
            isEnabled = uiState.pureBlack,
            onToggle = onSetPureBlack,
            icon = Icons.Default.Contrast
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
private fun GeneralSection(
    uiState: AppPreferences,
    languages: List<Pair<String, String>>,
    languageExpanded: Boolean,
    onToggleLanguageExpanded: () -> Unit,
    onSetLanguage: (String) -> Unit,
    defaultTabExpanded: Boolean,
    onToggleDefaultTabExpanded: () -> Unit,
    onSetDefaultTab: (Int) -> Unit,
    onSetAutoRouteToBrowseOnSearch: (Boolean) -> Unit
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
            isExpanded = languageExpanded,
            onToggle = onToggleLanguageExpanded,
            icon = Icons.Default.Translate
        ) {
            languages.forEach { (code, name) ->
                OptionItem(
                    label = name,
                    isSelected = activeLanguageCode == code,
                    onClick = { onSetLanguage(code) }
                )
            }
        }

        val tabs = listOf(stringResource(R.string.home_tab_browse), stringResource(R.string.home_tab_recent), stringResource(R.string.home_tab_library))

        ExpandableItem(
            title = stringResource(R.string.settings_default_tab),
            subtitle = tabs.getOrNull(uiState.defaultTab) ?: stringResource(R.string.home_tab_browse),
            isExpanded = defaultTabExpanded,
            onToggle = onToggleDefaultTabExpanded,
            icon = Icons.Default.StarRate
        ) {
            tabs.forEachIndexed { index, name ->
                OptionItem(
                    label = name,
                    isSelected = uiState.defaultTab == index,
                    onClick = { onSetDefaultTab(index) }
                )
            }
        }

        ToggleItem(
            title = stringResource(R.string.settings_auto_route_search_title),
            subtitle = stringResource(R.string.settings_auto_route_search_subtitle),
            isEnabled = uiState.autoRouteToBrowseOnSearch,
            onToggle = onSetAutoRouteToBrowseOnSearch,
            icon = Icons.Default.Search
        )
    }
}

@Composable
private fun PlayerSection(
    uiState: AppPreferences,
    onSetAutoPlayOnStart: (Boolean) -> Unit,
    onSetStopOnAudioBecomingNoisy: (Boolean) -> Unit,
    showHistoryLimitDialog: Boolean,
    onToggleHistoryLimitDialog: () -> Unit,
    onSetHistoryLimit: (Int) -> Unit,
    maxRetryDurationExpanded: Boolean,
    onToggleMaxRetryDurationExpanded: () -> Unit,
    onSetMaxRetryDuration: (Long) -> Unit
) {
    Section(title = stringResource(R.string.settings_player_section)) {
        ToggleItem(
            title = stringResource(R.string.settings_auto_play),
            subtitle = stringResource(R.string.settings_auto_play_desc),
            isEnabled = uiState.autoPlayOnStart,
            onToggle = onSetAutoPlayOnStart,
            icon = Icons.Default.PlayArrow
        )

        ToggleItem(
            title = stringResource(R.string.settings_stop_on_audio_noisy),
            subtitle = stringResource(R.string.settings_stop_on_audio_noisy_desc),
            isEnabled = uiState.stopOnAudioBecomingNoisy,
            onToggle = onSetStopOnAudioBecomingNoisy,
            icon = Icons.Default.Headphones
        )

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
            isExpanded = maxRetryDurationExpanded,
            onToggle = onToggleMaxRetryDurationExpanded,
            icon = Icons.Default.Update
        ) {
            retryOptions.forEach { (duration, label) ->
                OptionItem(
                    label = label,
                    isSelected = uiState.maxRetryDuration == duration,
                    onClick = { onSetMaxRetryDuration(duration) }
                )
            }
        }

        Item(
            title = stringResource(R.string.settings_track_history_limit),
            subtitle = "${uiState.trackHistoryLimit} tracks",
            onClick = onToggleHistoryLimitDialog,
            icon = Icons.Default.History
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
private fun AboutSection(
    onAboutClick: () -> Unit,
    onCheckUpdatesClick: () -> Unit
) {
    val context = LocalContext.current

    Section(title = stringResource(R.string.about_title)) {
        Item(
            title = stringResource(R.string.settings_rate_review),
            onClick = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data =
                            Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    // Handle error silently
                }
            },
            icon = Icons.Default.StarRate
        )
        Item(
            title = stringResource(R.string.about_us),
            onClick = onAboutClick,
            icon = Icons.Default.Info
        )
        Item(
            title = stringResource(R.string.settings_check_updates),
            onClick = onCheckUpdatesClick,
            icon = Icons.Default.Update
        )
    }
}

@Composable
private fun BackupSection(
    uiState: AppPreferences,
    conflictExpanded: Boolean,
    onToggleConflictExpanded: () -> Unit,
    onSetConflictStrategy: (ConflictStrategy) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    val conflictOptions = listOf(
        ConflictStrategy.SKIP to stringResource(R.string.settings_conflict_skip),
        ConflictStrategy.OVERWRITE to stringResource(R.string.settings_conflict_overwrite),
        ConflictStrategy.KEEP_NEWER to stringResource(R.string.settings_conflict_keep_newer)
    )
    val currentLabel = conflictOptions.find { it.first == uiState.conflictStrategy }?.second
        ?: stringResource(R.string.settings_conflict_skip)

    Section(title = stringResource(R.string.settings_backup_section)) {
        ExpandableItem(
            title = stringResource(R.string.settings_conflict_title),
            subtitle = currentLabel,
            isExpanded = conflictExpanded,
            onToggle = onToggleConflictExpanded,
            icon = Icons.AutoMirrored.Filled.CallMerge
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

        Item(
            title = stringResource(R.string.settings_export_title),
            subtitle = stringResource(R.string.settings_export_subtitle),
            onClick = onExport,
            icon = Icons.Default.FileUpload
        )

        Item(
            title = stringResource(R.string.settings_import_title),
            subtitle = stringResource(R.string.settings_import_subtitle),
            onClick = onImport,
            icon = Icons.Default.FileDownload
        )
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
