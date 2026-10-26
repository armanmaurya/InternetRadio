package com.armanmaurya.internetradio.ui.mobile.screens.player.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import coil3.compose.SubcomposeAsyncImage
import coil3.request.crossfade
import androidx.compose.ui.zIndex
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.ui.shared.components.shimmerEffect
import java.net.URLEncoder

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun SharedTransitionScope.TrackDialog(
    searchDialogTrack: String?,
    rawTrackName: String?,
    trackCoverArtUri: String?,
    isFetchingArtwork: Boolean,
    isStarred: Boolean = false,
    onToggleStar: (() -> Unit)? = null,
    onDismissRequest: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedToClipboardMessage = stringResource(R.string.player_copied_to_clipboard)
    val trackSavedMessage = stringResource(R.string.player_track_saved)
    val trackRemovedMessage = stringResource(R.string.player_track_removed)

    AnimatedVisibility(
        visible = searchDialogTrack != null,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(300)),
        modifier = Modifier.zIndex(100f)
    ) {
        val trackToSearch = searchDialogTrack ?: ""
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .sharedBounds(
                        sharedContentState = rememberSharedContentState(key = "search_container"),
                        animatedVisibilityScope = this@AnimatedVisibility,
                        enter = fadeIn(tween(300)),
                        exit = fadeOut(tween(300)),
                        boundsTransform = { _, _ -> tween(durationMillis = 350) },
                        clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(28.dp))
                    )
                    .widthIn(max = 480.dp)
                    .fillMaxWidth(0.88f)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(28.dp)
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Consume clicks so they don't dismiss the dialog
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isFetchingArtwork || trackCoverArtUri != null) {
                    Box(modifier = Modifier.padding(bottom = 20.dp)) {
                        if (isFetchingArtwork) {
                            Box(
                                modifier = Modifier
                                    .sharedElement(
                                        sharedContentState = rememberSharedContentState(key = "track_cover"),
                                        animatedVisibilityScope = this@AnimatedVisibility,
                                        boundsTransform = { _, _ -> tween(durationMillis = 350) }
                                    )
                                    .size(160.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .shimmerEffect()
                            )
                        } else if (trackCoverArtUri != null) {
                            SubcomposeAsyncImage(
                                model = coil3.request.ImageRequest.Builder(LocalContext.current)
                                    .data(trackCoverArtUri)
                                    .crossfade(true)
                                    .build(),
                                loading = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .shimmerEffect()
                                    )
                                },
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .sharedElement(
                                        sharedContentState = rememberSharedContentState(key = "track_cover"),
                                        animatedVisibilityScope = this@AnimatedVisibility,
                                        boundsTransform = { _, _ -> tween(durationMillis = 350) }
                                    )
                                    .size(160.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .sharedBounds(
                                sharedContentState = rememberSharedContentState(key = "track_text"),
                                animatedVisibilityScope = this@AnimatedVisibility,
                                boundsTransform = { _, _ -> tween(durationMillis = 350) }
                            )
                            .weight(1f)
                    ) {
                        val trackParts = trackToSearch.split(" - ", limit = 2)
                        
                        Text(
                            text = trackParts[0].trim(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                        )
                        
                        if (trackParts.size > 1) {
                            Text(
                                text = trackParts[1].trim(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                            )
                        }

                    }
                    if (onToggleStar != null) {
                        IconButton(
                            onClick = {
                                onToggleStar()
                                val msg = if (!isStarred) trackSavedMessage else trackRemovedMessage
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = if (isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (isStarred) {
                                    stringResource(R.string.player_cd_unstar_track)
                                } else {
                                    stringResource(R.string.player_cd_star_track)
                                },
                                tint = if (isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(trackToSearch))
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

                if (!rawTrackName.isNullOrBlank() && rawTrackName != trackToSearch) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                text = rawTrackName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                            )
                        }
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(rawTrackName))
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

                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(
                        onClick = {
                            val query = URLEncoder.encode(trackToSearch, "UTF-8")
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query"))
                            context.startActivity(intent)
                            onDismissRequest()
                        },
                        modifier = Modifier.size(80.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_youtube),
                            contentDescription = stringResource(R.string.player_cd_search_youtube),
                            modifier = Modifier
                                .sharedElement(
                                    sharedContentState = rememberSharedContentState(key = "youtube_icon"),
                                    animatedVisibilityScope = this@AnimatedVisibility,
                                    boundsTransform = { _, _ -> tween(durationMillis = 350) }
                                )
                                .size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            val query = URLEncoder.encode(trackToSearch, "UTF-8")
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$query"))
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/$query"))
                                context.startActivity(webIntent)
                            }
                            onDismissRequest()
                        },
                        modifier = Modifier.size(80.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_spotify),
                            contentDescription = stringResource(R.string.player_cd_search_spotify),
                            modifier = Modifier
                                .sharedElement(
                                    sharedContentState = rememberSharedContentState(key = "spotify_icon"),
                                    animatedVisibilityScope = this@AnimatedVisibility,
                                    boundsTransform = { _, _ -> tween(durationMillis = 350) }
                                )
                                .size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            val query = URLEncoder.encode(trackToSearch, "UTF-8")
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query"))
                            context.startActivity(intent)
                            onDismissRequest()
                        },
                        modifier = Modifier.size(80.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_google),
                            contentDescription = stringResource(R.string.player_cd_search_google),
                            modifier = Modifier
                                .sharedElement(
                                    sharedContentState = rememberSharedContentState(key = "google_icon"),
                                    animatedVisibilityScope = this@AnimatedVisibility,
                                    boundsTransform = { _, _ -> tween(durationMillis = 350) }
                                )
                                .size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
