package com.debanshu.shaderlab.shaderx.factory

import com.debanshu.shaderlab.shaderx.ShaderConstants
import com.debanshu.shaderlab.shaderx.effect.BlurEffect
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
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Surface

/**
 * Skia-based implementation of [ImageProcessor] for iOS and Desktop platforms.
 */
internal class SkiaImageProcessor : ImageProcessor {
    override fun process(
        imageBytes: ByteArray,
        effect: ShaderEffect,
        width: Float,
        height: Float,
    ): ShaderResult<ByteArray> {
        return try {
            val image = Image.makeFromEncoded(imageBytes)
            val imageWidth = image.width
            val imageHeight = image.height

            val effectWidth = if (width > 0) width else imageWidth.toFloat()
            val effectHeight = if (height > 0) height else imageHeight.toFloat()

            val imageFilterResult = createImageFilter(effect, effectWidth, effectHeight)
            val imageFilter = when (imageFilterResult) {
                is ShaderResult.Success -> imageFilterResult.value
                is ShaderResult.Failure -> return ShaderResult.failure(imageFilterResult.error)
            }

            val surface = Surface.makeRasterN32Premul(imageWidth, imageHeight)
            val canvas = surface.canvas

            val paint =
                Paint().apply {
                    this.imageFilter = imageFilter
                }
            canvas.drawImage(image, 0f, 0f, paint)

            val resultImage = surface.makeImageSnapshot()
            val data = resultImage.encodeToData(EncodedImageFormat.PNG)

            data?.bytes?.let { ShaderResult.success(it) }
                ?: ShaderResult.failure(
                    ShaderError.ProcessingError("Failed to encode result image"),
                )
        } catch (e: Exception) {
            ShaderResult.failure(
                ShaderError.ProcessingError("Image processing failed", e),
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
                    val runtimeEffect = RuntimeEffect.makeForShader(effect.shaderSource)
                    val builder = RuntimeShaderBuilder(runtimeEffect)
                    val uniforms = effect.buildUniforms(width, height)
                    SkiaShaderFactory.applyUniforms(builder, uniforms)
                    val filter =
                        ImageFilter.makeRuntimeShader(
                            builder,
                            ShaderConstants.CONTENT_UNIFORM_NAME,
                            null,
                        )
                    ShaderResult.success(filter)
                } catch (e: Exception) {
                    ShaderResult.failure(
                        ShaderError.CompilationError(
                            "Failed to compile shader for image processing",
                            effect.shaderSource,
                        ),
                    )
                }
            }

            else -> {
                ShaderResult.failure(
                    ShaderError.UnsupportedEffect(
                        "Effect type not supported for offline processing: ${effect::class.simpleName}",
                        effect.id,
                    ),
                )
            }
        }
}
