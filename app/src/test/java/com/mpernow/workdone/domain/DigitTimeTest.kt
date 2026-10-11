package com.mpernow.workdone.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DigitTimeTest {
    @Test
    fun minSecDigits() {
        assertEquals(90, DigitTime.parseMinSec("130"))
        assertEquals(45, DigitTime.parseMinSec("45"))
        assertEquals(45, DigitTime.parseMinSec("045"))
        assertEquals(20 * 60, DigitTime.parseMinSec("2000"))
        assertNull(DigitTime.parseMinSec("190")) // 1:90
        assertNull(DigitTime.parseMinSec(""))
        assertEquals("130", DigitTime.minSecDigits(90))
        assertEquals("045", DigitTime.minSecDigits(45))
    }

    @Test
    fun splitDigitsHaveTenths() {
        assertEquals(112.4, DigitTime.parseSplit("1524")!!, 1e-9)
        assertEquals(52.4, DigitTime.parseSplit("524")!!, 1e-9)
        assertNull(DigitTime.parseSplit("1604")) // 1:60.4
        assertEquals("1524", DigitTime.splitDigits(112.4))
        assertEquals("1:52.4", DigitTime.formatSplit(112.4))
        assertEquals("2:00.0", DigitTime.formatSplit(119.96))
    }

    @Test
    fun separatorsAppearAsYouType() {
        fun minSec(digits: String) = DigitTime.withSeparators(digits, DigitTime.minSecSeparators(digits.length))
        fun split(digits: String) = DigitTime.withSeparators(digits, DigitTime.splitSeparators(digits.length))

        assertEquals(listOf("1", "13", "1:30", "13:00"), listOf("1", "13", "130", "1300").map(::minSec))
        assertEquals(listOf("1", "1.5", "15.2", "1:52.4"), listOf("1", "15", "152", "1524").map(::split))
    }
}
