@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.armanmaurya.internetradio.ui.mobile.screens.player.components

import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.RecordingFile
import com.armanmaurya.internetradio.ui.mobile.components.RecordingFileItem
import com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.recordings.components.NowPlayingTrackQueue
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sin

@Composable
internal fun RecordingArtworkContent(
    folderArtworkUri: Uri?,
    trackCoverArtUrl: String?,
    showStationThumbnails: Boolean,
    showCoverArt: Boolean,
    onToggleCoverArt: () -> Unit,
    baseExpandedSize: Dp,
    progress: Float,
    historyProgress: Float,
    currentScale: Float
) {
    val context = LocalContext.current
    val coverUrl = trackCoverArtUrl?.takeIf { it.isNotBlank() }
    val hasCoverArt = coverUrl != null
    val isShowingCover = showCoverArt && hasCoverArt
    val coverFraction by animateFloatAsState(
        targetValue = if (isShowingCover) 1f else 0f,
        label = "recCoverFraction"
    )
    val visualOverlaySize = lerp(lerp(16.dp, 48.dp, progress), 16.dp, historyProgress)
    val overlaySize = visualOverlaySize / currentScale
    val visualOverlayPadding = lerp(lerp(2.dp, 8.dp, progress), 2.dp, historyProgress)
    val overlayPadding = visualOverlayPadding / currentScale
    val animatedThumbSize = lerp(baseExpandedSize, overlaySize, coverFraction)
    val animatedThumbPadding = lerp(0.dp, overlayPadding, coverFraction)
    val animatedCornerRadius = lerp(0.dp, 6.dp / currentScale, coverFraction)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = hasCoverArt, onClick = onToggleCoverArt)
    ) {
        if (showStationThumbnails && hasCoverArt) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .size(Size.ORIGINAL)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(coverFraction)
            )
        }

        if (showStationThumbnails && folderArtworkUri != null) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(folderArtworkUri)
                    .size(Size.ORIGINAL)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(animatedThumbPadding)
                    .size(animatedThumbSize)
                    .clip(RoundedCornerShape(animatedCornerRadius))
                    .then(
                        if (isShowingCover) {
                            Modifier
                                .background(MaterialTheme.colorScheme.surface)
                                .border(
                                    1.dp / currentScale,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    RoundedCornerShape(animatedCornerRadius)
                                )
                        } else {
                            Modifier
                        }
                    ),
                error = {
                    RecordingFallbackArtwork(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(animatedThumbPadding)
                            .size(animatedThumbSize)
                            .clip(RoundedCornerShape(animatedCornerRadius))
                    )
                },
                loading = {
                    RecordingFallbackArtwork(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(animatedThumbPadding)
                            .size(animatedThumbSize)
                            .clip(RoundedCornerShape(animatedCornerRadius))
                    )
                }
            )
        } else {
            RecordingFallbackArtwork(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(animatedThumbPadding)
                    .size(animatedThumbSize)
                    .clip(RoundedCornerShape(animatedCornerRadius))
                    .then(
                        if (isShowingCover) {
                            Modifier
                                .background(MaterialTheme.colorScheme.surface)
                                .border(
                                    1.dp / currentScale,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    RoundedCornerShape(animatedCornerRadius)
                                )
                        } else {
                            Modifier
                        }
                    )
            )
        }
    }
}

@Composable
private fun RecordingFallbackArtwork(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = 1.6f; scaleY = 1.6f }
        )
    }
}

