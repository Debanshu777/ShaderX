package com.debanshu.shaderlab.shaderx.factory

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
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/**
 * Skia-based implementation of [ShaderFactory] for iOS and Desktop platforms.
 *
 * Uses Skia's RuntimeEffect for custom shader compilation and ImageFilter for effects.
 *
 * This implementation caches compiled RuntimeEffects by source code to avoid
 * recompilation on every frame. Shader compilation is expensive, but
 * setting uniforms via RuntimeShaderBuilder is cheap.
 *
 * @param maxCacheSize Maximum number of shaders to cache (default: 50)
 */
internal class SkiaShaderFactoryImpl(
    maxCacheSize: Int = DEFAULT_CACHE_SIZE,
) : BaseShaderFactory(maxCacheSize) {

    /**
     * Cache of compiled RuntimeEffects keyed by shader source code.
     * RuntimeEffect compilation is expensive, so caching provides significant performance benefits.
     *
     * Uses LinkedHashMap to maintain insertion order for LRU eviction.
     */
    private val effectCache = linkedMapOf<String, RuntimeEffect>()

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
            ImageFilter
                .makeBlur(radiusPx, radiusPx, FilterTileMode.CLAMP)
                .asComposeRenderEffect()
        }
    }

    override fun createRuntimeShaderEffect(
        effect: RuntimeShaderEffect,
        width: Float,
        height: Float,
    ): ShaderResult<RenderEffect> {
        return try {
            val runtimeEffect = getOrCreateEffect(effect.shaderSource)
            val builder = RuntimeShaderBuilder(runtimeEffect)
            val uniforms = effect.buildUniforms(width, height)
            applyUniforms(builder, uniforms)

            ShaderResult.success(
                ImageFilter
                    .makeRuntimeShader(builder, ShaderConstants.CONTENT_UNIFORM_NAME, null)
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
     * Gets a cached RuntimeEffect or creates and caches a new one.
     * RuntimeEffect compilation is expensive, so caching provides significant performance benefits.
     */
    private fun getOrCreateEffect(source: String): RuntimeEffect {
        return effectCache.getOrPut(source) {
            evictIfNeeded(effectCache)
            RuntimeEffect.makeForShader(source)
        }
    }

    override fun isSupported(): Boolean = true

    override fun clearCache() {
        effectCache.clear()
    }

    override val cacheSize: Int
        get() = effectCache.size

    override fun chainEffects(first: RenderEffect, second: RenderEffect): RenderEffect {
        // Note: Compose RenderEffect doesn't expose its underlying Skia ImageFilter,
        // so we cannot use ImageFilter.makeCompose directly.
        // 
        // For now, return the second effect as a fallback.
        // Full chaining would require accessing internal Compose APIs or
        // restructuring to work with ImageFilters directly.
        //
        // Consider using CompositeEffect with individual effects applied in
        // sequence via the modifier for proper visual composition.
        return second
    }

    internal companion object {
        /**
         * Applies uniforms to a Skia RuntimeShaderBuilder.
         *
         * Shared between [SkiaShaderFactoryImpl] and [SkiaImageProcessorImpl].
         */
        internal fun applyUniforms(builder: RuntimeShaderBuilder, uniforms: List<Uniform>) {
            uniforms.forEach { uniform ->
                when (uniform) {
                    is FloatUniform -> builder.uniform(uniform.name, uniform.values)
                    is IntUniform -> {
                        // Skia's RuntimeShaderBuilder doesn't have a direct int array overload,
                        // so we handle different sizes explicitly
                        when (uniform.values.size) {
                            1 -> builder.uniform(uniform.name, uniform.values[0])
                            2 -> builder.uniform(
                                uniform.name,
                                uniform.values[0],
                                uniform.values[1]
                            )
                            else -> builder.uniform(uniform.name, uniform.values[0])
                        }
                    }
                    is ColorUniform -> {
                        // Skia doesn't have native color uniform support,
                        // so we pass as vec4 (r, g, b, a)
                        builder.uniform(uniform.name, uniform.toFloatArray())
                    }
                }
            }
        }
    }
}
