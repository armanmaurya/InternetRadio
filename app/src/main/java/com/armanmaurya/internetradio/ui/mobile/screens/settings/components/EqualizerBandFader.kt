@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.armanmaurya.internetradio.ui.mobile.screens.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.FilledTonalToggleButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.VerticalSlider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.EqualizerBand
import com.armanmaurya.internetradio.domain.model.EqualizerConfig
import com.armanmaurya.internetradio.domain.model.EqualizerPresets
import com.armanmaurya.internetradio.ui.shared.components.EqualizerConnectedCurve
import com.armanmaurya.internetradio.ui.shared.components.equalizerPresetLabelRes
import com.armanmaurya.internetradio.ui.shared.components.formatCompactGainDb
import com.armanmaurya.internetradio.ui.shared.components.formatGainDb
import kotlin.math.abs
import kotlin.math.round

@Composable
fun EqualizerPresetRow(
    selectedPresetId: String,
    enabled: Boolean,
    onSelectPreset: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val chipShapes = ToggleButtonShapes(
        shape = CircleShape,
        pressedShape = RoundedCornerShape(8.dp),
        checkedShape = RoundedCornerShape(12.dp)
    )
    val chipColors = FilledTonalToggleButtonDefaults.filledTonalToggleButtonColors(
        checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(
            visible = selectedPresetId == EqualizerPresets.CUSTOM_PRESET_ID,
            enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
            exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut()
        ) {
            Row {
                FilledTonalToggleButton(
                    checked = true,
                    onCheckedChange = {},
                    enabled = enabled,
                    shapes = chipShapes,
                    colors = chipColors,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(stringResource(R.string.equalizer_preset_custom))
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EqualizerPresets.BUILT_IN.forEach { preset ->
                val isSelected = selectedPresetId == preset.id
                FilledTonalToggleButton(
                    checked = isSelected,
                    onCheckedChange = { onSelectPreset(preset.id) },
                    enabled = enabled,
                    shapes = chipShapes,
                    colors = chipColors,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(stringResource(equalizerPresetLabelRes(preset.id)))
                }
            }
        }
    }
}

/**
 * Horizontally scrollable 10-band Material 3 Expressive [VerticalSlider] bank with the smooth
 * equalizer curve drawn directly across all 10 slider thumbs.
 */
@Composable
fun EqualizerBandsPanel(
    bandGainsDb: List<Float>,
    enabled: Boolean,
    onBandGainChange: (Int, Float) -> Unit,
    onChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val rowWidth = maxOf(maxWidth, 480.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Column(modifier = Modifier.width(rowWidth)) {
                // Top dB readout row
                Row(modifier = Modifier.fillMaxWidth()) {
                    EqualizerPresets.ISO_10_BANDS.forEach { band ->
                        val gain = bandGainsDb.getOrElse(band.index) { 0f }
                        Text(
                            text = formatCompactGainDb(gain),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (enabled && abs(gain) >= 0.05f) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (abs(gain) >= 0.05f) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Slider tracks with the connected curve running directly through every knob
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(188.dp)
                ) {
                    EqualizerConnectedCurve(
                        bandGainsDb = bandGainsDb,
                        enabled = enabled,
                        lineColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        fillColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        zeroLineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                        topInset = MOBILE_EQ_HANDLE_RADIUS,
                        bottomInset = MOBILE_EQ_HANDLE_RADIUS,
                        modifier = Modifier.fillMaxSize()
                    )

                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EqualizerPresets.ISO_10_BANDS.forEach { band ->
                            val gain = bandGainsDb.getOrElse(band.index) { 0f }
                            EqualizerBandSlider(
                                band = band,
                                gainDb = gain,
                                enabled = enabled,
                                onGainChange = { newGain -> onBandGainChange(band.index, newGain) },
                                onChangeFinished = onChangeFinished,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Bottom frequency label row
                Row(modifier = Modifier.fillMaxWidth()) {
                    EqualizerPresets.ISO_10_BANDS.forEach { band ->
                        Text(
                            text = band.shortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

private val MOBILE_EQ_HANDLE_SIZE = 22.dp
private val MOBILE_EQ_HANDLE_RADIUS = 11.dp

@Composable
internal fun EqualizerCircularDragHandle(
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isDragged || isPressed) 1.2f else 1f,
        label = "eqCircularHandleScale"
    )
    val fillColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    Box(
        modifier = modifier
            .size(MOBILE_EQ_HANDLE_SIZE)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(fillColor)
    )
}

@Composable
private fun EqualizerBandSlider(
    band: EqualizerBand,
    gainDb: Float,
    enabled: Boolean,
    onGainChange: (Float) -> Unit,
    onChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnGainChange by rememberUpdatedState(onGainChange)
    val currentOnFinished by rememberUpdatedState(onChangeFinished)
    val bandContentDesc = stringResource(R.string.equalizer_cd_band_slider, band.shortLabel)
    val formattedState = formatGainDb(gainDb)
    val interactionSource = remember { MutableInteractionSource() }
    val sliderColors = SliderDefaults.colors(
        activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    )

    val sliderState = remember(band.index) {
        SliderState(
            value = gainDb,
            valueRange = EqualizerConfig.MIN_GAIN_DB..EqualizerConfig.MAX_GAIN_DB
        )
    }
    sliderState.onValueChange = { raw ->
        val snapped = snapEqualizerDb(raw)
        sliderState.value = snapped
        currentOnGainChange(snapped)
    }
    sliderState.onValueChangeFinished = currentOnFinished

    LaunchedEffect(gainDb) {
        if (!sliderState.isDragging && abs(sliderState.value - gainDb) > 0.01f) {
            sliderState.value = gainDb
        }
    }

    Box(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = bandContentDesc
            stateDescription = formattedState
        },
        contentAlignment = Alignment.Center
    ) {
        VerticalSlider(
            state = sliderState,
            modifier = Modifier
                .fillMaxHeight()
                .width(48.dp),
            enabled = enabled,
            topToBottom = false,
            colors = sliderColors,
            interactionSource = interactionSource,
            thumb = { _ ->
                EqualizerCircularDragHandle(
                    interactionSource = interactionSource,
                    enabled = enabled
                )
            },
            track = { state ->
                SliderDefaults.CenteredTrack(
                    sliderState = state,
                    enabled = enabled,
                    colors = sliderColors,
                    thumbTrackGapSize = 0.dp,
                    trackInsideCornerSize = 0.dp
                )
            }
        )
    }
}

fun snapEqualizerDb(rawDb: Float): Float {
    val stepped = round(rawDb * 2f) / 2f
    return if (abs(stepped) < 0.3f) 0f else stepped.coerceIn(
        EqualizerConfig.MIN_GAIN_DB,
        EqualizerConfig.MAX_GAIN_DB
    )
}
