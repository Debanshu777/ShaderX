package com.debanshu.shaderlab.shaderx.factory

import android.graphics.Bitmap
import android.graphics.BitmapShader
import java.nio.ByteBuffer
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import com.debanshu.shaderlab.shaderx.authoring.definedContentError
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.debanshu.shaderlab.shaderx.ShaderConstants
import com.debanshu.shaderlab.shaderx.authoring.DefinedShaderEffect
import com.debanshu.shaderlab.shaderx.authoring.DefinedAnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.authoring.DefinitionShaderSession
import com.debanshu.shaderlab.shaderx.authoring.ShaderBindingException
import com.debanshu.shaderlab.shaderx.authoring.validateInputTree
import com.debanshu.shaderlab.shaderx.authoring.ShaderInputCompilationException
import com.debanshu.shaderlab.shaderx.authoring.validateInputAliases
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
import com.debanshu.shaderlab.shaderx.uniform.DataUniform
import com.debanshu.shaderlab.shaderx.uniform.DataSampling
import com.debanshu.shaderlab.shaderx.uniform.NestedUniform
import com.debanshu.shaderlab.shaderx.uniform.Uniform
import android.graphics.RenderEffect as AndroidRenderEffect

/**
 * Android implementation of [ShaderFactory] using AGSL (Android Graphics Shading Language).
 *
 * Requires Android 13 (API 33) or higher for RuntimeShader support.
 *
 * Compiled shaders are cached in a real access-order LRU ([LruCache]) so frequently-used
 * shaders are never evicted while stale ones are. Cache access is fully synchronized.
 *
 * @param maxCacheSize Maximum number of shaders to cache (default: 50)
 */
