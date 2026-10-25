package com.armanmaurya.internetradio.ui.mobile.screens.player.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.armanmaurya.internetradio.R
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.VolumeDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    isCastActive: Boolean = false,
    isVolumeBoostEnabled: Boolean = false
) {
    var previousVolume by remember { mutableFloatStateOf(volume.takeIf { it > 0f } ?: 0.5f) }

    LaunchedEffect(volume) {
        if (volume > 0f) {
            previousVolume = volume
        }
    }
    AnimatedVisibility(
        visible = showDialog,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(300)),
        modifier = Modifier.zIndex(100f)
    ) {
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
            val maxRange = if (!isCastActive && isVolumeBoostEnabled) 2.0f else 1.0f
            val isBoosted = isVolumeBoostEnabled && volume > 1.0f
            val boostAlpha by animateFloatAsState(
                targetValue = if (isBoosted) 1f else 0f,
                animationSpec = tween(durationMillis = 200),
                label = "boostAlpha"
            )

            Column(
                modifier = Modifier
                    .sharedBounds(
                        sharedContentState = rememberSharedContentState(key = "volume_container"),
                        animatedVisibilityScope = this@AnimatedVisibility,
                        enter = fadeIn(tween(300)),
                        exit = fadeOut(tween(300)),
                        boundsTransform = { _, _ -> tween(durationMillis = 350) },
                        clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(28.dp))
                    )
                    .width(84.dp)
                    .height(350.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(28.dp)
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Consume clicks
                    )
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.height(44.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "${(volume * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isBoosted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.player_volume_boost_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.alpha(boostAlpha)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Slider(
                        value = volume.coerceIn(0f, maxRange),
                        onValueChange = { rawValue ->
                            val snapped = if (!isCastActive && isVolumeBoostEnabled && rawValue in 0.97f..1.03f) 1.0f else rawValue
                            onVolumeChange(snapped)
                        },
                        valueRange = 0f..maxRange,
                        modifier = Modifier
                            .graphicsLayer {
                                rotationZ = 270f
                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                            }
                            .requiredWidth(200.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val volumeIcon = when {
                    volume == 0f -> Icons.AutoMirrored.Filled.VolumeOff
                    volume < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
                    else -> Icons.AutoMirrored.Filled.VolumeUp
                }

                Icon(
                    imageVector = volumeIcon,
                    contentDescription = stringResource(R.string.player_cd_volume),
                    tint = if (isBoosted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .sharedElement(
                            sharedContentState = rememberSharedContentState(key = "volume_icon"),
                            animatedVisibilityScope = this@AnimatedVisibility,
                            boundsTransform = { _, _ -> tween(durationMillis = 350) }
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            if (volume > 0f) {
                                onVolumeChange(0f)
                            } else {
                                onVolumeChange(previousVolume)
                            }
                        }
                        .size(32.dp)
                )
            }
        }
    }
}
