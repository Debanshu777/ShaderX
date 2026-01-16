package com.debanshu.shaderlab.shaderlib.factory

import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
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
import android.graphics.RenderEffect as AndroidRenderEffect

/**
 * Android implementation of [ShaderFactory] using AGSL (Android Graphics Shading Language).
 *
 * Requires Android 13 (API 33) or higher for RuntimeShader support.
 */
internal class AndroidShaderFactory : ShaderFactory {

    override fun createRenderEffect(
        effect: ShaderEffect,
        width: Float,
        height: Float,
    ): ShaderResult<RenderEffect> {
        if (!isSupported()) {
            return ShaderResult.failure(
                ShaderError.PlatformNotSupported(
                    "RuntimeShader requires Android 13 (API 33) or higher. Current: ${Build.VERSION.SDK_INT}"
                )
            )
        }

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
            AndroidRenderEffect
                .createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        }
    }

    private fun createRuntimeShaderEffect(
        effect: RuntimeShaderEffect,
        width: Float,
        height: Float,
    ): ShaderResult<RenderEffect> {
        return try {
            val shader = RuntimeShader(effect.shaderSource)
            val uniforms = effect.buildUniforms(width, height)
            applyUniforms(shader, uniforms)

            ShaderResult.success(
                AndroidRenderEffect
                    .createRuntimeShaderEffect(shader, CONTENT_UNIFORM_NAME)
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

    override fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    internal companion object {
        private const val CONTENT_UNIFORM_NAME = "content"
        internal const val MIN_BLUR_RADIUS = 0.1f

        /**
         * Applies uniforms to an Android RuntimeShader.
         */
        internal fun applyUniforms(shader: RuntimeShader, uniforms: List<Uniform>) {
            uniforms.forEach { uniform ->
                when (uniform) {
                    is FloatUniform -> shader.setFloatUniform(uniform.name, uniform.values)
                    is IntUniform -> shader.setIntUniform(uniform.name, uniform.values)
                }
            }
        }
    }
}

public actual fun ShaderFactory.Companion.create(): ShaderFactory = AndroidShaderFactory()
