package com.debanshu.shaderlab.shaderx.factory

import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.result.ShaderError
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
            ShaderError.ProcessingError(
                "RenderNode-based image processing requires a hardware-accelerated canvas. " +
                    "Software bitmap export is unsupported on Android. Use the Compose modifier " +
                    "for on-screen effects or export on a supported Skia platform.",
            ),
        )
}

public actual fun ImageProcessor.Companion.create(factory: ShaderFactory): ImageProcessor = AndroidImageProcessor()
