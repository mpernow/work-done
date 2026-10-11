package com.mpernow.workdone.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

class WorkoutDatesTest {
    private val now = Instant.parse("2026-10-11T18:00:00Z")
    private val sweden = Locale.forLanguageTag("sv-SE") // weeks start on Monday
    private val us = Locale.US // weeks start on Sunday

    @Test
    fun workoutStartedTodayIsNotBackdated() {
        assertFalse(WorkoutDates.isBackdated(now.minus(Duration.ofHours(2)), now))
        assertEquals(now, WorkoutDates.defaultEnd(now.minus(Duration.ofHours(2)), now))
    }

    @Test
    fun backdatedWorkoutEndsAnHourAfterItStarted() {
        val yesterday = now.minus(Duration.ofDays(1))
        assertTrue(WorkoutDates.isBackdated(yesterday, now))
        assertEquals(yesterday.plus(Duration.ofHours(1)), WorkoutDates.defaultEnd(yesterday, now))
    }

    @Test
    fun weekStartFollowsTheLocale() {
        val saturday = LocalDate.of(2026, 10, 10)
        assertEquals(LocalDate.of(2026, 10, 5), WorkoutDates.weekStart(saturday, sweden))
        assertEquals(LocalDate.of(2026, 10, 4), WorkoutDates.weekStart(saturday, us))
    }

    @Test
    fun weekLabels() {
        val today = LocalDate.of(2026, 10, 11) // a Sunday
        fun label(date: LocalDate) =
            WorkoutDates.weekLabel(WorkoutDates.weekStart(date, Locale.UK), today, Locale.UK)

        assertEquals("This week", label(LocalDate.of(2026, 10, 6)))
        assertEquals("Last week", label(LocalDate.of(2026, 10, 1)))
        assertEquals("Week of 21 Sept", label(LocalDate.of(2026, 9, 24)))
        assertEquals("Week of 29 Dec 2025", label(LocalDate.of(2025, 12, 30)))
    }

    @Test
    fun formatsDurations() {
        assertEquals("45 min", WorkoutDates.formatDuration(Duration.ofMinutes(45)))
        assertEquals("1 h", WorkoutDates.formatDuration(Duration.ofMinutes(60)))
        assertEquals("1 h 5 min", WorkoutDates.formatDuration(Duration.ofMinutes(65)))
        assertEquals("0 min", WorkoutDates.formatDuration(Duration.ofSeconds(-30)))
    }
}
