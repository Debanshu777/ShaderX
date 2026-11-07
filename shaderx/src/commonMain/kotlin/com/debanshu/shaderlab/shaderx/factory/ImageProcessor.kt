package com.debanshu.shaderlab.shaderx.factory

import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.result.ShaderResult

/**
 * Interface for applying shader effects to image data.
 *
 * Use this for offline/batch processing of images, as opposed to
 * real-time rendering with [ShaderFactory].
 *
 * On supported Skia platforms, the processor reuses the provided [ShaderFactory]'s shader cache so that
 * processing N images with the same effect compiles the shader only once.
 * On Skia, keep the factory and processor on one thread. Background exports should create
 * their own factory on the worker and close it there. Sharing requires serializing all factory,
 * processor, and session operations, including cache clearing and disposal.
 *
 * ## Usage
 * ```kotlin
 * val factory = ShaderFactory.create()
 * val processor = ImageProcessor.create(factory)
 * val result = processor.process(imageBytes, GrayscaleEffect())
 * result.onSuccess { processedBytes ->
 *     saveImage(processedBytes)
 * }
 * factory.close()
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
     *
     * **Platform support:** Skia platforms support individual runtime effects and native blur;
     * [com.debanshu.shaderlab.shaderx.effect.CompositeEffect] returns
     * [com.debanshu.shaderlab.shaderx.result.ShaderError.UnsupportedEffect]. Android's current
     * software-bitmap path cannot render its hardware `RenderNode` and returns
     * [com.debanshu.shaderlab.shaderx.result.ShaderError.ProcessingError] before decoding input
     * or compiling shaders; use the Compose modifier for on-screen Android rendering.
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
 * Creates the platform-specific [ImageProcessor] backed by [factory].
 *
 * On Skia, the processor shares [factory]'s compiled-shader cache, so a shader compiled by the
 * factory's [ShaderFactory.createRenderEffect] path won't be recompiled by the processor
 * (and vice versa).
 *
 * @param factory The [ShaderFactory] whose cache the processor will share.
 */
public expect fun ImageProcessor.Companion.create(factory: ShaderFactory): ImageProcessor
