package com.armanmaurya.internetradio.ui.tv.screens.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity
import com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.history.HistorySessionGroup
import com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.history.HistorySessionGrouping
import kotlinx.coroutines.launch

private val RailX = 14.dp
private val RailInset = 32.dp

/**
 * TV version of the player "Tracks" history: saved tracks on top, then listening sessions
 * on a timeline. Every track row is a single D-pad focusable card; OK toggles the star.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvTrackHistoryList(
    trackHistory: List<TrackHistoryEntity>,
    savedTracks: List<TrackHistoryEntity>,
    listState: LazyListState,
    firstItemModifier: Modifier,
    onToggleStar: (TrackHistoryEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionGroups = remember(trackHistory) { HistorySessionGrouping.groupTracks(trackHistory) }

    // Keeps the headers above the first row visible when focus returns to the top.
    val scrollToTopOnFocus = Modifier.onFocusChanged {
        if (it.isFocused) scope.launch { listState.animateScrollToItem(0) }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize()
    ) {
        if (savedTracks.isNotEmpty()) {
            item(key = "header_saved") {
                SectionHeader(text = "${stringResource(R.string.player_saved_songs)} (${savedTracks.size})")
            }
            itemsIndexed(savedTracks, key = { _, t -> "saved_${t.id}" }) { index, track ->
                TvTrackRow(
                    track = track,
                    onClick = { onToggleStar(track) },
                    modifier = if (index == 0) firstItemModifier.then(scrollToTopOnFocus) else Modifier
                )
            }
            item(key = "saved_spacer") { Spacer(Modifier.padding(top = 12.dp)) }
        }

        if (trackHistory.isEmpty()) {
            item(key = "empty_history") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.player_no_tracks_played),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            sessionGroups.forEachIndexed { sessionIndex, session ->
                if (sessionIndex > 0) {
                    item(key = "gap_${session.id}") {
                        val gapMs = (sessionGroups[sessionIndex - 1].startTime - session.endTime).coerceAtLeast(0L)
                        val gapText = remember(gapMs) { HistorySessionGrouping.formatGap(context, gapMs) }
                        TvGapPill(gapText)
                    }
                }
                item(key = "node_${session.id}") {
                    TvSessionHeader(session)
                }
                itemsIndexed(session.tracks, key = { _, t -> "history_${t.id}" }) { trackIndex, track ->
                    val isFirstOverall = savedTracks.isEmpty() && sessionIndex == 0 && trackIndex == 0
                    TvTimelineTrackRow(
                        track = track,
                        isFirstInSession = trackIndex == 0,
                        isLastInSession = trackIndex == session.tracks.lastIndex,
                        onClick = { onToggleStar(track) },
                        modifier = if (isFirstOverall) firstItemModifier.then(scrollToTopOnFocus) else Modifier
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TvSessionHeader(session: HistorySessionGroup) {
    val context = LocalContext.current
    val dayLabel = remember(session.startTime) { HistorySessionGrouping.formatDayLabel(context, session.startTime) }
    val timeRange = remember(session.startTime, session.endTime) {
        HistorySessionGrouping.formatTimeRange(context, session.startTime, session.endTime)
    }
    val summary = remember(session.durationMs, session.trackCount) {
        val duration = HistorySessionGrouping.formatDuration(context, session.durationMs)
        val songs = context.resources.getQuantityString(
            R.plurals.player_session_songs_count, session.trackCount, session.trackCount
        )
        "$duration • $songs"
    }
    val railColor = MaterialTheme.colorScheme.border.copy(alpha = 0.6f)
    val primary = MaterialTheme.colorScheme.primary
    val core = MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val x = RailX.toPx()
                val y = size.height / 2f
                drawLine(railColor, Offset(x, y), Offset(x, size.height), strokeWidth = 2.dp.toPx())
                drawCircle(primary, radius = 7.dp.toPx(), center = Offset(x, y))
                drawCircle(core, radius = 2.5.dp.toPx(), center = Offset(x, y))
            }
            .padding(start = RailInset, top = 10.dp, bottom = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = timeRange,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun TvGapPill(gapText: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(RailInset - 4.dp), contentAlignment = Alignment.Center) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) {
                    Box(
                        Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .padding(start = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.AccessTime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = gapText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvTimelineTrackRow(
    track: TrackHistoryEntity,
    isFirstInSession: Boolean,
    isLastInSession: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val railColor = MaterialTheme.colorScheme.border.copy(alpha = 0.6f)
    val dotColor = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val scope = rememberCoroutineScope()
    val bringIntoView = remember { BringIntoViewRequester() }
    // Extra room above the first row so its session header (and gap pill) scroll into view too.
    val headerRoomPx = with(LocalDensity.current) { 110.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val x = RailX.toPx()
                val y = size.height / 2f
                drawLine(
                    railColor,
                    Offset(x, 0f),
                    Offset(x, if (isLastInSession) y else size.height),
                    strokeWidth = 2.dp.toPx()
                )
                drawCircle(surface, radius = 5.dp.toPx(), center = Offset(x, y))
                drawCircle(dotColor, radius = 5.dp.toPx(), center = Offset(x, y), style = Stroke(2.dp.toPx()))
            }
            .padding(start = RailInset)
    ) {
        TvTrackRow(
            track = track,
            onClick = onClick,
            modifier = modifier
                .bringIntoViewRequester(bringIntoView)
                .onFocusChanged { state ->
                    if (state.isFocused && isFirstInSession) {
                        scope.launch {
                            bringIntoView.bringIntoView(Rect(0f, -headerRoomPx, 1f, 1f))
                        }
                    }
                }
        )
    }
}

@Composable
private fun TvTrackRow(
    track: TrackHistoryEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val (title, artist) = remember(track.trackTitle) {
        val split = track.trackTitle.split(" - ", limit = 2)
        split[0].trim() to (split.getOrNull(1)?.trim() ?: "")
    }
    val time = remember(track.timestamp) {
        android.text.format.DateFormat.getTimeFormat(context).format(track.timestamp)
    }
    val fallbackArt = painterResource(id = R.drawable.ic_launcher_foreground)

    ListItem(
        selected = false,
        onClick = onClick,
        modifier = modifier.padding(vertical = 4.dp),
        scale = ListItemDefaults.scale(focusedScale = 1.02f),
        shape = ListItemDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(track.coverArtUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                    placeholder = fallbackArt,
                    error = fallbackArt,
                    fallback = fallbackArt,
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
        headlineContent = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        },
        supportingContent = {
            val supporting = if (artist.isNotEmpty()) "$artist • $time" else time
            Text(
                text = supporting,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                color = LocalContentColor.current.copy(alpha = 0.7f)
            )
        },
        trailingContent = {
            Icon(
                imageVector = if (track.isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = stringResource(
                    if (track.isStarred) R.string.player_cd_unstar_track else R.string.player_cd_star_track
                ),
                tint = if (track.isStarred) MaterialTheme.colorScheme.primary
                else LocalContentColor.current.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
        }
    )
}
