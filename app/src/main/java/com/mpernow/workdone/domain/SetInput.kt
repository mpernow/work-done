package com.mpernow.workdone.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** What the user has typed into a set's input fields, in display units. */
data class SetDraft(
    val weight: String = "",
    val reps: String = "",
    val minutes: String = "",
    val seconds: String = "",
    val distanceKm: String = "",
    // Interval fields. Times are digits only; see DigitTime.
    val workTime: String = "",
    val distanceMeters: String = "",
    val split: String = "",
    val rest: String = "",
    val resistance: String = "",
)

/** Converts between typed set input, stored [SetValues] and display text. */
object SetInput {
    private const val KG_PER_LB = 0.45359237

    fun toKg(value: Double, unit: WeightUnit): Double = if (unit == WeightUnit.KG) value else value * KG_PER_LB

    fun fromKg(kg: Double, unit: WeightUnit): Double = if (unit == WeightUnit.KG) kg else kg / KG_PER_LB

    /** Returns null unless every field the tracking type needs holds a valid value. */
    fun parse(draft: SetDraft, type: TrackingType, unit: WeightUnit): SetValues? = when (type) {
        TrackingType.WEIGHT_REPS -> {
            val weight = parseDecimal(draft.weight)?.takeIf { it >= 0 }
            val reps = parseReps(draft.reps)
            if (weight == null || reps == null) null else SetValues(reps = reps, weightKg = toKg(weight, unit))
        }
        TrackingType.REPS -> parseReps(draft.reps)?.let { SetValues(reps = it) }
        TrackingType.DURATION -> parseDuration(draft)?.let { SetValues(durationSeconds = it) }
        TrackingType.DISTANCE_TIME -> {
            val km = parseDecimal(draft.distanceKm)?.takeIf { it > 0 }
            val duration = parseDuration(draft)
            if (km == null || duration == null) null else SetValues(durationSeconds = duration, distanceMeters = km * 1000)
        }
        TrackingType.INTERVALS_TIME -> {
            val work = DigitTime.parseMinSec(draft.workTime)?.takeIf { it > 0 }
            if (work == null) null else parseInterval(draft)?.copy(durationSeconds = work)
        }
        TrackingType.INTERVALS_DISTANCE -> {
            val meters = draft.distanceMeters.trim().toIntOrNull()?.takeIf { it > 0 }
            if (meters == null) null else parseInterval(draft)?.copy(distanceMeters = meters.toDouble())
        }
    }

    /** The split (required) plus rest and resistance (optional, but must be valid if given). */
    private fun parseInterval(draft: SetDraft): SetValues? {
        val split = DigitTime.parseSplit(draft.split)?.takeIf { it > 0 } ?: return null
        val rest = if (draft.rest.isBlank()) null else DigitTime.parseMinSec(draft.rest) ?: return null
        val resistance = if (draft.resistance.isBlank()) null else draft.resistance.trim().toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return SetValues(splitSeconds = split, restSeconds = rest, resistance = resistance)
    }

    /** Turns a stored set back into input text, used to pre-fill the next set. */
    fun draftFrom(values: SetValues, unit: WeightUnit): SetDraft = SetDraft(
        weight = values.weightKg?.let { formatDecimal(fromKg(it, unit)) }.orEmpty(),
        reps = values.reps?.toString().orEmpty(),
        minutes = values.durationSeconds?.let { (it / 60).toString() }.orEmpty(),
        seconds = values.durationSeconds?.let { (it % 60).toString() }.orEmpty(),
        distanceKm = values.distanceMeters?.let { formatDecimal(it / 1000) }.orEmpty(),
        workTime = values.durationSeconds?.let(DigitTime::minSecDigits).orEmpty(),
        distanceMeters = values.distanceMeters?.let { Math.round(it).toString() }.orEmpty(),
        split = values.splitSeconds?.let(DigitTime::splitDigits).orEmpty(),
        rest = values.restSeconds?.let(DigitTime::minSecDigits).orEmpty(),
        resistance = values.resistance?.toString().orEmpty(),
    )

    /** One-line summary of a set, such as "80 kg × 8" or "5 km in 25:00". */
    fun describe(values: SetValues, type: TrackingType, unit: WeightUnit): String = when (type) {
        TrackingType.WEIGHT_REPS ->
            "${formatDecimal(fromKg(values.weightKg ?: 0.0, unit))} ${unit.label} × ${values.reps ?: 0}"
        TrackingType.REPS -> "${values.reps ?: 0} reps"
        TrackingType.DURATION -> formatDuration(values.durationSeconds ?: 0)
        TrackingType.DISTANCE_TIME ->
            "${formatDecimal((values.distanceMeters ?: 0.0) / 1000)} km in ${formatDuration(values.durationSeconds ?: 0)}"
        TrackingType.INTERVALS_TIME, TrackingType.INTERVALS_DISTANCE -> listOfNotNull(
            if (type == TrackingType.INTERVALS_TIME) {
                formatDuration(values.durationSeconds ?: 0)
            } else {
                "${Math.round(values.distanceMeters ?: 0.0)} m"
            },
            values.splitSeconds?.let { "${DigitTime.formatSplit(it)} /500m" },
            values.restSeconds?.let { "rest ${formatDuration(it)}" },
            values.resistance?.let { "resistance $it" },
        ).joinToString(" · ")
    }

    /** Accepts both "." and "," as the decimal separator. */
    fun parseDecimal(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    /** At most two decimals, without trailing zeros: 80.0 → "80", 82.50 → "82.5". */
    fun formatDecimal(value: Double): String =
        BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    /** "m:ss", or "h:mm:ss" from one hour up. */
    fun formatDuration(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val minutes = totalSeconds % 3600 / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    }

    private fun parseReps(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it > 0 }

    /** Minutes may be any size (a 95-minute run); seconds must be under 60. Either may be left empty. */
    private fun parseDuration(draft: SetDraft): Int? {
        if (draft.minutes.isBlank() && draft.seconds.isBlank()) return null
        val minutes = if (draft.minutes.isBlank()) 0 else draft.minutes.trim().toIntOrNull() ?: return null
        val seconds = if (draft.seconds.isBlank()) 0 else draft.seconds.trim().toIntOrNull() ?: return null
        if (minutes < 0 || seconds !in 0..59) return null
        return (minutes * 60 + seconds).takeIf { it > 0 }
    }
}
