package com.mpernow.workdone.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ProgressTest {
    private val squatSets = listOf(
        SetValues(reps = 5, weightKg = 100.0),
        SetValues(reps = 3, weightKg = 110.0),
        SetValues(reps = 10, weightKg = 80.0),
    )

    @Test
    fun weightMetricsTakeTheBestSetOrTheSum() {
        // Epley: 100×5 → 116.67, 110×3 → 121, 80×10 → 106.67
        assertEquals(121.0, Progress.sessionValue(squatSets, ProgressMetric.EST_ONE_REP_MAX)!!, 1e-9)
        assertEquals(110.0, Progress.sessionValue(squatSets, ProgressMetric.MAX_WEIGHT))
        assertEquals(1630.0, Progress.sessionValue(squatSets, ProgressMetric.VOLUME))
    }

    @Test
    fun repsAndDurationMetrics() {
        val sets = listOf(SetValues(reps = 12, durationSeconds = 60), SetValues(reps = 8, durationSeconds = 45))
        assertEquals(12.0, Progress.sessionValue(sets, ProgressMetric.BEST_SET_REPS))
        assertEquals(20.0, Progress.sessionValue(sets, ProgressMetric.TOTAL_REPS))
        assertEquals(60.0, Progress.sessionValue(sets, ProgressMetric.LONGEST_SET))
        assertEquals(105.0, Progress.sessionValue(sets, ProgressMetric.TOTAL_TIME))
    }

    @Test
    fun paceIsSecondsPerKilometreOverAllTimedSets() {
        val sets = listOf(
            SetValues(distanceMeters = 5000.0, durationSeconds = 1500),
            SetValues(distanceMeters = 1000.0, durationSeconds = 240),
        )
        assertEquals(290.0, Progress.sessionValue(sets, ProgressMetric.PACE)!!, 1e-9)
        assertEquals(6000.0, Progress.sessionValue(sets, ProgressMetric.DISTANCE))
    }

    @Test
    fun missingFieldsGiveNoValue() {
        assertNull(Progress.sessionValue(listOf(SetValues(reps = 10)), ProgressMetric.EST_ONE_REP_MAX))
        assertNull(Progress.sessionValue(listOf(SetValues(durationSeconds = 60)), ProgressMetric.PACE))
        assertNull(Progress.sessionValue(emptyList(), ProgressMetric.VOLUME))
    }

    @Test
    fun bestPrefersTheFirstTimeARecordWasSet() {
        val points = listOf(
            ProgressPoint(1, LocalDate.of(2026, 9, 1), 100.0),
            ProgressPoint(2, LocalDate.of(2026, 9, 8), 110.0),
            ProgressPoint(3, LocalDate.of(2026, 9, 15), 110.0),
        )
        assertEquals(2L, Progress.best(points, ProgressMetric.MAX_WEIGHT)?.workoutId)
        assertEquals(1L, Progress.best(points, ProgressMetric.PACE)?.workoutId) // lower is better
    }

    @Test
    fun formatsValuesForDisplay() {
        assertEquals("225 lb", Progress.format(Progress.toDisplay(102.05828325, MetricKind.WEIGHT, WeightUnit.LB), MetricKind.WEIGHT, WeightUnit.LB))
        assertEquals("5.2 km", Progress.format(Progress.toDisplay(5200.0, MetricKind.DISTANCE, WeightUnit.KG), MetricKind.DISTANCE, WeightUnit.KG))
        assertEquals("4:50 /km", Progress.format(290.0, MetricKind.PACE, WeightUnit.KG))
        assertEquals("12 reps", Progress.format(12.0, MetricKind.REPS, WeightUnit.KG))
    }

    @Test
    fun compactNumbers() {
        assertEquals("950", Progress.compactNumber(950.0))
        assertEquals("1.2K", Progress.compactNumber(1234.0))
        assertEquals("12K", Progress.compactNumber(12_400.0))
        assertEquals("82.5", Progress.compactNumber(82.5))
    }

    @Test
    fun intervalWorkIsWorkedOutFromTheSplit() {
        val byTime = SetValues(durationSeconds = 120, splitSeconds = 120.0)
        val byDistance = SetValues(distanceMeters = 500.0, splitSeconds = 110.0)
        assertEquals(500.0, byTime.workMeters!!, 1e-9)
        assertEquals(110.0, byDistance.workSeconds!!, 1e-9)
    }

    @Test
    fun averageSplitIsWeightedByDistance() {
        // 1000 m at 2:00 and 500 m at 1:45: 360 s over 1500 m is 2:00 × 2/3 + 1:45 × 1/3 = 1:55.
        val sets = listOf(
            SetValues(distanceMeters = 1000.0, splitSeconds = 120.0),
            SetValues(distanceMeters = 500.0, splitSeconds = 105.0),
        )
        assertEquals(115.0, Progress.sessionValue(sets, ProgressMetric.AVERAGE_SPLIT)!!, 1e-9)
        assertEquals(105.0, Progress.sessionValue(sets, ProgressMetric.BEST_SPLIT))
        assertEquals(1500.0, Progress.sessionValue(sets, ProgressMetric.DISTANCE))
    }

    @Test
    fun splitsFormatWithTenths() {
        assertEquals("1:52.4 /500m", Progress.format(112.4, MetricKind.SPLIT, WeightUnit.KG))
    }

    @Test
    fun strokeRateIsWeightedByTime() {
        // 500 m at 2:00 (120 s) at 20 s/m and 500 m at 1:30 (90 s) at 30 s/m: (20×120 + 30×90) / 210.
        val sets = listOf(
            SetValues(distanceMeters = 500.0, splitSeconds = 120.0, strokeRate = 20),
            SetValues(distanceMeters = 500.0, splitSeconds = 90.0, strokeRate = 30),
        )
        assertEquals(5100.0 / 210, Progress.sessionValue(sets, ProgressMetric.STROKE_RATE)!!, 1e-9)
        assertNull(ProgressMetric.STROKE_RATE.recordLabel)
    }

    @Test
    fun singleDistancesAreGroupedByTotalDistance() {
        val sets = listOf(SetValues(distanceMeters = 500.0, splitSeconds = 110.0), SetValues(distanceMeters = 1499.6, splitSeconds = 112.0))
        assertEquals(2000, Progress.totalMeters(sets))
        assertNull(Progress.totalMeters(listOf(SetValues(splitSeconds = 110.0))))
        assertEquals("500 m", Progress.formatTotalDistance(500))
        assertEquals("2 km", Progress.formatTotalDistance(2000))
        assertEquals("21.1 km", Progress.formatTotalDistance(21097))
    }

    @Test
    fun splitsOfOneEntryAddUpButSeparateEntriesDoNot() {
        fun split(workoutId: Long, entry: Int, meters: Double) =
            LoggedSet(workoutId, entry, LocalDate.of(2026, 10, workoutId.toInt()), SetValues(distanceMeters = meters, splitSeconds = 110.0))
        val sets = listOf(
            // Two separate 2 km rows in one workout.
            split(1, 0, 1000.0), split(1, 0, 1000.0),
            split(1, 1, 1000.0), split(1, 1, 1000.0),
            // A 2 km row in another workout.
            split(2, 0, 1000.0), split(2, 0, 1000.0),
            // Two 2 km splits of one 4 km row.
            split(3, 0, 2000.0), split(3, 0, 2000.0),
        )
        val sessions = Progress.sessions(sets, TrackingType.DISTANCE_SPLITS)

        assertEquals(listOf(2000, 2000, 2000, 4000), Progress.distances(sessions))
        assertEquals(listOf(1L to 0, 1L to 1, 2L to 0), Progress.atDistance(sessions, 2000).map { it.workoutId to it.entry })
        assertEquals(listOf(3L), Progress.atDistance(sessions, 4000).map { it.workoutId })
    }

    @Test
    fun otherTypesAddUpAllEntriesOfAWorkout() {
        val sets = listOf(
            LoggedSet(1, 0, LocalDate.of(2026, 10, 1), SetValues(reps = 5, weightKg = 100.0)),
            LoggedSet(1, 1, LocalDate.of(2026, 10, 1), SetValues(reps = 5, weightKg = 100.0)),
        )
        val sessions = Progress.sessions(sets, TrackingType.WEIGHT_REPS)

        assertEquals(1, sessions.size)
        assertEquals(1000.0, Progress.sessionValue(sessions.single().sets, ProgressMetric.VOLUME))
    }
}
