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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.data.model.RadioStation

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StationCard(
    station: RadioStation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDeleteClick: (() -> Unit)? = null,
    onEditClick: (() -> Unit)? = null,
    isCurrentlyPlaying: Boolean = false,
    isPlaybackActive: Boolean = false,
    isFavorite: Boolean = false,
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

    val subtitleText = remember(station.country, station.language) {
        buildString {
            if (station.country.isNotBlank()) append(station.country)
            if (station.country.isNotBlank() && station.language.isNotBlank()) append(" • ")
            if (station.language.isNotBlank()) append(station.language)
        }
    }

    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = if (isCurrentlyPlaying) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = station.favicon.ifBlank { null },
                contentDescription = stringResource(R.string.home_cd_station_logo, station.name),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E1E1E)),
                error = painterResource(id = R.drawable.ic_launcher_foreground),
                fallback = painterResource(id = R.drawable.ic_launcher_foreground)
            )

            // Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(gradientBrush)
            )

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

            if (isFavorite) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = stringResource(R.string.home_cd_favorite),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                )
            }

            if (onDeleteClick != null || onEditClick != null) {
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
                        if (onDeleteClick != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home_delete_station)) },
                                onClick = {
                                    showMenu = false
                                    onDeleteClick()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
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
                    modifier = Modifier.basicMarquee()
                )
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
            }
        }
    }
}