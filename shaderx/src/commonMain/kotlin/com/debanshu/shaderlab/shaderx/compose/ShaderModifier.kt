package com.debanshu.shaderlab.shaderx.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import kotlinx.coroutines.isActive

/**
 * Applies a shader effect to the content rendered by this modifier.
 *
 * This is a convenience modifier that handles:
 * - Tracking size changes
 * - Creating the render effect from the shader definition
 * - Applying the effect via graphicsLayer
 * - Error handling via optional callback
 *
 * **Animated effects:** [Modifier.shaderEffect] runs the animation loop internally via
 * [com.debanshu.shaderlab.shaderx.factory.ShaderSession]. Do not pair with
 * [rememberShaderEffect] — use [rememberShaderEffect] only when applying [RenderEffect]
 * manually through [graphicsLayer].
 *
 * ## Usage
 * ```kotlin
 * Image(
 *     painter = painterResource("image.png"),
 *     modifier = Modifier.shaderEffect(GrayscaleEffect())
 * )
 * ```
 *
 * ## Error Handling
 * ```kotlin
 * Image(
 *     painter = painterResource("image.png"),
 *     modifier = Modifier.shaderEffect(
 *         effect = MyCustomEffect(),
 *         onError = { error ->
 *             Log.e("Shader", "Failed: ${error.message}")
 *         }
 *     )
 * )
 * ```
 *
 * ## First-frame behavior
 * By default, the effect is not applied on the very first frame because the composable
 * must be measured via `onSizeChanged` (which fires after layout) before shader dimensions
 * are known. For static images this is imperceptible (~16 ms). To avoid the skip entirely,
 * pass a pre-known [knownSize]:
 * ```kotlin
 * Image(
 *     painter = painterResource("photo.png"),
 *     modifier = Modifier.shaderEffect(
 *         effect = GrayscaleEffect(),
 *         knownSize = IntSize(width, height),
 *     )
 * )
 * ```
 *
 * @param effect The shader effect to apply, or null to disable
 * @param factory The factory to use for creating render effects. Defaults to [LocalShaderFactory],
 *   which provides a process-wide shared cache. Override with [rememberShaderFactory] to scope
 *   the cache to a subtree.
 * @param knownSize When non-null, this size is used directly for uniform calculations and the
 *   effect is applied from the first frame. When null (default), the composable measures itself
 *   via `onSizeChanged` and the effect is applied one frame after layout.
 * @param onError Optional callback invoked when shader creation fails
 * @return Modifier with the shader effect applied
 */
@Composable
public fun Modifier.shaderEffect(
    effect: ShaderEffect?,
    factory: ShaderFactory = LocalShaderFactory.current,
    knownSize: IntSize? = null,
    onError: ((ShaderError) -> Unit)? = null,
): Modifier {
    if (effect == null) return this
    return shaderEffectCore(
        effect = effect,
        factory = factory,
        knownSize = knownSize,
        reportResult = { result ->
            result.onFailure { error -> onError?.invoke(error) }
        },
    )
}

/**
 * Applies a shader effect with full result access via callback.
 *
 * This variant provides access to the full [ShaderResult] for more control
 * over success and failure handling.
 *
 * ## Usage
 * ```kotlin
 * var shaderState by remember { mutableStateOf<ShaderResult<RenderEffect>?>(null) }
 *
 * Image(
 *     painter = painterResource("image.png"),
 *     modifier = Modifier.shaderEffectWithResult(
 *         effect = effect,
 *         onResult = { shaderState = it }
 *     )
 * )
 *
 * // Show error UI if needed
 * shaderState?.onFailure { error ->
 *     Text("Error: ${error.message}")
 * }
 * ```
 *
 * @param effect The shader effect to apply, or null to disable
 * @param factory The factory to use for creating render effects. Defaults to [LocalShaderFactory].
 * @param knownSize When non-null, this size is used directly and the effect is applied from the
 *   first frame. When null (default), size is measured via `onSizeChanged` (one-frame skip).
 * @param onResult Callback invoked with the shader result (success or failure)
 * @return Modifier with the shader effect applied
 */
@Composable
public fun Modifier.shaderEffectWithResult(
    effect: ShaderEffect?,
    factory: ShaderFactory = LocalShaderFactory.current,
    knownSize: IntSize? = null,
    onResult: ((ShaderResult<RenderEffect>) -> Unit)? = null,
): Modifier {
    if (effect == null) return this
    return shaderEffectCore(
        effect = effect,
        factory = factory,
        knownSize = knownSize,
        reportResult = { result -> onResult?.invoke(result) },
    )
}

/**
 * Shared implementation for [shaderEffect] and [shaderEffectWithResult].
 *
 * Session identity is keyed on [effect]'s structural [ShaderEffect.hashCode] (excluding [time] for
 * animated effects) so structural parameter changes recreate the session immediately.
 */
