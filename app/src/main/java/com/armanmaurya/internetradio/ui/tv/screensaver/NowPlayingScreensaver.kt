package com.armanmaurya.internetradio.ui.tv.screensaver

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.RadioStation
import kotlinx.coroutines.delay
import java.util.Date
import kotlin.math.roundToInt

/** Where the info block sits, as fractions of the free space. It hops between these to avoid burn-in. */
private val DriftPositions = listOf(
    0.08f to 0.12f, 0.62f to 0.70f, 0.30f to 0.40f, 0.70f to 0.15f,
    0.12f to 0.75f, 0.45f to 0.10f, 0.05f to 0.45f, 0.55f to 0.45f
)
private const val DRIFT_INTERVAL_MS = 30_000L
private const val DIM_AFTER_MS = 10 * 60_000L

/**
 * Full-screen "now playing" screensaver: clock, artwork, song and station on black.
 * Used both by the in-app screensaver and the system screensaver (DreamService).
 */
@Composable
fun NowPlayingScreensaver(
    station: RadioStation?,
    trackTitle: String?,
    artist: String?,
    coverArtUri: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val timeFormat = remember(context) { android.text.format.DateFormat.getTimeFormat(context) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var position by remember { mutableIntStateOf(0) }
    var isDimmed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(10_000)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(DRIFT_INTERVAL_MS)
            position = (position + 1) % DriftPositions.size
        }
    }
    LaunchedEffect(Unit) {
        delay(DIM_AFTER_MS)
        isDimmed = true
    }

    val alpha by animateFloatAsState(if (isDimmed) 0.45f else 1f, tween(3000), label = "screensaverDim")
    val (fx, fy) = DriftPositions[position]
    val x by animateFloatAsState(fx, tween(4000), label = "screensaverX")
    val y by animateFloatAsState(fy, tween(4000), label = "screensaverY")

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val density = LocalDensity.current
        // Reserve roughly the block's size so it never leaves the screen.
        val freeWidth = with(density) { (maxWidth - 560.dp).coerceAtLeast(0.dp).toPx() }
        val freeHeight = with(density) { (maxHeight - 260.dp).coerceAtLeast(0.dp).toPx() }

        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .offset { IntOffset((freeWidth * x).roundToInt(), (freeHeight * y).roundToInt()) }
                .widthIn(max = 560.dp)
                .graphicsLayer { this.alpha = alpha }
        ) {
            Text(
                text = timeFormat.format(Date(now)),
                color = Color.White,
                fontSize = 72.sp,
                fontWeight = FontWeight.Light
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = coverArtUri?.ifBlank { null } ?: station?.favicon?.ifBlank { null },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    error = painterResource(R.drawable.ic_launcher_foreground),
                    fallback = painterResource(R.drawable.ic_launcher_foreground),
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1E1E))
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = trackTitle?.ifBlank { null }
                            ?: station?.name
                            ?: stringResource(R.string.widget_nothing_playing),
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    artist?.ifBlank { null }?.let {
                        Text(text = it, color = Color.White.copy(alpha = 0.75f), fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (station != null && !trackTitle.isNullOrBlank()) {
                        Text(text = station.name, color = Color.White.copy(alpha = 0.55f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
