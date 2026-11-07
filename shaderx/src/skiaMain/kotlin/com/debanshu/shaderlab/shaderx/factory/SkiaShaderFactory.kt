package com.debanshu.shaderlab.shaderx.factory

import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asSkiaBitmap
import com.debanshu.shaderlab.shaderx.ShaderConstants
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.BlurEffect
import com.debanshu.shaderlab.shaderx.effect.NativeEffect
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import com.debanshu.shaderlab.shaderx.uniform.ColorUniform
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.uniform.IntUniform
import com.debanshu.shaderlab.shaderx.uniform.MatrixUniform
import com.debanshu.shaderlab.shaderx.uniform.ShaderTileMode
import com.debanshu.shaderlab.shaderx.uniform.TextureUniform
import com.debanshu.shaderlab.shaderx.uniform.Uniform
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.impl.use

/**
 * Skia-based implementation of [ShaderFactory] for iOS and Desktop platforms.
 *
 * Uses Skia's RuntimeEffect for custom shader compilation and ImageFilter for effects.
 *
 * Compiled effects are cached in a real access-order LRU ([LruCache]). When [close] or
 * [clearCache] is called, each evicted [RuntimeEffect] is closed to release its native
 * off-heap memory.
 *
 * Composite chaining is not implemented by this backend. Multi-effect composites return
 * an explicit unsupported-effect result; callers can combine operations in one shader.
 *
 * @param maxCacheSize Maximum number of shaders to cache (default: 50)
 */
