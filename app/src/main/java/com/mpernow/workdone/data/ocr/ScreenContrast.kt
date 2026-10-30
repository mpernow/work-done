package com.mpernow.workdone.data.ocr

import kotlin.math.roundToInt

/**
 * Prepares a photo of a monitor screen for text recognition: grey, with uneven lighting (glare,
 * the dim corners of a PM4's LCD) evened out, which brings back faint strokes such as the dots
 * of a colon. Brightness and contrast alone don't help; the recogniser adjusts those itself.
 *
 * Works on plain pixel arrays, row by row, so it doesn't depend on Android.
 */
internal object ScreenContrast {
    /** The grey levels (0–255) of ARGB [pixels]. */
    fun gray(pixels: IntArray): IntArray = IntArray(pixels.size) { i ->
        val pixel = pixels[i]
        val r = pixel shr 16 and 0xFF
        val g = pixel shr 8 and 0xFF
        val b = pixel and 0xFF
        (0.299 * r + 0.587 * g + 0.114 * b).roundToInt()
    }

    /**
     * Divides each of the [gray] levels by the average brightness around it, so the screen's
     * background comes out the same everywhere, then stretches the result to the full range,
     * leaving out the darkest and lightest 1%. Changes [gray] in place.
     */
    fun flatten(gray: IntArray, width: Int, height: Int) {
        val background = background(gray, width, height)
        for (i in gray.indices) {
            gray[i] = (gray[i] * BACKGROUND_LEVEL / maxOf(background[i], 1f)).roundToInt().coerceIn(0, 255)
        }
        stretch(gray)
    }

    /**
     * The average brightness around each pixel: the mean of each block of pixels, about a
     * twenty-fourth of the photo across, blended smoothly between neighbouring blocks. Blocks this
     * size are larger than the screen's characters, so they follow the lighting, not the text.
     */
    private fun background(gray: IntArray, width: Int, height: Int): FloatArray {
        val block = maxOf(16, minOf(width, height) / 24)
        val columns = (width + block - 1) / block
        val rows = (height + block - 1) / block
        val sums = LongArray(columns * rows)
        val counts = IntArray(columns * rows)
        for (y in 0 until height) {
            val rowOffset = y / block * columns
            for (x in 0 until width) {
                sums[rowOffset + x / block] += gray[y * width + x].toLong()
                counts[rowOffset + x / block]++
            }
        }
        val means = FloatArray(sums.size) { sums[it].toFloat() / counts[it] }

        // Blends the four nearest block centres.
        fun position(pixel: Int, blockCount: Int): Triple<Int, Int, Float> {
            val at = ((pixel + 0.5f) / block - 0.5f).coerceIn(0f, (blockCount - 1).toFloat())
            val before = at.toInt()
            return Triple(before, minOf(before + 1, blockCount - 1), at - before)
        }
        val xs = Array(width) { position(it, columns) }
        val result = FloatArray(gray.size)
        for (y in 0 until height) {
            val (top, bottom, ty) = position(y, rows)
            for (x in 0 until width) {
                val (left, right, tx) = xs[x]
                val upper = means[top * columns + left] * (1 - tx) + means[top * columns + right] * tx
                val lower = means[bottom * columns + left] * (1 - tx) + means[bottom * columns + right] * tx
                result[y * width + x] = upper * (1 - ty) + lower * ty
            }
        }
        return result
    }

    private fun stretch(gray: IntArray) {
        val histogram = IntArray(256)
        gray.forEach { histogram[it]++ }
        val cutoff = gray.size / 100
        var low = 0
        var count = histogram[0]
        while (low < 255 && count <= cutoff) count += histogram[++low]
        var high = 255
        count = histogram[255]
        while (high > 0 && count <= cutoff) count += histogram[--high]
        if (high <= low) return
        for (i in gray.indices) gray[i] = ((gray[i] - low) * 255 / (high - low)).coerceIn(0, 255)
    }

    /** What the background is brought to before stretching, leaving room above it for highlights. */
    private const val BACKGROUND_LEVEL = 200f
}
