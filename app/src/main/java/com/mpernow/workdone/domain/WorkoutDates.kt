package com.mpernow.workdone.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/** Date and time rules for workouts, kept free of Android types so they can be unit tested. */
object WorkoutDates {
    /** A workout that started longer ago than this is treated as logged after the fact. */
    val BACKDATED_AFTER: Duration = Duration.ofHours(12)

    fun isBackdated(startedAt: Instant, now: Instant): Boolean =
        Duration.between(startedAt, now) > BACKDATED_AFTER

    /** Suggested end time when finishing: now, or an hour after the start for a backdated workout. */
    fun defaultEnd(startedAt: Instant, now: Instant): Instant =
        if (isBackdated(startedAt, now)) startedAt.plus(Duration.ofHours(1)) else now

    fun weekStart(date: LocalDate, locale: Locale = Locale.getDefault()): LocalDate =
        date.with(WeekFields.of(locale).dayOfWeek(), 1)

    /** "This week", "Last week", or "Week of 6 Oct" (with the year when it isn't the current one). */
    fun weekLabel(weekStart: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String {
        val thisWeek = weekStart(today, locale)
        return when (weekStart) {
            thisWeek -> "This week"
            thisWeek.minusWeeks(1) -> "Last week"
            else -> {
                val pattern = if (weekStart.year == today.year) "d MMM" else "d MMM yyyy"
                "Week of ${DateTimeFormatter.ofPattern(pattern, locale).format(weekStart)}"
            }
        }
    }

    /** "45 min", "1 h", "1 h 5 min". */
    fun formatDuration(duration: Duration): String {
        val totalMinutes = duration.toMinutes().coerceAtLeast(0)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours == 0L -> "$minutes min"
            minutes == 0L -> "$hours h"
            else -> "$hours h $minutes min"
        }
    }
}
