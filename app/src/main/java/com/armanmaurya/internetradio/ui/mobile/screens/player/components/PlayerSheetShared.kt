@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.armanmaurya.internetradio.ui.mobile.screens.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.controller.ActivePlaybackMode
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** One half of a connected header button pair: tonal when off, primary when on. */
@Composable
internal fun HeaderPairButton(
    checked: Boolean,
    shape: Shape,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        label = "headerPairContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
        label = "headerPairContent"
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(containerColor, shape)
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

@Composable
private fun AnimatedModeIcon(
    mode: ActivePlaybackMode,
    isPlaying: Boolean,
    tint: Color,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    if (mode == ActivePlaybackMode.STATION) {
        AnimatedRadioIcon(
            isPlaying = isPlaying,
            tint = tint,
            contentDescription = contentDescription,
            modifier = modifier
        )
    } else {
        AnimatedMicIcon(
            isPlaying = isPlaying,
            tint = tint,
            contentDescription = contentDescription,
            modifier = modifier
        )
    }
}

@Composable
private fun AnimatedRadioIcon(
    isPlaying: Boolean,
    tint: Color,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val wavePhase: Float
    val speakerPulse: Float
    if (isPlaying) {
        val transition = rememberInfiniteTransition(label = "radioIcon")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "radioWavePhase"
        )
        val pulse by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 460, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "radioSpeakerPulse"
        )
        wavePhase = phase
        speakerPulse = pulse
    } else {
        wavePhase = 0f
        speakerPulse = 0f
    }

    Canvas(
        modifier = modifier
            .semantics { this.contentDescription = contentDescription }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        val s = size.minDimension / 24f
        val stroke = 1.9f * s

        // Radio body
        drawRoundRect(
            color = tint,
            topLeft = Offset(2.5f * s, 9.5f * s),
            size = Size(17.0f * s, 11.5f * s),
            cornerRadius = CornerRadius(2.5f * s, 2.5f * s)
        )
        // Cutout tuning bar & indicator dot on left side
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(5.0f * s, 12.2f * s),
            size = Size(6.0f * s, 1.8f * s),
            cornerRadius = CornerRadius(0.9f * s, 0.9f * s),
            blendMode = BlendMode.Clear
        )
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(5.0f * s, 15.6f * s),
            size = Size(3.6f * s, 1.8f * s),
            cornerRadius = CornerRadius(0.9f * s, 0.9f * s),
            blendMode = BlendMode.Clear
        )
        // Cutout speaker circle (throbs gently while playing)
        val speakerRadius = (2.3f + if (isPlaying) speakerPulse * 0.55f else 0f) * s
        drawCircle(
            color = Color.Transparent,
            radius = speakerRadius,
            center = Offset(14.8f * s, 15.4f * s),
            blendMode = BlendMode.Clear
        )

        // Angled antenna anchored to top-left of body
        drawLine(
            color = tint,
            start = Offset(5.5f * s, 9.5f * s),
            end = Offset(14.8f * s, 6.0f * s),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )

        // Radiating broadcast arcs from the antenna tip when playing
        if (isPlaying) {
            val origin = Offset(15.0f * s, 6.0f * s)
            for (k in 0..1) {
                val p = (wavePhase + k * 0.5f) % 1f
                val r = (2.1f + p * 4.0f) * s
                val alpha = ((1f - p) * 0.9f).coerceIn(0f, 1f)
                drawArc(
                    color = tint.copy(alpha = alpha),
                    startAngle = -42f,
                    sweepAngle = 82f,
                    useCenter = false,
                    topLeft = Offset(origin.x - r, origin.y - r),
                    size = Size(r * 2f, r * 2f),
                    style = Stroke(width = 1.6f * s, cap = StrokeCap.Round)
                )
            }
        }
    }
}

