@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.armanmaurya.internetradio.ui.mobile.screens.edit

import androidx.compose.ui.res.stringResource
import com.armanmaurya.internetradio.R
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.armanmaurya.internetradio.core.media.prober.ProbeResult
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.StationCard
import com.armanmaurya.internetradio.ui.shared.viewmodels.LibraryViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditStationScreen(
    stationUuid: String?,
    viewModel: LibraryViewModel,
    onNavigateBack: () -> Unit,
    onPlayStation: (RadioStation) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val fieldsResetMessage = stringResource(R.string.edit_station_fields_reset_message)
    val failedFetchOriginalDataMessage = stringResource(R.string.edit_station_failed_fetch_original_data)
    val uploadedSuccessfullyMessage = stringResource(R.string.edit_station_uploaded_successfully)
    val savedMessage = stringResource(R.string.edit_station_saved_message)
    val uploadFailedTemplate = stringResource(R.string.edit_station_upload_failed)
    val stations by viewModel.stations.collectAsStateWithLifecycle()
    val station = if (stationUuid != null) stations?.find { it.stationUuid == stationUuid } else null

    val isEditing = stationUuid != null

    if (isEditing && station == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.edit_station_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(stringResource(R.string.error_station_not_found))
            }
        }
    } else {
        val coroutineScope = rememberCoroutineScope()
        var name by remember(station) { mutableStateOf(station?.name ?: "") }
        var url by remember(station) { mutableStateOf(station?.url ?: "") }
        var favicon by remember(station) { mutableStateOf(station?.favicon ?: "") }
        var tags by remember(station) { mutableStateOf(station?.tags?.joinToString(", ")?.lowercase() ?: "") }
        var countryCode by remember(station) { mutableStateOf(station?.countryCode ?: "") }
        
        val countries = remember {
            java.util.Locale.getISOCountries().map { code ->
                code to java.util.Locale("", code).getDisplayCountry(java.util.Locale.getDefault())
            }.sortedBy { it.second }
        }
        var expandedCountry by remember { mutableStateOf(false) }
        var countrySearchText by remember(station) {
            mutableStateOf(
                if (countryCode.isNotBlank()) {
                    val disp = java.util.Locale("", countryCode).getDisplayCountry(java.util.Locale.getDefault())
                    "$disp ($countryCode)"
                } else ""
            )
        }
        var iso3166_2 by remember(station) { mutableStateOf(station?.iso3166_2 ?: "") }
        val statesList = remember(countryCode) {
            if (countryCode.isNotBlank()) com.armanmaurya.internetradio.core.utils.StateUtils.getStatesForCountry(context, countryCode) else emptyList()
        }
        var expandedState by remember { mutableStateOf(false) }
        var stateSearchText by remember(station, countryCode) {
            mutableStateOf(
                if (iso3166_2.isNotBlank()) {
                    val st = statesList.find { it.code == iso3166_2 }
                    if (st != null) "${st.getDisplayName(java.util.Locale.getDefault().language)} (${st.code})" else iso3166_2
                } else ""
            )
        }
        var languageCodes by remember(station) { mutableStateOf(station?.languageCodes?.joinToString(", ") ?: "") }
        
        val allLanguages = remember {
            com.neovisionaries.i18n.LanguageAlpha3Code.values().mapNotNull { langCode ->
                val iso3B = langCode.alpha3B?.name
                if (iso3B != null) {
                    iso3B to langCode.getName()
                } else null
            }.sortedBy { it.second }.distinctBy { it.second }
        }
        var expandedLanguage by remember { mutableStateOf(false) }
        var languageSearchText by remember { mutableStateOf("") }
        var homepage by remember(station) { mutableStateOf(station?.homepage ?: "") }
        
        val fetchedTags by viewModel.fetchedTags.collectAsStateWithLifecycle()
        var expandedTags by remember { mutableStateOf(false) }
        var tagsSearchText by remember { mutableStateOf("") }

        var isProbingMetadata by remember { mutableStateOf(false) }
        var isProbingCodec by remember { mutableStateOf(false) }
        val isProbing = isProbingMetadata || isProbingCodec
        
        var probedCodec by remember(station) { mutableStateOf(station?.codec ?: "") }
        var probedBitrate by remember(station) { mutableStateOf(station?.bitrate ?: 0) }
        
        var showOverwriteDialog by remember { mutableStateOf(false) }
        var pendingProbeResult by remember { mutableStateOf<ProbeResult?>(null) }
        var probeErrorMessage by remember { mutableStateOf<String?>(null) }
        val handleProbeResult: (ProbeResult) -> Unit = { result ->
            probedCodec = result.codec
            probedBitrate = result.bitrate
            
            val hasNewMetadata = !result.name.isNullOrBlank() || !result.homepage.isNullOrBlank() || !result.genre.isNullOrBlank()
            val fieldsAlreadyFilled = name.isNotBlank() || homepage.isNotBlank() || tags.isNotBlank()
            
            if (hasNewMetadata) {
                if (fieldsAlreadyFilled) {
                    val nameDiffers = !result.name.isNullOrBlank() && result.name != name
                    val homepageDiffers = !result.homepage.isNullOrBlank() && result.homepage != homepage
                    val parsedGenre = result.genre?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.joinToString(", ")?.lowercase()
                    val tagsDiffer = parsedGenre != null && parsedGenre != tags.lowercase()
                    
                    if (nameDiffers || homepageDiffers || tagsDiffer) {
                        pendingProbeResult = result
                        showOverwriteDialog = true
                    }
                } else {
                    if (!result.name.isNullOrBlank()) name = result.name!!
                    if (!result.homepage.isNullOrBlank()) homepage = result.homepage!!
                    if (!result.genre.isNullOrBlank()) {
                        tags = result.genre!!.split(",").map { it.trim() }.filter { it.isNotBlank() }.joinToString(", ").lowercase()
                    }
                }
            }
        }

        LaunchedEffect(url) {
            if (url.startsWith("http")) {
                if (station != null && url == station.url) {
                    isProbingMetadata = false
                    isProbingCodec = false
                } else {
                    isProbingMetadata = true
                    isProbingCodec = true
                    kotlinx.coroutines.delay(500) // Debounce
                    val result = viewModel.probeStream(url)
                    if (result != null) {
                        handleProbeResult(result)
                    }
                    isProbingMetadata = false
                    isProbingCodec = false
                }
            } else {
                isProbingMetadata = false
                isProbingCodec = false
            }
        }

        // Auto-check for similar stations when opening a custom station for editing
        LaunchedEffect(station?.stationUuid) {
            if (isEditing && station?.isCustom == true && url.isNotBlank()) {
                viewModel.checkDuplicateUrl(url)
            }
        }

        val hasUnsavedChanges = if (isEditing && station != null) {
            name != station.name ||
            url != station.url ||
            favicon != station.favicon ||
            tags != station.tags.joinToString(", ") ||
            countryCode != station.countryCode ||
            iso3166_2 != (station.iso3166_2 ?: "") ||
            languageCodes != station.languageCodes.joinToString(", ") ||
            homepage != station.homepage ||
            probedCodec != station.codec ||
            probedBitrate != station.bitrate
        } else {
            name.isNotBlank() || url.isNotBlank() || favicon.isNotBlank() || tags.isNotBlank() || countryCode.isNotBlank() || iso3166_2.isNotBlank() || languageCodes.isNotBlank() || homepage.isNotBlank() || probedCodec.isNotBlank() || probedBitrate != 0
        }

        if (showOverwriteDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showOverwriteDialog = false 
                    pendingProbeResult = null
                },
                title = { Text(stringResource(R.string.edit_station_overwrite_title)) },
                text = { Text(stringResource(R.string.edit_station_overwrite_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        val result = pendingProbeResult
                        if (result != null) {
                            if (!result.name.isNullOrBlank()) name = result.name
                            if (!result.homepage.isNullOrBlank()) homepage = result.homepage
                            if (!result.genre.isNullOrBlank()) {
                                tags = result.genre.split(",").map { it.trim() }.filter { it.isNotBlank() }.joinToString(", ").lowercase()
                            }
                        }
                        showOverwriteDialog = false
                        pendingProbeResult = null
                    }) { Text(stringResource(R.string.settings_conflict_overwrite)) }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showOverwriteDialog = false 
                        pendingProbeResult = null
                    }) { Text(stringResource(R.string.general_cancel)) }
                }
            )
        }

        var showExitWarningDialog by remember { mutableStateOf(false) }
        var isUploading by remember { mutableStateOf(false) }
        val showUploadMode = true
        var uploadMode by remember { mutableStateOf(false) }

        val handleBackPress = {
            if (hasUnsavedChanges) {
                showExitWarningDialog = true
            } else {
                onNavigateBack()
            }
        }

        BackHandler(enabled = hasUnsavedChanges) {
            showExitWarningDialog = true
        }

        if (showExitWarningDialog) {
            AlertDialog(
                onDismissRequest = { showExitWarningDialog = false },
                title = { Text(stringResource(R.string.edit_station_unsaved_changes)) },
                text = { Text(stringResource(R.string.edit_station_unsaved_changes_message_exit)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showExitWarningDialog = false
                            onNavigateBack()
                        }
                    ) {
                        Text(stringResource(R.string.general_discard))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitWarningDialog = false }) {
                        Text(stringResource(R.string.general_cancel))
                    }
                }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (isEditing) stringResource(R.string.edit_station_title) else stringResource(R.string.edit_station_add_station)) },
                    navigationIcon = {
                        IconButton(onClick = handleBackPress) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    },
                    actions = {
                        if (isEditing && station != null && !station.isCustom) {
                            var isResetting by remember { mutableStateOf(false) }
                            var showResetDialog by remember { mutableStateOf(false) }

                            if (showResetDialog) {
                                AlertDialog(
                                    onDismissRequest = { showResetDialog = false },
                                    title = { Text(stringResource(R.string.edit_station_reset_title)) },
                                    text = { Text(stringResource(R.string.edit_station_reset_message)) },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                showResetDialog = false
                                                isResetting = true
                                                viewModel.fetchOriginalStation(station.stationUuid) { freshStation ->
                                                    isResetting = false
                                                    if (freshStation != null) {
                                                        name = freshStation.name
                                                        url = freshStation.url
                                                        favicon = freshStation.favicon
                                                        tags = freshStation.tags.joinToString(", ")
                                                        countryCode = freshStation.countryCode
                                                        countrySearchText = if (countryCode.isNotBlank()) {
                                                            val disp = java.util.Locale("", countryCode).getDisplayCountry(java.util.Locale.getDefault())
                                                            "$disp ($countryCode)"
                                                        } else ""
                                                        iso3166_2 = freshStation.iso3166_2 ?: ""
                                                        stateSearchText = if (iso3166_2.isNotBlank()) {
                                                            val st = com.armanmaurya.internetradio.core.utils.StateUtils.getStatesForCountry(context, countryCode).find { it.code == iso3166_2 }
                                                            if (st != null) "${st.getDisplayName(java.util.Locale.getDefault().language)} (${st.code})" else iso3166_2
                                                        } else ""
                                                        languageCodes = freshStation.languageCodes.joinToString(", ")
                                                        homepage = freshStation.homepage
                                                        uploadMode = false
                                                        Toast.makeText(context, fieldsResetMessage, Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, failedFetchOriginalDataMessage, Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        ) {
                                            Text(stringResource(R.string.general_reset))
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showResetDialog = false }) {
                                            Text(stringResource(R.string.general_cancel))
                                        }
                                    }
                                )
                            }

                            if (isResetting) {
                                LoadingIndicator(
                                    modifier = Modifier.padding(end = 8.dp).size(24.dp)
                                )
                            } else {
                                IconButton(
                                    onClick = { showResetDialog = true }
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.edit_station_cd_reset_to_original))
                                }
                            }
                        }

                        val canSave = name.isNotBlank() && url.isNotBlank() && !isProbing

                        Row(
                            modifier = Modifier.padding(end = 16.dp).height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (uploadMode && showUploadMode) {
                                        isUploading = true
                                        val tagList = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                        val langList = languageCodes.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                        viewModel.uploadStationToRadioBrowser(
                                            stationUuid = if (isEditing) station!!.stationUuid else "",
                                            name = name,
                                            url = url,
                                            homepage = homepage,
                                            favicon = favicon,
                                            countryCode = countryCode,
                                            iso31662 = iso3166_2.ifBlank { null },
                                            languageCodes = langList,
                                            tags = tagList,
                                            codec = probedCodec,
                                            bitrate = probedBitrate,
                                            onSuccess = {
                                                isUploading = false
                                                Toast.makeText(context, uploadedSuccessfullyMessage, Toast.LENGTH_SHORT).show()
                                                onNavigateBack()
                                            },
                                            onError = { error ->
                                                isUploading = false
                                                Toast.makeText(context, java.lang.String.format(uploadFailedTemplate, error), Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    } else {
                                        if (isEditing && station != null) {
                                            val tagList = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                            val langList = languageCodes.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                            viewModel.updateStation(
                                                stationUuid = station.stationUuid,
                                                name = name,
                                                url = url,
                                                favicon = favicon,
                                                tags = tagList,
                                                countryCode = countryCode,
                                                languageCodes = langList,
                                                homepage = homepage,
                                                iso31662 = iso3166_2.ifBlank { null },
                                                codec = probedCodec,
                                                bitrate = probedBitrate
                                            )
                                        } else {
                                            viewModel.addStation(
                                                name = name,
                                                url = url,
                                                favicon = favicon,
                                                tags = tags,
                                                countryCode = countryCode,
                                                languageCodes = languageCodes,
                                                homepage = homepage,
                                                iso31662 = iso3166_2.ifBlank { null },
                                                codec = probedCodec,
                                                bitrate = probedBitrate
                                            )
                                        }
                                        Toast.makeText(context, savedMessage, Toast.LENGTH_SHORT).show()
                                        onNavigateBack()
                                    }
                                },
                                enabled = canSave && (!isEditing || hasUnsavedChanges || (station?.isCustom == true && uploadMode)) && !isUploading,
                                shape = if (showUploadMode) RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 0.dp, bottomEnd = 0.dp) else RoundedCornerShape(24.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                AnimatedContent(
                                    targetState = uploadMode && showUploadMode,
                                    label = "saveButtonAnimation"
                                ) { isUpload ->
                                    Text(
                                        text = if (isUpload) (if (isEditing) stringResource(R.string.edit_station_upload_save) else stringResource(R.string.edit_station_upload_add)) else (if (isEditing) stringResource(R.string.general_save) else stringResource(R.string.general_add))
                                    )
                                }
                            }
                            
                            if (showUploadMode) {
                                Button(
                                    onClick = { uploadMode = !uploadMode },
                                    enabled = true,
                                    shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 24.dp, bottomEnd = 24.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                    modifier = Modifier.width(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SwapVert,
                                        contentDescription = stringResource(R.string.edit_station_cd_toggle_save_mode),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                )
            }
        ) { paddingValues ->
            val isPureBlack = MaterialTheme.colorScheme.surface == Color.Black
            val extraBottomPadding = if (contentPadding.calculateBottomPadding() > 0.dp) 72.dp else 0.dp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        top = paddingValues.calculateTopPadding() + 16.dp,
                        bottom = paddingValues.calculateBottomPadding() + extraBottomPadding + 16.dp,
                        start = 16.dp,
                        end = 16.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.edit_station_name_field)) },
                    modifier = Modifier.fillMaxWidth().then(
                        if (isPureBlack) Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(16.dp)
                        ) else Modifier
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = url,
                        onValueChange = {
                            url = it
                            viewModel.checkDuplicateUrl(it)
                        },
                        label = { Text(stringResource(R.string.edit_station_stream_url_field)) },
                        modifier = Modifier.weight(1f).then(
                            if (isPureBlack) Modifier.border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(16.dp)
                            ) else Modifier
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = if (isPureBlack) Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(16.dp)
                        ) else Modifier
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(enabled = !isProbingMetadata) {
                                    if (url.startsWith("http")) {
                                        isProbingMetadata = true
                                        coroutineScope.launch {
                                            val result = viewModel.probeStream(url)
                                            if (result != null) {
                                                handleProbeResult(result)
                                            }
                                            isProbingMetadata = false
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isProbingMetadata) {
                                LoadingIndicator(
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.edit_station_refresh_metadata))
                            }
                        }
                    }
                }


                TextField(
                    value = favicon,
                    onValueChange = { favicon = it },
                    label = { Text(stringResource(R.string.edit_station_favicon_url_optional)) },
                    modifier = Modifier.fillMaxWidth().then(
                        if (isPureBlack) Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(16.dp)
                        ) else Modifier
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                TextField(
                    value = homepage,
                    onValueChange = { homepage = it },
                    label = { Text(stringResource(R.string.edit_station_homepage_url_optional)) },
                    modifier = Modifier.fillMaxWidth().then(
                        if (isPureBlack) Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(16.dp)
                        ) else Modifier
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                ExposedDropdownMenuBox(
                    expanded = expandedCountry,
                    onExpandedChange = { expandedCountry = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                        TextField(
                            value = countrySearchText,
                            onValueChange = { newValue ->
                                countrySearchText = newValue
                                expandedCountry = true
                                val matched = countries.find { c -> c.second.equals(newValue, ignoreCase = true) }
                                countryCode = matched?.first ?: ""
                            },
                            label = { Text(stringResource(R.string.edit_station_country_field)) },
                            modifier = Modifier.menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryEditable, true).fillMaxWidth().then(
                                if (isPureBlack) Modifier.border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(16.dp)
                                ) else Modifier
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            trailingIcon = { 
                                if (countrySearchText.isNotEmpty()) {
                                    IconButton(onClick = { 
                                        countrySearchText = ""
                                        countryCode = ""
                                        iso3166_2 = ""
                                        stateSearchText = ""
                                        expandedCountry = true
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.general_clear))
                                    }
                                } else {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCountry) 
                                }
                            },
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                        
                        val filteredCountries = countries.filter { it.second.contains(countrySearchText, ignoreCase = true) }
                        if (filteredCountries.isNotEmpty() && expandedCountry) {
                            ExposedDropdownMenu(
                                expanded = expandedCountry,
                                onDismissRequest = { expandedCountry = false }
                            ) {
                                filteredCountries.forEach { selectionOption ->
                                    DropdownMenuItem(
                                        text = { Text("${selectionOption.second} (${selectionOption.first})") },
                                        onClick = {
                                            if (countryCode != selectionOption.first) {
                                                iso3166_2 = ""
                                                stateSearchText = ""
                                            }
                                            countryCode = selectionOption.first
                                            countrySearchText = "${selectionOption.second} (${selectionOption.first})"
                                            expandedCountry = false
                                        }
                                    )
                                }
                            }
                        }
                }
                androidx.compose.animation.AnimatedVisibility(visible = statesList.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = expandedState,
                        onExpandedChange = { expandedState = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                            TextField(
                                value = stateSearchText,
                                onValueChange = { newValue ->
                                    stateSearchText = newValue
                                    expandedState = true
                                    val matched = statesList.find { st -> st.getDisplayName(java.util.Locale.getDefault().language).equals(newValue, ignoreCase = true) }
                                    iso3166_2 = matched?.code ?: ""
                                },
                                label = { Text(stringResource(R.string.edit_station_state_field)) },
                                modifier = Modifier.menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryEditable, true).fillMaxWidth().then(
                                    if (isPureBlack) Modifier.border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                        RoundedCornerShape(16.dp)
                                    ) else Modifier
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                trailingIcon = { 
                                    if (stateSearchText.isNotEmpty()) {
                                        IconButton(onClick = { 
                                            stateSearchText = ""
                                            iso3166_2 = ""
                                            expandedState = true
                                        }) {
                                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.general_clear))
                                        }
                                    } else {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedState) 
                                    }
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedContainerColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                            
                            val filteredStates = statesList.filter { it.getDisplayName(java.util.Locale.getDefault().language).contains(stateSearchText, ignoreCase = true) }
                            if (filteredStates.isNotEmpty() && expandedState) {
                                ExposedDropdownMenu(
                                    expanded = expandedState,
                                    onDismissRequest = { expandedState = false }
                                ) {
                                    filteredStates.forEach { st ->
                                        val stName = st.getDisplayName(java.util.Locale.getDefault().language)
                                        DropdownMenuItem(
                                            text = { Text("$stName (${st.code})") },
                                            onClick = {
                                                iso3166_2 = st.code
                                                stateSearchText = "$stName (${st.code})"
                                                expandedState = false
                                            }
                                        )
                                    }
                                }
                            }
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = expandedLanguage,
                    onExpandedChange = { expandedLanguage = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                        TextField(
                            value = languageSearchText,
                            onValueChange = { 
                                languageSearchText = it
                                expandedLanguage = true
                            },
                            label = { Text(stringResource(R.string.edit_station_add_languages)) },
                            modifier = Modifier.menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryEditable, true).fillMaxWidth().then(
                                if (isPureBlack) Modifier.border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(16.dp)
                                ) else Modifier
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            trailingIcon = { 
                                if (languageSearchText.isNotEmpty()) {
                                    IconButton(onClick = { 
                                        languageSearchText = ""
                                        expandedLanguage = true
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.general_clear))
                                    }
                                } else {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedLanguage) 
                                }
                            },
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                        
                        val filteredLanguages = allLanguages.filter { it.second.contains(languageSearchText, ignoreCase = true) }
                        if (filteredLanguages.isNotEmpty() && expandedLanguage) {
                            ExposedDropdownMenu(
                                expanded = expandedLanguage,
                                onDismissRequest = { expandedLanguage = false }
                            ) {
                                filteredLanguages.forEach { selectionOption ->
                                    DropdownMenuItem(
                                        text = { Text("${selectionOption.second} (${selectionOption.first})") },
                                        onClick = {
                                            val currentCodes = languageCodes.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                            if (!currentCodes.contains(selectionOption.first)) {
                                                val sep = if (currentCodes.isEmpty()) "" else ", "
                                                languageCodes = languageCodes.trimEnd(',', ' ') + sep + selectionOption.first
                                            }
                                            languageSearchText = ""
                                            expandedLanguage = false
                                        }
                                    )
                                }
                            }
                        }
                }
                
                val selectedLanguagesList = languageCodes.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (selectedLanguagesList.isNotEmpty()) {
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        selectedLanguagesList.forEach { code ->
                            val dispName = com.neovisionaries.i18n.LanguageAlpha3Code.getByCodeIgnoreCase(code)?.getName() ?: java.util.Locale(code).getDisplayLanguage(java.util.Locale.getDefault())
                            InputChip(
                                selected = true,
                                onClick = { },
                                label = { Text(if (dispName.isNotBlank()) "$dispName ($code)" else code) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.edit_station_cd_remove_language),
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable {
                                                val updated = selectedLanguagesList.toMutableList()
                                                updated.remove(code)
                                                languageCodes = updated.joinToString(", ")
                                            }
                                    )
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = expandedTags,
                    onExpandedChange = { expandedTags = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = tagsSearchText,
                        onValueChange = { 
                            tagsSearchText = it.lowercase()
                            expandedTags = true
                            viewModel.onTagSearchQueryChange(it)
                        },
                        label = { Text(stringResource(R.string.edit_station_add_tags)) },
                        modifier = Modifier.menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryEditable, true).fillMaxWidth().then(
                            if (isPureBlack) Modifier.border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(16.dp)
                            ) else Modifier
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        trailingIcon = { 
                            if (tagsSearchText.isNotEmpty()) {
                                IconButton(onClick = { 
                                    tagsSearchText = ""
                                    expandedTags = true
                                    viewModel.onTagSearchQueryChange("")
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.general_clear))
                                }
                            } else {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTags) 
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    
                    val query = tagsSearchText.trim()
                    if ((fetchedTags.isNotEmpty() || query.isNotEmpty()) && expandedTags) {
                        ExposedDropdownMenu(
                            expanded = expandedTags,
                            onDismissRequest = { expandedTags = false }
                        ) {
                            if (query.isNotEmpty() && fetchedTags.none { it.name.equals(query, ignoreCase = true) }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.edit_station_create_tag, query)) },
                                    onClick = {
                                        val currentTags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                        if (!currentTags.any { it.equals(query, ignoreCase = true) }) {
                                            val sep = if (currentTags.isEmpty()) "" else ", "
                                            tags = tags.trimEnd(',', ' ') + sep + query.lowercase()
                                        }
                                        tagsSearchText = ""
                                        expandedTags = false
                                        viewModel.onTagSearchQueryChange("")
                                    }
                                )
                            }
                            fetchedTags.forEach { tag ->
                                DropdownMenuItem(
                                    text = { Text(tag.name) },
                                    onClick = {
                                        val currentTags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                        if (!currentTags.any { it.equals(tag.name, ignoreCase = true) }) {
                                            val sep = if (currentTags.isEmpty()) "" else ", "
                                            tags = tags.trimEnd(',', ' ') + sep + tag.name.lowercase()
                                        }
                                        tagsSearchText = ""
                                        expandedTags = false
                                        viewModel.onTagSearchQueryChange("")
                                    }
                                )
                            }
                        }
                    }
                }
                
                val selectedTagsList = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (selectedTagsList.isNotEmpty()) {
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        selectedTagsList.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { },
                                label = { Text(tag) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.edit_station_cd_remove_tag),
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable {
                                                val updated = selectedTagsList.toMutableList()
                                                updated.remove(tag)
                                                tags = updated.joinToString(", ")
                                            }
                                    )
                                }
                            )
                        }
                    }
                }
                
                val kbpsUnit = stringResource(R.string.unit_kbps)
                val bitrateText = if (probedBitrate > 0) "$probedBitrate $kbpsUnit" else "-"
                val isCodecUnknown = probedCodec.equals("unknown", ignoreCase = true) || probedCodec.isBlank()
                val codecText = if (!isCodecUnknown) probedCodec.uppercase() else "-"
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp).fillMaxHeight(),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "$codecText • $bitrateText",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(enabled = !isProbingCodec) {
                                    if (url.startsWith("http")) {
                                        isProbingCodec = true
                                        coroutineScope.launch {
                                            val result = viewModel.probeStream(url)
                                            if (result != null) {
                                                probedCodec = result.codec
                                                probedBitrate = result.bitrate
                                            }
                                            isProbingCodec = false
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isProbingCodec) {
                                LoadingIndicator(
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.edit_station_refresh_metadata))
                            }
                        }
                    }
                }
                
                if (isEditing && station?.isCustom == true) {
                    Text(
                        text = stringResource(R.string.edit_station_custom),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                val duplicateStations by viewModel.duplicateStations.collectAsStateWithLifecycle()
                val isCheckingUrl by viewModel.isCheckingUrl.collectAsStateWithLifecycle()

                if ((isCheckingUrl || duplicateStations.isNotEmpty()) && (!isEditing || station?.isCustom == true)) {
                    Text(
                        text = stringResource(R.string.edit_station_similar_stations),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (isCheckingUrl) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator(modifier = Modifier.size(32.dp))
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(150.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 600.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            userScrollEnabled = false
                        ) {
                            items(duplicateStations) { s ->
                                StationCard(
                                    station = s,
                                    onClick = { onPlayStation(s) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                val safeDrawingBottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
                Spacer(modifier = Modifier.height(96.dp + safeDrawingBottom))
            }
        }
    }
}
