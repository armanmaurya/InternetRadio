package com.armanmaurya.internetradio.ui.shared.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.EqualizerConfig
import com.armanmaurya.internetradio.domain.model.EqualizerPresets
import java.util.Locale
import kotlin.math.abs

@StringRes
fun equalizerPresetLabelRes(presetId: String): Int = when (presetId) {
    EqualizerPresets.FLAT_ID -> R.string.equalizer_preset_flat
    EqualizerPresets.VOCAL_ID -> R.string.equalizer_preset_vocal
    EqualizerPresets.BASS_BOOST_ID -> R.string.equalizer_preset_bass_boost
    EqualizerPresets.TREBLE_BOOST_ID -> R.string.equalizer_preset_treble_boost
    EqualizerPresets.ROCK_ID -> R.string.equalizer_preset_rock
    EqualizerPresets.POP_ID -> R.string.equalizer_preset_pop
    EqualizerPresets.JAZZ_ID -> R.string.equalizer_preset_jazz
    EqualizerPresets.CLASSICAL_ID -> R.string.equalizer_preset_classical
    else -> R.string.equalizer_preset_custom
}

fun formatGainDb(gainDb: Float): String {
    val rounded = kotlin.math.round(gainDb * 10f) / 10f
    return when {
        abs(rounded) < 0.05f -> "0.0 dB"
        rounded > 0f -> String.format(Locale.US, "+%.1f dB", rounded)
        else -> String.format(Locale.US, "%.1f dB", rounded)
    }
}

fun formatCompactGainDb(gainDb: Float): String {
    val rounded = kotlin.math.round(gainDb * 10f) / 10f
    val isWhole = abs(rounded - rounded.toInt()) < 0.05f
    return when {
        abs(rounded) < 0.05f -> "0"
        isWhole && rounded > 0f -> "+${rounded.toInt()}"
        isWhole -> "${rounded.toInt()}"
        rounded > 0f -> String.format(Locale.US, "+%.1f", rounded)
        else -> String.format(Locale.US, "%.1f", rounded)
    }
}

/**
 * Draws the smooth EQ curve ("string") directly across all 10 equalizer band columns so the
 * line passes straight through the center of every band's slider knob / level indicator.
 *
 * [verticalThumbInset] matches half the thumb/handle height at top and bottom of the slider
 * track so +12 dB and -12 dB align pixel-for-pixel with the slider thumb center.
 */
@Composable
fun EqualizerConnectedCurve(
    bandGainsDb: List<Float>,
    enabled: Boolean,
    lineColor: Color,
    fillColor: Color,
    gridColor: Color,
    zeroLineColor: Color,
    modifier: Modifier = Modifier,
    topInset: Dp = 2.dp,
    bottomInset: Dp = 2.dp,
    columnGap: Dp = 0.dp
) {
    Canvas(modifier = modifier) {
        val count = EqualizerPresets.BAND_COUNT
        if (count < 2 || size.width <= 0f || size.height <= 0f) return@Canvas

        val topPx = topInset.toPx()
        val bottomPx = bottomInset.toPx()
        val usableHeight = (size.height - topPx - bottomPx).coerceAtLeast(1f)
        val centerY = topPx + usableHeight / 2f

        fun gainToY(gainDb: Float): Float {
            val fraction = ((gainDb - EqualizerConfig.MIN_GAIN_DB) /
                (EqualizerConfig.MAX_GAIN_DB - EqualizerConfig.MIN_GAIN_DB)).coerceIn(0f, 1f)
            return topPx + usableHeight * (1f - fraction)
        }

        // Subtle reference guides (+12 dB, 0 dB, -12 dB)
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))
        drawLine(
            color = gridColor,
            start = Offset(0f, topPx),
            end = Offset(size.width, topPx),
            strokeWidth = 1.dp.toPx(),
            pathEffect = dashEffect
        )
        drawLine(
            color = gridColor,
            start = Offset(0f, topPx + usableHeight),
            end = Offset(size.width, topPx + usableHeight),
            strokeWidth = 1.dp.toPx(),
            pathEffect = dashEffect
        )
        drawLine(
            color = zeroLineColor,
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 1.dp.toPx()
        )

        val gapPx = columnGap.toPx()
        val colWidth = (size.width - gapPx * (count - 1)) / count
        val points = List(count) { idx ->
            val gain = if (enabled) bandGainsDb.getOrElse(idx) { 0f } else 0f
            val x = idx * (colWidth + gapPx) + colWidth / 2f
            Offset(x, gainToY(gain))
        }

        val linePath = Path()
        val fillPath = Path()

        // Start cleanly at the left edge on the first band's height
        linePath.moveTo(0f, points.first().y)
        linePath.lineTo(points.first().x, points.first().y)

        fillPath.moveTo(0f, centerY)
        fillPath.lineTo(0f, points.first().y)
        fillPath.lineTo(points.first().x, points.first().y)

        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            val midX = (prev.x + curr.x) / 2f
            linePath.cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
            fillPath.cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
        }

        linePath.lineTo(size.width, points.last().y)
        fillPath.lineTo(size.width, points.last().y)
        fillPath.lineTo(size.width, centerY)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(fillColor.copy(alpha = 0.24f), fillColor.copy(alpha = 0.04f))
            )
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
