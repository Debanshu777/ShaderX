package com.debanshu.shaderlab.shaderx.factory

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import com.debanshu.shaderlab.shaderx.ShaderConstants
import com.debanshu.shaderlab.shaderx.effect.BlurEffect
import com.debanshu.shaderlab.shaderx.effect.NativeEffect
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import java.io.ByteArrayOutputStream

/**
 * Android implementation of [ImageProcessor] for applying shader effects to images.
 */
internal class AndroidImageProcessor : ImageProcessor {

    override fun process(
        imageBytes: ByteArray,
        effect: ShaderEffect,
        width: Float,
        height: Float,
    ): ShaderResult<ByteArray> {
        return try {
            val sourceBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                ?: return ShaderResult.failure(
                    ShaderError.ProcessingError("Failed to decode image bytes")
                )

            val imageWidth = sourceBitmap.width
            val imageHeight = sourceBitmap.height
            val effectWidth = if (width > 0) width else imageWidth.toFloat()
            val effectHeight = if (height > 0) height else imageHeight.toFloat()

            val resultBitmap = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888)

            val renderEffect = createRenderEffect(effect, effectWidth, effectHeight)
                ?: return ShaderResult.failure(
                    ShaderError.ProcessingError("Failed to create render effect for ${effect.id}")
                )

            val success = applyEffectToBitmap(sourceBitmap, resultBitmap, renderEffect)

            if (!success) {
                sourceBitmap.recycle()
                resultBitmap.recycle()
                return ShaderResult.success(imageBytes) // Return original on failure
            }

            val outputStream = ByteArrayOutputStream()
            resultBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)

            sourceBitmap.recycle()
            resultBitmap.recycle()

            ShaderResult.success(outputStream.toByteArray())
        } catch (e: Exception) {
            ShaderResult.failure(
                ShaderError.ProcessingError("Image processing failed: ${e.message}", e)
            )
        }
    }

    private fun createRenderEffect(
        effect: ShaderEffect,
        width: Float,
        height: Float,
    ): RenderEffect? {
        return when (effect) {
            is BlurEffect -> {
                val radiusPx = effect.radius.coerceAtLeast(ShaderConstants.MIN_BLUR_RADIUS)
                RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
            }
            is NativeEffect -> null // Other native effects not yet supported
            is RuntimeShaderEffect -> {
                try {
                    val shader = RuntimeShader(effect.shaderSource)
                    val uniforms = effect.buildUniforms(width, height)
                    AndroidShaderFactory.applyUniforms(shader, uniforms)
                    RenderEffect.createRuntimeShaderEffect(shader, ShaderConstants.CONTENT_UNIFORM_NAME)
                } catch (e: Exception) {
                    null
                }
            }
            else -> null
        }
    }

    private fun applyEffectToBitmap(
        source: Bitmap,
        result: Bitmap,
        renderEffect: RenderEffect,
    ): Boolean {
        return try {
            val node = RenderNode("effect")
            node.setPosition(0, 0, source.width, source.height)
            node.setRenderEffect(renderEffect)

            val canvas = node.beginRecording()
            canvas.drawBitmap(source, 0f, 0f, null)
            node.endRecording()

            val resultCanvas = Canvas(result)
            if (resultCanvas.isHardwareAccelerated) {
                resultCanvas.drawRenderNode(node)
            } else {
                resultCanvas.drawBitmap(source, 0f, 0f, null)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

}

public actual fun ImageProcessor.Companion.create(): ImageProcessor = AndroidImageProcessor()
