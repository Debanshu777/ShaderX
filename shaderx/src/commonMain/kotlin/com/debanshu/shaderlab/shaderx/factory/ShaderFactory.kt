package com.debanshu.shaderlab.shaderx.factory

import androidx.compose.ui.graphics.RenderEffect
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.CompositeEffect
import com.debanshu.shaderlab.shaderx.effect.NativeEffect
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult

/**
 * Factory interface for creating platform-specific render effects from shader definitions.
 *
 * Each platform (Android, iOS, Desktop) provides its own implementation that
 * handles the specifics of shader compilation and effect creation.
 *
 * Implements [AutoCloseable] — call [close] to release compiled GPU resources when the
 * factory is no longer needed (e.g., when the screen leaves the composition).
 * The process-wide singleton provided by [LocalShaderFactory] is never closed.
 *
 * ## Thread safety
 * Platform implementations differ:
 * - **Android ([AndroidShaderFactory]):** The internal LRU cache is fully synchronized;
 *   safe for concurrent use from multiple threads.
 * - **Skia ([SkiaShaderFactory], used on iOS, Desktop/JVM, and Wasm):** **Not thread-safe.**
 *   Compose's Skia rendering runs single-threaded per window, which is safe in normal usage.
 *   In multi-window Desktop apps or other multi-threaded access patterns, either use separate
 *   factory instances per thread, or serialize all factory, processor, and session operations
 *   externally, including cache clearing and disposal.
 *
 * ## Usage
 * ```kotlin
 * val factory = ShaderFactory.create()
 * val result = factory.createRenderEffect(effect, width, height)
 * result.onSuccess { renderEffect ->
 *     modifier.graphicsLayer { this.renderEffect = renderEffect }
 * }.onFailure { error ->
 *     Log.e("Shader", error.message)
 * }
 * ```
 *
 * @see ShaderResult for handling success/failure cases
 */
public interface ShaderFactory : AutoCloseable {
    /**
     * Creates a [RenderEffect] from the given shader effect definition.
     *
     * @param effect The effect definition containing shader code or native effect parameters
     * @param width The width of the render target in pixels
     * @param height The height of the render target in pixels
     * @return [ShaderResult] containing the render effect or error information
     */
    public fun createRenderEffect(
        effect: ShaderEffect,
        width: Float,
        height: Float,
    ): ShaderResult<RenderEffect>

    /**
     * Creates a [ShaderSession] for an animated effect that requires per-frame uniform updates.
     *
     * The session owns mutable shader state so [ShaderSession.refresh] can update uniforms
     * without recompilation: a separate runtime shader on Android, or a builder retaining
     * the cached compiled effect on Skia.
     *
     * Custom factories that only implement static rendering may keep the default, which returns
     * [ShaderError.UnsupportedEffect]. Override this method to opt into session animation.
     *
     * @param effect The animated effect to create a session for
     * @param width Render target width in pixels
     * @param height Render target height in pixels
     * @return [ShaderResult] containing the session or error information
     */
    public fun createAnimatedSession(
        effect: AnimatedShaderEffect,
        width: Float,
        height: Float,
    ): ShaderResult<ShaderSession> =
        ShaderResult.failure(
            ShaderError.UnsupportedEffect(
                "Animated shader sessions are not supported by this factory",
                effect.id,
            ),
        )

    /**
     * Checks if shaders are supported on the current platform/device.
     *
     * @return true if shader effects can be created and applied
     */
    public fun isSupported(): Boolean

    /**
     * Clears the internal shader cache and releases compiled GPU resources.
     *
     * Call this when you need to free memory or when shader sources have changed.
     * After clearing, shaders will be recompiled on next use.
     */
    public fun clearCache()

    /**
     * Releases all compiled GPU resources held by this factory.
     * Equivalent to [clearCache]. Safe to call multiple times.
     */
    override fun close()

    /**
     * Returns the current number of cached shader entries.
     */
    public val cacheSize: Int

    public companion object
}

/**
 * Creates the platform-specific [ShaderFactory] instance.
 *
 * Use a shared factory instance when applying effects across multiple composables
 * to benefit from a shared shader cache. Each call creates a new factory with its
 * own cache.
 *
 * @param maxCacheSize Maximum number of compiled shaders to cache (default: 50).
 *   Must be positive. Reduce for memory-constrained environments.
 */
public expect fun ShaderFactory.Companion.create(maxCacheSize: Int = BaseShaderFactory.DEFAULT_CACHE_SIZE): ShaderFactory

