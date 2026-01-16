package com.debanshu.shaderlab.shaderlib.factory

import androidx.compose.ui.graphics.RenderEffect
import com.debanshu.shaderlab.shaderlib.effect.ShaderEffect
import com.debanshu.shaderlab.shaderlib.result.ShaderResult

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

    public companion object
}

/**
 * Context information passed to shader factories.
 *
 * Contains dimensional information needed for uniform calculations.
 */
public data class ShaderContext(
    public val width: Float,
    public val height: Float,
)

/**
 * Creates the platform-specific [ShaderFactory] instance.
 *
 * This is an expect function that each platform implements.
 */
public expect fun ShaderFactory.Companion.create(): ShaderFactory
