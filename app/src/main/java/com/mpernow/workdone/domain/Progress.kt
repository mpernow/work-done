package com.mpernow.workdone.domain

import java.time.LocalDate
import kotlin.math.roundToInt

/** How a metric's values are measured, which decides how they're converted and formatted. */
enum class MetricKind { WEIGHT, REPS, DURATION, DISTANCE, PACE, SPLIT, STROKE_RATE }

/**
 * A per-workout measure of an exercise. Values are in storage units: kilograms, reps,
 * seconds, metres, seconds per kilometre for pace and seconds per 500 m for splits.
 */
enum class ProgressMetric(
    val label: String,
    /** Title of the metric's personal record, or null if it has none. */
    val recordLabel: String?,
    val kind: MetricKind,
    val lowerIsBetter: Boolean = false,
) {
    EST_ONE_REP_MAX("Est. 1RM", "Best est. 1RM", MetricKind.WEIGHT),
    MAX_WEIGHT("Max weight", "Heaviest weight", MetricKind.WEIGHT),
    VOLUME("Volume", "Most volume in a workout", MetricKind.WEIGHT),
    BEST_SET_REPS("Best set", "Most reps in a set", MetricKind.REPS),
    TOTAL_REPS("Total reps", "Most reps in a workout", MetricKind.REPS),
    LONGEST_SET("Longest", "Longest set", MetricKind.DURATION),
    TOTAL_TIME("Total time", "Most time in a workout", MetricKind.DURATION),
    DISTANCE("Distance", "Longest distance", MetricKind.DISTANCE),
    PACE("Pace", "Fastest pace", MetricKind.PACE, lowerIsBetter = true),
    AVERAGE_SPLIT("Avg split", "Best average split", MetricKind.SPLIT, lowerIsBetter = true),
    BEST_SPLIT("Best split", "Fastest split", MetricKind.SPLIT, lowerIsBetter = true),
    /** Higher isn't better for stroke rate, so it has no personal record. */
    STROKE_RATE("Rate", null, MetricKind.STROKE_RATE),
}

/** A set of an exercise, with the workout and entry it was logged in. */
data class LoggedSet(val workoutId: Long, val entry: Int, val date: LocalDate, val values: SetValues)

/** The sets of one exercise in one workout, or in one entry of it; see [Progress.sessions]. */
data class ExerciseSession(val workoutId: Long, val date: LocalDate, val sets: List<SetValues>, val entry: Int = 0)

data class ProgressPoint(val workoutId: Long, val date: LocalDate, val value: Double)

object Progress {
    fun metricsFor(type: TrackingType): List<ProgressMetric> = when (type) {
        TrackingType.WEIGHT_REPS -> listOf(ProgressMetric.EST_ONE_REP_MAX, ProgressMetric.MAX_WEIGHT, ProgressMetric.VOLUME)
        TrackingType.REPS -> listOf(ProgressMetric.BEST_SET_REPS, ProgressMetric.TOTAL_REPS)
        TrackingType.DURATION -> listOf(ProgressMetric.LONGEST_SET, ProgressMetric.TOTAL_TIME)
        TrackingType.DISTANCE_TIME -> listOf(ProgressMetric.DISTANCE, ProgressMetric.PACE, ProgressMetric.TOTAL_TIME)
        TrackingType.INTERVALS_TIME, TrackingType.INTERVALS_DISTANCE ->
            listOf(ProgressMetric.AVERAGE_SPLIT, ProgressMetric.BEST_SPLIT, ProgressMetric.DISTANCE, ProgressMetric.STROKE_RATE)
        // Workouts are compared at one distance at a time, so distance itself isn't a metric.
        TrackingType.DISTANCE_SPLITS -> listOf(ProgressMetric.AVERAGE_SPLIT, ProgressMetric.BEST_SPLIT, ProgressMetric.STROKE_RATE)
    }

