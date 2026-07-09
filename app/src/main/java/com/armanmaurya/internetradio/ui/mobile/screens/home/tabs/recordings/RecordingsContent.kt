package com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.recordings

import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.armanmaurya.internetradio.data.repository.RecordingFile
import com.armanmaurya.internetradio.data.repository.RecordingFolder
import com.armanmaurya.internetradio.ui.shared.viewmodels.RecordingsViewModel
import java.util.Locale
import androidx.compose.animation.*
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import android.media.MediaPlayer
import com.armanmaurya.internetradio.ui.mobile.components.RecordingFileItem

@Composable
fun RecordingsContent(
    viewModel: RecordingsViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel(),
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    var selectedFolder by remember { mutableStateOf<RecordingFolder?>(null) }
    val context = LocalContext.current
    val isPureBlack = MaterialTheme.colorScheme.surface == androidx.compose.ui.graphics.Color.Black

    LaunchedEffect(Unit) {
        viewModel.loadFolders()
    }

    if (folders.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No recordings found",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        AnimatedContent(
            targetState = selectedFolder,
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
        ) { currentFolder ->
            if (currentFolder == null) {
                // Show Folders
                LazyColumn(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(folders) { folder ->
                    ListItem(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (isPureBlack) Modifier.border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(12.dp)
                                ) else Modifier
                            )
                            .clickable { selectedFolder = folder },
                        headlineContent = {
                            Text(
                                text = folder.stationName,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = {
                            Text(
                                text = "${folder.recordings.size} recordings",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (isPureBlack) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        } else {
            // Show Files in Folder
            Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedFolder = null }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back to Folders",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = currentFolder.stationName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                var expandedRecording by remember { mutableStateOf<RecordingFile?>(null) }
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 0.dp)
                ) {
                    items(currentFolder.recordings) { recording ->
                        val isExpanded = expandedRecording?.uri == recording.uri
                        RecordingFileItem(
                            recording = recording,
                            isExpanded = isExpanded,
                            onClick = {
                                expandedRecording = if (isExpanded) null else recording
                            }
                        )
                    }
                }
            }
        }
    }
}
}
