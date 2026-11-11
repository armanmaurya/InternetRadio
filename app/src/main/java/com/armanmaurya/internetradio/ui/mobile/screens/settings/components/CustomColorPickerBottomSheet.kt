package com.armanmaurya.internetradio.ui.mobile.screens.settings.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.ui.shared.theme.AppColor
import com.armanmaurya.internetradio.ui.shared.theme.DEFAULT_CUSTOM_HUE
import com.armanmaurya.internetradio.ui.shared.theme.customColorFromHue
import com.armanmaurya.internetradio.ui.shared.theme.hueOfColor

/**
 * Hue-only custom colour picker. Saturation and brightness are fixed (see [customColorFromHue]),
 * so the result is always a clearly coloured palette; black & white live in the Mono swatch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomColorPickerBottomSheet(
    initialColorArgb: Int,
    darkTheme: Boolean,
    onColorApplied: (Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    var hue by remember { mutableFloatStateOf(hueOfColor(initialColorArgb) ?: DEFAULT_CUSTOM_HUE) }

    val currentArgb = remember(hue) { customColorFromHue(hue) }

    val previewColors = remember(currentArgb, darkTheme) {
        AppColor.CUSTOM.getPreviewColors(darkTheme, currentArgb)
    }

    fun dismiss() {
        coroutineScope.launch {
            sheetState.hide()
        }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                onDismissRequest()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.settings_custom_color_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Live 4-quadrant palette preview
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(20.dp)
                    )
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        PreviewQuadrant(previewColors.getOrElse(0) { Color(currentArgb) })
                        PreviewQuadrant(previewColors.getOrElse(1) { Color(currentArgb) })
                    }
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        PreviewQuadrant(previewColors.getOrElse(2) { Color(currentArgb) })
                        PreviewQuadrant(previewColors.getOrElse(3) { Color(currentArgb) })
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            HueSliderBar(
                hue = hue,
                onHueChange = { hue = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { dismiss() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.settings_custom_color_cancel))
                }
                Button(
                    onClick = {
                        onColorApplied(currentArgb)
                        dismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.settings_custom_color_apply))
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PreviewQuadrant(color: Color) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(color)
    )
}

@Composable
private fun HueSliderBar(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val rainbowBrush = remember {
        Brush.horizontalGradient(
            listOf(
                Color.Red,
                Color.Yellow,
                Color.Green,
                Color.Cyan,
                Color.Blue,
                Color.Magenta,
                Color.Red
            )
        )
    }

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(18.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onHueChange((offset.x / size.width).coerceIn(0f, 1f) * 360f)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onHueChange((offset.x / size.width).coerceIn(0f, 1f) * 360f)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        onHueChange((change.position.x / size.width).coerceIn(0f, 1f) * 360f)
                    }
                )
            }
    ) {
        drawRect(brush = rainbowBrush)

        val thumbRadius = 14.dp.toPx()
        val centerY = size.height / 2
        val thumbX = ((hue / 360f).coerceIn(0f, 1f) * size.width)
            .coerceIn(thumbRadius, size.width - thumbRadius)

        // Outer shadow
        drawCircle(
            color = Color.Black.copy(alpha = 0.4f),
            radius = thumbRadius,
            center = Offset(thumbX, centerY)
        )
        // White ring
        drawCircle(
            color = Color.White,
            radius = 12.dp.toPx(),
            center = Offset(thumbX, centerY)
        )
        // Selected hue core
        drawCircle(
            color = Color.hsv(hue, 1f, 1f),
            radius = 8.dp.toPx(),
            center = Offset(thumbX, centerY)
        )
    }
}
