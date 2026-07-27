package com.debanshu.shaderlab.audiolib

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Android packing: write one magnitude per column into the red channel of a 1-px-tall ARGB bitmap.
 */
public actual fun packMagnitudesToImage(magnitudes: FloatArray): ImageBitmap {
    val width = magnitudes.size.coerceAtLeast(1)
    val bytes = Fft.toBytes(magnitudes)
    val pixels =
        IntArray(width) { x ->
            val v = if (x < bytes.size) bytes[x].toInt() and 0xFF else 0
            Color.argb(255, v, 0, 0)
        }
    val bitmap = Bitmap.createBitmap(width, 1, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, width, 0, 0, width, 1)
    return bitmap.asImageBitmap()
}
