package com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity

@Composable
fun TimelineSessionNode(
    session: HistorySessionGroup,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dayLabel = remember(session.startTime) {
        HistorySessionGrouping.formatDayLabel(context, session.startTime)
    }
    val timeRange = remember(session.startTime, session.endTime) {
        HistorySessionGrouping.formatTimeRange(context, session.startTime, session.endTime)
    }
    val durationText = remember(session.durationMs) {
        HistorySessionGrouping.formatDuration(context, session.durationMs)
    }
    val songsCountText = remember(session.trackCount) {
        context.resources.getQuantityString(
            R.plurals.player_session_songs_count,
            session.trackCount,
            session.trackCount
        )
    }

    val isPureBlack = MaterialTheme.colorScheme.surfaceContainerLow == Color.Black
    val surfaceColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow
    val railColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val railX = 14.dp.toPx()
                val nodeY = size.height / 2f

                // Rail line downwards to the tracks
                drawLine(
                    color = railColor,
                    start = Offset(railX, nodeY),
                    end = Offset(railX, size.height),
                    strokeWidth = 2.dp.toPx()
                )

                // Purple Session Node
                drawCircle(
                    color = primaryColor,
                    radius = 6.dp.toPx(),
                    center = Offset(railX, nodeY)
                )
                // Inner core
                drawCircle(
                    color = surfaceColor,
                    radius = 2.dp.toPx(),
                    center = Offset(railX, nodeY)
                )
            }
            .padding(start = 28.dp)
            .padding(top = 10.dp, bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = timeRange,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Text(
                    text = "$durationText • $songsCountText",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun TimelineTrackRow(
    track: TrackHistoryEntity,
    isLastInSession: Boolean,
    modifier: Modifier = Modifier,
    onToggleStar: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val isPureBlack = MaterialTheme.colorScheme.surfaceContainerLow == Color.Black
    val surfaceColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow
    val railColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val dotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val railX = 14.dp.toPx()
                val dotY = size.height / 2f

                // Rail line
                val endY = if (isLastInSession) dotY else size.height
                drawLine(
                    color = railColor,
                    start = Offset(railX, 0f),
                    end = Offset(railX, endY),
                    strokeWidth = 2.dp.toPx()
                )

                // Hollow Accent Dot
                drawCircle(
                    color = surfaceColor,
                    radius = 4.dp.toPx(),
                    center = Offset(railX, dotY)
                )
                drawCircle(
                    color = dotColor,
                    radius = 4.dp.toPx(),
                    center = Offset(railX, dotY),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
            .padding(start = 28.dp)
    ) {
        TrackHistoryItem(
            track = track,
            showClockTime = true,
            onToggleStar = onToggleStar,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun TimelineGapPill(
    gapText: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(width = 2.dp, height = 20.dp)) {
                val dotColor = Color.Gray.copy(alpha = 0.35f)
                drawCircle(
                    color = dotColor,
                    radius = 1.5.dp.toPx(),
                    center = Offset(size.width / 2f, 3.dp.toPx())
                )
                drawCircle(
                    color = dotColor,
                    radius = 1.5.dp.toPx(),
                    center = Offset(size.width / 2f, 10.dp.toPx())
                )
                drawCircle(
                    color = dotColor,
                    radius = 1.5.dp.toPx(),
                    center = Offset(size.width / 2f, 17.dp.toPx())
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.padding(start = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Text(
                    text = gapText,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
