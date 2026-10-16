package com.mpernow.workdone.domain

import kotlin.math.roundToInt

/** Turns a parsed PM5 screen into sets for the matching tracking type. */
object Pm5Import {
    fun trackingType(workout: Pm5Workout): TrackingType = when (workout) {
        is Pm5Workout.DistanceIntervals -> TrackingType.INTERVALS_DISTANCE
        is Pm5Workout.TimeIntervals -> TrackingType.INTERVALS_TIME
        is Pm5Workout.SingleDistance, is Pm5Workout.SingleTime -> TrackingType.DISTANCE_SPLITS
    }

    fun restSeconds(workout: Pm5Workout): Int? = when (workout) {
        is Pm5Workout.DistanceIntervals -> workout.restSeconds
        is Pm5Workout.TimeIntervals -> workout.restSeconds
        is Pm5Workout.SingleDistance, is Pm5Workout.SingleTime -> null
    }

    /** One set per row. Rest is the workout's; resistance isn't on the screen, so it's left out. */
    fun toSetValues(workout: Pm5Workout, rows: List<Pm5Row>): List<SetValues> = rows.map { row ->
        val base = SetValues(splitSeconds = row.splitSeconds, strokeRate = row.strokeRate, restSeconds = restSeconds(workout))
        when (workout) {
            is Pm5Workout.TimeIntervals ->
                base.copy(durationSeconds = row.timeSeconds?.roundToInt() ?: workout.workSeconds)
            is Pm5Workout.DistanceIntervals, is Pm5Workout.SingleDistance, is Pm5Workout.SingleTime ->
                base.copy(distanceMeters = row.meters?.toDouble())
        }
    }

    /** A suggested name for a new exercise of [type], distinct per type since names are unique. */
    fun suggestedExerciseName(type: TrackingType): String = when (type) {
        TrackingType.INTERVALS_DISTANCE -> "Rowing intervals"
        TrackingType.INTERVALS_TIME -> "Rowing intervals (time)"
        else -> "Rowing (splits)"
    }

    /** "6 × 500 m, 2:00 rest", "8 × 2:00, 1:00 rest", "2000 m" or "10:00". */
    fun describe(workout: Pm5Workout): String = when (workout) {
        is Pm5Workout.DistanceIntervals ->
            "${workout.count} × ${workout.meters} m, ${SetInput.formatDuration(workout.restSeconds)} rest"
        is Pm5Workout.TimeIntervals ->
            "${workout.count} × ${SetInput.formatDuration(workout.workSeconds)}, ${SetInput.formatDuration(workout.restSeconds)} rest"
        is Pm5Workout.SingleDistance -> "${workout.meters} m"
        is Pm5Workout.SingleTime -> SetInput.formatDuration(workout.seconds)
    }
}
