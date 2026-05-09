package com.kbul.spicycrab.domain.nutrition

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import android.util.Base64

object ImageUtils {

    private const val MAX_LONG_EDGE = 1024
    private const val JPEG_QUALITY = 85

    fun fileToBase64Jpeg(file: File): String {
        val rotated = rotateIfNeeded(file)
        val resized = resize(rotated, MAX_LONG_EDGE)
        val out = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        val bytes = out.toByteArray()
        if (rotated !== resized) resized.recycle()
        rotated.recycle()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun rotateIfNeeded(file: File): Bitmap {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            ?: error("Cannot decode image")
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun resize(bitmap: Bitmap, maxLongEdge: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val longest = maxOf(w, h)
        if (longest <= maxLongEdge) return bitmap
        val scale = maxLongEdge.toFloat() / longest
        val nw = (w * scale).toInt()
        val nh = (h * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, nw, nh, true)
    }
}
