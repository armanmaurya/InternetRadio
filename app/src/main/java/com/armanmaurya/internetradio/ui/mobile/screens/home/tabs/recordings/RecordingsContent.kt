package com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.recordings

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.armanmaurya.internetradio.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.armanmaurya.internetradio.domain.model.PlaybackSource
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.model.RecordingFile
import com.armanmaurya.internetradio.ui.shared.viewmodels.RecordingsViewModel
import androidx.compose.animation.*
import com.armanmaurya.internetradio.domain.model.RecordingSession
import com.armanmaurya.internetradio.ui.mobile.components.RecordingFileItem
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.StationCard

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RecordingsContent(
    viewModel: RecordingsViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel(),
    activeSessions: Map<String, RecordingSession>? = null,
    onStopRecording: ((String) -> Unit)? = null,
    onStationClick: (List<RadioStation>, Int, PlaybackSource) -> Unit = { _, _, _ -> },
    onEditStation: (String) -> Unit = {},
    onExportStation: ((RadioStation) -> Unit)? = null,
    playingStationUuid: String? = null,
    isPlaybackActive: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val vmActiveSessions by viewModel.activeSessions.collectAsStateWithLifecycle()
    val currentActiveSessions = activeSessions ?: vmActiveSessions
    var selectedStationName by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val isPureBlack = MaterialTheme.colorScheme.surface == androidx.compose.ui.graphics.Color.Black

    var selectionMode by remember { mutableStateOf(false) }
    var selectedFolders by remember { mutableStateOf(emptySet<String>()) }
    var selectedFiles by remember { mutableStateOf(emptySet<String>()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadFolders()
    }

    LaunchedEffect(folders, selectedStationName) {
        if (selectedStationName != null && folders.none { it.stationName == selectedStationName }) {
            selectedStationName = null
        }
    }

    LaunchedEffect(selectedStationName) {
        selectionMode = false
        selectedFolders = emptySet()
        selectedFiles = emptySet()
    }

    if (showDeleteConfirmDialog) {
        val deleteCount = if (selectedStationName == null) selectedFolders.size else selectedFiles.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(stringResource(R.string.delete_recording)) },
            text = { Text(
                pluralStringResource(
                    R.plurals.delete_recordings_message,
                    deleteCount,
                    deleteCount
                )
            ) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmDialog = false
                    if (selectedStationName == null) {
                        viewModel.deleteFolders(selectedFolders.toList())
                    } else {
                        val currentFolder = folders.find { it.stationName == selectedStationName }
                        if (currentFolder != null) {
                            val filesToDelete = currentFolder.recordings.filter { it.uri.toString() in selectedFiles }
                            viewModel.deleteRecordings(filesToDelete)
                        }
                    }
                    selectionMode = false
                    selectedFolders = emptySet()
                    selectedFiles = emptySet()
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        AnimatedVisibility(visible = selectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { 
                            selectionMode = false
                            selectedFolders = emptySet()
                            selectedFiles = emptySet()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close, 
                        contentDescription = "Cancel",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Text(
                    text = stringResource(R.string.recordings_selected, if (selectedStationName == null) selectedFolders.size else selectedFiles.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                )
                
                val isAllSelected = if (selectedStationName == null) {
                    selectedFolders.size == folders.size && folders.isNotEmpty()
                } else {
                    val currentFolder = folders.find { it.stationName == selectedStationName }
                    currentFolder != null && selectedFiles.size == currentFolder.recordings.size && currentFolder.recordings.isNotEmpty()
                }
                
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { 
                            if (isAllSelected) {
                                selectedFolders = emptySet()
                                selectedFiles = emptySet()
                            } else {
                                if (selectedStationName == null) {
                                    selectedFolders = folders.map { it.stationName }.toSet()
                                } else {
                                    val currentFolder = folders.find { it.stationName == selectedStationName }
                                    if (currentFolder != null) {
                                        selectedFiles = currentFolder.recordings.map { it.uri.toString() }.toSet()
                                    }
                                }
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isAllSelected) Icons.Default.Deselect else Icons.Default.SelectAll, 
                        contentDescription = "Select All",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .clickable { showDeleteConfirmDialog = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Delete, 
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        
        val libraryStationUuids by viewModel.libraryStationUuids.collectAsStateWithLifecycle()
        var expandedRecording by remember { mutableStateOf<RecordingFile?>(null) }
        
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            if (currentActiveSessions.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.recordings_currently_recording),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                val sessionsList = currentActiveSessions.values.toList()
                items(
                    items = sessionsList,
                    key = { "session_" + it.station.stationUuid }
                ) { session ->
                    val duration by session.durationSeconds.collectAsState(initial = 0L)
                    StationCard(
                        station = session.station,
                        onClick = { onStationClick(sessionsList.map { it.station }, sessionsList.indexOf(session), PlaybackSource.None) },
                        isRecordingOverlay = true,
                        recordingDuration = duration,
                        onStopRecordingClick = { (onStopRecording ?: viewModel::stopRecording)(session.station.stationUuid) },
                        isFavorite = libraryStationUuids.contains(session.station.stationUuid),
                        onToggleFavoriteClick = { viewModel.toggleLibrary(session.station) },
                        onEditClick = if (libraryStationUuids.contains(session.station.stationUuid)) { { onEditStation(session.station.stationUuid) } } else null,
                        onExportClick = { onExportStation?.invoke(session.station) },
                        isCurrentlyPlaying = playingStationUuid == session.station.stationUuid,
                        isPlaybackActive = isPlaybackActive,
                        modifier = Modifier.animateItem()
                    )
                }
            }

            if (folders.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AnimatedContent(
                        targetState = selectedStationName,
                        transitionSpec = {
                            if (targetState != null) {
                                // Entering folder (slide left)
                                (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                    slideOutHorizontally { width -> -width } + fadeOut()
                                )
                            } else {
                                // Exiting folder (slide right)
                                (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                    slideOutHorizontally { width -> width } + fadeOut()
                                )
                            }
                        },
                        label = "FolderTransition"
                    ) { stationName ->
                        val currentFolder = folders.find { it.stationName == stationName }
                        
                        if (stationName == null || currentFolder == null) {
                            // Show Folders
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 7.dp)
                            ) {
                                folders.forEach { folder ->
                                    val isSelected = folder.stationName in selectedFolders
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                else if (isPureBlack) androidx.compose.ui.graphics.Color.Black 
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                            .then(
                                                if (isPureBlack) Modifier.border(
                                                    1.dp,
                                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                                    RoundedCornerShape(12.dp)
                                                ) else Modifier
                                            )
                                            .combinedClickable(
                                                onClick = { 
                                                    if (selectionMode) {
                                                        if (isSelected) selectedFolders -= folder.stationName else selectedFolders += folder.stationName
                                                        if (selectedFolders.isEmpty()) selectionMode = false
                                                    } else {
                                                        selectedStationName = folder.stationName 
                                                    }
                                                },
                                                onLongClick = {
                                                    if (!selectionMode) {
                                                        selectionMode = true
                                                        selectedFolders += folder.stationName
                                                    }
                                                }
                                            ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(RoundedCornerShape(
                                                    topStart = 12.dp,
                                                    bottomStart = 12.dp,
                                                    topEnd = 8.dp,
                                                    bottomEnd = 8.dp
                                                ))
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.width(16.dp))
                                        
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = folder.stationName,
                                                style = MaterialTheme.typography.bodyLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = pluralStringResource(
                                                    R.plurals.recordings_count,
                                                    folder.recordings.size,
                                                    folder.recordings.size
                                                ),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (selectionMode) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = null,
                                                modifier = Modifier.padding(end = 16.dp)
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.width(16.dp))
                                        }
                                    }
                                }
                            }
                        } else {
                            // Show Files in Folder
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(MaterialTheme.shapes.small)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { selectedStationName = null }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = stringResource(R.string.home_cd_back_to_folders),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(MaterialTheme.shapes.small)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = currentFolder.stationName,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                
                                var expandedRecording by remember { mutableStateOf<RecordingFile?>(null) }
                                
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp)
                                ) {
                                    currentFolder.recordings.forEach { recording ->
                                        val isExpanded = expandedRecording?.uri == recording.uri
                                        val isSelected = recording.uri.toString() in selectedFiles
                                        RecordingFileItem(
                                            recording = recording,
                                            isExpanded = isExpanded,
                                            isSelected = isSelected,
                                            selectionMode = selectionMode,
                                            onClick = {
                                                if (selectionMode) {
                                                    if (isSelected) selectedFiles -= recording.uri.toString() else selectedFiles += recording.uri.toString()
                                                    if (selectedFiles.isEmpty()) selectionMode = false
                                                } else {
                                                    expandedRecording = if (isExpanded) null else recording
                                                }
                                            },
                                            onLongClick = {
                                                if (!selectionMode) {
                                                    selectionMode = true
                                                    selectedFiles += recording.uri.toString()
                                                }
                                            },
                                            onDelete = {
                                                viewModel.deleteRecording(recording)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        if (folders.isEmpty() && currentActiveSessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.general_no_recordings),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}}