@Composable
private fun Modifier.shaderEffectCore(
    effect: ShaderEffect,
    factory: ShaderFactory,
    knownSize: IntSize?,
    reportResult: (ShaderResult<RenderEffect>) -> Unit,
): Modifier {
    var measuredSize by remember { mutableStateOf(Pair(0f, 0f)) }
    var renderEffect by remember { mutableStateOf<RenderEffect?>(null) }
    var renderFrame by remember { mutableIntStateOf(0) }

    val effectWidth = knownSize?.width?.toFloat() ?: measuredSize.first
    val effectHeight = knownSize?.height?.toFloat() ?: measuredSize.second

    val animatedEffect = effect as? AnimatedShaderEffect
    val useAnimatedSession =
        animatedEffect?.isAnimating == true && effectWidth > 0 && effectHeight > 0

    if (useAnimatedSession) {
        val latestEffect by rememberUpdatedState(effect)

        val sessionIdentity =
            remember(animatedEffect.id, animatedEffect.hashCode()) {
                animatedEffect.id to animatedEffect.hashCode()
            }
        val sessionResult =
            remember(sessionIdentity, effectWidth, effectHeight) {
                factory.createAnimatedSession(animatedEffect, effectWidth, effectHeight)
            }

        DisposableEffect(sessionResult) {
            onDispose { sessionResult.getOrNull()?.close() }
        }

        LaunchedEffect(sessionResult, sessionIdentity, effectWidth, effectHeight) {
            val session = sessionResult.getOrNull()
            if (session == null) {
                sessionResult.onFailure { error ->
                    renderEffect = null
                    reportResult(ShaderResult.failure(error))
                }
                return@LaunchedEffect
            }
            renderEffect = session.currentRenderEffect
            reportResult(ShaderResult.success(session.currentRenderEffect))
            var animationStartMillis = 0L
            while (isActive) {
                withFrameMillis { frameTime ->
                    val effectAtFrame =
                        latestEffect as? AnimatedShaderEffect ?: return@withFrameMillis
                    if (!effectAtFrame.isAnimating) {
                        animationStartMillis = 0L
                        return@withFrameMillis
                    }
                    if (animationStartMillis == 0L) {
                        animationStartMillis = frameTime
                    }
                    val elapsedSeconds = (frameTime - animationStartMillis) / 1000f
                    val timed = effectAtFrame.withTime(elapsedSeconds)
                    val result = session.refresh(timed, effectWidth, effectHeight)
                    reportResult(result)
                    result.onSuccess {
                        renderEffect = it
                        renderFrame++
                    }
                }
            }
        }
    } else {
        LaunchedEffect(effect, effectWidth, effectHeight) {
            if (effectWidth > 0 && effectHeight > 0) {
                val result = factory.createRenderEffect(effect, effectWidth, effectHeight)
                reportResult(result)
                result
                    .onSuccess { renderEffect = it }
                    .onFailure { renderEffect = null }
            }
        }
    }

    return (
        if (knownSize == null) {
            this.onSizeChanged { newSize ->
                measuredSize = Pair(newSize.width.toFloat(), newSize.height.toFloat())
            }
        } else {
            this
        }
    ).graphicsLayer {
        this.renderEffect = renderEffect
        // Tie layer invalidation to the animation clock on all platforms.
        rotationZ = renderFrame * 0.000001f
    }
}

/**
 * Remembers a shader effect with automatic animation handling.
 *
 * If the effect implements [AnimatedShaderEffect] and is animating,
 * this composable will automatically update the time and return
 * the updated effect instance.
 *
 * **Prefer [Modifier.shaderEffect] for most use cases** — it runs the animation loop
 * internally. Use this helper only when you apply [RenderEffect] manually via [graphicsLayer]
 * or [rememberRenderEffect].
 *
 * ## Usage
 * ```kotlin
 * val effect = rememberShaderEffect(WaveEffect(animate = true))
 * Box(
 *     modifier = Modifier.graphicsLayer {
 *         this.renderEffect = rememberRenderEffect(effect, width, height)
 *     }
 * )
 * ```
 *
 * @param effect The base effect to remember and potentially animate
 * @return The effect, with time updated if animating
 */
@Composable
public fun <T : ShaderEffect> rememberShaderEffect(effect: T): T {
    var currentEffect by remember { mutableStateOf(effect) }

    // Synchronously update currentEffect when caller changes parameters (M4 fix: SideEffect
    // instead of LaunchedEffect — the assignment is synchronous and doesn't need a coroutine)
    SideEffect { currentEffect = effect }

    // Handle animation — keyed by effect.id and isAnimating, not by the full effect instance,
    // so the loop is not torn down every frame when time changes (Phase 4 fix)
    if (effect is AnimatedShaderEffect && effect.isAnimating) {
        LaunchedEffect(effect.id, effect.isAnimating) {
            while (isActive) {
                withFrameMillis { frameTime ->
                    val animated = currentEffect as? AnimatedShaderEffect
                    if (animated != null) {
                        @Suppress("UNCHECKED_CAST")
                        currentEffect = animated.withTime(frameTime / 1000f) as T
                    }
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
 * **For animated effects:** Prefer [Modifier.shaderEffect], which handles animation
 * automatically. If using this API, pass an effect from [rememberShaderEffect].
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
 * @param factory The factory to use. Defaults to [LocalShaderFactory].
 * @return The created RenderEffect, or null if creation failed
 */
@Composable
public fun rememberRenderEffect(
    effect: ShaderEffect,
    width: Float,
    height: Float,
    factory: ShaderFactory = LocalShaderFactory.current,
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
 * **For animated effects:** Prefer [Modifier.shaderEffect]. See [rememberRenderEffect].
 *
 * @param effect The shader effect definition
 * @param width Width of the render target
 * @param height Height of the render target
 * @param factory The factory to use. Defaults to [LocalShaderFactory].
 * @return The ShaderResult containing either the effect or error information
 */
@Composable
public fun rememberRenderEffectResult(
    effect: ShaderEffect,
    width: Float,
    height: Float,
    factory: ShaderFactory = LocalShaderFactory.current,
): ShaderResult<RenderEffect>? {
    return remember(effect, width, height) {
        if (width > 0 && height > 0) {
            factory.createRenderEffect(effect, width, height)
        } else {
            null
        }
    }
}
