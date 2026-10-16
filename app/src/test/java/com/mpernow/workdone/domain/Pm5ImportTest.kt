package com.mpernow.workdone.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class Pm5ImportTest {
    private val row = Pm5Row(timeSeconds = 104.5, meters = 500, splitSeconds = 104.5, strokeRate = 28)

    @Test
    fun distanceIntervalsBecomeIntervalSetsWithTheWorkoutsRest() {
        val workout = Pm5Workout.DistanceIntervals(count = 1, meters = 500, restSeconds = 120)
        assertEquals(TrackingType.INTERVALS_DISTANCE, Pm5Import.trackingType(workout))
        assertEquals(
            listOf(SetValues(distanceMeters = 500.0, splitSeconds = 104.5, strokeRate = 28, restSeconds = 120)),
            Pm5Import.toSetValues(workout, listOf(row)),
        )
    }

    @Test
    fun aSingleDistanceBecomesSplitsWithoutRest() {
        val workout = Pm5Workout.SingleDistance(2000)
        assertEquals(TrackingType.DISTANCE_SPLITS, Pm5Import.trackingType(workout))
        assertEquals(
            listOf(SetValues(distanceMeters = 500.0, splitSeconds = 104.5, strokeRate = 28)),
            Pm5Import.toSetValues(workout, listOf(row)),
        )
    }

    @Test
    fun aSingleTimeBecomesSplitsLikeASingleDistance() {
        val workout = Pm5Workout.SingleTime(600)
        assertEquals(TrackingType.DISTANCE_SPLITS, Pm5Import.trackingType(workout))
        assertEquals("10:00", Pm5Import.describe(workout))
        val timed = row.copy(timeSeconds = 120.0, meters = 465, splitSeconds = 129.0)
        assertEquals(
            listOf(SetValues(distanceMeters = 465.0, splitSeconds = 129.0, strokeRate = 28)),
            Pm5Import.toSetValues(workout, listOf(timed)),
        )
    }

    @Test
    fun timeIntervalsKeepTheWorkTime() {
        val workout = Pm5Workout.TimeIntervals(count = 1, workSeconds = 120, restSeconds = 60)
        val timed = row.copy(timeSeconds = 120.0, meters = 515, splitSeconds = 116.5)
        assertEquals(
            listOf(SetValues(durationSeconds = 120, splitSeconds = 116.5, strokeRate = 28, restSeconds = 60)),
            Pm5Import.toSetValues(workout, listOf(timed)),
        )
    }
}
