package com.debanshu.shaderlab.shaderx.factory

import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.result.ShaderResult

/**
 * Android's software-bitmap export path is unsupported: a hardware RenderNode cannot be
 * drawn into a software Canvas. Reject before decoding, shader compilation, or bitmap allocation.
 * The factory parameter remains part of the multiplatform creation API for Skia cache sharing.
 */
internal class AndroidImageProcessor : ImageProcessor {
    override fun process(
        imageBytes: ByteArray,
        effect: ShaderEffect,
        width: Float,
        height: Float,
    ): ShaderResult<ByteArray> =
        ShaderResult.failure(
            builtInSupport(33, ShaderOperation.IMAGE_EXPORT, null)
                .asError(ShaderOperation.IMAGE_EXPORT, null),
        )
}

public actual fun ImageProcessor.Companion.create(factory: ShaderFactory): ImageProcessor = AndroidImageProcessor()