@Composable
private fun AnimatedMicIcon(
    isPlaying: Boolean,
    tint: Color,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val levelFraction: Float
    val wavePhase: Float
    if (isPlaying) {
        val transition = rememberInfiniteTransition(label = "micIcon")
        val primary by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.92f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "micLevelPrimary"
        )
        val secondary by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 680, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "micLevelSecondary"
        )
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "micWavePhase"
        )
        levelFraction = ((primary + secondary) * 0.5f).coerceIn(0.3f, 1f)
        wavePhase = phase
    } else {
        levelFraction = 1f
        wavePhase = 0f
    }

    Canvas(modifier = modifier.semantics { this.contentDescription = contentDescription }) {
        val s = size.minDimension / 24f
        val stroke = 1.9f * s
        val capsuleLeft = 8.8f * s
        val capsuleTop = 2.8f * s
        val capsuleWidth = 6.4f * s
        val capsuleHeight = 11.0f * s
        val capsuleRadius = CornerRadius(3.2f * s, 3.2f * s)

        if (isPlaying) {
            val capsulePath = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(capsuleLeft, capsuleTop, capsuleLeft + capsuleWidth, capsuleTop + capsuleHeight),
                        cornerRadius = capsuleRadius
                    )
                )
            }
            // Capsule outline + live bouncing fill inside
            drawRoundRect(
                color = tint,
                topLeft = Offset(capsuleLeft, capsuleTop),
                size = Size(capsuleWidth, capsuleHeight),
                cornerRadius = capsuleRadius,
                style = Stroke(width = 1.5f * s)
            )
            clipPath(capsulePath) {
                val fillHeight = capsuleHeight * levelFraction
                drawRect(
                    color = tint,
                    topLeft = Offset(capsuleLeft, capsuleTop + capsuleHeight - fillHeight),
                    size = Size(capsuleWidth, fillHeight)
                )
            }
            // Symmetrical outward-expanding sound arcs on both sides of the capsule
            val origin = Offset(12.0f * s, 8.3f * s)
            for (k in 0..1) {
                val p = (wavePhase + k * 0.5f) % 1f
                val r = (5.2f + p * 5.0f) * s
                val sweep = 46f + p * 14f
                val alpha = ((p / 0.18f).coerceAtMost(1f) * (1f - p) * 0.95f).coerceIn(0f, 1f)
                val arcTopLeft = Offset(origin.x - r, origin.y - r)
                val arcSize = Size(r * 2f, r * 2f)
                val arcStyle = Stroke(width = 1.5f * s, cap = StrokeCap.Round)
                drawArc(
                    color = tint.copy(alpha = alpha),
                    startAngle = -sweep / 2f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = arcStyle
                )
                drawArc(
                    color = tint.copy(alpha = alpha),
                    startAngle = 180f - sweep / 2f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = arcStyle
                )
            }
        } else {
            drawRoundRect(
                color = tint,
                topLeft = Offset(capsuleLeft, capsuleTop),
                size = Size(capsuleWidth, capsuleHeight),
                cornerRadius = capsuleRadius
            )
        }

        // Mic U-cradle
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(6.4f * s, 5.4f * s),
            size = Size(11.2f * s, 11.0f * s),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        // Stem and base
        drawLine(
            color = tint,
            start = Offset(12.0f * s, 16.4f * s),
            end = Offset(12.0f * s, 20.6f * s),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(8.5f * s, 20.6f * s),
            end = Offset(15.5f * s, 20.6f * s),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

/** Extra collapsed mini-player peek height reserved when [MiniModeSwitcherBar] is visible. */
internal val MINI_MODE_BAR_HEIGHT = 48.dp

/** Animated wrapper that shows [MiniModeSwitcherBar] smoothly when both sources are available. */
@Composable
internal fun AnimatedMiniModeSwitcherBar(
    visible: Boolean,
    activeMode: ActivePlaybackMode,
    stationName: String?,
    recordingName: String?,
    isStationPlaying: Boolean,
    isRecordingPlaying: Boolean,
    enabled: Boolean,
    onSelectMode: (ActivePlaybackMode) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && stationName != null && recordingName != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        if (stationName != null && recordingName != null) {
            MiniModeSwitcherBar(
                activeMode = activeMode,
                stationName = stationName,
                recordingName = recordingName,
                isStationPlaying = isStationPlaying,
                isRecordingPlaying = isRecordingPlaying,
                enabled = enabled,
                onSelectMode = onSelectMode,
                modifier = modifier
            )
        }
    }
}

