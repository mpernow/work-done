package com.mpernow.workdone.data.ocr

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mpernow.workdone.domain.OcrWord
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Recognises text in a photo on the device with ML Kit, returning each word and where it is. */
class PhotoTextReader(private val context: Context) {
    suspend fun read(photo: Uri): List<OcrWord> {
        // Applies the photo's EXIF rotation, so boxes are in upright coordinates.
        val image = InputImage.fromFilePath(context, photo)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resumeWithException(it) }
            }
            val words = text.textBlocks.flatMap { it.lines }.flatMap { it.elements }.mapNotNull { element ->
                element.boundingBox?.let { OcrWord(element.text, it.left, it.top, it.right, it.bottom) }
            }
            if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                // Debug builds log what was recognised, to turn real photos into parser tests.
                words.forEach { Log.d(TAG, "${it.text}\t${it.left}\t${it.top}\t${it.right}\t${it.bottom}") }
            }
            return words
        } finally {
            recognizer.close()
        }
    }

    private companion object {
        const val TAG = "Pm5Ocr"
    }
}
