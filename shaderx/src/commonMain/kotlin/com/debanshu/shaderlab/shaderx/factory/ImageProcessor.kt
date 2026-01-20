package com.debanshu.shaderlab.shaderx.factory

import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.result.ShaderResult

/**
 * Interface for applying shader effects to image data.
 *
 * Use this for offline/batch processing of images, as opposed to
 * real-time rendering with [ShaderFactory].
 *
 * ## Usage
 * ```kotlin
 * val processor = ImageProcessor.create()
 * val result = processor.process(imageBytes, effect)
 * result.onSuccess { processedBytes ->
 *     saveImage(processedBytes)
 * }
 * ```
 */
public interface ImageProcessor {
    /**
     * Applies a shader effect to image data.
     *
     * @param imageBytes The input image as a byte array (PNG, JPEG, etc.)
     * @param effect The effect to apply
     * @param width Optional width override for uniform calculations (uses image width if 0)
     * @param height Optional height override for uniform calculations (uses image height if 0)
     * @return [ShaderResult] containing the processed image bytes or error information
     */
    public fun process(
        imageBytes: ByteArray,
        effect: ShaderEffect,
        width: Float = 0f,
        height: Float = 0f,
    ): ShaderResult<ByteArray>

    public companion object
}

/**
 * Creates the platform-specific [ImageProcessor] instance.
 *
 * This is an expect function that each platform implements.
 */
public expect fun ImageProcessor.Companion.create(): ImageProcessor
