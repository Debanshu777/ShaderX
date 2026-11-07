package com.debanshu.shaderlab.shaderx.factory

import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
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
internal class AndroidShaderFactory(
    maxCacheSize: Int = DEFAULT_CACHE_SIZE,
) : BaseShaderFactory(maxCacheSize) {
    /**
     * Access-order LRU cache of compiled RuntimeShaders keyed by shader source code.
     * Shader compilation is the expensive operation; updating uniforms is cheap.
     */
    private val shaderCache = LruCache<String, RuntimeShader>(maxCacheSize)

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
    ): ShaderResult<RenderEffect> =
        try {
            val shader = getOrCreateShader(effect.shaderSource)
            val uniforms = effect.buildUniforms(width, height)
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
            // Instance-scoped shader — not shared with the LRU cache entry
            val instanceShader = RuntimeShader(effect.shaderSource)
            val uniforms = effect.buildUniforms(width, height)
            applyUniforms(instanceShader, uniforms)
            val renderEffect =
                AndroidRenderEffect
                    .createRuntimeShaderEffect(instanceShader, ShaderConstants.CONTENT_UNIFORM_NAME)
                    .asComposeRenderEffect()
            ShaderResult.success(AndroidShaderSession(instanceShader, renderEffect))
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
            uniforms.forEach { uniform ->
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
private class AndroidShaderSession(
    private val shader: RuntimeShader,
    initialRenderEffect: RenderEffect,
) : ShaderSession {
    override val currentRenderEffect: RenderEffect = initialRenderEffect
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
                    AndroidShaderFactory.applyUniforms(shader, animated.buildFrameUniforms())
                    AndroidRenderEffect
                        .createRuntimeShaderEffect(shader, ShaderConstants.CONTENT_UNIFORM_NAME)
                        .asComposeRenderEffect()
                }
            ShaderResult.success(updatedRenderEffect)
        } catch (e: Exception) {
            ShaderResult.failure(
                ShaderError.CompilationError(
                    "Failed to refresh animated session: ${e.message}",
                    effect.shaderSource,
                ),
            )
        }
    }

    override fun close() {
        // RuntimeShader has no explicit release API on Android
    }
}

public actual fun ShaderFactory.Companion.create(maxCacheSize: Int): ShaderFactory = AndroidShaderFactory(maxCacheSize)
