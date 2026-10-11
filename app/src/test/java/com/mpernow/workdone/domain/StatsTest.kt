package com.mpernow.workdone.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StatsTest {
    @Test
    fun epleyWithOneRepIsTheLiftedWeight() {
        assertEquals(100.0, Stats.epleyOneRepMax(100.0, 1), 1e-9)
    }

    @Test
    fun epleyScalesWithReps() {
        assertEquals(120.0, Stats.epleyOneRepMax(90.0, 10), 1e-9)
    }

    @Test
    fun epleyWithNoRepsIsZero() {
        assertEquals(0.0, Stats.epleyOneRepMax(100.0, 0), 1e-9)
    }

    @Test
    fun volumeIsWeightTimesReps() {
        assertEquals(500.0, Stats.volume(50.0, 10), 1e-9)
    }
}
