package com.mpernow.workdone.ui.charts

import org.junit.Assert.assertEquals
import org.junit.Test

class NiceStepTest {
    @Test
    fun stepsAreRoundNumbers() {
        assertEquals(2500.0, niceStep(8800.0, AxisSteps.DECIMAL), 1e-9) // ticks 0, 2.5K, 5K, 7.5K, 10K
        assertEquals(50.0, niceStep(180.0, AxisSteps.DECIMAL), 1e-9)
        assertEquals(0.5, niceStep(1.6, AxisSteps.DECIMAL), 1e-9)
    }

    @Test
    fun countsStepByWholeNumbers() {
        assertEquals(1.0, niceStep(3.0, AxisSteps.INTEGER), 1e-9)
        assertEquals(2.0, niceStep(7.0, AxisSteps.INTEGER), 1e-9)
    }

    @Test
    fun timesStepByClockFriendlyAmounts() {
        assertEquals(60.0, niceStep(200.0, AxisSteps.TIME), 1e-9) // 50 s → 1 min
        assertEquals(300.0, niceStep(1000.0, AxisSteps.TIME), 1e-9) // 250 s → 5 min
    }

    @Test
    fun lineRangeIsPaddedAndAlignedToTicks() {
        // Est. 1RM from 72 to 96 kg: padded to 68.4–99.6, then widened to whole 10 kg steps.
        val (low, high, step) = steppedLineRange(72.0, 96.0, AxisSteps.DECIMAL)
        assertEquals(10.0, step, 1e-9)
        assertEquals(60.0, low, 1e-9)
        assertEquals(100.0, high, 1e-9)
    }

    @Test
    fun lineRangeNeverGoesBelowZero() {
        assertEquals(0.0, steppedLineRange(1.0, 3.0, AxisSteps.INTEGER).first, 1e-9)
    }
}
