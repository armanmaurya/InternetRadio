package com.armanmaurya.internetradio.ui.mobile.screens.edit

import androidx.compose.ui.res.stringResource
import com.armanmaurya.internetradio.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.armanmaurya.internetradio.ui.shared.viewmodels.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditStationScreen(
    stationUuid: String?,
    viewModel: LibraryViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
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
        var name by remember(station) { mutableStateOf(station?.name ?: "") }
        var url by remember(station) { mutableStateOf(station?.url ?: "") }
        var favicon by remember(station) { mutableStateOf(station?.favicon ?: "") }
        var tags by remember(station) { mutableStateOf(station?.tags?.joinToString(", ") ?: "") }
        var country by remember(station) { mutableStateOf(station?.country ?: "") }
        var language by remember(station) { mutableStateOf(station?.language ?: "") }

        val hasUnsavedChanges = if (isEditing && station != null) {
            name != station.name ||
            url != station.url ||
            favicon != station.favicon ||
            tags != station.tags.joinToString(", ") ||
            country != station.country ||
            language != station.language
        } else {
            name.isNotBlank() || url.isNotBlank() || favicon.isNotBlank() || tags.isNotBlank() || country.isNotBlank() || language.isNotBlank()
        }

        var showExitWarningDialog by remember { mutableStateOf(false) }

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
                                                        country = freshStation.country
                                                        language = freshStation.language
                                                        Toast.makeText(context, context.getString(R.string.edit_station_fields_reset_message), Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, context.getString(R.string.edit_station_failed_fetch_original_data), Toast.LENGTH_SHORT).show()
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
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(end = 8.dp).size(24.dp), 
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(
                                    onClick = { showResetDialog = true }
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.edit_station_cd_reset_to_original))
                                }
                            }
                        }
                        Button(
                            onClick = {
                                if (isEditing && station != null) {
                                    val tagList = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                    viewModel.updateStation(
                                        stationUuid = station.stationUuid,
                                        name = name,
                                        url = url,
                                        favicon = favicon,
                                        tags = tagList
                                    )
                                } else {
                                    viewModel.addStation(
                                        name = name,
                                        url = url,
                                        favicon = favicon,
                                        tags = tags,
                                        country = country,
                                        state = "",
                                        language = language
                                    )
                                }
                                Toast.makeText(context, context.getString(R.string.edit_station_saved_message), Toast.LENGTH_SHORT).show()
                                onNavigateBack()
                            },
                            modifier = Modifier.padding(end = 16.dp),
                            enabled = name.isNotBlank() && url.isNotBlank() && hasUnsavedChanges
                        ) {
                            Text(if (isEditing) stringResource(R.string.general_save) else stringResource(R.string.general_add))
                        }
                    }
                )
            }
        ) { paddingValues ->
            val isPureBlack = MaterialTheme.colorScheme.surface == Color.Black
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
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
                TextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.edit_station_stream_url_field)) },
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = country,
                        onValueChange = { country = it },
                        label = { Text(stringResource(R.string.edit_station_country_field)) },
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
                    TextField(
                        value = language,
                        onValueChange = { language = it },
                        label = { Text(stringResource(R.string.edit_station_language_field)) },
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
                }
                TextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text(stringResource(R.string.edit_station_tags_optional)) },
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
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
