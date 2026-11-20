package com.debanshu.shaderlab.shaderx.factory

import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import kotlinx.coroutines.CancellationException
import org.jetbrains.skia.*
import org.jetbrains.skia.impl.use

/** Eager decode and two raster stages keep destination-pixel coordinates independent of resampling. */
internal fun exportSkiaPng(
    bytes: ByteArray,
    effect: ShaderEffect,
    options: ImageExportOptions,
    createFilter: (ShaderEffect, Float, Float) -> ShaderResult<ImageFilter>,
): ShaderResult<ByteArray> {
    if (bytes.isEmpty() || bytes.size > MAX_EXPORT_INPUT_BYTES) {
        return ShaderResult.failure(ShaderError.ProcessingError("Encoded source must be 1..8388608 bytes"))
    }
    return try {
        // This Skia PNG codec can expose only the default APNG image (frameCount == 1).
        // Reject the animation control chunk explicitly instead of silently exporting that frame.
        require(!hasPngAnimationControl(bytes)) { "Animated source images are unsupported" }
        Data.makeFromBytes(bytes).use { data ->
            Codec.makeFromData(data).use { codec ->
                val source = codec.size
                require(validExportDimensions(source.x, source.y)) { "Decoded source exceeds export pixel limits" }
                require(codec.encodedImageFormat == EncodedImageFormat.PNG || codec.encodedImageFormat == EncodedImageFormat.JPEG) {
                    "Only PNG/JPEG source images are supported"
                }
                require(codec.encodedOrigin == EncodedOrigin.TOP_LEFT) { "Normalize source orientation before export" }
                require(codec.frameCount <= 1) { "Animated source images are unsupported" }
                val outputInfo = ImageInfo(options.pixelWidth, options.pixelHeight, ColorType.RGBA_8888, ColorAlphaType.PREMUL, ColorSpace.sRGB)
                Bitmap().use { bitmap ->
                    check(bitmap.allocPixels(ImageInfo(source.x, source.y, ColorType.RGBA_8888, ColorAlphaType.PREMUL, ColorSpace.sRGB))) {
                        "Failed to allocate decoded source"
                    }
                    codec.readPixels(bitmap)
                    Image.makeFromBitmap(bitmap).use { image ->
                        Surface.makeRaster(outputInfo).use { resized ->
                            resized.canvas.clear(Color.TRANSPARENT)
                            resized.canvas.drawImageRect(image, Rect.makeWH(source.x.toFloat(), source.y.toFloat()),
                                Rect.makeWH(options.pixelWidth.toFloat(), options.pixelHeight.toFloat()), SamplingMode.LINEAR, null, true)
                            createFilter(effect, options.coordinateWidth ?: options.pixelWidth.toFloat(),
                                options.coordinateHeight ?: options.pixelHeight.toFloat()).flatMap { filter ->
                                filter.use {
                                    resized.makeImageSnapshot().use { resizedImage ->
                                        Surface.makeRaster(outputInfo).use { output ->
                                            output.canvas.clear(Color.TRANSPARENT)
                                            Paint().use { paint ->
                                                paint.imageFilter = filter
                                                output.canvas.drawImage(resizedImage, 0f, 0f, paint)
                                            }
                                            output.makeImageSnapshot().use { snapshot ->
                                                val encoded = snapshot.encodeToData(EncodedImageFormat.PNG)
                                                if (encoded == null) ShaderResult.failure(ShaderError.ProcessingError("PNG encoding failed"))
                                                else encoded.use { ShaderResult.success(it.bytes) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        ShaderResult.failure(ShaderError.ProcessingError("PNG export failed: ${error.message}", error))
    }
}

/** Walk actual PNG chunks; lengths use Long so hostile unsigned lengths cannot wrap offsets. */
private fun hasPngAnimationControl(bytes: ByteArray): Boolean {
    val signature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
    if (bytes.size < 8 || !signature.indices.all { bytes[it] == signature[it] }) return false
    var offset = 8
    while (offset.toLong() + 12 <= bytes.size) {
        var length = 0L
        repeat(4) { length = (length shl 8) or (bytes[offset + it].toLong() and 255) }
        val end = offset.toLong() + 12 + length
        if (end > bytes.size) return false // Eager codec decode reports malformed/truncated input.
        if (bytes[offset + 4] == 97.toByte() && bytes[offset + 5] == 99.toByte() &&
            bytes[offset + 6] == 84.toByte() && bytes[offset + 7] == 76.toByte()) return true
        offset = end.toInt()
    }
    return false
}