/** Full-width 2-source switcher bar shown below the 72.dp mini-player when both station and recording exist. */
@Composable
internal fun MiniModeSwitcherBar(
    activeMode: ActivePlaybackMode,
    stationName: String,
    recordingName: String,
    isStationPlaying: Boolean,
    isRecordingPlaying: Boolean,
    enabled: Boolean,
    onSelectMode: (ActivePlaybackMode) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val trackPadding = 3.dp
        val thumbWidth = ((maxWidth - trackPadding * 2) / 2f).coerceAtLeast(1.dp)
        val thumbHeight = 32.dp
        SlidingModeTrack(
            activeMode = activeMode,
            isStationPlaying = isStationPlaying,
            isRecordingPlaying = isRecordingPlaying,
            thumbWidth = thumbWidth,
            thumbHeight = thumbHeight,
            trackPadding = trackPadding,
            enabled = enabled,
            onSelectMode = onSelectMode
        ) { mode, isTabPlaying, tint, labelRes ->
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AnimatedModeIcon(
                    mode = mode,
                    isPlaying = isTabPlaying,
                    tint = tint,
                    contentDescription = stringResource(labelRes),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (mode == ActivePlaybackMode.STATION) stationName else recordingName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Sliding Radio / Recording switcher centered in the expanded player header, matching top-bar styling. */
@Composable
internal fun PlayerModeSegmentedPill(
    activeMode: ActivePlaybackMode,
    isStationPlaying: Boolean,
    isRecordingPlaying: Boolean,
    enabled: Boolean,
    onSelectMode: (ActivePlaybackMode) -> Unit,
    modifier: Modifier = Modifier
) {
    SlidingModeTrack(
        activeMode = activeMode,
        isStationPlaying = isStationPlaying,
        isRecordingPlaying = isRecordingPlaying,
        thumbWidth = 46.dp,
        thumbHeight = 34.dp,
        trackPadding = 3.dp,
        enabled = enabled,
        onSelectMode = onSelectMode,
        modifier = modifier
    ) { mode, isTabPlaying, tint, labelRes ->
        AnimatedModeIcon(
            mode = mode,
            isPlaying = isTabPlaying,
            tint = tint,
            contentDescription = stringResource(labelRes),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SlidingModeTrack(
    activeMode: ActivePlaybackMode,
    isStationPlaying: Boolean,
    isRecordingPlaying: Boolean,
    thumbWidth: Dp,
    thumbHeight: Dp,
    trackPadding: Dp,
    enabled: Boolean,
    onSelectMode: (ActivePlaybackMode) -> Unit,
    modifier: Modifier = Modifier,
    slotContent: @Composable (mode: ActivePlaybackMode, isTabPlaying: Boolean, tint: Color, labelRes: Int) -> Unit
) {
    val maxTravelPx = with(LocalDensity.current) { thumbWidth.toPx() }
    val coroutineScope = rememberCoroutineScope()
    val thumbFraction = remember {
        Animatable(if (activeMode == ActivePlaybackMode.RECORDING) 1f else 0f)
    }
    var isDragging by remember { mutableStateOf(false) }

    val targetFraction = if (activeMode == ActivePlaybackMode.RECORDING) 1f else 0f
    LaunchedEffect(targetFraction, isDragging) {
        if (!isDragging) {
            thumbFraction.animateTo(
                targetValue = targetFraction,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    val modes = listOf(
        Triple(ActivePlaybackMode.STATION, isStationPlaying, R.string.player_mode_radio),
        Triple(ActivePlaybackMode.RECORDING, isRecordingPlaying, R.string.player_mode_recording)
    )

    Box(
        modifier = modifier
            .size(width = thumbWidth * 2 + trackPadding * 2, height = thumbHeight + trackPadding * 2)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .draggable(
                orientation = Orientation.Horizontal,
                enabled = enabled,
                state = rememberDraggableState { deltaPx ->
                    if (maxTravelPx > 0f) {
                        coroutineScope.launch {
                            thumbFraction.snapTo((thumbFraction.value + deltaPx / maxTravelPx).coerceIn(0f, 1f))
                        }
                    }
                },
                onDragStarted = { isDragging = true },
                onDragStopped = { velocity ->
                    val targetMode = when {
                        velocity > 300f -> ActivePlaybackMode.RECORDING
                        velocity < -300f -> ActivePlaybackMode.STATION
                        thumbFraction.value >= 0.5f -> ActivePlaybackMode.RECORDING
                        else -> ActivePlaybackMode.STATION
                    }
                    isDragging = false
                    if (targetMode != activeMode) {
                        onSelectMode(targetMode)
                    } else {
                        coroutineScope.launch {
                            thumbFraction.animateTo(
                                targetValue = targetFraction,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                        }
                    }
                }
            )
            .padding(trackPadding)
    ) {
        // Sliding primary thumb
        Box(
            modifier = Modifier
                .offset { IntOffset((thumbFraction.value * maxTravelPx).roundToInt(), 0) }
                .size(width = thumbWidth, height = thumbHeight)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.primary)
        )

        Row(modifier = Modifier.fillMaxSize()) {
            modes.forEachIndexed { index, (mode, isTabPlaying, labelRes) ->
                val isUnderThumb = if (index == 0) thumbFraction.value < 0.5f else thumbFraction.value >= 0.5f
                val contentTint by animateColorAsState(
                    targetValue = if (isUnderThumb) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    label = "modeSliderTint_$index"
                )
                Box(
                    modifier = Modifier
                        .size(width = thumbWidth, height = thumbHeight)
                        .semantics {
                            role = Role.Tab
                            selected = activeMode == mode
                        }
                        .clickable(
                            enabled = enabled && activeMode != mode,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { onSelectMode(mode) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    slotContent(mode, isTabPlaying, contentTint, labelRes)
                }
            }
        }
    }
}

@Composable
internal fun rememberBottomDrawerNestedScroll(
    currentListState: LazyListState,
    historyProgressAnim: Animatable<Float, AnimationVector1D>,
    maxDragDistance: Float,
    coroutineScope: CoroutineScope
): NestedScrollConnection {
    var wasAtTopWhenScrollStarted by remember { mutableStateOf(true) }

    LaunchedEffect(currentListState.isScrollInProgress) {
        if (currentListState.isScrollInProgress) {
            wasAtTopWhenScrollStarted = currentListState.firstVisibleItemIndex == 0 &&
                currentListState.firstVisibleItemScrollOffset == 0
        }
    }

    return remember(currentListState, historyProgressAnim, maxDragDistance) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0 && historyProgressAnim.value < 1f) {
                    val delta = -available.y / maxDragDistance
                    coroutineScope.launch {
                        historyProgressAnim.snapTo((historyProgressAnim.value + delta).coerceIn(0f, 1f))
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && historyProgressAnim.value > 0f) {
                    val isDrag = source.toString().contains("Drag") || source.toString().contains("UserInput")
                    if (isDrag && wasAtTopWhenScrollStarted) {
                        val delta = -available.y / maxDragDistance
                        coroutineScope.launch {
                            historyProgressAnim.snapTo((historyProgressAnim.value + delta).coerceIn(0f, 1f))
                        }
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (historyProgressAnim.value > 0f && historyProgressAnim.value < 1f) {
                    if (available.y < -1000f) {
                        historyProgressAnim.animateTo(1f)
                    } else if (available.y > 1000f) {
                        historyProgressAnim.animateTo(0f)
                    } else {
                        historyProgressAnim.animateTo(if (historyProgressAnim.value > 0.5f) 1f else 0f)
                    }
                    return Velocity(0f, available.y)
                }
                return Velocity.Zero
            }
        }
    }
}

@Composable
internal fun PlayerBottomTabsBar(
    tabTitles: List<String>,
    bottomPagerState: PagerState,
    historyProgressAnim: Animatable<Float, AnimationVector1D>,
    maxDragDistance: Float,
    coroutineScope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    val historyProgress = historyProgressAnim.value
    val isHistoryExpanded = historyProgress > 0.5f
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val tabWidths = remember(tabTitles.size) { mutableStateListOf<Dp>().apply { repeat(tabTitles.size) { add(0.dp) } } }
    val tabOffsets = remember(tabTitles.size) { mutableStateListOf<Dp>().apply { repeat(tabTitles.size) { add(0.dp) } } }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    coroutineScope.launch {
                        historyProgressAnim.snapTo((historyProgressAnim.value - delta / maxDragDistance).coerceIn(0f, 1f))
                    }
                },
                onDragStopped = { velocity ->
                    coroutineScope.launch {
                        if (velocity < -1000f) {
                            historyProgressAnim.animateTo(1f)
                        } else if (velocity > 1000f) {
                            historyProgressAnim.animateTo(0f)
                        } else {
                            historyProgressAnim.animateTo(if (historyProgressAnim.value > 0.5f) 1f else 0f)
                        }
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.horizontalScroll(scrollState)) {
                if (isHistoryExpanded) {
                    val currentOffset = tabOffsets.getOrElse(bottomPagerState.currentPage) { 0.dp }
                    val currentWidth = tabWidths.getOrElse(bottomPagerState.currentPage) { 0.dp }
                    val animOffset by animateDpAsState(currentOffset, label = "tabOffset")
                    val animWidth by animateDpAsState(currentWidth, label = "tabWidth")

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = animOffset)
                            .width(animWidth)
                            .height(36.dp)
                            .padding(horizontal = 4.dp)
                            .zIndex(-1f)
                            .background(
                                if (MaterialTheme.colorScheme.surface == MaterialTheme.colorScheme.surfaceContainerLow) {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                RoundedCornerShape(10.dp)
                            )
                    )
                }

                Row {
                    tabTitles.forEachIndexed { index, title ->
                        val textColor by animateColorAsState(
                            targetValue = if (isHistoryExpanded && bottomPagerState.currentPage == index) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            label = "tabText_$index"
                        )
                        Box(
                            modifier = Modifier
                                .semantics { role = Role.Tab }
                                .onGloballyPositioned { coords ->
                                    val width = with(density) { coords.size.width.toDp() }
                                    val offset = with(density) { coords.positionInParent().x.toDp() }
                                    if (tabWidths[index] != width) tabWidths[index] = width
                                    if (tabOffsets[index] != offset) tabOffsets[index] = offset
                                }
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = {
                                        if (bottomPagerState.currentPage != index) {
                                            coroutineScope.launch {
                                                launch { bottomPagerState.animateScrollToPage(index) }
                                                if (!isHistoryExpanded) {
                                                    launch { historyProgressAnim.animateTo(1f) }
                                                }
                                            }
                                        } else {
                                            coroutineScope.launch {
                                                historyProgressAnim.animateTo(if (isHistoryExpanded) 0f else 1f)
                                            }
                                        }
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                        }
                    }
                }
            }
        }
    }
}