    /**
     * Groups an exercise's sets into sessions, in the order of [sets]: one per workout, or one
     * per entry for types that are compared by distance, so two separate 2 km rows in one
     * workout stay two 2 km rows.
     */
    fun sessions(sets: List<LoggedSet>, type: TrackingType): List<ExerciseSession> =
        sets.groupBy { it.workoutId to if (filtersByDistance(type)) it.entry else 0 }
            .map { (key, entrySets) ->
                ExerciseSession(key.first, entrySets.first().date, entrySets.map { it.values }, key.second)
            }

    /** Whether workouts of [type] are only compared with others of the same total distance. */
    fun filtersByDistance(type: TrackingType): Boolean = type == TrackingType.DISTANCE_SPLITS

    /** The workout's total distance in whole metres, or null if none of its sets have one. */
    fun totalMeters(sets: List<SetValues>): Int? = sessionValue(sets, ProgressMetric.DISTANCE)?.roundToInt()

    /**
     * Each session's total distance, in the order of [sessions]. A session's sets add up to one
     * distance, so 4 × 500 m splits are one 2 km row.
     */
    fun distances(sessions: List<ExerciseSession>): List<Int> = sessions.mapNotNull { totalMeters(it.sets) }

    /** The sessions whose total distance is [meters]. */
    fun atDistance(sessions: List<ExerciseSession>, meters: Int): List<ExerciseSession> =
        sessions.filter { totalMeters(it.sets) == meters }

    /** "500 m", "2 km", "21.1 km". */
    fun formatTotalDistance(meters: Int): String =
        if (meters < 1000) "$meters m" else "${SetInput.formatDecimal(Math.round(meters / 100.0) / 10.0)} km"

    /** The metric's value for one workout, or null if none of its sets have the needed fields. */
    fun sessionValue(sets: List<SetValues>, metric: ProgressMetric): Double? = when (metric) {
        ProgressMetric.EST_ONE_REP_MAX ->
            sets.mapNotNull { set -> set.weightKg?.let { w -> set.reps?.let { r -> Stats.epleyOneRepMax(w, r) } } }.maxOrNull()
        ProgressMetric.MAX_WEIGHT -> sets.mapNotNull { it.weightKg }.maxOrNull()
        ProgressMetric.VOLUME ->
            sets.mapNotNull { set -> set.weightKg?.let { w -> set.reps?.let { r -> Stats.volume(w, r) } } }
                .takeIf { it.isNotEmpty() }?.sum()
        ProgressMetric.BEST_SET_REPS -> sets.mapNotNull { it.reps }.maxOrNull()?.toDouble()
        ProgressMetric.TOTAL_REPS -> sets.mapNotNull { it.reps }.takeIf { it.isNotEmpty() }?.sum()?.toDouble()
        ProgressMetric.LONGEST_SET -> sets.mapNotNull { it.durationSeconds }.maxOrNull()?.toDouble()
        ProgressMetric.TOTAL_TIME -> sets.mapNotNull { it.workSeconds }.takeIf { it.isNotEmpty() }?.sum()
        ProgressMetric.DISTANCE -> sets.mapNotNull { it.workMeters }.takeIf { it.isNotEmpty() }?.sum()
        ProgressMetric.PACE -> {
            val timed = sets.filter { (it.distanceMeters ?: 0.0) > 0 && it.durationSeconds != null }
            val km = timed.sumOf { it.distanceMeters!! } / 1000
            if (km > 0) timed.sumOf { it.durationSeconds!! } / km else null
        }
        // Weighted by distance: total time over total distance, not the mean of the splits.
        ProgressMetric.AVERAGE_SPLIT -> {
            val intervals = sets.filter { it.splitSeconds != null && it.workSeconds != null && (it.workMeters ?: 0.0) > 0 }
            val meters = intervals.sumOf { it.workMeters!! }
            if (meters > 0) intervals.sumOf { it.workSeconds!! } / meters * 500 else null
        }
        ProgressMetric.BEST_SPLIT -> sets.mapNotNull { it.splitSeconds }.minOrNull()
        // Weighted by time, so a long split counts for more than a short one.
        ProgressMetric.STROKE_RATE -> {
            val rated = sets.filter { it.strokeRate != null && (it.workSeconds ?: 0.0) > 0 }
            val seconds = rated.sumOf { it.workSeconds!! }
            if (seconds > 0) rated.sumOf { it.strokeRate!! * it.workSeconds!! } / seconds else null
        }
    }

