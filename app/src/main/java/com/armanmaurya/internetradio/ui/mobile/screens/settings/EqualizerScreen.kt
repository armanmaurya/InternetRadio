@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.armanmaurya.internetradio.ui.mobile.screens.settings

import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.EqualizerConfig
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.EqualizerBandsPanel
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.EqualizerPresetRow
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.Item
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.Section
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.ToggleItem
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.snapEqualizerDb
import com.armanmaurya.internetradio.ui.shared.components.equalizerPresetLabelRes
import com.armanmaurya.internetradio.ui.shared.components.formatGainDb
import com.armanmaurya.internetradio.ui.shared.viewmodels.EqualizerUiState
import com.armanmaurya.internetradio.ui.shared.viewmodels.EqualizerViewModel
import kotlin.math.abs

@Composable
fun EqualizerScreen(
    onBackClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: EqualizerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    EqualizerScreenContent(
        uiState = uiState,
        onEnabledChange = viewModel::setEnabled,
        onPresetSelected = viewModel::selectPreset,
        onBandGainChange = viewModel::updateBandGain,
        onPreampChange = viewModel::updatePreamp,
        onSliderChangeFinished = viewModel::onSliderChangeFinished,
        onReset = viewModel::reset,
        onBackClick = onBackClick,
        contentPadding = contentPadding
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreenContent(
    uiState: EqualizerUiState,
    onEnabledChange: (Boolean) -> Unit,
    onPresetSelected: (String) -> Unit,
    onBandGainChange: (Int, Float) -> Unit,
    onPreampChange: (Float) -> Unit,
    onSliderChangeFinished: () -> Unit,
    onReset: () -> Unit,
    onBackClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    val config = uiState.config
    val context = LocalContext.current
    val systemEqIntent = remember(context) {
        Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        }
    }
    val hasSystemEqualizer = remember(context, systemEqIntent) {
        try {
            context.packageManager.resolveActivity(systemEqIntent, 0) != null
        } catch (_: Exception) {
            false
        }
    }

    val topShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
    val bottomShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
    val singleShape = RoundedCornerShape(24.dp)

    val currentOnPreampChange by rememberUpdatedState(onPreampChange)
    val currentOnFinished by rememberUpdatedState(onSliderChangeFinished)
    val preampInteractionSource = remember { MutableInteractionSource() }
    val preampState = remember {
        SliderState(
            value = config.preampDb,
            valueRange = EqualizerConfig.MIN_PREAMP_DB..EqualizerConfig.MAX_PREAMP_DB
        )
    }
    preampState.onValueChange = { raw ->
        val snapped = snapEqualizerDb(raw)
        preampState.value = snapped
        currentOnPreampChange(snapped)
    }
    preampState.onValueChangeFinished = currentOnFinished

    LaunchedEffect(config.preampDb) {
        if (!preampState.isDragging && abs(preampState.value - config.preampDb) > 0.01f) {
            preampState.value = config.preampDb
        }
    }

    Scaffold(
        modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_equalizer_title)) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBackClick,
                        shapes = IconButtonShapes(
                            shape = RoundedCornerShape(12.dp),
                            pressedShape = RoundedCornerShape(6.dp)
                        ),
                        modifier = Modifier.padding(start = 8.dp, end = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = onReset,
                        enabled = !config.isFlat,
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(text = stringResource(R.string.settings_equalizer_reset))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val activePresetName = stringResource(equalizerPresetLabelRes(config.presetId))
            ToggleItem(
                title = stringResource(R.string.settings_equalizer_enable_label),
                subtitle = if (config.enabled) activePresetName else stringResource(R.string.settings_equalizer_enable_desc),
                isEnabled = config.enabled,
                onToggle = onEnabledChange,
                icon = Icons.Default.Equalizer,
                shape = singleShape
            )

            AnimatedVisibility(visible = uiState.isCasting) {
                Surface(
                    shape = singleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cast,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.settings_equalizer_cast_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Section(title = stringResource(R.string.settings_equalizer_presets)) {
                EqualizerPresetRow(
                    selectedPresetId = config.presetId,
                    enabled = config.enabled,
                    onSelectPreset = onPresetSelected
                )
            }

            Column {
                Surface(
                    shape = topShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 16.dp)
                            .alpha(if (config.enabled) 1f else 0.55f)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_equalizer_bands),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        EqualizerBandsPanel(
                            bandGainsDb = config.bandGainsDb,
                            enabled = config.enabled,
                            onBandGainChange = onBandGainChange,
                            onChangeFinished = onSliderChangeFinished
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Surface(
                    shape = bottomShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .alpha(if (config.enabled) 1f else 0.55f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_equalizer_preamp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = CircleShape,
                                color = if (config.enabled && abs(config.preampDb) >= 0.05f) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Text(
                                    text = formatGainDb(config.preampDb),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (config.enabled && abs(config.preampDb) >= 0.05f) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            state = preampState,
                            enabled = config.enabled,
                            interactionSource = preampInteractionSource,
                            track = { state ->
                                SliderDefaults.CenteredTrack(
                                    sliderState = state,
                                    enabled = config.enabled
                                )
                            }
                        )
                    }
                }
            }

            if (hasSystemEqualizer) {
                Item(
                    title = stringResource(R.string.settings_equalizer_system_panel),
                    subtitle = stringResource(R.string.settings_equalizer_system_panel_desc),
                    icon = Icons.Default.GraphicEq,
                    shape = singleShape,
                    onClick = {
                        try {
                            context.startActivity(systemEqIntent)
                        } catch (_: Exception) {
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
