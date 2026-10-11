package com.mpernow.workdone.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class TrainingStatsTest {
    private val locale = Locale.UK // weeks start on Monday
    private val today = LocalDate.of(2026, 10, 11) // Sunday; this week started Mon 5 Oct

    private fun row(workoutId: Long, date: LocalDate, sets: Int = 5, volume: Double = 1000.0) =
        WorkoutTotals(workoutId, date, sets, volume)

    @Test
    fun weeklyTotalsIncludeEmptyWeeks() {
        val rows = listOf(
            row(1, LocalDate.of(2026, 10, 6)),
            row(2, LocalDate.of(2026, 10, 8)),
            row(3, LocalDate.of(2026, 9, 22)),
        )
        val weeks = TrainingStats.weeklyTotals(rows, today, weeks = 3, locale = locale)

        assertEquals(listOf(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 5)), weeks.map { it.weekStart })
        assertEquals(listOf(1, 0, 2), weeks.map { it.workouts })
        assertEquals(listOf(1000.0, 0.0, 2000.0), weeks.map { it.volumeKg })
    }

    @Test
    fun streakCountsBackFromThisWeek() {
        val dates = listOf(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 9, 29), LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 1))
        assertEquals(3, TrainingStats.weekStreak(dates, today, locale))
    }

    @Test
    fun streakSurvivesAnUntrainedCurrentWeek() {
        val dates = listOf(LocalDate.of(2026, 9, 29), LocalDate.of(2026, 9, 22))
        assertEquals(2, TrainingStats.weekStreak(dates, today, locale))
    }

    @Test
    fun streakIsZeroAfterAMissedWeek() {
        assertEquals(0, TrainingStats.weekStreak(listOf(LocalDate.of(2026, 9, 22)), today, locale))
        assertEquals(0, TrainingStats.weekStreak(emptyList(), today, locale))
    }

    @Test
    fun setsPerDayAddsUpSameDayWorkoutsAndRespectsTheStartDate() {
        val rows = listOf(
            row(1, LocalDate.of(2026, 10, 6), sets = 6),
            row(2, LocalDate.of(2026, 10, 6), sets = 4), // a second workout the same day
            row(3, LocalDate.of(2026, 10, 8), sets = 9),
            row(4, LocalDate.of(2026, 1, 1), sets = 20),
        )
        val from = LocalDate.of(2026, 7, 1)

        assertEquals(mapOf(LocalDate.of(2026, 10, 6) to 10, LocalDate.of(2026, 10, 8) to 9), TrainingStats.setsPerDay(rows, from))
    }

    @Test
    fun heatLevels() {
        assertEquals(listOf(0, 1, 1, 2, 3, 4), listOf(0, 1, 9, 10, 25, 40).map(TrainingStats::heatLevel))
    }
}
