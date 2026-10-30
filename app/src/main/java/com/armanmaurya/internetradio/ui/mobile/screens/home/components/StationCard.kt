package com.armanmaurya.internetradio.ui.mobile.screens.home.components

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StationCard(
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
    var showMenu by remember { mutableStateOf(false) }

    val gradientBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                Color.Black.copy(alpha = 0.8f)
            )
        )
    }

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

    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = if (isCurrentlyPlaying) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        val showThumbnails = LocalAppPreferences.current.showStationThumbnails

        Box(modifier = Modifier.fillMaxSize()) {
            if (showThumbnails && station.favicon.isNotBlank()) {
                coil3.compose.SubcomposeAsyncImage(
                    model = coil3.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(station.favicon)
                        .build(),
                    contentDescription = stringResource(R.string.home_cd_station_logo, station.name),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    error = {
                        androidx.compose.foundation.Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxSize()
                        )
                    },
                    loading = {
                        androidx.compose.foundation.Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )
            } else {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }

            // Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(gradientBrush)
            )

            // Top-Right Corner Gradient for Icon Visibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val radialGradient = Brush.radialGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent),
                            center = Offset(size.width, 0f),
                            radius = size.width * 0.4f
                        )
                        onDrawBehind {
                            drawRect(radialGradient)
                        }
                    }
            )

            // Top-Left corner gradient for badge/bookmark visibility
            if (isFavorite || isRecordingOverlay || isRecording) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithCache {
                            val radialGradient = Brush.radialGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent),
                                center = Offset(0f, 0f),
                                radius = size.width * 0.45f
                            )
                            onDrawBehind {
                                drawRect(radialGradient)
                            }
                        }
                )
            }

            // Dark overlay for playing/recording states
            if (isCurrentlyPlaying || isRecordingOverlay || isRecording) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCurrentlyPlaying) {
                        PlayingVisualizer(isPlaybackActive = isPlaybackActive)
                    }
                }
            }
            
            // Top-left indicators
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
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
                        modifier = Modifier.padding(2.dp)
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
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)
                    ) {
                        if (isReconnecting) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.recording_reconnecting),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = Color.Red,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = String.format(java.util.Locale.US, "%02d:%02d", recordingDuration / 60, recordingDuration % 60),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            if (onToggleFavoriteClick != null || onEditClick != null || onRemoveFromRecentClick != null || onExportClick != null || onRecordClick != null || onStopRecordingClick != null) {
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.home_cd_more_options),
                            tint = Color.White
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

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                )
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                )
            }
        }
    }
}