package com.debanshu.shaderlab.shaderx.factory

import com.debanshu.shaderlab.shaderx.authoring.definedContentError
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asSkiaBitmap
import com.debanshu.shaderlab.shaderx.effect.CompositeEffect
import com.debanshu.shaderlab.shaderx.ShaderConstants
import com.debanshu.shaderlab.shaderx.authoring.DefinedAnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.authoring.DefinitionShaderSession
import com.debanshu.shaderlab.shaderx.authoring.ShaderBindingException
import com.debanshu.shaderlab.shaderx.authoring.ShaderInputCompilationException
import com.debanshu.shaderlab.shaderx.authoring.validateInputTree
import com.debanshu.shaderlab.shaderx.authoring.validateInputAliases
import com.debanshu.shaderlab.shaderx.uniform.DataUniform
import com.debanshu.shaderlab.shaderx.uniform.NestedUniform
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.BlurEffect
import com.debanshu.shaderlab.shaderx.effect.NativeEffect
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
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
 * Ordered composite snapshots use native ImageFilter composition. Temporary child wrappers are
 * closed after composition retains their native references. Offline export remains independent.
 *
 * @param maxCacheSize Maximum number of shaders to cache (default: 50)
 */
internal class SkiaShaderFactory(
    maxCacheSize: Int = DEFAULT_CACHE_SIZE,
) : BaseShaderFactory(maxCacheSize), ShaderCapabilityProvider, ShaderPipelineProvider {
    /**
     * Access-order LRU cache of compiled RuntimeEffects keyed by shader source code.
     * Skia's RuntimeEffect compilation is expensive; evicted effects are closed to
     * release native off-heap memory.
     */
    private val effectCache = LruCache<String, RuntimeEffect>(maxCacheSize) { it.close() }

    override fun querySupport(
        operation: ShaderOperation,
        effect: ShaderEffect?,
        inputs: Set<ShaderInput>,
    ): ShaderSupport = builtInSupport(null, operation, effect, inputs)

    override val supportsChaining: Boolean = true

    override fun createPipelineSession(effect: CompositeEffect, width: Float, height: Float, timeSeconds: Float): ShaderResult<ShaderPipelineSession> =
        SnapshotPipelineSession.create(this, effect, width, height, timeSeconds)

    // Each child result is created here and exclusively owned until the final Compose wrapper.
    // makeCompose retains native references; release temporary Managed wrappers deterministically.
    override fun createCompositeEffect(effect: CompositeEffect, width: Float, height: Float): ShaderResult<RenderEffect> {
        var accumulated: ImageFilter? = null
        try {
            for (child in effect.effects) {
                val result = createRenderEffect(child, width, height)
                if (result is ShaderResult.Failure) return result
                val next = (result as ShaderResult.Success).value.asSkiaImageFilter()
                val prior = accumulated
                accumulated = null
                accumulated = if (prior == null) next else {
                    try { ImageFilter.makeCompose(next, prior) } finally { prior.close(); next.close() }
                }
            }
            val final = checkNotNull(accumulated)
            accumulated = null
            return ShaderResult.success(final.asComposeRenderEffect())
        } catch (e: Exception) {
            return ShaderResult.failure(ShaderError.Unknown("Pipeline composition failed: ${e.message}", e))
        } finally { accumulated?.close() }
    }

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
    ): ShaderResult<RenderEffect> {
        var capturedSource: String? = null
        return try {
            val source = effect.shaderSource
            capturedSource = source
            val uniforms = effect.buildUniforms(width, height)
            validateInputTree(uniforms, rawSupported = false)
            validateInputAliases(source, uniforms)
            val runtimeEffect = getOrCreateEffect(source)
            RuntimeShaderBuilder(runtimeEffect).use { builder ->
                applyUniforms(builder, uniforms, this)
                ShaderResult.success(
                    ImageFilter
                        .makeRuntimeShader(builder, ShaderConstants.CONTENT_UNIFORM_NAME, null)
                        .asComposeRenderEffect(),
                )
            }
        } catch (e: ShaderInputCompilationException) {
            ShaderResult.failure(e.asError())
        } catch (e: ShaderBindingException) {
            ShaderResult.failure(ShaderError.Unknown(e.message ?: "Effect '${effect.id}' binding failed", e))
        } catch (e: Exception) {
            ShaderResult.failure(
                ShaderError.CompilationError(
                    "Effect '${effect.id}' compile/bind failed: ${e.message}",
                    capturedSource,
                ),
            )
        }

    }

    override fun createAnimatedSession(
        effect: AnimatedShaderEffect,
        width: Float,
        height: Float,
    ): ShaderResult<ShaderSession> {
        val support = querySupport(ShaderOperation.ANIMATED_SESSION, effect, emptySet())
        if (support.status == ShaderSupportStatus.UNSUPPORTED) {
            return ShaderResult.failure(support.asError(ShaderOperation.ANIMATED_SESSION, effect))
        }
        return createNativeAnimatedSession(effect, width, height).map { session ->
            if (effect is DefinedAnimatedShaderEffect) {
                DefinitionShaderSession(effect, width, height, session, ::createNativeAnimatedSession)
            } else session
        }
    }

    private fun createNativeAnimatedSession(
        effect: AnimatedShaderEffect,
        width: Float,
        height: Float,
    ): ShaderResult<ShaderSession> {
        definedContentError(effect)?.let { return ShaderResult.failure(it) }
        var capturedSource: String? = null
        return try {
            val source = effect.shaderSource
            capturedSource = source
            val uniforms = effect.buildUniforms(width, height)
            validateInputTree(uniforms, rawSupported = false)
            validateInputAliases(source, uniforms)
            val runtimeEffect = getOrCreateEffect(source)
            val builder = RuntimeShaderBuilder(runtimeEffect)
            try {
                applyUniforms(builder, uniforms, this)
                val renderEffect =
                    ImageFilter
                        .makeRuntimeShader(builder, ShaderConstants.CONTENT_UNIFORM_NAME, null)
                        .asComposeRenderEffect()
                ShaderResult.success(SkiaShaderSession(builder, renderEffect, this, source))
            } catch (e: Exception) {
                builder.close()
                throw e
            }
        } catch (e: ShaderInputCompilationException) {
            ShaderResult.failure(e.asError())
        } catch (e: ShaderBindingException) {
            ShaderResult.failure(ShaderError.Unknown(e.message ?: "Effect '${effect.id}' binding failed", e))
        } catch (e: Exception) {
            ShaderResult.failure(
                ShaderError.CompilationError(
                    "Effect '${effect.id}' session compile/bind failed: ${e.message}",
                    capturedSource,
                ),
            )
        }
    }

    /**
     * Gets a cached RuntimeEffect or creates and caches a new one via the LRU cache.
     * Accessible from [SkiaImageProcessor] in the same source set to share the cache.
     */
    internal fun getOrCreateEffect(source: String): RuntimeEffect = effectCache.getOrPut(source) {
        // Skiko native/Wasm signals SkSL compiler diagnostics with kotlin.Error rather than
        // Exception. Contain that signal at the compiler boundary, preserving structured failure.
        try { RuntimeEffect.makeForShader(source) } catch (error: Error) {
            if (error::class != Error::class) throw error
            throw IllegalArgumentException(error.message ?: "SkSL compilation failed", error)
        }
    }

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
        ) = applyUniforms(builder, uniforms, null)

        internal fun applyUniforms(
            builder: RuntimeShaderBuilder,
            uniforms: List<Uniform>,
            factory: SkiaShaderFactory?,
        ) {
            validateInputTree(uniforms, rawSupported = false)
            uniforms.forEach { uniform ->
                try {
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
                                        uniform.values[3],
                                    )
                                }

                                else -> builder.uniform(
                                    uniform.name,
                                    uniform.values[0],
                                ) // AGSL/SkSL vectors cap at vec4
                            }
                        }

                        is ColorUniform -> {
                            builder.uniform(uniform.name, uniform.toFloatArray())
                        }

                        is DataUniform -> error("Raw data input unavailable on Skiko 0.9.37.4")

                        is NestedUniform -> {
                            val definition = uniform.input.background.definition
                            val source = definition.program.source
                            val path = "${uniform.name}(${definition.id})"
                            val shader = try {
                                val program = factory?.getOrCreateEffect(source) ?: compileChild(source)
                                try {
                                    RuntimeShaderBuilder(program).use { child ->
                                        applyUniforms(child, uniform.input.uniforms, factory)
                                        child.makeShader()
                                    }
                                } finally { if (factory == null) program.close() }
                            } catch (e: ShaderInputCompilationException) {
                                throw e.beneath(path)
                            } catch (e: Exception) {
                                throw ShaderInputCompilationException(source, path, e)
                            }
                            // Assignment addresses the parent declaration: attribute alias/type errors
                            // to that parent, not the successfully compiled child's source.
                            shader.use { builder.child(uniform.name, it) }
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
                } catch (e: ShaderInputCompilationException) {
                    throw e
                } catch (e: Exception) {
                    throw IllegalArgumentException("Binding '${uniform.name}' native assignment failed: ${e.message}", e)
                }
            }
        }

        private fun compileChild(source: String): RuntimeEffect = try {
            RuntimeEffect.makeForShader(source)
        } catch (error: Error) {
            if (error::class != Error::class) throw error
            throw IllegalArgumentException(error.message ?: "SkSL compilation failed", error)
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
    currentRenderEffect: RenderEffect,
    private val factory: SkiaShaderFactory?,
    private val shaderSource: String?,
) : ShaderSession {
    constructor(builder: RuntimeShaderBuilder, currentRenderEffect: RenderEffect) : this(builder, currentRenderEffect, null, null)
    constructor(builder: RuntimeShaderBuilder, currentRenderEffect: RenderEffect, factory: SkiaShaderFactory?) : this(builder,currentRenderEffect,factory,null)
    private var closed = false
    private var current: RenderEffect? = currentRenderEffect
    override val currentRenderEffect: RenderEffect get() {
        check(!closed) { "Shader session is closed" }
        return checkNotNull(current)
    }

    override fun refresh(
        effect: RuntimeShaderEffect,
        width: Float,
        height: Float,
    ): ShaderResult<RenderEffect> =
        try {
            check(!closed) { "Shader session is closed" }
            val source = shaderSource ?: effect.shaderSource
            val animated = effect as? AnimatedShaderEffect
            val frameUniforms =
                if (animated != null) {
                    animated.buildFrameUniforms()
                } else {
                    effect.buildUniforms(width, height)
                }
            validateInputAliases(source, frameUniforms)
            SkiaShaderFactory.applyUniforms(builder, frameUniforms, factory)
            ShaderResult.success(
                ImageFilter
                    .makeRuntimeShader(builder, ShaderConstants.CONTENT_UNIFORM_NAME, null)
                    .asComposeRenderEffect(),
            )
        } catch (e: ShaderInputCompilationException) {
            ShaderResult.failure(e.asError())
        } catch (e: ShaderBindingException) {
            ShaderResult.failure(ShaderError.Unknown(e.message ?: "Effect '${effect.id}' binding failed", e))
        } catch (e: Exception) {
            ShaderResult.failure(
                ShaderError.CompilationError(
                    "Effect '${effect.id}' frame binding failed: ${e.message}",
                    shaderSource ?: effect.shaderSource,
                ),
            )
        }

    override fun close() {
        if (!closed) {
            closed = true
            current = null
            builder.close()
        }
    }
}
