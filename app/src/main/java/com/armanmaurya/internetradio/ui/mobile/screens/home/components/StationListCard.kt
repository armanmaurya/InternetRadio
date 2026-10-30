package com.armanmaurya.internetradio.ui.mobile.screens.home.components

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.ui.shared.theme.LocalAppPreferences
import androidx.compose.ui.unit.sp

@Composable
fun StationListCard(
    station: RadioStation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleFavoriteClick: (() -> Unit)? = null,
    onEditClick: (() -> Unit)? = null,
    onRemoveFromRecentClick: (() -> Unit)? = null,
    onExportClick: (() -> Unit)? = null,
    isCurrentlyPlaying: Boolean = false,
    isPlaybackActive: Boolean = false,
    isFavorite: Boolean = false,
    isRecordingOverlay: Boolean = false,
    recordingDuration: Long = 0L,
    onStopRecordingClick: (() -> Unit)? = null,
    onRecordClick: (() -> Unit)? = null,
    isRecording: Boolean = false,
    isReconnecting: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }

    val kbpsUnit = stringResource(R.string.unit_kbps)
    val subtitleText = remember(station.country, station.countryCode, station.language, station.languageCodes, station.codec, station.bitrate, kbpsUnit) {
        buildString {
            val displayCountry = if (station.countryCode.isNotBlank()) {
                java.util.Locale("", station.countryCode).getDisplayCountry(java.util.Locale.getDefault())
            } else {
                station.country
            }

            val displayLanguage = if (station.languageCodes.isNotEmpty()) {
                station.languageCodes.joinToString(", ") { code ->
                    java.util.Locale(code).getDisplayLanguage(java.util.Locale.getDefault())
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                }
            } else {
                station.language
            }

            if (displayCountry.isNotBlank()) append(displayCountry)
            if (displayCountry.isNotBlank() && displayLanguage.isNotBlank()) append(" • ")
            if (displayLanguage.isNotBlank()) append(displayLanguage)
            
            val hasPrevious = displayCountry.isNotBlank() || displayLanguage.isNotBlank()
            if (hasPrevious && (station.codec.isNotBlank() || station.bitrate > 0)) {
                append(" | ")
            }
            if (station.codec.isNotBlank()) append(station.codec)
            if (station.codec.isNotBlank() && station.bitrate > 0) append(" ")
            if (station.bitrate > 0) append("${station.bitrate} $kbpsUnit")
        }
    }

    val isPureBlack = MaterialTheme.colorScheme.surface == Color.Black

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(12.dp),
        border = if (isPureBlack) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentlyPlaying) MaterialTheme.colorScheme.secondaryContainer else CardDefaults.cardColors().containerColor
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (isCurrentlyPlaying) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        else Modifier
                    )
            ) {
                val showThumbnails = LocalAppPreferences.current.showStationThumbnails

                if (showThumbnails && station.favicon.isNotBlank()) {
                    coil3.compose.SubcomposeAsyncImage(
                        model = coil3.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(station.favicon)
                            .build(),
                        contentDescription = stringResource(R.string.home_cd_station_logo, station.name),
                        contentScale = ContentScale.FillBounds,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        error = {
                            androidx.compose.foundation.Image(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary),
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier.fillMaxSize()
                            )
                        },
                        loading = {
                            androidx.compose.foundation.Image(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary),
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    )
                } else {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary),
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }

                if (isCurrentlyPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        PlayingVisualizer(isPlaybackActive = isPlaybackActive)
                    }
                }

                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(2.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isFavorite) {
                        val isLight = MaterialTheme.colorScheme.surface.luminance() > 0.5f
                        val overlayColor = if (isLight) MaterialTheme.colorScheme.inversePrimary else MaterialTheme.colorScheme.primary
                        val overlayVisible = isCurrentlyPlaying || isRecordingOverlay || isRecording

                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = stringResource(R.string.home_cd_favorite),
                            tint = if (overlayVisible) overlayColor else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    if (isRecordingOverlay || isRecording) {
                        val pillBg = if (isReconnecting) {
                            Color(0xFFE65100).copy(alpha = 0.85f)
                        } else {
                            Color.Black.copy(alpha = 0.55f)
                        }
                        // Mic or Sync + timer/reconnecting pill
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier
                                .background(
                                    color = pillBg,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 3.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)
                        ) {
                            if (isReconnecting) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = stringResource(R.string.recording_reconnecting),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = Color.Red,
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = String.format(java.util.Locale.US, "%02d:%02d", recordingDuration / 60, recordingDuration % 60),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.onSecondaryContainer else Color.Unspecified,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                )
            }

            if (onToggleFavoriteClick != null || onEditClick != null || onRemoveFromRecentClick != null || onExportClick != null || onRecordClick != null || onStopRecordingClick != null) {
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.home_cd_more_options),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        // Shortcuts only exist for library stations
                        if (isFavorite) {
                            com.armanmaurya.internetradio.ui.mobile.components.CreateShortcutMenuItem(
                                station = station,
                                onDismissMenu = { showMenu = false }
                            )
                        }
                        if (onStopRecordingClick != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_stop_recording)) },
                                onClick = {
                                    showMenu = false
                                    onStopRecordingClick()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Stop, contentDescription = null, tint = Color.Red)
                                }
                            )
                        } else if (onRecordClick != null) {
                            DropdownMenuItem(
                                text = { Text(if (isRecording) stringResource(R.string.action_stop_recording) else stringResource(R.string.action_record_station)) },
                                onClick = {
                                    showMenu = false
                                    onRecordClick()
                                },
                                leadingIcon = {
                                    Icon(
                                        if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = if (isRecording) Color.Red else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            )
                        }
                        if (onExportClick != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home_export_station)) },
                                onClick = {
                                    showMenu = false
                                    onExportClick()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.FileUpload, contentDescription = null)
                                }
                            )
                        }
                        if (onEditClick != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_station_title)) },
                                onClick = {
                                    showMenu = false
                                    onEditClick()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                }
                            )
                        }
                        if (onToggleFavoriteClick != null) {
                            DropdownMenuItem(
                                text = { Text(if (isFavorite) stringResource(R.string.home_remove_from_library) else stringResource(R.string.home_add_to_library)) },
                                onClick = {
                                    showMenu = false
                                    onToggleFavoriteClick()
                                },
                                leadingIcon = {
                                    Icon(
                                        if (isFavorite) Icons.Default.BookmarkRemove else Icons.Default.BookmarkAdd,
                                        contentDescription = null,
                                        tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            )
                        }
                        if (onRemoveFromRecentClick != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home_remove_from_recent)) },
                                onClick = {
                                    showMenu = false
                                    onRemoveFromRecentClick()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
