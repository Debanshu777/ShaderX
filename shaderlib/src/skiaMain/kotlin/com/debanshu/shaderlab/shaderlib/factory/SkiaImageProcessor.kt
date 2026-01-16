package com.debanshu.shaderlab.shaderlib.factory

import com.debanshu.shaderlab.shaderlib.effect.BlurEffect
import com.debanshu.shaderlab.shaderlib.effect.NativeEffect
import com.debanshu.shaderlab.shaderlib.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderlib.effect.ShaderEffect
import com.debanshu.shaderlab.shaderlib.result.ShaderError
import com.debanshu.shaderlab.shaderlib.result.ShaderResult
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
internal class SkiaImageProcessorImpl : ImageProcessor {

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

            val imageFilter = createImageFilter(effect, effectWidth, effectHeight)
                ?: return ShaderResult.success(imageBytes) // Return original if filter creation fails

            val surface = Surface.makeRasterN32Premul(imageWidth, imageHeight)
            val canvas = surface.canvas

            val paint = Paint().apply {
                this.imageFilter = imageFilter
            }
            canvas.drawImage(image, 0f, 0f, paint)

            val resultImage = surface.makeImageSnapshot()
            val data = resultImage.encodeToData(EncodedImageFormat.PNG)

            data?.bytes?.let { ShaderResult.success(it) }
                ?: ShaderResult.failure(
                    ShaderError.ProcessingError("Failed to encode result image")
                )
        } catch (e: Exception) {
            ShaderResult.failure(
                ShaderError.ProcessingError("Image processing failed: ${e.message}", e)
            )
        }
    }

    private fun createImageFilter(
        effect: ShaderEffect,
        width: Float,
        height: Float,
    ): ImageFilter? {
        return when (effect) {
            is BlurEffect -> {
                val radiusPx = effect.radius.coerceAtLeast(SkiaShaderFactoryImpl.MIN_BLUR_RADIUS)
                ImageFilter.makeBlur(radiusPx, radiusPx, FilterTileMode.CLAMP)
            }
            is NativeEffect -> null // Other native effects not yet supported
            is RuntimeShaderEffect -> {
                try {
                    val runtimeEffect = RuntimeEffect.makeForShader(effect.shaderSource)
                    val builder = RuntimeShaderBuilder(runtimeEffect)
                    val uniforms = effect.buildUniforms(width, height)
                    SkiaShaderFactoryImpl.applyUniforms(builder, uniforms)
                    ImageFilter.makeRuntimeShader(builder, CONTENT_UNIFORM_NAME, null)
                } catch (e: Exception) {
                    null
                }
            }
            else -> null
        }
    }

    private companion object {
        private const val CONTENT_UNIFORM_NAME = "content"
    }
}
