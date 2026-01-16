package com.debanshu.shaderlab.shaderlib.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import com.debanshu.shaderlab.shaderlib.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderlib.effect.ShaderEffect
import com.debanshu.shaderlab.shaderlib.factory.ShaderFactory
import com.debanshu.shaderlab.shaderlib.factory.create
import com.debanshu.shaderlab.shaderlib.result.ShaderResult
import kotlinx.coroutines.isActive

/**
 * Applies a shader effect to the content rendered by this modifier.
 *
 * This is a convenience modifier that handles:
 * - Tracking size changes
 * - Creating the render effect from the shader definition
 * - Applying the effect via graphicsLayer
 *
 * ## Usage
 * ```kotlin
 * Image(
 *     painter = painterResource("image.png"),
 *     modifier = Modifier.shaderEffect(GrayscaleEffect())
 * )
 * ```
 *
 * @param effect The shader effect to apply, or null to disable
 * @param factory The factory to use for creating render effects (defaults to platform factory)
 * @return Modifier with the shader effect applied
 */
@Composable
public fun Modifier.shaderEffect(
    effect: ShaderEffect?,
    factory: ShaderFactory = remember { ShaderFactory.create() },
): Modifier {
    if (effect == null) return this

    var size by remember { mutableStateOf(Pair(0f, 0f)) }
    var renderEffect by remember { mutableStateOf<RenderEffect?>(null) }

    // Update render effect when size or effect changes
    LaunchedEffect(effect, size) {
        if (size.first > 0 && size.second > 0) {
            val result = factory.createRenderEffect(effect, size.first, size.second)
            renderEffect = result.getOrNull()
        }
    }

    return this
        .onSizeChanged { newSize ->
            size = Pair(newSize.width.toFloat(), newSize.height.toFloat())
        }
        .graphicsLayer {
            this.renderEffect = renderEffect
        }
}

/**
 * Remembers a shader effect with automatic animation handling.
 *
 * If the effect implements [AnimatedShaderEffect] and is animating,
 * this composable will automatically update the time and return
 * the updated effect instance.
 *
 * ## Usage
 * ```kotlin
 * val effect = rememberShaderEffect(WaveEffect(animate = true))
 * Image(
 *     painter = painterResource("image.png"),
 *     modifier = Modifier.shaderEffect(effect)
 * )
 * ```
 *
 * @param effect The base effect to remember and potentially animate
 * @return The effect, with time updated if animating
 */
@Composable
public fun <T : ShaderEffect> rememberShaderEffect(effect: T): T {
    var currentEffect by remember { mutableStateOf(effect) }

    // Update if the base effect changes (parameters changed)
    LaunchedEffect(effect) {
        currentEffect = effect
    }

    // Handle animation
    if (effect is AnimatedShaderEffect && effect.isAnimating) {
        LaunchedEffect(effect.id, effect.isAnimating) {
            while (isActive) {
                withFrameMillis { frameTime ->
                    val timeSeconds = frameTime / 1000f
                    @Suppress("UNCHECKED_CAST")
                    currentEffect = (currentEffect as AnimatedShaderEffect).withTime(timeSeconds) as T
                }
            }
        }
    }

    return currentEffect
}

/**
 * Creates and remembers a render effect from a shader effect definition.
 *
 * This is useful when you need direct access to the RenderEffect,
 * for example when combining multiple effects or applying them manually.
 *
 * ## Usage
 * ```kotlin
 * val renderEffect = rememberRenderEffect(GrayscaleEffect(), width, height)
 * Box(
 *     modifier = Modifier.graphicsLayer {
 *         this.renderEffect = renderEffect
 *     }
 * )
 * ```
 *
 * @param effect The shader effect definition
 * @param width Width of the render target
 * @param height Height of the render target
 * @param factory The factory to use (defaults to platform factory)
 * @return The created RenderEffect, or null if creation failed
 */
@Composable
public fun rememberRenderEffect(
    effect: ShaderEffect,
    width: Float,
    height: Float,
    factory: ShaderFactory = remember { ShaderFactory.create() },
): RenderEffect? {
    return remember(effect, width, height) {
        if (width > 0 && height > 0) {
            factory.createRenderEffect(effect, width, height).getOrNull()
        } else {
            null
        }
    }
}

/**
 * Creates and remembers a render effect result with full error information.
 *
 * Unlike [rememberRenderEffect], this returns the full [ShaderResult]
 * allowing you to handle errors appropriately.
 *
 * @param effect The shader effect definition
 * @param width Width of the render target
 * @param height Height of the render target
 * @param factory The factory to use (defaults to platform factory)
 * @return The ShaderResult containing either the effect or error information
 */
@Composable
public fun rememberRenderEffectResult(
    effect: ShaderEffect,
    width: Float,
    height: Float,
    factory: ShaderFactory = remember { ShaderFactory.create() },
): ShaderResult<RenderEffect>? {
    return remember(effect, width, height) {
        if (width > 0 && height > 0) {
            factory.createRenderEffect(effect, width, height)
        } else {
            null
        }
    }
}
