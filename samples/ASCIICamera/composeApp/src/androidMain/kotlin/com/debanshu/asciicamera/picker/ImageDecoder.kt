package com.debanshu.asciicamera.picker

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter

fun decodeImageBytes(bytes: ByteArray?): Painter? {
    if (bytes == null || bytes.isEmpty()) return null
    return try {
        val bitmap =
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return null
        BitmapPainter(bitmap.asImageBitmap())
    } catch (_: Exception) {
        null
    }
}