    /** One point per workout, in the order of [sessions]. */
    fun points(sessions: List<ExerciseSession>, metric: ProgressMetric): List<ProgressPoint> =
        sessions.mapNotNull { session ->
            sessionValue(session.sets, metric)?.let { ProgressPoint(session.workoutId, session.date, it) }
        }

    /** The best point; on a tie, the first time it was reached. Expects [points] oldest first. */
    fun best(points: List<ProgressPoint>, metric: ProgressMetric): ProgressPoint? =
        if (metric.lowerIsBetter) points.minByOrNull { it.value } else points.maxByOrNull { it.value }

    /** Converts a stored value to the unit it's shown in: kg or lb, km, or seconds unchanged. */
    fun toDisplay(value: Double, kind: MetricKind, unit: WeightUnit): Double = when (kind) {
        MetricKind.WEIGHT -> SetInput.fromKg(value, unit)
        MetricKind.DISTANCE -> value / 1000
        MetricKind.REPS, MetricKind.DURATION, MetricKind.PACE, MetricKind.SPLIT, MetricKind.STROKE_RATE -> value
    }

    /** Formats a display value with its unit: "102.5 kg", "12 reps", "25:30", "5.2 km", "4:55 /km". */
    fun format(displayValue: Double, kind: MetricKind, unit: WeightUnit): String = when (kind) {
        MetricKind.WEIGHT -> "${SetInput.formatDecimal(displayValue)} ${unit.label}"
        MetricKind.REPS -> "${displayValue.roundToInt()} reps"
        MetricKind.DURATION -> SetInput.formatDuration(displayValue.roundToInt())
        MetricKind.DISTANCE -> "${SetInput.formatDecimal(displayValue)} km"
        MetricKind.PACE -> "${SetInput.formatDuration(displayValue.roundToInt())} /km"
        MetricKind.SPLIT -> "${DigitTime.formatSplit(displayValue)} /500m"
        MetricKind.STROKE_RATE -> "${SetInput.formatDecimal(Math.round(displayValue * 10) / 10.0)} s/m"
    }

    /** The unit the axis is in, for a chart title: "kg", "km", "min/km"; null for plain counts. */
    fun axisUnit(kind: MetricKind, unit: WeightUnit): String? = when (kind) {
        MetricKind.WEIGHT -> unit.label
        MetricKind.DISTANCE -> "km"
        MetricKind.PACE -> "min/km"
        MetricKind.DURATION -> "min:sec"
        MetricKind.SPLIT -> "per 500 m"
        MetricKind.STROKE_RATE -> "s/m"
        MetricKind.REPS -> null
    }

    /** Short axis labels, without units: "1.2K", "12", "25:30". */
    fun formatAxis(displayValue: Double, kind: MetricKind): String = when (kind) {
        MetricKind.DURATION, MetricKind.PACE, MetricKind.SPLIT -> SetInput.formatDuration(displayValue.roundToInt())
        MetricKind.REPS, MetricKind.STROKE_RATE -> displayValue.roundToInt().toString()
        MetricKind.WEIGHT, MetricKind.DISTANCE -> compactNumber(displayValue)
    }

    /** "950", "1.2K", "12K". */
    fun compactNumber(value: Double): String = when {
        value >= 10_000 -> "${(value / 1000).roundToInt()}K"
        value >= 1_000 -> "${SetInput.formatDecimal(Math.round(value / 100) / 10.0)}K"
        else -> SetInput.formatDecimal(Math.round(value * 10) / 10.0)
    }
}
