package com.debanshu.shaderlab.shaderx.factory

import com.debanshu.shaderlab.shaderx.ShaderConstants
import com.debanshu.shaderlab.shaderx.effect.BlurEffect
import com.debanshu.shaderlab.shaderx.effect.CompositeEffect
import com.debanshu.shaderlab.shaderx.effect.NativeEffect
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Surface
import org.jetbrains.skia.impl.use

/**
 * Skia-based implementation of [ImageProcessor] for iOS and Desktop platforms.
 *
 * Shader compilation is delegated to the provided [SkiaShaderFactory] so that
 * the compiled RuntimeEffect cache is shared with the Compose rendering path.
 *
 * All per-call Skia [Surface], [Paint], and [Image] objects are closed after use
 * to release their off-heap native memory.
 */
internal class SkiaImageProcessor(
    private val factory: SkiaShaderFactory,
) : ImageProcessor {
    override fun process(
        imageBytes: ByteArray,
        effect: ShaderEffect,
        width: Float,
        height: Float,
    ): ShaderResult<ByteArray> {
        return try {
            Image.makeFromEncoded(imageBytes).use { image ->
                val effectWidth = if (width > 0) width else image.width.toFloat()
                val effectHeight = if (height > 0) height else image.height.toFloat()

                createImageFilter(effect, effectWidth, effectHeight).flatMap { filter ->
                    filter.use {
                        Surface.makeRasterN32Premul(image.width, image.height).use { surface ->
                            Paint().use { paint ->
                                paint.imageFilter = filter
                                surface.canvas.drawImage(image, 0f, 0f, paint)
                            }
                            surface.makeImageSnapshot().use { snapshot ->
                                val data = snapshot.encodeToData(EncodedImageFormat.PNG)
                                if (data == null) {
                                    ShaderResult.failure(
                                        ShaderError.ProcessingError("Failed to encode result image"),
                                    )
                                } else {
                                    data.use { ShaderResult.success(it.bytes) }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            ShaderResult.failure(
                ShaderError.ProcessingError("Image processing failed: ${e.message}", e),
            )
        }
    }

    private fun createImageFilter(
        effect: ShaderEffect,
        width: Float,
        height: Float,
    ): ShaderResult<ImageFilter> =
        when (effect) {
            is BlurEffect -> {
                ShaderResult.runCatching {
                    val radiusPx = effect.radius.coerceAtLeast(ShaderConstants.MIN_BLUR_RADIUS)
                    ImageFilter.makeBlur(radiusPx, radiusPx, FilterTileMode.CLAMP)
                }
            }

            is NativeEffect -> {
                ShaderResult.failure(
                    ShaderError.UnsupportedEffect(
                        "Native effect not supported for offline processing: ${effect::class.simpleName}",
                        effect.id,
                    ),
                )
            }

            is RuntimeShaderEffect -> {
                try {
                    // Reuse the factory's cached RuntimeEffect — no recompilation
                    val runtimeEffect = factory.getOrCreateEffect(effect.shaderSource)
                    RuntimeShaderBuilder(runtimeEffect).use { builder ->
                        SkiaShaderFactory.applyUniforms(builder, effect.buildUniforms(width, height))
                        ShaderResult.success(
                            ImageFilter.makeRuntimeShader(
                                builder,
                                ShaderConstants.CONTENT_UNIFORM_NAME,
                                null,
                            ),
                        )
                    }
                } catch (e: Exception) {
                    ShaderResult.failure(
                        ShaderError.CompilationError(
                            "Failed to compile shader for image processing: ${e.message}",
                            effect.shaderSource,
                        ),
                    )
                }
            }

            is CompositeEffect -> {
                ShaderResult.failure(
                ShaderError.UnsupportedEffect(
                    "Composite effects are not supported for offline image processing",
                    effect.id,
                ),
            )
            }
        }
}
