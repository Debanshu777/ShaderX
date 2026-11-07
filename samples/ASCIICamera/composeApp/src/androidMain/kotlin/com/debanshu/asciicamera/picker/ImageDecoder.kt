package com.debanshu.asciicamera.picker

import android.graphics.ImageDecoder
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import java.nio.ByteBuffer
import kotlin.math.roundToInt

fun decodeImageBytes(bytes: ByteArray?): Painter? {
    if (bytes == null || bytes.isEmpty()) return null
    return try {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            // Bound the display bitmap; a phone photo can otherwise consume tens of MB per preview.
            val scale = minOf(1.0, 2048.0 / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize(
                (info.size.width * scale).roundToInt().coerceAtLeast(1),
                (info.size.height * scale).roundToInt().coerceAtLeast(1),
            )
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        BitmapPainter(bitmap.asImageBitmap())
    } catch (_: Exception) {
        null
    }
}
