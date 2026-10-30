package com.mpernow.workdone.data.ocr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenContrastTest {
    private fun gray(level: Int) = (0xFF shl 24) or (level shl 16) or (level shl 8) or level

    @Test
    fun turnsColoursToGreyByBrightness() {
        val pixels = intArrayOf(gray(0), gray(255), gray(128), 0xFFFF0000.toInt(), 0xFF00FF00.toInt())

        assertArrayEquals(intArrayOf(0, 255, 128, 76, 150), ScreenContrast.gray(pixels))
    }

    @Test
    fun evensOutLightingSoTextIsEquallyDarkEverywhere() {
        // A screen lit from the left, from 200 down to 60, with a dark mark at each end at 70% of
        // the background there. The background on the dim side is darker than the mark on the bright side.
        val width = 400
        val height = 100
        val background = { x: Int -> 200 - 140 * x / (width - 1) }
        val marks = listOf(40 until 52, 348 until 360)
        val gray = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            val level = background(x)
            if (y in 44 until 56 && marks.any { x in it }) level * 7 / 10 else level
        }
        assertTrue(gray[10 * width + 390] < gray[50 * width + 46])

        ScreenContrast.flatten(gray, width, height)

        val brightMark = gray[50 * width + 46]
        val dimMark = gray[50 * width + 354]
        val backgrounds = listOf(gray[10 * width + 10], gray[10 * width + 200], gray[10 * width + 390])
        assertTrue("marks $brightMark and $dimMark should be alike", kotlin.math.abs(brightMark - dimMark) < 30)
        assertTrue("background $backgrounds should be even", backgrounds.max() - backgrounds.min() < 30)
        assertTrue("marks should be darker than the background", maxOf(brightMark, dimMark) < backgrounds.min() - 40)
    }
}
