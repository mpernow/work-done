package com.mpernow.workdone.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SplitsSummaryTest {
    // The 2000 m from the PM5 photo: its summary row reads 8:37.7 and 2:09.4 /500m.
    private val splits = listOf(
        SetValues(distanceMeters = 500.0, splitSeconds = 109.4, strokeRate = 28),
        SetValues(distanceMeters = 500.0, splitSeconds = 110.2, strokeRate = 28),
        SetValues(distanceMeters = 500.0, splitSeconds = 154.9, strokeRate = 19),
        SetValues(distanceMeters = 500.0, splitSeconds = 143.2, strokeRate = 21),
    )

    @Test
    fun matchesThePm5SummaryRow() {
        val summary = SplitsSummary.of(splits)!!
        assertEquals("2000 m · 8:37.7", summary.totalText)
        // (28×109.4 + 28×110.2 + 19×154.9 + 21×143.2) / 517.7 = 23.37…
        assertEquals("2:09.4 /500m · 23.4 s/m", summary.averageText)
    }

    @Test
    fun leavesOutTheRateWhenNoSplitHasOne() {
        val summary = SplitsSummary.of(splits.map { it.copy(strokeRate = null) })!!
        assertEquals("2:09.4 /500m", summary.averageText)
    }

    @Test
    fun nothingToSummariseWithoutDistanceAndTime() {
        assertNull(SplitsSummary.of(emptyList()))
        assertNull(SplitsSummary.of(listOf(SetValues(strokeRate = 28))))
    }

    @Test
    fun longPiecesShowHours() {
        assertEquals("1:02:03.4", SplitsSummary.formatTenths(3723.4))
        assertEquals("0:59.9", SplitsSummary.formatTenths(59.94))
    }

    @Test
    fun intervalsMatchThePm5SummaryRowToo() {
        // The 6 × 500 m photo: its summary row reads 10:35.4, 3000 m, 1:45.9 /500m, 28 s/m.
        val intervals = listOf(104.5 to 28, 104.3 to 28, 104.9 to 29, 105.6 to 28, 107.3 to 27, 108.8 to 28).map { (split, rate) ->
            SetValues(distanceMeters = 500.0, splitSeconds = split, strokeRate = rate, restSeconds = 120)
        }
        val summary = SplitsSummary.of(intervals)!!
        assertEquals("3000 m · 10:35.4", summary.totalText)
        assertEquals("1:45.9 /500m · 28 s/m", summary.averageText)
    }

    @Test
    fun timeIntervalsWorkOutTheirDistanceFromTheSplit() {
        val intervals = List(4) { SetValues(durationSeconds = 120, splitSeconds = 120.0) }
        assertEquals("2000 m · 8:00.0", SplitsSummary.of(intervals)!!.totalText)
    }
}
