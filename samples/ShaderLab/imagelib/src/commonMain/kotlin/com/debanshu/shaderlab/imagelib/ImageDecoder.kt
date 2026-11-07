package com.debanshu.shaderlab.imagelib

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Preview decode only. Original encoded bytes remain available for full-resolution export. */
expect fun decodeImageBytes(bytes: ByteArray, maxDimension: Int = 2048): ImageBitmap?

internal fun previewSize(width: Int, height: Int, maxDimension: Int): IntSize {
    require(width > 0 && height > 0 && maxDimension > 0)
    val scale = minOf(1.0, maxDimension.toDouble() / maxOf(width, height))
    return IntSize(
        (width * scale).roundToInt().coerceAtLeast(1),
        (height * scale).roundToInt().coerceAtLeast(1),
    )
}

data class DecodedImageState(
    val bitmap: ImageBitmap? = null,
    val isLoading: Boolean = false,
)

/** Decode once per byte array and size, outside composition and off the UI thread where supported. */
@Composable
fun rememberDecodedImage(bytes: ByteArray?, maxDimension: Int = 2048): DecodedImageState {
    require(maxDimension > 0)
    var state by remember(bytes, maxDimension) { mutableStateOf(DecodedImageState(isLoading = bytes != null)) }
    LaunchedEffect(bytes, maxDimension) {
        state =
            DecodedImageState(
                bitmap = bytes?.let { withContext(Dispatchers.Default) { decodeImageBytes(it, maxDimension) } },
            )
    }
    return state
}
