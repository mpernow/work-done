package com.mpernow.workdone.domain

/**
 * Times typed as digits only, so the number keyboard works: the separators are added for
 * display. "130" is 1:30 (m:ss), and a 500 m split "1524" is 1:52.4 (m:ss.t).
 */
object DigitTime {
    /** m:ss digits to seconds; the last two digits are seconds and must be under 60. */
    fun parseMinSec(digits: String): Int? {
        if (digits.isEmpty() || !digits.all(Char::isDigit)) return null
        val seconds = digits.takeLast(2).toInt()
        val minutes = digits.dropLast(2).ifEmpty { "0" }.toInt()
        return if (seconds < 60) minutes * 60 + seconds else null
    }

    /** m:ss.t digits to seconds; the last digit is tenths, the two before it seconds (under 60). */
    fun parseSplit(digits: String): Double? {
        if (digits.isEmpty() || !digits.all(Char::isDigit)) return null
        val tenths = digits.last().digitToInt()
        val rest = digits.dropLast(1)
        val seconds = rest.takeLast(2).ifEmpty { "0" }.toInt()
        val minutes = rest.dropLast(2).ifEmpty { "0" }.toInt()
        return if (seconds < 60) minutes * 60 + seconds + tenths / 10.0 else null
    }

    fun minSecDigits(totalSeconds: Int): String = "%d%02d".format(totalSeconds / 60, totalSeconds % 60)

    fun splitDigits(seconds: Double): String {
        val tenths = Math.round(seconds * 10).toInt()
        return "%d%02d%d".format(tenths / 600, tenths % 600 / 10, tenths % 10)
    }

    /** "1:52.4". */
    fun formatSplit(seconds: Double): String {
        val tenths = Math.round(seconds * 10).toInt()
        return "%d:%02d.%d".format(tenths / 600, tenths % 600 / 10, tenths % 10)
    }

    /** Where to show separators in [length] typed m:ss digits: (index in the digits, character). */
    fun minSecSeparators(length: Int): List<Pair<Int, Char>> =
        if (length > 2) listOf(length - 2 to ':') else emptyList()

    /** Separators for m:ss.t digits: the point before the last digit, the colon before the seconds. */
    fun splitSeparators(length: Int): List<Pair<Int, Char>> = buildList {
        if (length >= 4) add(length - 3 to ':')
        if (length >= 2) add(length - 1 to '.')
    }

    /** Inserts [separators] (positions in the digits) into [digits]. */
    fun withSeparators(digits: String, separators: List<Pair<Int, Char>>): String = buildString {
        digits.forEachIndexed { index, digit ->
            separators.filter { it.first == index }.forEach { append(it.second) }
            append(digit)
        }
    }
}
