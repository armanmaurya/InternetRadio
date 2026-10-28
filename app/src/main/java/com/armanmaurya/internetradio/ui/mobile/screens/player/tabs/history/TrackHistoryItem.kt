package com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.history

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity
import com.armanmaurya.internetradio.domain.model.RecordingTrack
import java.net.URLEncoder

@Composable
fun TrackHistoryItem(
    track: TrackHistoryEntity,
    modifier: Modifier = Modifier,
    showClockTime: Boolean = false,
    onToggleStar: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val time = if (showClockTime) {
        android.text.format.DateFormat.getTimeFormat(context).format(track.timestamp)
    } else {
        DateUtils.getRelativeTimeSpanString(
            track.timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }

    TrackHistoryItem(
        title = track.trackTitle,
        subtitle = "",
        rawTrackTitle = track.rawTrackTitle,
        coverArtUrl = track.coverArtUrl,
        trailingText = time,
        isStarred = track.isStarred,
        onToggleStar = onToggleStar,
        isActive = false,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
fun TrackHistoryItem(
    track: RecordingTrack,
    isActive: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val time = DateUtils.formatElapsedTime(track.offsetMs / 1000L)

    TrackHistoryItem(
        title = track.title,
        subtitle = track.artist.orEmpty(),
        rawTrackTitle = track.rawTitle,
        coverArtUrl = track.coverArtUrl,
        trailingText = time,
        isActive = isActive,
        onClick = onClick,
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackHistoryItem(
    title: String,
    subtitle: String = "",
    rawTrackTitle: String = "",
    coverArtUrl: String? = null,
    trailingText: String = "",
    isStarred: Boolean = false,
    onToggleStar: (() -> Unit)? = null,
    isActive: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val trackCopiedMessage = stringResource(R.string.player_copied_track_to_clipboard)
    val copiedToClipboardMessage = stringResource(R.string.player_copied_to_clipboard)
    val isPureBlack = MaterialTheme.colorScheme.surfaceContainerLow == androidx.compose.ui.graphics.Color.Black

    val parts = remember(title, subtitle) {
        if (subtitle.isNotBlank()) {
            Pair(title.trim(), subtitle.trim())
        } else {
            val split = title.split(" - ", limit = 2)
            Pair(split[0].trim(), if (split.size > 1) split[1].trim() else "")
        }
    }
    val displayTitle = parts.first
    val displaySubtitle = parts.second
    val fullTrackName = if (displaySubtitle.isNotEmpty()) "$displayTitle - $displaySubtitle" else displayTitle

    var isExpanded by remember { mutableStateOf(false) }

    val showTimeInSupporting = onToggleStar != null && trailingText.isNotBlank()
    val showTimeInTrailing = onToggleStar == null && trailingText.isNotBlank()

    val supportingText = remember(displaySubtitle, trailingText, showTimeInSupporting) {
        if (showTimeInSupporting) {
            if (displaySubtitle.isNotEmpty()) {
                "$displaySubtitle | $trailingText"
            } else {
                trailingText
            }
        } else {
            displaySubtitle
        }
    }

    Column(
        modifier = modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else if (isPureBlack) androidx.compose.ui.graphics.Color.Black
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
            .then(
                if (isActive) Modifier.border(
                    1.5.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(12.dp)
                )
                else if (isPureBlack) Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    RoundedCornerShape(12.dp)
                ) else Modifier
            )
    ) {
        ListItem(
            modifier = Modifier
                .combinedClickable(
                    onClick = {
                        if (onClick != null) {
                            onClick.invoke()
                        } else {
                            isExpanded = !isExpanded
                        }
                    },
                    onLongClick = {
                        if (onClick != null) {
                            isExpanded = !isExpanded
                        } else {
                            clipboardManager.setText(AnnotatedString(fullTrackName))
                            Toast.makeText(context, trackCopiedMessage, Toast.LENGTH_SHORT).show()
                        }
                    }
                ),
            headlineContent = {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                )
            },
            supportingContent = if (supportingText.isNotEmpty()) {
                {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    )
                }
            } else null,
            trailingContent = if (onToggleStar != null || showTimeInTrailing) {
                {
                    if (onToggleStar != null) {
                        IconButton(
                            onClick = onToggleStar,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (isStarred) {
                                    stringResource(R.string.player_cd_unstar_track)
                                } else {
                                    stringResource(R.string.player_cd_star_track)
                                },
                                tint = if (isStarred) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else if (showTimeInTrailing) {
                        Text(
                            text = trailingText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else null,
            leadingContent = {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(coverArtUrl)
                        .size(Size.ORIGINAL)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    error = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent
            )
        )

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                animationSpec = tween(durationMillis = 250),
                expandFrom = Alignment.Top
            ) + fadeIn(animationSpec = tween(durationMillis = 200)),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = 250),
                shrinkTowards = Alignment.Top
            ) + fadeOut(animationSpec = tween(durationMillis = 150))
        ) {
            Column {
                if (rawTrackTitle.isNotBlank() && rawTrackTitle != fullTrackName) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.player_original_stream_metadata),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = rawTrackTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                            )
                        }
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(rawTrackTitle))
                                Toast.makeText(context, copiedToClipboardMessage, Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.player_cd_copy_track_name),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(fullTrackName))
                            Toast.makeText(context, copiedToClipboardMessage, Toast.LENGTH_SHORT).show()
                            isExpanded = false
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.player_cd_copy_track_name),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val query = URLEncoder.encode(fullTrackName, "UTF-8")
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query"))
                            context.startActivity(intent)
                            isExpanded = false
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_youtube),
                            contentDescription = stringResource(R.string.player_cd_search_youtube),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val query = URLEncoder.encode(fullTrackName, "UTF-8")
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$query"))
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/$query"))
                                context.startActivity(webIntent)
                            }
                            isExpanded = false
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_spotify),
                            contentDescription = stringResource(R.string.player_cd_search_spotify),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val query = URLEncoder.encode(fullTrackName, "UTF-8")
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query"))
                            context.startActivity(intent)
                            isExpanded = false
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_google),
                            contentDescription = stringResource(R.string.player_cd_search_google),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}
