package com.debanshu.shaderlab.audiolib

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/**
 * Skia (iOS / Desktop / Wasm) packing: write one magnitude per column into the red channel of a
 * 1-px-tall RGBA bitmap.
 */
public actual fun packMagnitudesToImage(magnitudes: FloatArray): ImageBitmap {
    val width = magnitudes.size.coerceAtLeast(1)
    val info = ImageInfo(width, 1, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
    val bytes = Fft.toBytes(magnitudes)
    val pixels = ByteArray(width * 4)
    for (x in 0 until width) {
        val v = if (x < bytes.size) bytes[x] else 0
        val o = x * 4
        pixels[o] = v // R = magnitude
        pixels[o + 1] = 0
        pixels[o + 2] = 0
        pixels[o + 3] = 255.toByte() // A (premultiplied: R<=A holds)
    }
    val bitmap = Bitmap()
    bitmap.installPixels(info, pixels, width * 4)
    bitmap.setImmutable()
    return Image.makeFromBitmap(bitmap).toComposeImageBitmap()
}
