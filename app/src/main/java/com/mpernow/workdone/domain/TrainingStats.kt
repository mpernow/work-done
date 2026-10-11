package com.mpernow.workdone.domain

import java.time.LocalDate
import java.util.Locale

/** Sets and volume of one category in one finished workout. */
data class CategoryTotals(
    val workoutId: Long,
    val date: LocalDate,
    val category: ExerciseCategory,
    val setCount: Int,
    val volumeKg: Double,
)

data class WeekTotals(val weekStart: LocalDate, val workouts: Int, val volumeKg: Double)

enum class StatsRange(val label: String, val weeks: Int) {
    THREE_MONTHS("3 months", 13),
    SIX_MONTHS("6 months", 26),
    ONE_YEAR("1 year", 52),
}

object TrainingStats {
    /** Totals for the last [weeks] weeks, oldest first and ending with the current week, including empty weeks. */
    fun weeklyTotals(
        rows: List<CategoryTotals>,
        today: LocalDate,
        weeks: Int,
        locale: Locale = Locale.getDefault(),
    ): List<WeekTotals> {
        val thisWeek = WorkoutDates.weekStart(today, locale)
        val byWeek = rows.groupBy { WorkoutDates.weekStart(it.date, locale) }
        return (weeks - 1 downTo 0).map { ago ->
            val weekStart = thisWeek.minusWeeks(ago.toLong())
            val weekRows = byWeek[weekStart].orEmpty()
            WeekTotals(weekStart, weekRows.map { it.workoutId }.distinct().size, weekRows.sumOf { it.volumeKg })
        }
    }

    /**
     * Consecutive weeks with at least one workout. The current week only breaks the streak once
     * it's over, so a streak still counts on Monday before you've trained.
     */
    fun weekStreak(dates: Collection<LocalDate>, today: LocalDate, locale: Locale = Locale.getDefault()): Int {
        val weeks = dates.map { WorkoutDates.weekStart(it, locale) }.toSet()
        var week = WorkoutDates.weekStart(today, locale)
        if (week !in weeks) week = week.minusWeeks(1)
        var streak = 0
        while (week in weeks) {
            streak++
            week = week.minusWeeks(1)
        }
        return streak
    }

    fun setsPerDay(rows: List<CategoryTotals>, from: LocalDate): Map<LocalDate, Int> =
        rows.filter { !it.date.isBefore(from) }
            .groupBy { it.date }
            .mapValues { (_, dayRows) -> dayRows.sumOf { it.setCount } }

    /** Sets per category since [from], largest first; categories without sets are left out. */
    fun categorySplit(rows: List<CategoryTotals>, from: LocalDate): List<Pair<ExerciseCategory, Int>> =
        rows.filter { !it.date.isBefore(from) }
            .groupBy { it.category }
            .map { (category, categoryRows) -> category to categoryRows.sumOf { it.setCount } }
            .sortedWith(compareByDescending<Pair<ExerciseCategory, Int>> { it.second }.thenBy { it.first.ordinal })

    /** Heatmap shade for a day: 0 for rest days, then 1–4 by number of sets. */
    fun heatLevel(sets: Int): Int = when {
        sets <= 0 -> 0
        sets < 10 -> 1
        sets < 20 -> 2
        sets < 30 -> 3
        else -> 4
    }
}