internal class SkiaShaderFactory(
    maxCacheSize: Int = DEFAULT_CACHE_SIZE,
) : BaseShaderFactory(maxCacheSize) {
    /**
     * Access-order LRU cache of compiled RuntimeEffects keyed by shader source code.
     * Skia's RuntimeEffect compilation is expensive; evicted effects are closed to
     * release native off-heap memory.
     */
    private val effectCache = LruCache<String, RuntimeEffect>(maxCacheSize) { it.close() }

    override val supportsChaining: Boolean = false

    override fun createNativeEffect(effect: NativeEffect): ShaderResult<RenderEffect> =
        when (effect) {
            is BlurEffect -> {
                createBlurEffect(effect.radius)
            }

            else -> {
                ShaderResult.failure(
                    ShaderError.UnsupportedEffect(
                        "Unsupported native effect: ${effect::class.simpleName}",
                        effect.id,
                ),
            )
            }
        }

    private fun createBlurEffect(radius: Float): ShaderResult<RenderEffect> =
        ShaderResult.runCatching {
            val radiusPx = radius.coerceAtLeast(ShaderConstants.MIN_BLUR_RADIUS)
            ImageFilter
                .makeBlur(radiusPx, radiusPx, FilterTileMode.CLAMP)
                .asComposeRenderEffect()
        }

    override fun createRuntimeShaderEffect(
        effect: RuntimeShaderEffect,
        width: Float,
        height: Float,
    ): ShaderResult<RenderEffect> =
        try {
            val runtimeEffect = getOrCreateEffect(effect.shaderSource)
            RuntimeShaderBuilder(runtimeEffect).use { builder ->
                applyUniforms(builder, effect.buildUniforms(width, height))
                ShaderResult.success(
                    ImageFilter
                        .makeRuntimeShader(builder, ShaderConstants.CONTENT_UNIFORM_NAME, null)
                        .asComposeRenderEffect(),
                )
            }
        } catch (e: Exception) {
            ShaderResult.failure(
                ShaderError.CompilationError(
                    "Failed to compile shader: ${e.message}",
                    effect.shaderSource,
                ),
            )
        }

    override fun createAnimatedSession(
        effect: AnimatedShaderEffect,
        width: Float,
        height: Float,
    ): ShaderResult<ShaderSession> {
        if (!isSupported()) {
            return ShaderResult.failure(platformNotSupportedError())
        }
        return try {
            val runtimeEffect = getOrCreateEffect(effect.shaderSource)
            val builder = RuntimeShaderBuilder(runtimeEffect)
            try {
                applyUniforms(builder, effect.buildUniforms(width, height))
                val renderEffect =
                    ImageFilter
                        .makeRuntimeShader(builder, ShaderConstants.CONTENT_UNIFORM_NAME, null)
                        .asComposeRenderEffect()
                ShaderResult.success(SkiaShaderSession(builder, renderEffect))
            } catch (e: Exception) {
                builder.close()
                throw e
            }
        } catch (e: Exception) {
            ShaderResult.failure(
                ShaderError.CompilationError(
                    "Failed to create animated session: ${e.message}",
                    effect.shaderSource,
                ),
            )
        }
    }

    /**
     * Gets a cached RuntimeEffect or creates and caches a new one via the LRU cache.
     * Accessible from [SkiaImageProcessor] in the same source set to share the cache.
     */
    internal fun getOrCreateEffect(source: String): RuntimeEffect = effectCache.getOrPut(source) { RuntimeEffect.makeForShader(source) }

    override fun isSupported(): Boolean = true

    /** Clears the cache and closes each cached [RuntimeEffect] to free native memory. */
    override fun clearCache() = effectCache.clear()

    /** Equivalent to [clearCache]. */
    override fun close() = effectCache.clear()

    override val cacheSize: Int get() = effectCache.size

    internal companion object {
        /**
         * Applies uniforms to a Skia RuntimeShaderBuilder.
         *
         * Shared between [SkiaShaderFactory] and [SkiaImageProcessor].
         */
        internal fun applyUniforms(
            builder: RuntimeShaderBuilder,
            uniforms: List<Uniform>,
        ) {
            uniforms.forEach { uniform ->
                when (uniform) {
                    is FloatUniform -> {
                        when (uniform.values.size) {
                            1 -> {
                                builder.uniform(uniform.name, uniform.values[0])
                            }

                            2 -> {
                                builder.uniform(uniform.name, uniform.values[0], uniform.values[1])
                            }

                            3 -> {
                                builder.uniform(
                                    uniform.name,
                                    uniform.values[0],
                                    uniform.values[1],
                                    uniform.values[2],
                                )
                            }

                            4 -> {
                                builder.uniform(
                                    uniform.name,
                                    uniform.values[0],
                                    uniform.values[1],
                                    uniform.values[2],
                                    uniform.values[3],
                                )
                            }

                            else -> builder.uniform(uniform.name, uniform.values)
                        }
                    }

                    is MatrixUniform -> {
                        builder.uniform(uniform.name, uniform.values)
                    }

                    is IntUniform -> {
                        when (uniform.values.size) {
                            1 -> {
                                builder.uniform(uniform.name, uniform.values[0])
                            }

                            2 -> {
                                builder.uniform(uniform.name, uniform.values[0], uniform.values[1])
                            }

                            3 -> {
                                builder.uniform(
                                    uniform.name,
                                    uniform.values[0],
                                    uniform.values[1],
                                    uniform.values[2],
                                )
                            }

                            4 -> {
                                builder.uniform(
                                    uniform.name,
                                    uniform.values[0],
                                    uniform.values[1],
                            uniform.values[2],
                            uniform.values[3]
                        )
                        }

                        else ->
                            builder.uniform(
                            uniform.name,
                            uniform.values[0]
                        ) // AGSL/SkSL vectors cap at vec4
                        }
                    }

                    is ColorUniform -> {
                        builder.uniform(uniform.name, uniform.toFloatArray())
                    }

                    is TextureUniform -> {
                        // The builder retains the native child; release temporary wrappers now
                        // instead of waiting for GC on every live-texture frame.
                        Image.makeFromBitmap(uniform.image.asSkiaBitmap()).use { image ->
                            image.makeShader(
                                uniform.tileModeX.toSkiaTileMode(),
                                uniform.tileModeY.toSkiaTileMode(),
                            ).use { shader -> builder.child(uniform.name, shader) }
                        }
                    }
                }
            }
        }

        private fun ShaderTileMode.toSkiaTileMode(): FilterTileMode =
            when (this) {
                ShaderTileMode.CLAMP -> FilterTileMode.CLAMP
                ShaderTileMode.REPEAT -> FilterTileMode.REPEAT
                ShaderTileMode.MIRROR -> FilterTileMode.MIRROR
            }
    }
}

/**
 * Skia [ShaderSession] holding a [RuntimeShaderBuilder] bound to a cached [RuntimeEffect].
 *
 * [refresh] updates builder uniforms and recreates the [ImageFilter] (cheap — compilation
 * is cached; only uniform binding + filter creation runs each frame).
 */
internal class SkiaShaderSession(
    private val builder: RuntimeShaderBuilder,
    override val currentRenderEffect: RenderEffect,
) : ShaderSession {
    private var closed = false

    override fun refresh(
        effect: RuntimeShaderEffect,
        width: Float,
        height: Float,
    ): ShaderResult<RenderEffect> =
        try {
            check(!closed) { "Shader session is closed" }
            val animated = effect as? AnimatedShaderEffect
            val frameUniforms =
                if (animated != null) {
                    animated.buildFrameUniforms()
                } else {
                    effect.buildUniforms(width, height)
                }
            SkiaShaderFactory.applyUniforms(builder, frameUniforms)
            ShaderResult.success(
                ImageFilter
                    .makeRuntimeShader(builder, ShaderConstants.CONTENT_UNIFORM_NAME, null)
                    .asComposeRenderEffect(),
            )
        } catch (e: Exception) {
            ShaderResult.failure(
                ShaderError.CompilationError(
                    "Failed to refresh animated session: ${e.message}",
                    effect.shaderSource,
                ),
            )
        }

    override fun close() {
        if (!closed) {
            closed = true
            builder.close()
        }
    }
}
