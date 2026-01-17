package com.debanshu.shaderlab.shaderx.factory

import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.debanshu.shaderlab.shaderx.effect.BlurEffect
import com.debanshu.shaderlab.shaderx.effect.NativeEffect
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import com.debanshu.shaderlab.shaderx.ShaderConstants
import com.debanshu.shaderlab.shaderx.uniform.ColorUniform
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.uniform.IntUniform
import com.debanshu.shaderlab.shaderx.uniform.Uniform
import android.graphics.RenderEffect as AndroidRenderEffect

/**
 * Android implementation of [ShaderFactory] using AGSL (Android Graphics Shading Language).
 *
 * Requires Android 13 (API 33) or higher for RuntimeShader support.
 *
 * This implementation caches compiled shaders by source code to avoid
 * recompilation on every frame. Shader compilation is expensive, but
 * setting uniforms is cheap.
 *
 * @param maxCacheSize Maximum number of shaders to cache (default: 50)
 */
internal class AndroidShaderFactory(
    maxCacheSize: Int = DEFAULT_CACHE_SIZE,
) : BaseShaderFactory(maxCacheSize) {

    /**
     * Cache of compiled RuntimeShaders keyed by shader source code.
     * Shader compilation is the expensive operation, so we cache compiled shaders
     * and just update uniforms each frame.
     *
     * Uses LinkedHashMap to maintain insertion order for LRU eviction.
     */
    private val shaderCache = linkedMapOf<String, RuntimeShader>()

    override fun createNativeEffect(effect: NativeEffect): ShaderResult<RenderEffect> {
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
            val radiusPx = radius.coerceAtLeast(ShaderConstants.MIN_BLUR_RADIUS)
            AndroidRenderEffect
                .createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        }
    }

    override fun createRuntimeShaderEffect(
        effect: RuntimeShaderEffect,
        width: Float,
        height: Float,
    ): ShaderResult<RenderEffect> {
        return try {
            val shader = getOrCreateShader(effect.shaderSource)
            val uniforms = effect.buildUniforms(width, height)
            applyUniforms(shader, uniforms)

            ShaderResult.success(
                AndroidRenderEffect
                    .createRuntimeShaderEffect(shader, ShaderConstants.CONTENT_UNIFORM_NAME)
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

    /**
     * Gets a cached RuntimeShader or creates and caches a new one.
     * Shader compilation is expensive, so caching provides significant performance benefits.
     */
    private fun getOrCreateShader(source: String): RuntimeShader {
        return shaderCache.getOrPut(source) {
            evictIfNeeded(shaderCache)
            RuntimeShader(source)
        }
    }

    override fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    override fun platformNotSupportedError(): ShaderError =
        ShaderError.PlatformNotSupported(
            "RuntimeShader requires Android 13 (API 33) or higher. Current: ${Build.VERSION.SDK_INT}"
        )

    override fun clearCache() {
        shaderCache.clear()
    }

    override val cacheSize: Int
        get() = shaderCache.size

    override fun chainEffects(first: RenderEffect, second: RenderEffect): RenderEffect {
        // On Android, we need to access the underlying Android RenderEffect
        // Since Compose RenderEffect wraps Android RenderEffect, we chain them
        // using Android's RenderEffect.createChainEffect (API 31+)
        // For now, return second as a simple fallback
        // Full chaining would require unwrapping Compose RenderEffect
        return second
    }

    internal companion object {
        /**
         * Applies uniforms to an Android RuntimeShader.
         */
        internal fun applyUniforms(shader: RuntimeShader, uniforms: List<Uniform>) {
            uniforms.forEach { uniform ->
                when (uniform) {
                    is FloatUniform -> shader.setFloatUniform(uniform.name, uniform.values)
                    is IntUniform -> shader.setIntUniform(uniform.name, uniform.values)
                    is ColorUniform -> shader.setColorUniform(
                        uniform.name,
                        android.graphics.Color.valueOf(
                            uniform.red,
                            uniform.green,
                            uniform.blue,
                            uniform.alpha
                        )
                    )
                }
            }
        }
    }
}

public actual fun ShaderFactory.Companion.create(): ShaderFactory = AndroidShaderFactory()
