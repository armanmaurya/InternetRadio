package com.armanmaurya.internetradio.ui.mobile.screens.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
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
    val station = if (stationUuid != null) stations.find { it.stationUuid == stationUuid } else null

    val isEditing = stationUuid != null

    if (isEditing && station == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Edit Station") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                Text("Station not found")
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
                title = { Text("Unsaved Changes") },
                text = { Text("You have unsaved changes. Are you sure you want to discard them and exit?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showExitWarningDialog = false
                            onNavigateBack()
                        }
                    ) {
                        Text("Discard")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitWarningDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (isEditing) "Edit Station" else "Add Custom Station") },
                    navigationIcon = {
                        IconButton(onClick = handleBackPress) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (isEditing && station != null && !station.isCustom) {
                            var isResetting by remember { mutableStateOf(false) }
                            var showResetDialog by remember { mutableStateOf(false) }

                            if (showResetDialog) {
                                AlertDialog(
                                    onDismissRequest = { showResetDialog = false },
                                    title = { Text("Reset Station") },
                                    text = { Text("Are you sure you want to reset this station to its original data? All your edits will be lost.") },
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
                                                        Toast.makeText(context, "Fields reset to original data", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "Failed to fetch original data", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        ) {
                                            Text("Reset")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showResetDialog = false }) {
                                            Text("Cancel")
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
                                    Icon(Icons.Default.Refresh, contentDescription = "Reset to Original")
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
                                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                onNavigateBack()
                            },
                            modifier = Modifier.padding(end = 16.dp),
                            enabled = name.isNotBlank() && url.isNotBlank() && hasUnsavedChanges
                        ) {
                            Text(if (isEditing) "Save" else "Add")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Stream URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = favicon,
                    onValueChange = { favicon = it },
                    label = { Text("Favicon URL (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = country,
                        onValueChange = { country = it },
                        label = { Text("Country (Optional)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = language,
                        onValueChange = { language = it },
                        label = { Text("Language (Optional)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (comma separated)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
