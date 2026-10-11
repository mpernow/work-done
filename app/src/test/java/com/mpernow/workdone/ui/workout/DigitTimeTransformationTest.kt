package com.mpernow.workdone.ui.workout

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

class DigitTimeTransformationTest {
    @Test
    fun cursorStaysNextToTheSameDigit() {
        val transformed = DigitTimeTransformation(DigitFormat.SPLIT).filter(AnnotatedString("1524"))
        assertEquals("1:52.4", transformed.text.text)
        val mapping = transformed.offsetMapping
        // Digit offsets 0..4 sit at "|1:52.4", "1|:52.4" … "1:52.4|".
        assertEquals(listOf(0, 1, 3, 4, 6), (0..4).map(mapping::originalToTransformed))
        // Every display position maps back to a digit offset, and round-trips where it can.
        assertEquals(listOf(0, 1, 1, 2, 3, 3, 4), (0..6).map(mapping::transformedToOriginal))
    }
}
