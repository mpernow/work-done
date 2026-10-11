package com.mpernow.workdone.domain

import kotlin.math.roundToInt

/**
 * Totals and averages over the splits of a piece or the intervals of a session (rest not
 * included), like the summary row on a PM5 results screen.
 */
data class SplitsSummary(
    val meters: Double,
    val seconds: Double,
    /** Total time over total distance, per 500 m; matches the PM5's average split. */
    val averageSplitSeconds: Double,
    /** Weighted by time; null if no split has a stroke rate. */
    val averageStrokeRate: Double?,
) {
    /** "2000 m · 8:37.7" */
    val totalText: String
        get() = "${meters.roundToInt()} m · ${formatTenths(seconds)}"

    /** "2:09.4 /500m · 23.4 s/m" */
    val averageText: String
        get() = listOfNotNull(
            "${DigitTime.formatSplit(averageSplitSeconds)} /500m",
            averageStrokeRate?.let { "${SetInput.formatDecimal(Math.round(it * 10) / 10.0)} s/m" },
        ).joinToString(" · ")

    companion object {
        /** Null unless at least one split has both a distance and a time (or split). */
        fun of(sets: List<SetValues>): SplitsSummary? {
            val worked = sets.filter { (it.workMeters ?: 0.0) > 0 && it.workSeconds != null }
            if (worked.isEmpty()) return null
            val meters = worked.sumOf { it.workMeters!! }
            val seconds = worked.sumOf { it.workSeconds!! }
            return SplitsSummary(
                meters = meters,
                seconds = seconds,
                averageSplitSeconds = seconds / meters * 500,
                averageStrokeRate = Progress.sessionValue(worked, ProgressMetric.STROKE_RATE),
            )
        }

        /** "8:37.7", or "1:02:03.4" from an hour up. */
        internal fun formatTenths(seconds: Double): String {
            val tenths = (seconds * 10).roundToInt()
            val hours = tenths / 36_000
            val minutes = tenths % 36_000 / 600
            val rest = tenths % 600
            return if (hours > 0) {
                "%d:%02d:%02d.%d".format(hours, minutes, rest / 10, rest % 10)
            } else {
                "%d:%02d.%d".format(minutes, rest / 10, rest % 10)
            }
        }
    }
}
