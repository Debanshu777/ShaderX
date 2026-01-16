package com.debanshu.shaderlab.shaderlib.factory

import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.debanshu.shaderlab.shaderlib.effect.BlurEffect
import com.debanshu.shaderlab.shaderlib.effect.NativeEffect
import com.debanshu.shaderlab.shaderlib.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderlib.effect.ShaderEffect
import com.debanshu.shaderlab.shaderlib.result.ShaderError
import com.debanshu.shaderlab.shaderlib.result.ShaderResult
import com.debanshu.shaderlab.shaderlib.uniform.FloatUniform
import com.debanshu.shaderlab.shaderlib.uniform.IntUniform
import com.debanshu.shaderlab.shaderlib.uniform.Uniform
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/**
 * Skia-based implementation of [ShaderFactory] for iOS and Desktop platforms.
 *
 * Uses Skia's RuntimeEffect for custom shader compilation and ImageFilter for effects.
 */
internal class SkiaShaderFactoryImpl : ShaderFactory {

    override fun createRenderEffect(
        effect: ShaderEffect,
        width: Float,
        height: Float,
    ): ShaderResult<RenderEffect> {
        return when (effect) {
            is NativeEffect -> createNativeEffect(effect)
            is RuntimeShaderEffect -> createRuntimeShaderEffect(effect, width, height)
            else -> ShaderResult.failure(
                ShaderError.UnsupportedEffect(
                    "Unknown effect type: ${effect::class.simpleName}",
                    effect.id
                )
            )
        }
    }

    private fun createNativeEffect(effect: NativeEffect): ShaderResult<RenderEffect> {
        return when (effect) {
            is BlurEffect -> createBlurEffect(effect.radius)
            else -> ShaderResult.failure(
                ShaderError.UnsupportedEffect(
                    "Unsupported native effect: ${effect::class.simpleName}",
                    effect.id
                )
            )
        }
    }

    private fun createBlurEffect(radius: Float): ShaderResult<RenderEffect> {
        return ShaderResult.runCatching {
            val radiusPx = radius.coerceAtLeast(MIN_BLUR_RADIUS)
            ImageFilter
                .makeBlur(radiusPx, radiusPx, FilterTileMode.CLAMP)
                .asComposeRenderEffect()
        }
    }

    private fun createRuntimeShaderEffect(
        effect: RuntimeShaderEffect,
        width: Float,
        height: Float,
    ): ShaderResult<RenderEffect> {
        return try {
            val runtimeEffect = RuntimeEffect.makeForShader(effect.shaderSource)
            val builder = RuntimeShaderBuilder(runtimeEffect)
            val uniforms = effect.buildUniforms(width, height)
            applyUniforms(builder, uniforms)

            ShaderResult.success(
                ImageFilter
                    .makeRuntimeShader(builder, CONTENT_UNIFORM_NAME, null)
                    .asComposeRenderEffect()
            )
        } catch (e: Exception) {
            ShaderResult.failure(
                ShaderError.CompilationError(
                    "Failed to compile shader: ${e.message}",
                    effect.shaderSource
                )
            )
        }
    }

    override fun isSupported(): Boolean = true

    internal companion object {
        private const val CONTENT_UNIFORM_NAME = "content"
        internal const val MIN_BLUR_RADIUS = 0.1f

        /**
         * Applies uniforms to a Skia RuntimeShaderBuilder.
         *
         * Shared between [SkiaShaderFactoryImpl] and [SkiaImageProcessorImpl].
         */
        internal fun applyUniforms(builder: RuntimeShaderBuilder, uniforms: List<Uniform>) {
            uniforms.forEach { uniform ->
                when (uniform) {
                    is FloatUniform -> builder.uniform(uniform.name, uniform.values)
                    is IntUniform -> builder.uniform(uniform.name, uniform.values.first())
                }
            }
        }
    }
}
