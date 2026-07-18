package com.armanmaurya.internetradio.ui.tv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.PlayingVisualizer

@Composable
fun StationCard(
    station: RadioStation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrentlyPlaying: Boolean = false,
    isPlaybackActive: Boolean = false,
    isFavorite: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.aspectRatio(1f),
        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = station.favicon.ifBlank { null },
                contentDescription = "${station.name} logo",
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
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
            )

            // Top-Right Corner Gradient for Icon Visibility
            if (isFavorite) {
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

            if (isFavorite) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = "In Library",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
                Text(
                    text = buildString {
                        if (station.country.isNotBlank()) append(station.country)
                        if (station.country.isNotBlank() && station.language.isNotBlank()) append(" • ")
                        if (station.language.isNotBlank()) append(station.language)
                        
                        val hasPrevious = station.country.isNotBlank() || station.language.isNotBlank()
                        if (hasPrevious && (station.codec.isNotBlank() || station.bitrate > 0)) {
                            append(" | ")
                        }
                        if (station.codec.isNotBlank()) append(station.codec)
                        if (station.codec.isNotBlank() && station.bitrate > 0) append(" ")
                        if (station.bitrate > 0) append("${station.bitrate} kbps")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
            }
        }
    }
}
