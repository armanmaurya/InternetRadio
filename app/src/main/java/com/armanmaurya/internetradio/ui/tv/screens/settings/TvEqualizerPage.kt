package com.armanmaurya.internetradio.ui.tv.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.EqualizerConfig
import com.armanmaurya.internetradio.domain.model.EqualizerPresets
import com.armanmaurya.internetradio.ui.shared.components.EqualizerConnectedCurve
import com.armanmaurya.internetradio.ui.shared.components.equalizerPresetLabelRes
import com.armanmaurya.internetradio.ui.shared.components.formatCompactGainDb
import com.armanmaurya.internetradio.ui.shared.components.formatGainDb
import com.armanmaurya.internetradio.ui.shared.viewmodels.EqualizerViewModel
import com.armanmaurya.internetradio.ui.tv.components.TvSelectionDialog
import com.armanmaurya.internetradio.ui.tv.components.TvSelectionOption
import kotlin.math.abs

private const val TV_EQ_STEP_DB = 1f

private sealed interface TvEditingTarget {
    data class Band(val index: Int) : TvEditingTarget
    data object Preamp : TvEditingTarget
}

@Composable
fun TvEqualizerPage(
    viewModel: EqualizerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val config = uiState.config
    val toggleFocus = remember { FocusRequester() }
    val presetRowFocus = remember { FocusRequester() }
    var showPresetDialog by remember { mutableStateOf(false) }
    var wasPresetDialogOpen by remember { mutableStateOf(false) }
    val editingTargetState = remember { mutableStateOf<TvEditingTarget?>(null) }
    val editingTarget = editingTargetState.value

    LaunchedEffect(config.enabled) {
        if (!config.enabled) {
            editingTargetState.value = null
        }
    }

    LaunchedEffect(Unit) {
        try {
            toggleFocus.requestFocus()
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(showPresetDialog) {
        if (showPresetDialog) {
            wasPresetDialogOpen = true
        } else if (wasPresetDialogOpen) {
            wasPresetDialogOpen = false
            try {
                presetRowFocus.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    // Pressing Back while editing a band or preamp exits edit mode instead of leaving the page.
    BackHandler(enabled = editingTarget != null) {
        editingTargetState.value = null
    }

    if (showPresetDialog) {
        val options = EqualizerPresets.BUILT_IN.map { preset ->
            TvSelectionOption(
                label = stringResource(equalizerPresetLabelRes(preset.id)),
                selected = config.presetId == preset.id,
                onClick = { viewModel.selectPreset(preset.id) }
            )
        }
        TvSelectionDialog(
            title = stringResource(R.string.settings_equalizer_presets),
            options = options,
            onDismiss = { showPresetDialog = false }
        )
    }

    SettingsColumn(title = stringResource(R.string.settings_equalizer_title)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SettingsToggleRow(
                title = stringResource(R.string.settings_equalizer_enable_label),
                description = stringResource(R.string.settings_equalizer_enable_desc),
                icon = Icons.Default.Equalizer,
                checked = config.enabled,
                onCheckedChange = viewModel::setEnabled,
                modifier = Modifier.focusRequester(toggleFocus)
            )

            if (uiState.isCasting) {
                Text(
                    text = stringResource(R.string.settings_equalizer_cast_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            SettingsRow(
                title = stringResource(R.string.settings_equalizer_presets),
                value = stringResource(equalizerPresetLabelRes(config.presetId)),
                icon = Icons.Default.Tune,
                onClick = { showPresetDialog = true },
                modifier = Modifier.focusRequester(presetRowFocus)
            )

            if (!config.isFlat) {
                ListItem(
                    selected = false,
                    onClick = {
                        editingTargetState.value = null
                        // Move focus before removing the Reset row so focus stays inside Settings.
                        try {
                            presetRowFocus.requestFocus()
                        } catch (_: Exception) {
                        }
                        viewModel.reset()
                    },
                    headlineContent = { Text(stringResource(R.string.settings_equalizer_reset)) },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null
                        )
                    },
                    scale = ListItemDefaults.scale(focusedScale = 1.02f),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SettingsSectionHeader(stringResource(R.string.settings_equalizer_bands))

            Surface(
                shape = RoundedCornerShape(16.dp),
                colors = SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .alpha(if (config.enabled) 1f else 0.5f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(188.dp)
                    ) {
                        // Connected EQ curve flows directly across the 10 band bar tracks
                        EqualizerConnectedCurve(
                            bandGainsDb = config.bandGainsDb,
                            enabled = config.enabled,
                            lineColor = MaterialTheme.colorScheme.primary,
                            fillColor = MaterialTheme.colorScheme.primary,
                            gridColor = MaterialTheme.colorScheme.borderVariant.copy(alpha = 0.5f),
                            zeroLineColor = MaterialTheme.colorScheme.border.copy(alpha = 0.6f),
                            topInset = 33.dp,
                            bottomInset = 33.dp,
                            columnGap = 6.dp,
                            modifier = Modifier.fillMaxSize()
                        )

                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            EqualizerPresets.ISO_10_BANDS.forEach { band ->
                                val gain = config.bandGainsDb.getOrElse(band.index) { 0f }
                                val target = TvEditingTarget.Band(band.index)
                                val isEditingBand = editingTarget == target
                                TvEqualizerBandColumn(
                                    label = band.shortLabel,
                                    gainDb = gain,
                                    enabled = config.enabled,
                                    isEditing = isEditingBand,
                                    isEditingNow = { editingTargetState.value == target },
                                    onToggleEdit = {
                                        editingTargetState.value =
                                            if (editingTargetState.value == target) null else target
                                    },
                                    onFinishEdit = {
                                        if (editingTargetState.value == target) {
                                            editingTargetState.value = null
                                        }
                                    },
                                    onStep = { delta ->
                                        viewModel.stepBandGain(band.index, delta)
                                        viewModel.onSliderChangeFinished()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    val isEditingPreamp = editingTarget == TvEditingTarget.Preamp
                    TvPreampBar(
                        preampDb = config.preampDb,
                        enabled = config.enabled,
                        isEditing = isEditingPreamp,
                        isEditingNow = { editingTargetState.value == TvEditingTarget.Preamp },
                        onToggleEdit = {
                            editingTargetState.value =
                                if (editingTargetState.value == TvEditingTarget.Preamp) null else TvEditingTarget.Preamp
                        },
                        onFinishEdit = {
                            if (editingTargetState.value == TvEditingTarget.Preamp) {
                                editingTargetState.value = null
                            }
                        },
                        onStep = { delta ->
                            viewModel.stepPreamp(delta)
                            viewModel.onSliderChangeFinished()
                        }
                    )

                    val hintText = when (editingTarget) {
                        is TvEditingTarget.Band -> stringResource(R.string.settings_equalizer_tv_editing_band_hint)
                        TvEditingTarget.Preamp -> stringResource(R.string.settings_equalizer_tv_editing_preamp_hint)
                        null -> stringResource(R.string.settings_equalizer_tv_hint)
                    }
                    Text(
                        text = hintText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun Modifier.tvEditableControl(
    enabled: Boolean,
    adjustVertical: Boolean,
    isEditingNow: () -> Boolean,
    onFocusedChange: (Boolean) -> Unit,
    onToggleEdit: () -> Unit,
    onFinishEdit: () -> Unit,
    onStep: (Float) -> Unit
): Modifier = this
    .onFocusChanged {
        onFocusedChange(it.isFocused)
        if (!it.isFocused && isEditingNow()) {
            onFinishEdit()
        }
    }
    .onKeyEvent { event ->
        if (!enabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
        val editing = isEditingNow()
        when (event.key) {
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                onToggleEdit()
                true
            }
            Key.DirectionUp -> if (editing) {
                if (adjustVertical) onStep(TV_EQ_STEP_DB)
                true
            } else false
            Key.DirectionDown -> if (editing) {
                if (adjustVertical) onStep(-TV_EQ_STEP_DB)
                true
            } else false
            Key.DirectionLeft -> if (editing) {
                if (!adjustVertical) onStep(-TV_EQ_STEP_DB)
                true
            } else false
            Key.DirectionRight -> if (editing) {
                if (!adjustVertical) onStep(TV_EQ_STEP_DB)
                true
            } else false
            else -> false
        }
    }
    .clickable(
        enabled = enabled,
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onToggleEdit
    )

@Composable
private fun TvEqualizerBandColumn(
    label: String,
    gainDb: Float,
    enabled: Boolean,
    isEditing: Boolean,
    isEditingNow: () -> Boolean,
    onToggleEdit: () -> Unit,
    onFinishEdit: () -> Unit,
    onStep: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (isEditing) 1.06f else if (isFocused) 1.03f else 1f,
        label = "tvEqScale"
    )
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    val containerColor = when {
        isEditing -> scheme.primaryContainer.copy(alpha = 0.85f)
        isFocused -> scheme.inverseSurface.copy(alpha = 0.85f)
        else -> scheme.surface.copy(alpha = 0.16f)
    }
    val textColor = when {
        isEditing -> scheme.onPrimaryContainer
        isFocused -> scheme.inverseOnSurface
        else -> scheme.onSurface
    }
    val subTextColor = when {
        isEditing -> scheme.onPrimaryContainer.copy(alpha = 0.85f)
        isFocused -> scheme.inverseOnSurface.copy(alpha = 0.8f)
        else -> scheme.onSurfaceVariant
    }
    val knobColor = when {
        isEditing -> scheme.primary
        isFocused -> scheme.inversePrimary
        else -> scheme.primary
    }
    val fraction = ((gainDb - EqualizerConfig.MIN_GAIN_DB) /
        (EqualizerConfig.MAX_GAIN_DB - EqualizerConfig.MIN_GAIN_DB)).coerceIn(0f, 1f)
    val bandDesc = stringResource(R.string.equalizer_cd_band_slider, label)
    val bandState = formatGainDb(gainDb)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .semantics(mergeDescendants = true) {
                contentDescription = bandDesc
                stateDescription = bandState
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .then(
                if (isEditing) Modifier.border(2.dp, scheme.primary, shape) else Modifier
            )
            .background(containerColor)
            .tvEditableControl(
                enabled = enabled,
                adjustVertical = true,
                isEditingNow = isEditingNow,
                onFocusedChange = { isFocused = it },
                onToggleEdit = onToggleEdit,
                onFinishEdit = onFinishEdit,
                onStep = onStep
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = formatCompactGainDb(gainDb),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isEditing || isFocused || abs(gainDb) >= 0.05f) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )
        Spacer(modifier = Modifier.height(6.dp))
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val knobSize = 12.dp
            val trackHeight = (maxHeight - knobSize).coerceAtLeast(1.dp)
            val knobOffsetY = trackHeight * (1f - fraction)

            // Center vertical track
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(
                        when {
                            isEditing -> scheme.onPrimaryContainer.copy(alpha = 0.25f)
                            isFocused -> scheme.inverseOnSurface.copy(alpha = 0.25f)
                            else -> scheme.surfaceVariant.copy(alpha = 0.55f)
                        }
                    )
            )
            // Knob aligned directly with the connected curve
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = knobOffsetY)
                    .size(knobSize)
                    .clip(CircleShape)
                    .background(knobColor)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = subTextColor
        )
    }
}

@Composable
private fun TvPreampBar(
    preampDb: Float,
    enabled: Boolean,
    isEditing: Boolean,
    isEditingNow: () -> Boolean,
    onToggleEdit: () -> Unit,
    onFinishEdit: () -> Unit,
    onStep: (Float) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (isEditing) 1.03f else if (isFocused) 1.02f else 1f,
        label = "tvPreampScale"
    )
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val fraction = ((preampDb - EqualizerConfig.MIN_PREAMP_DB) /
        (EqualizerConfig.MAX_PREAMP_DB - EqualizerConfig.MIN_PREAMP_DB)).coerceIn(0f, 1f)
    val containerColor = when {
        isEditing -> scheme.primaryContainer
        isFocused -> scheme.inverseSurface
        else -> scheme.surface.copy(alpha = 0.35f)
    }
    val textColor = when {
        isEditing -> scheme.onPrimaryContainer
        isFocused -> scheme.inverseOnSurface
        else -> scheme.onSurface
    }
    val preampLabel = stringResource(R.string.settings_equalizer_preamp)
    val preampState = formatGainDb(preampDb)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = preampLabel
                stateDescription = preampState
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .then(
                if (isEditing) Modifier.border(2.dp, scheme.primary, shape) else Modifier
            )
            .background(containerColor)
            .tvEditableControl(
                enabled = enabled,
                adjustVertical = false,
                isEditingNow = isEditingNow,
                onFocusedChange = { isFocused = it },
                onToggleEdit = onToggleEdit,
                onFinishEdit = onFinishEdit,
                onStep = onStep
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = preampLabel,
            style = MaterialTheme.typography.titleSmall,
            color = textColor
        )
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .background(
                    color = textColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(50)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(
                        color = when {
                            isEditing -> scheme.primary
                            isFocused -> scheme.inversePrimary
                            else -> scheme.primary
                        },
                        shape = RoundedCornerShape(50)
                    )
            )
            Box(
                modifier = Modifier
                    .offset(x = maxWidth * 0.5f - 1.dp)
                    .size(width = 2.dp, height = 10.dp)
                    .border(1.dp, textColor.copy(alpha = 0.6f))
            )
        }
        Text(
            text = preampState,
            style = MaterialTheme.typography.titleSmall,
            color = textColor
        )
    }
}
