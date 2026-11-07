package com.debanshu.shaderlab.imagelib

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.impl.use

actual fun decodeImageBytes(bytes: ByteArray, maxDimension: Int): ImageBitmap? {
    require(maxDimension > 0)
    return try {
        Image.makeFromEncoded(bytes).use { image ->
            val size = previewSize(image.width, image.height, maxDimension)
            val bitmap = Bitmap()
            var transferred = false
            try {
                check(bitmap.allocPixels(ImageInfo(size.width, size.height, ColorType.N32, ColorAlphaType.PREMUL, ColorSpace.sRGB)))
                bitmap.peekPixels()!!.use { pixels ->
                    check(image.scalePixels(pixels, SamplingMode.LINEAR, cache = false))
                }
                bitmap.setImmutable()
                // Compose owns the returned Bitmap. Only temporary Image/Pixmap wrappers close here.
                bitmap.asComposeImageBitmap().also { transferred = true }
            } finally {
                if (!transferred) bitmap.close()
            }
        }
    } catch (_: Exception) {
        null
    }
}
