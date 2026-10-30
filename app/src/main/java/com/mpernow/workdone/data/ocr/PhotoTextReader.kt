package com.mpernow.workdone.data.ocr

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mpernow.workdone.domain.OcrWord
import com.mpernow.workdone.domain.PhotoText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Recognises text in a photo on the device with ML Kit, returning each word and where it is. The
 * photo is read twice, with its lighting evened out (see [ScreenContrast]) and as it was taken:
 * each version gets some characters right that the other misses.
 */
class PhotoTextReader(private val context: Context) {
    suspend fun read(photo: Uri): PhotoText {
        val upright = withContext(Dispatchers.IO) { decodeUpright(photo) }
        val original = recognize(upright, "as taken")
        val evened = withContext(Dispatchers.Default) { evenedOut(upright).also { upright.recycle() } }
        return PhotoText(listOf(recognize(evened, "lighting evened out").also { evened.recycle() }, original))
    }

    private suspend fun recognize(image: Bitmap, version: String): List<OcrWord> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = suspendCancellableCoroutine { continuation ->
                recognizer.process(InputImage.fromBitmap(image, 0))
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resumeWithException(it) }
            }
            val words = text.textBlocks.flatMap { it.lines }.flatMap { it.elements }.mapNotNull { element ->
                element.boundingBox?.let { OcrWord(element.text, it.left, it.top, it.right, it.bottom) }
            }
            if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                // Debug builds log what was recognised, to turn real photos into parser tests.
                Log.d(TAG, "# $version")
                words.forEach { Log.d(TAG, "${it.text}\t${it.left}\t${it.top}\t${it.right}\t${it.bottom}") }
            }
            return words
        } finally {
            recognizer.close()
        }
    }

    /** The photo turned upright as its EXIF orientation says, so both readings' boxes are in upright coordinates. */
    private fun decodeUpright(photo: Uri): Bitmap {
        val resolver = context.contentResolver
        val orientation = resolver.openInputStream(photo)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val decoded = resolver.openInputStream(photo)?.use { BitmapFactory.decodeStream(it) }
            ?: throw IOException("The photo couldn't be opened")
        if (rotation == 0f) return decoded
        val matrix = Matrix().apply { postRotate(rotation) }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, false).also { decoded.recycle() }
    }

    /** A grey copy of [photo] with its lighting evened out. */
    private fun evenedOut(photo: Bitmap): Bitmap {
        val width = photo.width
        val height = photo.height
        val pixels = IntArray(width * height)
        photo.getPixels(pixels, 0, width, 0, 0, width, height)
        val gray = ScreenContrast.gray(pixels)
        ScreenContrast.flatten(gray, width, height)
        for (i in gray.indices) pixels[i] = (0xFF shl 24) or (gray[i] shl 16) or (gray[i] shl 8) or gray[i]
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private companion object {
        const val TAG = "Pm5Ocr"
    }
}
