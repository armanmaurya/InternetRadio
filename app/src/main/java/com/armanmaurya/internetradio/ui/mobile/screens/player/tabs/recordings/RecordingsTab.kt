package com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.recordings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.data.repository.RecordingFile
import com.armanmaurya.internetradio.ui.mobile.components.RecordingFileItem

@Composable
fun RecordingsTab(
    stationRecordings: List<RecordingFile>,
    listState: LazyListState,
    nestedScrollConnection: NestedScrollConnection
) {
    var expandedRecording by remember { mutableStateOf<RecordingFile?>(null) }
    
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .nestedScroll(nestedScrollConnection)
    ) {
        if (stationRecordings.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.general_no_recordings),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(stationRecordings.size) { index ->
                val recording = stationRecordings[index]
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
