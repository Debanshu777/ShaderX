package com.debanshu.shaderlab.imagelib

import android.graphics.ImageDecoder
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.nio.ByteBuffer

actual fun decodeImageBytes(bytes: ByteArray, maxDimension: Int): ImageBitmap? {
    require(maxDimension > 0)
    return try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            val size = previewSize(info.size.width, info.size.height, maxDimension)
            decoder.setTargetSize(size.width, size.height)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}
