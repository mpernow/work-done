package com.mpernow.workdone.ui.workout

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.mpernow.workdone.domain.DigitTime

/** Shows typed digits with their time separators: "1524" as "1:52.4", keeping the cursor in step. */
class DigitTimeTransformation(private val format: DigitFormat) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val separators = when (format) {
            DigitFormat.MIN_SEC -> DigitTime.minSecSeparators(digits.length)
            DigitFormat.SPLIT -> DigitTime.splitSeparators(digits.length)
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = offset + separators.count { it.first < offset }

            override fun transformedToOriginal(offset: Int): Int =
                (0..digits.length).last { originalToTransformed(it) <= offset }
        }
        return TransformedText(AnnotatedString(DigitTime.withSeparators(digits, separators)), mapping)
    }

    override fun equals(other: Any?): Boolean = other is DigitTimeTransformation && other.format == format

    override fun hashCode(): Int = format.hashCode()
}
