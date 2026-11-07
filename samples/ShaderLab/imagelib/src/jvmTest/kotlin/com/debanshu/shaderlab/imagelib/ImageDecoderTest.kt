package com.debanshu.shaderlab.imagelib

import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import org.jetbrains.skia.impl.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ImageDecoderTest {
    private fun png(width: Int, height: Int, color: Int): ByteArray =
        Surface.makeRasterN32Premul(width, height).use { surface ->
            surface.canvas.clear(color)
            surface.makeImageSnapshot().use { image ->
                image.encodeToData(EncodedImageFormat.PNG)!!.use { it.bytes }
            }
        }

    @Test
    fun largePhotoRetainsOnlyPreviewPixelsAndKeepsOriginalBytes() {
        val bytes = png(4000, 3000, 0xffe04020.toInt())
        val original = bytes.copyOf()
        val preview = assertNotNull(decodeImageBytes(bytes, 200))
        try {
            assertEquals(200, preview.width)
            assertEquals(150, preview.height)
            val pixel = IntArray(1)
            preview.readPixels(pixel, 100, 75, 1, 1)
            assertEquals(0xffe04020.toInt(), pixel[0])
            kotlin.test.assertContentEquals(original, bytes)
        } finally {
            preview.asSkiaBitmap().close()
        }
    }

    @Test
    fun retainsTransparencyAndDoesNotUpscale() {
        val preview = assertNotNull(decodeImageBytes(png(16, 12, 0x80402010.toInt()), 2048))
        try {
            assertEquals(16, preview.width)
            assertEquals(12, preview.height)
            val pixel = IntArray(1)
            preview.readPixels(pixel, 8, 6, 1, 1)
            assertEquals(0x80402010.toInt(), pixel[0])
        } finally {
            preview.asSkiaBitmap().close()
        }
    }

    @Test
    fun handlesInvalidImagesAndRejectsInvalidLimit() {
        assertNull(decodeImageBytes(byteArrayOf()))
        assertNull(decodeImageBytes(byteArrayOf(1, 2, 3), 128))
        assertFailsWith<IllegalArgumentException> { decodeImageBytes(byteArrayOf(), 0) }
    }
}