@Composable
internal fun RecordingSeekBarSection(
    recordingUri: Uri,
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val effectiveDuration = durationMs.coerceAtLeast(1L)
    var isScrubbing by remember(recordingUri) { mutableStateOf(false) }
    var scrubFraction by remember(recordingUri) { mutableFloatStateOf(0f) }
    val liveFraction = (currentPositionMs.toFloat() / effectiveDuration).coerceIn(0f, 1f)
    val displayedFraction = if (isScrubbing) scrubFraction else liveFraction
    val displayedPositionMs = if (isScrubbing) (scrubFraction * effectiveDuration).toLong() else currentPositionMs
    val elapsedText = DateUtils.formatElapsedTime((displayedPositionMs / 1000L).coerceAtLeast(0L))
    val totalText = if (effectiveDuration > 1L) DateUtils.formatElapsedTime(effectiveDuration / 1000L) else "00:00"

    val replayInteraction = remember { MutableInteractionSource() }
    val forwardInteraction = remember { MutableInteractionSource() }
    val skipButtonShapes = IconButtonShapes(
        shape = RoundedCornerShape(20.dp),
        pressedShape = RoundedCornerShape(12.dp)
    )
    val skipButtonColors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    )

    ButtonGroup(
        overflowIndicator = {},
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        customItem(
            buttonGroupContent = {
                FilledTonalIconButton(
                    onClick = { onSeek((currentPositionMs - 5_000L).coerceAtLeast(0L)) },
                    shapes = skipButtonShapes,
                    colors = skipButtonColors,
                    interactionSource = replayInteraction,
                    modifier = Modifier
                        .animateWidth(replayInteraction)
                        .width(48.dp)
                        .height(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay5,
                        contentDescription = stringResource(R.string.recording_cd_rewind_5s),
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            menuContent = {}
        )

        customItem(
            buttonGroupContent = {
                WaterWaveProgressPill(
                    recordingUri = recordingUri,
                    displayedFraction = displayedFraction,
                    isPlaying = isPlaying,
                    isScrubbing = isScrubbing,
                    elapsedText = elapsedText,
                    totalText = totalText,
                    onScrubStart = { frac ->
                        isScrubbing = true
                        scrubFraction = frac
                    },
                    onScrubDrag = { frac -> scrubFraction = frac },
                    onScrubEnd = {
                        onSeek((scrubFraction * effectiveDuration).toLong())
                        isScrubbing = false
                    },
                    onScrubCancel = { isScrubbing = false },
                    onTapSeek = { frac -> onSeek((frac * effectiveDuration).toLong()) },
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                )
            },
            menuContent = {}
        )

        customItem(
            buttonGroupContent = {
                FilledTonalIconButton(
                    onClick = { onSeek((currentPositionMs + 5_000L).coerceAtMost(effectiveDuration)) },
                    shapes = skipButtonShapes,
                    colors = skipButtonColors,
                    interactionSource = forwardInteraction,
                    modifier = Modifier
                        .animateWidth(forwardInteraction)
                        .width(48.dp)
                        .height(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward5,
                        contentDescription = stringResource(R.string.recording_cd_forward_5s),
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            menuContent = {}
        )
    }
}

@Composable
private fun WaterWaveProgressPill(
    recordingUri: Uri,
    displayedFraction: Float,
    isPlaying: Boolean,
    isScrubbing: Boolean,
    elapsedText: String,
    totalText: String,
    onScrubStart: (Float) -> Unit,
    onScrubDrag: (Float) -> Unit,
    onScrubEnd: () -> Unit,
    onScrubCancel: () -> Unit,
    onTapSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val waveAmplitudeFraction by animateFloatAsState(
        targetValue = if (isPlaying || isScrubbing) 1f else 0f,
        label = "waterWaveAmplitude"
    )
    val wavePhase: Float
    val surgePhase: Float
    if (isPlaying || waveAmplitudeFraction > 0.01f) {
        val transition = rememberInfiniteTransition(label = "waterWave")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "waterWavePhase"
        )
        val surge by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "waterWaveSurge"
        )
        wavePhase = phase
        surgePhase = surge
    } else {
        wavePhase = 0f
        surgePhase = 0f
    }

    val fractionState: State<Float> = rememberUpdatedState(displayedFraction)
    val phaseState: State<Float> = rememberUpdatedState(wavePhase)
    val surgeState: State<Float> = rememberUpdatedState(surgePhase)
    val amplitudeState: State<Float> = rememberUpdatedState(waveAmplitudeFraction)
    val waveClipShape = remember {
        object : Shape {
            override fun createOutline(
                size: androidx.compose.ui.geometry.Size,
                layoutDirection: LayoutDirection,
                density: Density
            ): Outline {
                val maxAmpPx = with(density) { 6.dp.toPx() } * amplitudeState.value
                val path = buildWaterWavePath(
                    size = size,
                    fillFraction = fractionState.value,
                    phase = phaseState.value,
                    surgePhase = surgeState.value,
                    maxAmplitudePx = maxAmpPx
                )
                return Outline.Generic(path)
            }
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary

    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(20.dp)
            )
            .clip(RoundedCornerShape(20.dp))
            .pointerInput(recordingUri) {
                detectTapGestures { offset ->
                    if (size.width > 0) {
                        onTapSeek((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }
            }
            .pointerInput(recordingUri) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val frac = if (size.width > 0) (offset.x / size.width).coerceIn(0f, 1f) else 0f
                        onScrubStart(frac)
                    },
                    onDragEnd = onScrubEnd,
                    onDragCancel = onScrubCancel,
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        if (size.width > 0) {
                            onScrubDrag((change.position.x / size.width).coerceIn(0f, 1f))
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxAmpPx = 6.dp.toPx() * amplitudeState.value
            if (maxAmpPx > 0.5f) {
                val backPath = buildWaterWavePath(
                    size = size,
                    fillFraction = (fractionState.value + (2.5.dp.toPx() / size.width.coerceAtLeast(1f))).coerceAtMost(1f),
                    phase = phaseState.value + 1.8f,
                    surgePhase = surgeState.value + 2.4f,
                    maxAmplitudePx = maxAmpPx
                )
                drawPath(path = backPath, color = primaryColor.copy(alpha = 0.3f))
            }
            val frontPath = buildWaterWavePath(
                size = size,
                fillFraction = fractionState.value,
                phase = phaseState.value,
                surgePhase = surgeState.value,
                maxAmplitudePx = maxAmpPx
            )
            drawPath(path = frontPath, color = primaryColor)
        }

        // Base timestamps over unfilled container
        RecordingTimeStack(
            elapsedText = elapsedText,
            totalText = totalText,
            elapsedColor = if (isScrubbing) primaryColor else MaterialTheme.colorScheme.onSurface,
            totalColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(horizontal = 16.dp)
        )

        // Clipped overlay switches covered pixels smoothly to onPrimary along the water edge
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = true
                    shape = waveClipShape
                }
        ) {
            RecordingTimeStack(
                elapsedText = elapsedText,
                totalText = totalText,
                elapsedColor = onPrimaryColor,
                totalColor = onPrimaryColor.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun RecordingTimeStack(
    elapsedText: String,
    totalText: String,
    elapsedColor: Color,
    totalColor: Color,
    modifier: Modifier = Modifier
) {
    val timeStyle = MaterialTheme.typography.labelSmall.copy(
        fontFeatureSettings = "tnum",
        lineHeight = 14.sp
    )
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = elapsedText,
            style = timeStyle,
            fontWeight = FontWeight.SemiBold,
            color = elapsedColor
        )
        Text(
            text = totalText,
            style = timeStyle,
            color = totalColor
        )
    }
}

private fun buildWaterWavePath(
    size: androidx.compose.ui.geometry.Size,
    fillFraction: Float,
    phase: Float,
    surgePhase: Float,
    maxAmplitudePx: Float
): Path {
    val path = Path()
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f || fillFraction <= 0f) return path

    val fillX = (fillFraction.coerceIn(0f, 1f)) * width
    if (fillFraction >= 1f || maxAmplitudePx <= 0.1f) {
        path.moveTo(0f, 0f)
        path.lineTo(fillX, 0f)
        path.lineTo(fillX, height)
        path.lineTo(0f, height)
        path.close()
        return path
    }

    // Taper wave crest near 0% and 100% so empty/full edges stay clean
    val maxPeak = maxAmplitudePx * 1.15f
    val edgeTaper = minOf(
        (fillX / maxPeak).coerceIn(0f, 1f),
        ((width - fillX) / maxPeak).coerceIn(0f, 1f)
    )
    val amp = maxAmplitudePx * edgeTaper
    val steps = 36
    val twoPi = (2f * PI).toFloat()
    path.moveTo(0f, 0f)
    for (i in 0..steps) {
        val fy = i.toFloat() / steps
        val y = fy * height
        // Combine coprime vertical frequencies & phases (integer multipliers keep loop seam-free)
        val primaryWave = sin(phase + fy * twoPi * 1.25f)
        val crossWave = sin(-phase + surgePhase * 2f + fy * twoPi * 2.6f + 0.9f)
        val fineRipple = sin(phase * 2f - surgePhase + fy * twoPi * 4.1f + 2.3f)
        val surgeScale = 0.85f + 0.25f * sin(surgePhase + fy * PI.toFloat())
        val wave = (0.58f * primaryWave + 0.29f * crossWave + 0.13f * fineRipple) * surgeScale
        val x = (fillX + wave * amp).coerceIn(0f, width)
        path.lineTo(x, y)
    }
    path.lineTo(0f, height)
    path.close()
    return path
}

internal const val SPEED_CONTAINER_KEY = "speed_container"
internal const val SPEED_VALUE_KEY = "speed_value"

internal fun formatPlaybackSpeed(speed: Float): String {
    val hundredths = round(speed * 100f).toInt()
    return if (hundredths % 10 == 0) {
        String.format(Locale.US, "%.1fx", hundredths / 100f)
    } else {
        String.format(Locale.US, "%.2fx", hundredths / 100f)
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun SharedTransitionScope.RecordingSpeedDialog(
    showDialog: Boolean,
    currentSpeed: Float,
    onDismissRequest: () -> Unit,
    onSelectSpeed: (Float) -> Unit
) {
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
            val sliderInteraction = remember { MutableInteractionSource() }
            val sliderColors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.16f)
            )
            val presets = remember { listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f) }

            Column(
                modifier = Modifier
                    .sharedBounds(
                        sharedContentState = rememberSharedContentState(key = SPEED_CONTAINER_KEY),
                        animatedVisibilityScope = this@AnimatedVisibility,
                        enter = fadeIn(tween(300)),
                        exit = fadeOut(tween(300)),
                        boundsTransform = { _, _ -> tween(durationMillis = 350) },
                        clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(28.dp))
                    )
                    .widthIn(min = 280.dp, max = 360.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(28.dp)
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.recording_speed),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatPlaybackSpeed(currentSpeed),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (abs(currentSpeed - 1.0f) >= 0.01f) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        maxLines = 1,
                        modifier = Modifier.sharedElement(
                            sharedContentState = rememberSharedContentState(key = SPEED_VALUE_KEY),
                            animatedVisibilityScope = this@AnimatedVisibility,
                            boundsTransform = { _, _ -> tween(durationMillis = 350) }
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Slider(
                    value = currentSpeed.coerceIn(0.5f, 2.0f),
                    onValueChange = { raw ->
                        val stepped = (round(raw * 20f) / 20f).coerceIn(0.5f, 2.0f)
                        if (abs(stepped - currentSpeed) >= 0.01f) {
                            onSelectSpeed(stepped)
                        }
                    },
                    valueRange = 0.5f..2.0f,
                    interactionSource = sliderInteraction,
                    colors = sliderColors,
                    thumb = {
                        SliderDefaults.Thumb(
                            interactionSource = sliderInteraction,
                            colors = sliderColors
                        )
                    },
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            colors = sliderColors
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.chunked(3).forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowPresets.forEach { speed ->
                                val isSelected = abs(currentSpeed - speed) < 0.01f
                                val buttonColors = if (isSelected) {
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f),
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                Button(
                                    onClick = { onSelectSpeed(speed) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = buttonColors
                                ) {
                                    Text(formatPlaybackSpeed(speed))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { onSelectSpeed(1.0f) },
                        enabled = abs(currentSpeed - 1.0f) >= 0.01f
                    ) {
                        Text(stringResource(R.string.general_reset))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(R.string.general_ok))
                    }
                }
            }
        }
    }
}

@Composable
internal fun RecordingTracksTabPage(
    recording: RecordingFile,
    currentPositionMs: Long,
    listState: LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    onSeek: (Long) -> Unit
) {
    if (recording.tracks.isEmpty()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = stringResource(R.string.player_no_tracks_played),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .nestedScroll(nestedScrollConnection),
            contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
        ) {
            item(key = "recording_track_queue") {
                NowPlayingTrackQueue(
                    recording = recording,
                    currentPositionMs = currentPositionMs,
                    onSeek = onSeek
                )
            }
        }
    }
}

@Composable
internal fun RecordingFolderTabPage(
    currentRecording: RecordingFile,
    folderRecordings: List<RecordingFile>,
    isPlaying: Boolean,
    listState: LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    onTogglePlayPause: () -> Unit,
    onPlayRecording: (RecordingFile, List<RecordingFile>) -> Unit,
    onDeleteRecording: (RecordingFile) -> Unit,
    onSaveNote: (RecordingFile, String?) -> Unit
) {
    if (folderRecordings.isEmpty()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = stringResource(R.string.general_no_recordings),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .nestedScroll(nestedScrollConnection),
            contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
        ) {
            items(
                items = folderRecordings,
                key = { it.uri.toString() }
            ) { item ->
                val isCurrent = item.uri == currentRecording.uri
                RecordingFileItem(
                    recording = item,
                    isCurrentlyPlaying = isCurrent,
                    playbackIsPlaying = isCurrent && isPlaying,
                    onClick = {
                        if (isCurrent) {
                            onTogglePlayPause()
                        } else {
                            onPlayRecording(item, folderRecordings)
                        }
                    },
                    onDelete = { onDeleteRecording(item) },
                    onSaveNote = { newNote -> onSaveNote(item, newNote) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

@Composable
internal fun RecordingNoteEditDialog(
    recording: RecordingFile,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit
) {
    var noteDraft by remember(recording.uri, recording.note) { mutableStateOf(recording.note.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (recording.note.isNullOrBlank()) R.string.recording_add_note
                    else R.string.recording_edit_note
                )
            )
        },
        text = {
            OutlinedTextField(
                value = noteDraft,
                onValueChange = { noteDraft = it },
                placeholder = { Text(stringResource(R.string.recording_note_placeholder)) },
                maxLines = 5,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(noteDraft.trim().takeIf { it.isNotEmpty() })
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.recording_save_note))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.recording_cancel_note))
            }
        }
    )
}