@androidx.annotation.RequiresApi(33)
internal class AndroidShaderFactory(
    maxCacheSize: Int = DEFAULT_CACHE_SIZE,
) : BaseShaderFactory(maxCacheSize), ShaderCapabilityProvider, ShaderPipelineProvider {
    /**
     * Access-order LRU cache of compiled RuntimeShaders keyed by shader source code.
     * Shader compilation is the expensive operation; updating uniforms is cheap.
     */
    private val shaderCache = LruCache<String, RuntimeShader>(maxCacheSize)

    override fun querySupport(
        operation: ShaderOperation,
        effect: ShaderEffect?,
        inputs: Set<ShaderInput>,
    ): ShaderSupport = builtInSupport(Build.VERSION.SDK_INT, operation, effect, inputs)

    override fun createPipelineSession(effect: com.debanshu.shaderlab.shaderx.effect.CompositeEffect, width: Float, height: Float, timeSeconds: Float): ShaderResult<ShaderPipelineSession> =
        SnapshotPipelineSession.create(this, effect, width, height, timeSeconds)

    // Aligns with isSupported() — both require API 33. The old S (API 31) check was dead
    // code because isSupported() gates the entire createRenderEffect path at API 33+.
    override val supportsChaining: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

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
            AndroidRenderEffect
                .createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        }

    override fun createRuntimeShaderEffect(
        effect: RuntimeShaderEffect,
        width: Float,
        height: Float,
    ): ShaderResult<RenderEffect> {
        var source: String? = null
        return try {
            source = effect.shaderSource
            val uniforms = effect.buildUniforms(width, height)
            validateInputTree(uniforms)
            validateInputAliases(source, uniforms)
            // Bind declarative colors once on a fresh native object, preserving the no in-place
            // session color-update boundary. This conservatively bypasses the shared shader cache.
            val defined = effect is DefinedShaderEffect || effect is DefinedAnimatedShaderEffect || effect is PipelineSnapshotEffect
            val shader = if (defined && uniforms.any { it is ColorUniform }) {
                RuntimeShader(source)
            } else getOrCreateShader(source)
            // Cache lookup alone does not protect this mutable shader. Keep binding and
            // snapshot creation together when callers share a source across threads.
            synchronized(shader) {
                applyUniforms(shader, uniforms)
                ShaderResult.success(
                    AndroidRenderEffect
                        .createRuntimeShaderEffect(shader, ShaderConstants.CONTENT_UNIFORM_NAME)
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
                    source,
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
        var source: String? = null
        return try {
            source = effect.shaderSource
            val uniforms = effect.buildUniforms(width, height)
            validateInputTree(uniforms)
            validateInputAliases(source, uniforms)
            // Instance-scoped shader — not shared with the LRU cache entry
            val instanceShader = RuntimeShader(source)
            applyUniforms(instanceShader, uniforms)
            val renderEffect =
                AndroidRenderEffect
                    .createRuntimeShaderEffect(instanceShader, ShaderConstants.CONTENT_UNIFORM_NAME)
                    .asComposeRenderEffect()
            ShaderResult.success(AndroidShaderSession(instanceShader, renderEffect, source))
        } catch (e: ShaderInputCompilationException) {
            ShaderResult.failure(e.asError())
        } catch (e: ShaderBindingException) {
            ShaderResult.failure(ShaderError.Unknown(e.message ?: "Effect '${effect.id}' binding failed", e))
        } catch (e: Exception) {
            ShaderResult.failure(
                ShaderError.CompilationError(
                    "Effect '${effect.id}' session compile/bind failed: ${e.message}",
                    source,
                ),
            )
        }
    }

    /**
     * Gets a cached RuntimeShader or creates and caches a new one via the LRU cache.
     * The LRU handles eviction automatically.
     */
    internal fun getOrCreateShader(source: String): RuntimeShader =
        shaderCache.getOrPut(source) { RuntimeShader(source) }

    override fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    override fun platformNotSupportedError(): ShaderError =
        ShaderError.PlatformNotSupported(
            "Shader effects require Android 13 or higher on this device",
        )

    override fun clearCache() = shaderCache.clear()

    /** Releases all compiled shader objects held by this factory. */
    override fun close() = shaderCache.clear()

    override val cacheSize: Int get() = shaderCache.size

    // chainEffects is only called when supportsChaining == true (i.e. API 33+),
    // so createChainEffect (API 31) is always available here.
    @Suppress("NewApi")
    override fun chainEffects(
        inner: RenderEffect,
        outer: RenderEffect,
    ): RenderEffect {
        val innerNative = inner.asAndroidRenderEffect()
        val outerNative = outer.asAndroidRenderEffect()
        return AndroidRenderEffect
            .createChainEffect(outerNative, innerNative)
            .asComposeRenderEffect()
    }

    internal companion object {
        /**
         * Applies uniforms to an Android RuntimeShader.
         */
        internal fun applyUniforms(
            shader: RuntimeShader,
            uniforms: List<Uniform>,
        ) {
            // Reject the whole graph before any assignment, child compilation or bitmap allocation.
            validateInputTree(uniforms)
            uniforms.forEach { uniform ->
                try {
                    when (uniform) {
                        is FloatUniform -> {
                            when (uniform.values.size) {
                                1 -> shader.setFloatUniform(uniform.name, uniform.values[0])
                                else -> shader.setFloatUniform(uniform.name, uniform.values)
                            }
                        }

                        is IntUniform -> {
                            shader.setIntUniform(uniform.name, uniform.values)
                        }

                        is MatrixUniform -> {
                            shader.setFloatUniform(uniform.name, uniform.values)
                        }

                        is ColorUniform -> {
                            shader.setColorUniform(
                                uniform.name,
                                android.graphics.Color.valueOf(
                                    uniform.red,
                                    uniform.green,
                                    uniform.blue,
                                    uniform.alpha,
                                ),
                            )
                        }

                        is DataUniform -> {
                            // copyPixelsFromBuffer copies RGBA memory without color conversion or
                            // premultiplication. RuntimeShader retains BitmapShader, which retains
                            // Bitmap. Do not recycle it while a native RenderEffect can retain it.
                            val bitmap = Bitmap.createBitmap(uniform.data.width, uniform.data.height, Bitmap.Config.ARGB_8888)
                            bitmap.setPremultiplied(false)
                            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(uniform.data.bytes()))
                            val input = BitmapShader(bitmap, uniform.tileModeX.toAndroidTileMode(), uniform.tileModeY.toAndroidTileMode())
                            input.setFilterMode(if (uniform.sampling == DataSampling.NEAREST) BitmapShader.FILTER_MODE_NEAREST else BitmapShader.FILTER_MODE_LINEAR)
                            shader.setInputBuffer(uniform.name, input)
                        }

                        is NestedUniform -> {
                            // Every occurrence has an independent mutable native shader. Programs,
                            // values and explicit child time are immutable common declarations.
                            val definition = uniform.input.background.definition
                            val childSource = definition.program.source
                            val path = "${uniform.name}(${definition.id})"
                            val child = try {
                                RuntimeShader(childSource).also { applyUniforms(it, uniform.input.uniforms) }
                            } catch (e: ShaderInputCompilationException) {
                                throw e.beneath(path)
                            } catch (e: Exception) {
                                throw ShaderInputCompilationException(childSource, path, e)
                            }
                            // An invalid parent alias belongs to the parent source, not the child.
                            shader.setInputShader(uniform.name, child)
                        }

                        is TextureUniform -> {
                            // setInputShader is re-callable per frame — unlike setColorUniform, it does
                            // not corrupt JNI uniform names, so it is safe to reapply during refresh().
                            val bitmap = uniform.image.asAndroidBitmap()
                            shader.setInputShader(
                                uniform.name,
                                BitmapShader(
                                    bitmap,
                                    uniform.tileModeX.toAndroidTileMode(),
                                    uniform.tileModeY.toAndroidTileMode(),
                                ),
                            )
                        }
                    }
                } catch (e: ShaderInputCompilationException) {
                    throw e
                } catch (e: Exception) {
                    throw IllegalArgumentException("Binding '${uniform.name}' native assignment failed: ${e.message}", e)
                }
            }
        }

        private fun ShaderTileMode.toAndroidTileMode(): Shader.TileMode =
            when (this) {
                ShaderTileMode.CLAMP -> Shader.TileMode.CLAMP
                ShaderTileMode.REPEAT -> Shader.TileMode.REPEAT
                ShaderTileMode.MIRROR -> Shader.TileMode.MIRROR
            }
    }
}

/**
 * Android [ShaderSession] holding an instance-scoped [RuntimeShader].
 *
 * [refresh] updates only [AnimatedShaderEffect.buildFrameUniforms] in-place (typically `time`),
 * then returns a new [RenderEffect] wrapper so Compose/Android re-samples the shader.
 * Structural uniforms including [com.debanshu.shaderlab.shaderx.uniform.ColorUniform] are set
 * once at session creation — re-applying them during refresh corrupts JNI uniform names.
 */
@androidx.annotation.RequiresApi(33)
private class AndroidShaderSession(
    shader: RuntimeShader,
    initialRenderEffect: RenderEffect,
    private val source: String,
) : ShaderSession {
    private var shader: RuntimeShader? = shader
    private var initialEffect: RenderEffect? = initialRenderEffect
    override val currentRenderEffect: RenderEffect get() = checkNotNull(initialEffect) { "Shader session is closed" }
    private val lock = Any()

    @Suppress("NewApi")
    override fun refresh(
        effect: RuntimeShaderEffect,
        width: Float,
        height: Float,
    ): ShaderResult<RenderEffect> {
        val animated =
            effect as? AnimatedShaderEffect
                ?: return ShaderResult.failure(
                    ShaderError.UnsupportedEffect(
                        "Animated session refresh requires an animated effect",
                        effect.id,
                    ),
                )
        return try {
            val updatedRenderEffect =
                synchronized(lock) {
                    val instance = checkNotNull(shader) { "Shader session is closed" }
                    val uniforms = animated.buildFrameUniforms()
                    validateInputAliases(source, uniforms)
                    AndroidShaderFactory.applyUniforms(instance, uniforms)
                    AndroidRenderEffect
                        .createRuntimeShaderEffect(instance, ShaderConstants.CONTENT_UNIFORM_NAME)
                        .asComposeRenderEffect()
                }
            ShaderResult.success(updatedRenderEffect)
        } catch (e: ShaderInputCompilationException) {
            ShaderResult.failure(e.asError())
        } catch (e: ShaderBindingException) {
            ShaderResult.failure(ShaderError.Unknown(e.message ?: "Effect '${effect.id}' binding failed", e))
        } catch (e: Exception) {
            ShaderResult.failure(
                ShaderError.CompilationError(
                    "Effect '${effect.id}' frame binding failed: ${e.message}",
                    source,
                ),
            )
        }
    }

    override fun close() {
        // RuntimeShader/BitmapShader have no close API. Release Java ownership; retained
        // RenderEffect snapshots keep their native children valid until their own collection.
        synchronized(lock) { shader = null; initialEffect = null }
    }
}

// Keep the entry class free of RuntimeShader method/field signatures on API 31–32.
public actual fun ShaderFactory.Companion.create(maxCacheSize: Int): ShaderFactory =
    if (Build.VERSION.SDK_INT >= 33) Api33Factory.create(maxCacheSize)
    else AndroidNativeShaderFactory(maxCacheSize)

@androidx.annotation.RequiresApi(33)
private object Api33Factory {
    @androidx.annotation.DoNotInline
    fun create(maxCacheSize: Int): ShaderFactory = AndroidShaderFactory(maxCacheSize)
}
