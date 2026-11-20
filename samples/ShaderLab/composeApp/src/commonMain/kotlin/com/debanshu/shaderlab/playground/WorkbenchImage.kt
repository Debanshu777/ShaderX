package com.debanshu.shaderlab.playground

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import org.jetbrains.compose.resources.decodeToImageBitmap

/** PNG-only intake preflight before native decode; not an execution or allocation sandbox. */
fun validateWorkbenchPng(bytes: ByteArray) {
    require(bytes.size in 33..1_048_576) { "PNG must be at most 1 MiB" }
    val signature = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
    require(bytes.take(8).toByteArray().contentEquals(signature) && bytes.copyOfRange(12, 16).decodeToString() == "IHDR") { "Choose a PNG with an IHDR header" }
    fun dimension(offset: Int): Long = (offset..offset + 3).fold(0L) { n, i -> (n shl 8) or (bytes[i].toLong() and 255) }
    require(dimension(16) in 1..256 && dimension(20) in 1..256) { "PNG dimensions must be 1–256 pixels on each side" }
}

/** Browser upload is bounded before reading; other hosts offer the bundled input. */
@Composable
expect fun rememberWorkbenchImagePicker(): (((Result<ByteArray>) -> Unit) -> Unit)?

/** Input and rendered PNGs are bounded before Compose's native resource decoder is invoked. */
fun decodeWorkbenchImage(bytes: ByteArray): ImageBitmap {
    validateWorkbenchPng(bytes)
    return bytes.decodeToImageBitmap().also { require(it.width in 1..256 && it.height in 1..256) }
}