/**
 * Abstract base class for [ShaderFactory] implementations.
 *
 * Provides common routing logic for effect types, reducing duplication
 * across platform-specific implementations. Subclasses only need to
 * implement the platform-specific effect creation methods.
 *
 * @param maxCacheSize Maximum number of shaders to cache (default: 50)
 */
public abstract class BaseShaderFactory(
    private val maxCacheSize: Int = DEFAULT_CACHE_SIZE,
) : ShaderFactory {
    /**
     * Whether this backend supports chaining multiple effects via [CompositeEffect].
     *
     * Defaults to false. Override with true only on backends that have a working
     * [chainEffects] implementation. When false, [createCompositeEffect] returns
     * [ShaderError.UnsupportedEffect] for composites with more than one effect.
     */
    protected open val supportsChaining: Boolean = false

    /**
     * Routes the effect to the appropriate creation method based on its type.
     *
     * The [when] is exhaustive over the sealed [ShaderEffect] hierarchy:
     * [CompositeEffect], [NativeEffect], [RuntimeShaderEffect] (which also covers
     * [AnimatedShaderEffect] as a subtype).
     */
    override fun createRenderEffect(
        effect: ShaderEffect,
        width: Float,
        height: Float,
    ): ShaderResult<RenderEffect> {
        if (!isSupported()) {
            return ShaderResult.failure(platformNotSupportedError())
        }

        return when (effect) {
            is CompositeEffect -> createCompositeEffect(effect, width, height)
            is NativeEffect -> createNativeEffect(effect)
            is RuntimeShaderEffect -> createRuntimeShaderEffect(effect, width, height)
        }
    }

    /**
     * Creates a render effect from a composite effect by chaining effects.
     *
     * Returns [ShaderError.UnsupportedEffect] if the platform does not support
     * multi-effect chaining (i.e. [supportsChaining] is false). Single-effect
     * composites are always rendered without chaining.
     *
     * @param effect The composite effect containing multiple effects
     * @param width The width of the render target in pixels
     * @param height The height of the render target in pixels
     * @return Result containing the chained render effect or error
     */
    protected open fun createCompositeEffect(
        effect: CompositeEffect,
        width: Float,
        height: Float,
    ): ShaderResult<RenderEffect> {
        if (effect.effects.isEmpty()) {
            return ShaderResult.failure(
                ShaderError.UnsupportedEffect("Empty composite effect", effect.id),
            )
        }

        if (effect.effects.size > 1 && !supportsChaining) {
            return ShaderResult.failure(
                ShaderError.UnsupportedEffect(
                    "Composite effect chaining of ${effect.effects.size} effects is not supported on this platform. " +
                        "Use a single effect, or run the app on Android API 33+.",
                    effect.id,
                ),
            )
        }

        // Start with the first effect
        var currentResult = createRenderEffect(effect.effects.first(), width, height)

        // Chain each subsequent effect — flatMap propagates any per-effect Failure outward
        // instead of silently falling back to the previous result.
        for (i in 1 until effect.effects.size) {
            currentResult =
                currentResult.flatMap { inner ->
                    createRenderEffect(effect.effects[i], width, height).map { outer ->
                        chainEffects(inner, outer)
                    }
                }
        }

        return currentResult
    }

    /**
     * Chains two render effects together.
     *
     * Only called when [supportsChaining] is true. Override in platforms that
     * support composing [RenderEffect] instances (e.g. this library's Android API 33+ backend).
     *
     * @param inner The effect applied to the content first (input layer)
     * @param outer The effect applied on top of [inner]'s output
     * @return The combined effect
     */
    protected open fun chainEffects(
        inner: RenderEffect,
        outer: RenderEffect,
    ): RenderEffect = outer

    /**
     * Creates a render effect from a native platform effect.
     *
     * @param effect The native effect to create
     * @return Result containing the render effect or error
     */
    protected abstract fun createNativeEffect(effect: NativeEffect): ShaderResult<RenderEffect>

    /**
     * Creates a render effect from a runtime shader effect.
     *
     * @param effect The runtime shader effect containing AGSL/SkSL code
     * @param width The width of the render target in pixels
     * @param height The height of the render target in pixels
     * @return Result containing the render effect or error
     */
    protected abstract fun createRuntimeShaderEffect(
        effect: RuntimeShaderEffect,
        width: Float,
        height: Float,
    ): ShaderResult<RenderEffect>

    /**
     * Returns the platform-specific "not supported" error.
     *
     * Override to provide more specific error messages per platform.
     */
    protected open fun platformNotSupportedError(): ShaderError =
        ShaderError.PlatformNotSupported("Shader effects are not supported on this platform")

    internal companion object {
        /**
         * Default maximum number of cached shaders.
         */
        internal const val DEFAULT_CACHE_SIZE: Int = 50
    }
}
