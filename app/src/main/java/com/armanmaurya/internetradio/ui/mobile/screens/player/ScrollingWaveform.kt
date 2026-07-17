package com.armanmaurya.internetradio.ui.mobile.screens.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds

@Composable
fun ScrollingWaveform(
    amplitude: Float,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    emptyColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
) {
    val animatedAmplitude by animateFloatAsState(
        targetValue = amplitude,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "amplitudeAnimation"
    )

    BoxWithConstraints(
        modifier = modifier
            .height(48.dp)
            .padding(vertical = 4.dp)
            .clipToBounds(),
        contentAlignment = Alignment.CenterStart
    ) {
        val barWidth = 3.dp
        val spacing = 3.dp
        val barWithSpacing = barWidth + spacing
        
        // Calculate exactly how many bars can fit in the available width, plus one to cover fractional edge space
        val maxBars = (maxWidth / barWithSpacing).toInt() + 1
        val maxBarsState = rememberUpdatedState(maxBars)
        
        val amplitudes = remember { mutableStateListOf<Float>() }

        // Update the list at a fixed framerate for smooth scrolling
        LaunchedEffect(Unit) {
            while (true) {
                amplitudes.add(animatedAmplitude)
                while (amplitudes.size > maxBarsState.value) {
                    amplitudes.removeAt(0)
                }
                delay(33) // ~30 FPS
            }
        }

        val density = LocalDensity.current
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            val barWidthPx = with(density) { barWidth.toPx() }
            val spacingPx = with(density) { spacing.toPx() }
            
            val minHeightPx = with(density) { 2.dp.toPx() }
            val maxHeightPx = with(density) { 40.dp.toPx() }
            val cornerRadius = CornerRadius(barWidthPx / 2f)
            
            val centerY = size.height / 2f
            
            // Draw all bars anchored to the right edge
            for (i in 0 until maxBars) {
                val x = size.width - (i + 1) * (barWidthPx + spacingPx) + spacingPx
                
                if (i < amplitudes.size) {
                    val amp = amplitudes[amplitudes.size - 1 - i]
                    val barHeight = (minHeightPx + (amp * maxHeightPx * 2.5f)).coerceIn(minHeightPx, maxHeightPx)
                    
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, centerY - barHeight / 2f),
                        size = Size(barWidthPx, barHeight),
                        cornerRadius = cornerRadius
                    )
                } else {
                    drawRoundRect(
                        color = emptyColor,
                        topLeft = Offset(x, centerY - minHeightPx / 2f),
                        size = Size(barWidthPx, minHeightPx),
                        cornerRadius = cornerRadius
                    )
                }
            }
        }
    }
}
