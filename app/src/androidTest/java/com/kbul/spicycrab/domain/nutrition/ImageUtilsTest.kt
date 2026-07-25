package com.kbul.spicycrab.domain.nutrition

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageUtilsTest {

    @Test
    fun downsampledDecodeStillProducesA1024LongEdgeJpeg() {
        val file = writeJpeg(width = 4000, height = 3000)

        val bytes = Base64.decode(ImageUtils.fileToBase64Jpeg(file), Base64.NO_WRAP)
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        assertEquals(1024, decoded.width)
        assertEquals(768, decoded.height)
    }

    @Test
    fun imagesSmallerThanTheTargetAreLeftAlone() {
        val file = writeJpeg(width = 640, height = 480)

        val bytes = Base64.decode(ImageUtils.fileToBase64Jpeg(file), Base64.NO_WRAP)
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        assertEquals(640, decoded.width)
        assertEquals(480, decoded.height)
    }

    private fun writeJpeg(width: Int, height: Int): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "imageutils_${width}x$height.jpg")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        return file
    }
}
