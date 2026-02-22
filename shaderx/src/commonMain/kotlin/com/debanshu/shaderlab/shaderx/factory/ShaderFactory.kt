package com.debanshu.shaderlab.shaderx.factory

import androidx.compose.ui.graphics.RenderEffect
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
public interface ShaderFactory {
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
     * Checks if shaders are supported on the current platform/device.
     *
     * @return true if shader effects can be created and applied
     */
    public fun isSupported(): Boolean

    /**
     * Clears the internal shader cache.
     *
     * Call this when you need to free memory or when shader sources have changed.
     * After clearing, shaders will be recompiled on next use.
     */
    public fun clearCache()

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
 *   Reduce for memory-constrained environments.
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
     * Routes the effect to the appropriate creation method based on its type.
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
            is CompositeEffect -> {
                createCompositeEffect(effect, width, height)
            }

            is NativeEffect -> {
                createNativeEffect(effect)
            }

            is RuntimeShaderEffect -> {
                createRuntimeShaderEffect(effect, width, height)
            }

            else -> {
                ShaderResult.failure(
                    ShaderError.UnsupportedEffect(
                        "Unknown effect type: ${effect::class.simpleName}",
                        effect.id,
                    ),
                )
            }
        }
    }

    /**
     * Creates a render effect from a composite effect by chaining effects.
     *
     * The default implementation applies effects sequentially using platform
     * effect chaining. Subclasses can override for platform-specific optimization.
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

        // Start with the first effect
        var currentResult = createRenderEffect(effect.effects.first(), width, height)

        // Chain each subsequent effect
        for (i in 1 until effect.effects.size) {
            currentResult =
                currentResult.map { current ->
                    val nextResult = createRenderEffect(effect.effects[i], width, height)
                    nextResult.getOrNull()?.let { next ->
                        chainEffects(current, next)
                    } ?: current
                }
        }

        return currentResult
    }

    /**
     * Chains two render effects together.
     *
     * @param first The first effect to apply
     * @param second The second effect to apply on top
     * @return The combined effect
     */
    protected open fun chainEffects(
        first: RenderEffect,
        second: RenderEffect,
    ): RenderEffect {
        // Default: just return second effect
        // Platform implementations override with proper chaining
        return second
    }

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

    /**
     * Evicts oldest entries from cache if over the limit.
     *
     * @param cache The cache map to manage
     */
    internal fun <K, V> evictIfNeeded(cache: MutableMap<K, V>) {
        while (cache.size > maxCacheSize) {
            val oldestKey = cache.keys.firstOrNull() ?: break
            cache.remove(oldestKey)
        }
    }

    internal companion object {
        /**
         * Default maximum number of cached shaders.
         */
        internal const val DEFAULT_CACHE_SIZE: Int = 50
    }
}
