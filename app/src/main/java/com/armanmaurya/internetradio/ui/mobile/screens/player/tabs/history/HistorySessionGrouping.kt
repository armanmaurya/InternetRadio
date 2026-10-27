package com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.history

import android.content.Context
import android.text.format.DateUtils
import com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class HistorySessionGroup(
    val id: String,
    val startTime: Long,
    val endTime: Long,
    val durationMs: Long,
    val tracks: List<TrackHistoryEntity>
) {
    val trackCount: Int get() = tracks.size
}

object HistorySessionGrouping {

    const val DEFAULT_GAP_MINUTES: Int = 15
    const val DEFAULT_GAP_MS: Long = DEFAULT_GAP_MINUTES * 60 * 1000L

    /**
     * Groups tracks into listening sessions based on [gapMs].
     * Input tracks can be in any order; they are sorted descending (newest first).
     * Output sessions are ordered newest first; within each session, tracks are newest first.
     */
    fun groupTracks(
        tracks: List<TrackHistoryEntity>,
        gapMs: Long = DEFAULT_GAP_MS
    ): List<HistorySessionGroup> {
        if (tracks.isEmpty()) return emptyList()

        val sorted = tracks.sortedByDescending { it.timestamp }
        val sessions = mutableListOf<HistorySessionGroup>()
        var currentSessionTracks = mutableListOf<TrackHistoryEntity>()

        for (i in sorted.indices) {
            val track = sorted[i]
            if (currentSessionTracks.isEmpty()) {
                currentSessionTracks.add(track)
            } else {
                val previousTrack = currentSessionTracks.last() // newer track in time
                val olderTrackEnd = maxOf(track.timestamp, track.endTimestamp)
                val gap = (previousTrack.timestamp - olderTrackEnd).coerceAtLeast(0L)
                if (gap >= gapMs) {
                    sessions.add(buildSessionGroup(currentSessionTracks))
                    currentSessionTracks = mutableListOf(track)
                } else {
                    currentSessionTracks.add(track)
                }
            }
        }

        if (currentSessionTracks.isNotEmpty()) {
            sessions.add(buildSessionGroup(currentSessionTracks))
        }

        return sessions
    }

    private fun buildSessionGroup(tracks: List<TrackHistoryEntity>): HistorySessionGroup {
        val start = tracks.minOf { it.timestamp }
        val end = tracks.maxOf { maxOf(it.timestamp, it.endTimestamp) }
        var totalDuration = 0L
        for (i in tracks.indices) {
            val track = tracks[i]
            val isLegacyRow = track.durationMs == 0L && track.endTimestamp == 0L
            totalDuration += if (!isLegacyRow) {
                track.durationMs
            } else if (i > 0) {
                // tracks is sorted descending (newest first), so tracks[i - 1] is the next newer track
                (tracks[i - 1].timestamp - track.timestamp).coerceAtLeast(0L)
            } else {
                0L
            }
        }
        return HistorySessionGroup(
            id = "session_${start}",
            startTime = start,
            endTime = end,
            durationMs = totalDuration,
            tracks = tracks.toList()
        )
    }

    internal fun computeCalendarDaysDiff(nowMs: Long, timestampMs: Long): Long {
        val calNow = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val calThen = Calendar.getInstance().apply {
            timeInMillis = timestampMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Add 12 hours before dividing by 24h to safely handle 23h/25h DST transition days
        val diffMs = calNow.timeInMillis - calThen.timeInMillis
        return if (diffMs >= 0L) {
            (diffMs + 12 * 3600 * 1000L) / (24 * 3600 * 1000L)
        } else {
            (diffMs - 12 * 3600 * 1000L) / (24 * 3600 * 1000L)
        }
    }

    fun formatDayLabel(context: Context, timestamp: Long, nowMs: Long = System.currentTimeMillis()): String {
        val diffDays = computeCalendarDaysDiff(nowMs, timestamp)
        return when {
            diffDays == 0L -> context.getString(com.armanmaurya.internetradio.R.string.player_session_today)
            diffDays == 1L -> context.getString(com.armanmaurya.internetradio.R.string.player_session_yesterday)
            diffDays in 2L..6L -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestamp))
            else -> {
                val calNow = Calendar.getInstance().apply { timeInMillis = nowMs }
                val calThen = Calendar.getInstance().apply { timeInMillis = timestamp }
                if (calNow.get(Calendar.YEAR) == calThen.get(Calendar.YEAR)) {
                    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
                } else {
                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
                }
            }
        }
    }

    fun formatTimeRange(context: Context, startMs: Long, endMs: Long): String {
        val timeFormat = android.text.format.DateFormat.getTimeFormat(context)
        val startTimeStr = timeFormat.format(Date(startMs))
        val endTimeStr = timeFormat.format(Date(endMs))
        return if (startTimeStr == endTimeStr) {
            startTimeStr
        } else {
            "$startTimeStr – $endTimeStr"
        }
    }

    fun formatDuration(context: Context, durationMs: Long): String {
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> context.getString(
                com.armanmaurya.internetradio.R.string.player_session_duration_hm,
                hours.toInt(),
                minutes.toInt()
            )
            hours > 0 -> "${hours}h"
            minutes > 0 -> context.getString(
                com.armanmaurya.internetradio.R.string.player_session_duration_m,
                minutes.toInt()
            )
            else -> "< 1m"
        }
    }

    fun formatGap(context: Context, gapMs: Long): String {
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(gapMs)
        val hours = totalMinutes / 60
        return when {
            totalMinutes < 60 -> {
                val mins = totalMinutes.coerceAtLeast(1)
                context.getString(com.armanmaurya.internetradio.R.string.player_session_gap_minutes, mins.toInt())
            }
            hours in 1..48 -> {
                context.getString(com.armanmaurya.internetradio.R.string.player_session_gap_hours, hours.toInt())
            }
            else -> {
                val days = TimeUnit.MILLISECONDS.toDays(gapMs).coerceAtLeast(1)
                context.getString(com.armanmaurya.internetradio.R.string.player_session_gap_days, days.toInt())
            }
        }
    }
}
