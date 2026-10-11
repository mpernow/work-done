package com.mpernow.workdone.domain

/** Decides which set fields are filled in for an exercise. */
enum class TrackingType(val label: String) {
    WEIGHT_REPS("Weight × reps"),
    REPS("Reps only"),
    DURATION("Duration"),
    DISTANCE_TIME("Distance + time"),

    /** One set per interval of fixed time, e.g. on a rowing machine. */
    INTERVALS_TIME("Intervals (time)"),

    /** One set per interval of fixed distance, e.g. on a rowing machine. */
    INTERVALS_DISTANCE("Intervals (distance)"),

    /** A single continuous piece, one set per split (e.g. each 500 m of a 2000 m row). */
    DISTANCE_SPLITS("Distance (splits)"),
}

/**
 * Rowing-machine style types: one set per interval or split, each with a 500 m split, stroke
 * rate and resistance. The machine keeps time, so the app's rest timer stays off for these.
 */
val TrackingType.usesSplits: Boolean
    get() = this == TrackingType.INTERVALS_TIME || this == TrackingType.INTERVALS_DISTANCE ||
        this == TrackingType.DISTANCE_SPLITS

enum class WeightUnit(val label: String) { KG("kg"), LB("lb") }

/** The measured values of one set, in storage units (kilograms, seconds, metres). */
data class SetValues(
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
    /** Rest after an interval. */
    val restSeconds: Int? = null,
    /** Average time per 500 m over an interval. */
    val splitSeconds: Double? = null,
    /** The machine's resistance or damper setting. */
    val resistance: Int? = null,
    /** Strokes per minute. */
    val strokeRate: Int? = null,
) {
    /** Time worked: as logged, or worked out from the distance and split. */
    val workSeconds: Double?
        get() = durationSeconds?.toDouble()
            ?: if (distanceMeters != null && splitSeconds != null) distanceMeters / 500 * splitSeconds else null

    /** Distance covered: as logged, or worked out from the time and split. */
    val workMeters: Double?
        get() = distanceMeters
            ?: if (durationSeconds != null && splitSeconds != null && splitSeconds > 0) durationSeconds / splitSeconds * 500 else null
}
