package com.armanmaurya.internetradio.ui.mobile.components

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.armanmaurya.internetradio.R
import androidx.compose.ui.unit.dp
import com.armanmaurya.internetradio.domain.model.RecordingFile
import java.util.Locale

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RecordingFileItem(
    recording: RecordingFile,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    isCurrentlyPlaying: Boolean = false,
    playbackIsPlaying: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDelete: () -> Unit,
    onSaveNote: ((String?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val sizeMb = recording.sizeBytes / (1024 * 1024f)
    val timeStr = DateUtils.getRelativeTimeSpanString(
        recording.lastModified,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()

    var showMenu by remember(recording.uri) { mutableStateOf(false) }
    var isDeleting by remember(recording.uri) { mutableStateOf(false) }
    var isEditingNote by remember(recording.uri) { mutableStateOf(false) }
    var isNoteExpanded by remember(recording.uri) { mutableStateOf(false) }
    var noteDraft by remember(recording.uri, recording.note) { mutableStateOf(recording.note.orEmpty()) }
    val focusRequester = remember { FocusRequester() }
    val isPureBlack = MaterialTheme.colorScheme.surface == androidx.compose.ui.graphics.Color.Black

    LaunchedEffect(selectionMode) {
        if (selectionMode && isEditingNote) {
            isEditingNote = false
        }
    }

    LaunchedEffect(isEditingNote) {
        if (isEditingNote) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    BackHandler(enabled = isEditingNote) {
        isEditingNote = false
        noteDraft = recording.note.orEmpty()
    }

    val alpha by animateFloatAsState(
        targetValue = if (isDeleting) 0.5f else 1f,
        label = "deletingAlpha"
    )

    val durationStr = if (recording.durationMs > 0L) {
        DateUtils.formatElapsedTime(recording.durationMs / 1000L)
    } else null
    val detailsStr = if (durationStr != null) {
        String.format(Locale.getDefault(), "%s • %.1f MB • %s", durationStr, sizeMb, timeStr)
    } else {
        String.format(Locale.getDefault(), "%.1f MB • %s", sizeMb, timeStr)
    }

    val hasNote = !recording.note.isNullOrBlank()
    val hasBottomContent = (isEditingNote && onSaveNote != null) || (!isEditingNote && hasNote)
    val saveAction = {
        val clean = noteDraft.trim().takeIf { it.isNotEmpty() }
        onSaveNote?.invoke(clean)
        isEditingNote = false
    }

    Column(
        modifier = modifier
            .alpha(alpha)
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else if (isPureBlack) androidx.compose.ui.graphics.Color.Black 
                else if (isCurrentlyPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .then(
                if (isPureBlack) Modifier.border(
                    1.dp,
                    if (isSelected || isCurrentlyPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    RoundedCornerShape(12.dp)
                ) else Modifier
            )
            .animateContentSize()
            .combinedClickable(
                enabled = !isDeleting,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 4.dp,
                    top = 12.dp,
                    bottom = if (hasBottomContent) 4.dp else 12.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = recording.fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = detailsStr,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null,
                    modifier = Modifier.padding(start = 8.dp, end = 12.dp)
                )
            } else if (isEditingNote && onSaveNote != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            isEditingNote = false
                            noteDraft = recording.note.orEmpty()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.recording_cancel_note),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = saveAction
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.recording_save_note),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more_options),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (onSaveNote != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (recording.note.isNullOrBlank()) R.string.recording_add_note
                                            else R.string.recording_edit_note
                                        )
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    noteDraft = recording.note.orEmpty()
                                    isEditingNote = true
                                },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_recording)) },
                            onClick = {
                                showMenu = false
                                isDeleting = true
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null)
                            }
                        )
                    }
                }
            }
        }

        if (!isEditingNote && !recording.note.isNullOrBlank()) {
            Text(
                text = recording.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (isNoteExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .then(
                        if (!selectionMode) {
                            Modifier.clickable { isNoteExpanded = !isNoteExpanded }
                        } else {
                            Modifier
                        }
                    )
            )
        } else if (isEditingNote && onSaveNote != null) {
            OutlinedTextField(
                value = noteDraft,
                onValueChange = { noteDraft = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.recording_note_placeholder),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                shape = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { saveAction() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
                    .focusRequester(focusRequester)
            )
        }
    }
}
