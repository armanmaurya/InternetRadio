package com.armanmaurya.internetradio.ui.mobile.screens.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.armanmaurya.internetradio.R

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.TrackPill(
    displayTrack: String,
    canSearch: Boolean,
    isSearchExpanded: Boolean,
    onOpenSearch: (String) -> Unit
) {
    // Wrap in Box with invisible placeholder to prevent layout shift when pill hides
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        // Invisible placeholder — keeps the space reserved always
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(0f)
                .padding(start = 12.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = displayTrack,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp)
            )
            Spacer(modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.size(36.dp))
        }

        // PILL: visible when dialog is closed
        AnimatedVisibility(
            visible = !isSearchExpanded,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300))
        ) {
            Row(
                modifier = Modifier
                    .sharedBounds(
                        sharedContentState = rememberSharedContentState(key = "search_container"),
                        animatedVisibilityScope = this@AnimatedVisibility,
                        enter = fadeIn(tween(300)),
                        exit = fadeOut(tween(300)),
                        boundsTransform = { _, _ -> tween(durationMillis = 350) },
                        clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(12.dp))
                    )
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        if (canSearch) {
                            onOpenSearch(displayTrack)
                        }
                    }
                    .padding(start = 12.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayTrack,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    modifier = Modifier
                        .sharedElement(
                            sharedContentState = rememberSharedContentState(key = "track_text"),
                            animatedVisibilityScope = this@AnimatedVisibility,
                            boundsTransform = { _, _ -> tween(durationMillis = 350) }
                        )
                        .weight(1f)
                        .padding(vertical = 4.dp)
                        .basicMarquee()
                )
                // Decorative icons — no click, the whole pill row handles tap
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_youtube),
                        contentDescription = null,
                        modifier = Modifier
                            .sharedElement(
                                sharedContentState = rememberSharedContentState(key = "youtube_icon"),
                                animatedVisibilityScope = this@AnimatedVisibility,
                                boundsTransform = { _, _ -> tween(durationMillis = 350) }
                            )
                            .size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_spotify),
                        contentDescription = null,
                        modifier = Modifier
                            .sharedElement(
                                sharedContentState = rememberSharedContentState(key = "spotify_icon"),
                                animatedVisibilityScope = this@AnimatedVisibility,
                                boundsTransform = { _, _ -> tween(durationMillis = 350) }
                            )
                            .size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_google),
                        contentDescription = null,
                        modifier = Modifier
                            .sharedElement(
                                sharedContentState = rememberSharedContentState(key = "google_icon"),
                                animatedVisibilityScope = this@AnimatedVisibility,
                                boundsTransform = { _, _ -> tween(durationMillis = 350) }
                            )
                            .size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
