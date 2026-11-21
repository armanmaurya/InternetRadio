package com.armanmaurya.internetradio.ui.mobile.screens.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepTimerCalculationTest {

    private fun calculateRemainingTime(endTime: Long?, currentTime: Long): Long {
        return endTime?.let { (it - currentTime).coerceAtLeast(0L) } ?: 0L
    }

    private fun calculateMinutesLeft(remainingTime: Long): Int {
        val safeRemaining = remainingTime.coerceAtLeast(0L)
        return ((safeRemaining + 59999L) / 60000L).toInt().coerceAtLeast(0)
    }

    private fun calculateTimerProgress(remainingTime: Long, totalDuration: Long): Float {
        return if (totalDuration > 0) {
            (remainingTime.toFloat() / totalDuration).coerceIn(0f, 1f)
        } else 0f
    }

    private fun calculateTvMinutesLeft(endTime: Long?, currentTime: Long): Int? {
        return endTime?.let {
            val diff = it - currentTime
            if (diff <= 0L) null
            else ((diff + 59999L) / 60000L).toInt().coerceAtLeast(1)
        }
    }

    @Test
    fun `positive remaining time calculates correct minutes rounded up`() {
        assertEquals(15, calculateMinutesLeft(15 * 60_000L))
        assertEquals(15, calculateMinutesLeft(14 * 60_000L + 1L))
        assertEquals(14, calculateMinutesLeft(14 * 60_000L))
        assertEquals(2, calculateMinutesLeft(90_000L))
        assertEquals(1, calculateMinutesLeft(60_000L))
        assertEquals(1, calculateMinutesLeft(30_000L))
        assertEquals(1, calculateMinutesLeft(1L))
    }

    @Test
    fun `zero or negative remaining time stops at zero and never returns negative`() {
        assertEquals(0, calculateMinutesLeft(0L))
        assertEquals(0, calculateMinutesLeft(-1L))
        assertEquals(0, calculateMinutesLeft(-60_000L))
        assertEquals(0, calculateMinutesLeft(-120_000L))
        assertEquals(0, calculateMinutesLeft(-28_800_000L)) // 8 hours past (overnight)
    }

    @Test
    fun `remainingTime calculation coerces to zero when endTime has passed`() {
        val now = 1_000_000L
        assertEquals(60_000L, calculateRemainingTime(now + 60_000L, now))
        assertEquals(0L, calculateRemainingTime(now, now))
        assertEquals(0L, calculateRemainingTime(now - 60_000L, now))
        assertEquals(0L, calculateRemainingTime(now - 28_800_000L, now))
    }

    @Test
    fun `timer progress is clamped to zero when remaining time is zero or negative`() {
        val total = 30 * 60_000L
        assertEquals(1f, calculateTimerProgress(total, total), 0.001f)
        assertEquals(0.5f, calculateTimerProgress(total / 2, total), 0.001f)
        assertEquals(0f, calculateTimerProgress(0L, total), 0.001f)
        assertEquals(0f, calculateTimerProgress(-10_000L, total), 0.001f)
    }

    @Test
    fun `tv minutes left returns null when timer has expired`() {
        val now = 1_000_000L
        assertEquals(10, calculateTvMinutesLeft(now + 10 * 60_000L, now))
        assertEquals(1, calculateTvMinutesLeft(now + 30_000L, now))
        assertNull(calculateTvMinutesLeft(now, now))
        assertNull(calculateTvMinutesLeft(now - 1_000L, now))
        assertNull(calculateTvMinutesLeft(now - 28_800_000L, now))
    }
}
