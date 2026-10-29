package com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.history

import com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySessionGroupingTest {

    private fun createTrack(
        id: Long,
        timestamp: Long,
        title: String = "Track $id",
        endTimestamp: Long = 0L,
        durationMs: Long = 0L
    ): TrackHistoryEntity {
        return TrackHistoryEntity(
            id = id,
            stationUuid = "station-1",
            trackTitle = title,
            timestamp = timestamp,
            endTimestamp = endTimestamp,
            durationMs = durationMs
        )
    }

    @Test
    fun groupTracks_emptyList_returnsEmpty() {
        val result = HistorySessionGrouping.groupTracks(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun groupTracks_singleTrack_returnsSingleSession() {
        val now = 10000000L
        val track = createTrack(1, now)
        val sessions = HistorySessionGrouping.groupTracks(listOf(track))

        assertEquals(1, sessions.size)
        assertEquals(now, sessions.first().startTime)
        assertEquals(now, sessions.first().endTime)
        assertEquals(1, sessions.first().trackCount)
    }

    @Test
    fun groupTracks_singleLongStaticMetadataTrack_usesEndTimestampAndDurationMs() {
        val start = 10000000L
        val oneHourMs = 60 * 60 * 1000L
        val track = createTrack(
            id = 1,
            timestamp = start,
            endTimestamp = start + oneHourMs,
            durationMs = oneHourMs
        )
        val sessions = HistorySessionGrouping.groupTracks(listOf(track))

        assertEquals(1, sessions.size)
        assertEquals(start, sessions.first().startTime)
        assertEquals(start + oneHourMs, sessions.first().endTime)
        assertEquals(oneHourMs, sessions.first().durationMs)
    }

    @Test
    fun groupTracks_pausedTimeWithinThreshold_isExcludedFromActiveDuration() {
        val start = 10000000L
        val fiveMins = 5 * 60 * 1000L
        val shortPause = 30 * 1000L

        // Track 1 played 5 mins, paused 30s (< 1m default gap), then Track 2 played 5 mins
        val t1 = createTrack(
            id = 1,
            timestamp = start,
            endTimestamp = start + fiveMins,
            durationMs = fiveMins
        )
        val t2Start = start + fiveMins + shortPause
        val t2 = createTrack(
            id = 2,
            timestamp = t2Start,
            endTimestamp = t2Start + fiveMins,
            durationMs = fiveMins
        )

        val sessions = HistorySessionGrouping.groupTracks(listOf(t1, t2))

        assertEquals(1, sessions.size)
        assertEquals(2, sessions.first().trackCount)
        // Wall-clock span is 10m30s, but active listening duration is 10m (5m + 5m)
        assertEquals(start, sessions.first().startTime)
        assertEquals(t2Start + fiveMins, sessions.first().endTime)
        assertEquals(2 * fiveMins, sessions.first().durationMs)
    }

    @Test
    fun groupTracks_idleGapUsesOlderTrackEndTimestamp_soLongSongDoesNotFalseSplit() {
        val start = 10000000L
        val twentyMins = 20 * 60 * 1000L
        // Track 1 is a 20-minute mix played continuously without any pause
        val t1 = createTrack(
            id = 1,
            timestamp = start,
            endTimestamp = start + twentyMins,
            durationMs = twentyMins
        )
        // Track 2 starts immediately when Track 1 ends (idle gap = 0ms, even though start-to-start is 20m)
        val t2 = createTrack(
            id = 2,
            timestamp = start + twentyMins,
            endTimestamp = start + twentyMins + 3 * 60 * 1000L,
            durationMs = 3 * 60 * 1000L
        )

        val sessions = HistorySessionGrouping.groupTracks(listOf(t1, t2))

        assertEquals(1, sessions.size)
        assertEquals(2, sessions.first().trackCount)
    }

    @Test
    fun groupTracks_tracksWithinThreshold_remainInSameSession() {
        val baseTime = 10000000L
        val t1 = createTrack(1, baseTime)
        val t2 = createTrack(2, baseTime + 5 * 60 * 1000L) // +5 mins
        val t3 = createTrack(3, baseTime + 10 * 60 * 1000L) // +10 mins

        val sessions = HistorySessionGrouping.groupTracks(listOf(t1, t2, t3), gapMs = 30 * 60 * 1000L)

        assertEquals(1, sessions.size)
        assertEquals(3, sessions.first().trackCount)
        assertEquals(baseTime, sessions.first().startTime)
        assertEquals(baseTime + 10 * 60 * 1000L, sessions.first().endTime)
        assertEquals(10 * 60 * 1000L, sessions.first().durationMs)
    }

    @Test
    fun groupTracks_tracksExceedingThreshold_splitIntoSeparateSessions() {
        val baseTime = 10000000L
        // Session 1: 3 tracks
        val s1_t1 = createTrack(1, baseTime)
        val s1_t2 = createTrack(2, baseTime + 3 * 60 * 1000L)

        // Gap: 45 minutes (> 30 mins)
        val s2_time = baseTime + 48 * 60 * 1000L
        val s2_t1 = createTrack(3, s2_time)
        val s2_t2 = createTrack(4, s2_time + 4 * 60 * 1000L)

        val sessions = HistorySessionGrouping.groupTracks(
            listOf(s1_t1, s1_t2, s2_t1, s2_t2),
            gapMs = 30 * 60 * 1000L
        )

        assertEquals(2, sessions.size)
        // Sessions are ordered newest first
        assertEquals(2, sessions[0].trackCount)
        assertEquals(s2_time, sessions[0].startTime)

        assertEquals(2, sessions[1].trackCount)
        assertEquals(baseTime, sessions[1].startTime)
    }

    @Test
    fun groupTracks_rapidPlayPauseSpam_doesNotSplitSession() {
        val baseTime = 10000000L
        // Tracks played every 1-2 minutes (even with rapid play/pause)
        val tracks = (0 until 10).map { i ->
            createTrack(i.toLong(), baseTime + i * 90 * 1000L)
        }

        val sessions = HistorySessionGrouping.groupTracks(tracks, gapMs = 30 * 60 * 1000L)

        assertEquals(1, sessions.size)
        assertEquals(10, sessions.first().trackCount)
    }

    @Test
    fun groupTracks_customGapParameter_respectsConfiguredMinutes() {
        val baseTime = 10000000L
        val t1 = createTrack(1, baseTime)
        val t2 = createTrack(2, baseTime + 20 * 60 * 1000L) // 20 minutes gap

        // With 15 minutes gap -> splits into 2
        val sessions15 = HistorySessionGrouping.groupTracks(listOf(t1, t2), gapMs = 15 * 60 * 1000L)
        assertEquals(2, sessions15.size)

        // With 30 minutes gap -> stays 1 session
        val sessions30 = HistorySessionGrouping.groupTracks(listOf(t1, t2), gapMs = 30 * 60 * 1000L)
        assertEquals(1, sessions30.size)
    }

    @Test
    fun groupTracks_unsortedInput_isProperlyOrdered() {
        val baseTime = 10000000L
        val t1 = createTrack(1, baseTime)
        val t2 = createTrack(2, baseTime + 5 * 60 * 1000L)
        val t3 = createTrack(3, baseTime + 50 * 60 * 1000L)

        // Pass in random order
        val sessions = HistorySessionGrouping.groupTracks(listOf(t2, t3, t1), gapMs = 30 * 60 * 1000L)

        assertEquals(2, sessions.size)
        assertEquals(1, sessions[0].trackCount) // newest session (t3)
        assertEquals(2, sessions[1].trackCount) // older session (t1, t2)
    }

    @Test
    fun groupTracks_mixedLegacyAndTrackedRows_combinesDurationsAccurately() {
        val baseTime = 10000000L
        val fiveMins = 5 * 60 * 1000L
        // Legacy row (endTimestamp = 0, durationMs = 0), followed 5m later by a v13 row played for 3m
        val legacyTrack = createTrack(1, baseTime, endTimestamp = 0L, durationMs = 0L)
        val newTrack = createTrack(
            2,
            baseTime + fiveMins,
            endTimestamp = baseTime + fiveMins + 3 * 60 * 1000L,
            durationMs = 3 * 60 * 1000L
        )

        // Legacy rows have no end time, so they only group under a gap wider than a song
        val sessions = HistorySessionGrouping.groupTracks(listOf(legacyTrack, newTrack), gapMs = 15 * 60 * 1000L)

        assertEquals(1, sessions.size)
        // Legacy track span to next track (5m) + new track active duration (3m) = 8m
        assertEquals(8 * 60 * 1000L, sessions.first().durationMs)
    }

    @Test
    fun computeCalendarDaysDiff_handlesCrossMidnightAndSameWeekdayLastWeek() {
        val calNow = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 27, 9, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        // 2 calendar days ago at 11:00 PM (only 34 hours ago, < 48h)
        val calTwoDaysAgo = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 25, 23, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        // 7 calendar days ago at 11:00 PM (6 days and 10 hours ago, < 7 * 24h)
        val calSevenDaysAgo = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 20, 23, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        assertEquals(
            2L,
            HistorySessionGrouping.computeCalendarDaysDiff(calNow.timeInMillis, calTwoDaysAgo.timeInMillis)
        )
        assertEquals(
            7L,
            HistorySessionGrouping.computeCalendarDaysDiff(calNow.timeInMillis, calSevenDaysAgo.timeInMillis)
        )
    }

    @Test
    fun groupTracks_pauseOfOneMinuteOrMore_startsNewSession() {
        val start = 10000000L
        val fiveMins = 5 * 60 * 1000L
        val t1 = createTrack(id = 1, timestamp = start, endTimestamp = start + fiveMins, durationMs = fiveMins)
        val t2Start = start + fiveMins + 60 * 1000L
        val t2 = createTrack(id = 2, timestamp = t2Start, endTimestamp = t2Start + fiveMins, durationMs = fiveMins)

        val sessions = HistorySessionGrouping.groupTracks(listOf(t1, t2))

        assertEquals(2, sessions.size)
    }
}
