package com.debanshu.shaderlab.shaderx.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonSkippableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.referentialEqualityPolicy
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
 * By default, the composable must be measured via `onSizeChanged` before shader dimensions
 * are known, so the initial render may precede the effect. Pass a pre-known [knownSize] to
 * avoid waiting for measurement. Effect creation still runs asynchronously after composition;
 * this does not guarantee a shader on the first displayed frame.
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
 * @param knownSize When non-null, this size is used directly for uniform calculations without
 *   waiting for measurement. When null (default), the composable measures itself via
 *   `onSizeChanged`. Nonpositive dimensions disable the filter until a positive size is supplied.
 * @param frameDataKey Optional key for per-frame data excluded from [ShaderEffect.equals] (e.g.
 *   identity of a live [com.debanshu.shaderlab.shaderx.uniform.TextureUniform] image). Pass a value
 *   that changes whenever that data changes so Compose does not skip this modifier and leave the
 *   animation loop bound to a stale effect. Session identity still uses structural equality only.
 * @param onError Optional callback invoked when shader creation fails
 * @return Modifier with the shader effect applied
 */
@Composable
public fun Modifier.shaderEffect(
    effect: ShaderEffect?,
    factory: ShaderFactory = LocalShaderFactory.current,
    knownSize: IntSize? = null,
    frameDataKey: Any? = null,
    onError: ((ShaderError) -> Unit)? = null,
): Modifier {
    if (effect == null) return this
    return shaderEffectCore(
        effect = effect,
        factory = factory,
        knownSize = knownSize,
        frameDataKey = frameDataKey,
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
 * @param knownSize When non-null, this size is used directly without waiting for measurement.
 *   When null (default), size is measured via `onSizeChanged`, so initial content may be unfiltered.
 *   Nonpositive dimensions disable the filter until a positive size is supplied.
 * @param frameDataKey Optional key for per-frame data excluded from [ShaderEffect.equals]; see
 *   [Modifier.shaderEffect].
 * @param onResult Callback invoked with the shader result (success or failure)
 * @return Modifier with the shader effect applied
 */
@Composable
public fun Modifier.shaderEffectWithResult(
    effect: ShaderEffect?,
    factory: ShaderFactory = LocalShaderFactory.current,
    knownSize: IntSize? = null,
    frameDataKey: Any? = null,
    onResult: ((ShaderResult<RenderEffect>) -> Unit)? = null,
): Modifier {
    if (effect == null) return this
    return shaderEffectCore(
        effect = effect,
        factory = factory,
        knownSize = knownSize,
        frameDataKey = frameDataKey,
        reportResult = { result -> onResult?.invoke(result) },
    )
}

/**
 * Shared implementation for [shaderEffect] and [shaderEffectWithResult].
 *
 * Session identity is keyed on [effect]'s structural equality (excluding time for
 * animated effects) so structural parameter changes recreate the session immediately.
 *
 * [frameDataKey] is intentionally **not** part of session identity — it only prevents Compose from
 * skipping this composable when live texture/time-excluded data changes under a structurally equal
 * effect instance.
 */
@Composable
private fun Modifier.shaderEffectCore(
    effect: ShaderEffect,
    factory: ShaderFactory,
    knownSize: IntSize?,
    frameDataKey: Any?,
    reportResult: (ShaderResult<RenderEffect>) -> Unit,
): Modifier {
    // Read so the compiler treats [frameDataKey] as a real input for skip invalidation.
    @Suppress("UNUSED_EXPRESSION")
    frameDataKey

    var measuredSize by remember { mutableStateOf(Pair(0f, 0f)) }
    var renderEffect by remember { mutableStateOf<RenderEffect?>(null) }
    var renderFrame by remember { mutableIntStateOf(0) }

    val effectWidth = knownSize?.width?.toFloat() ?: measuredSize.first
    val effectHeight = knownSize?.height?.toFloat() ?: measuredSize.second

    val animatedEffect = effect as? AnimatedShaderEffect
    // Keep a ShaderSession for every AnimatedShaderEffect so buildFrameUniforms() (e.g. live FFT
    // textures) can refresh each frame even when time animation is paused.
    val useAnimatedSession = animatedEffect != null && effectWidth > 0 && effectHeight > 0

    val latestReportResult by rememberUpdatedState(reportResult)

    if (useAnimatedSession && animatedEffect != null) {
        // Frame data is intentionally excluded from structural equality. Keep the actual latest
        // instance when frameDataKey requests recomposition, without recreating the session.
        val latestEffect by remember {
            mutableStateOf(effect, referentialEqualityPolicy())
        }.apply { value = effect }
        val sessionResult =
            remember(factory, animatedEffect, effectWidth, effectHeight) {
                factory.createAnimatedSession(animatedEffect, effectWidth, effectHeight)
            }

        DisposableEffect(sessionResult) {
            onDispose { sessionResult.getOrNull()?.close() }
        }

        LaunchedEffect(sessionResult, factory, animatedEffect, effectWidth, effectHeight) {
            val session = sessionResult.getOrNull()
            if (session == null) {
                sessionResult.onFailure { error ->
                    renderEffect = null
                    latestReportResult(ShaderResult.failure(error))
                }
                return@LaunchedEffect
            }
            renderEffect = session.currentRenderEffect
            latestReportResult(ShaderResult.success(session.currentRenderEffect))
            var animationStartMillis = 0L
            while (isActive) {
                withFrameMillis { frameTime ->
                    val effectAtFrame =
                        latestEffect as? AnimatedShaderEffect ?: return@withFrameMillis
                    val frameEffect =
                        if (effectAtFrame.isAnimating) {
                            if (animationStartMillis == 0L) {
                                animationStartMillis = frameTime
                            }
                            val elapsedSeconds = (frameTime - animationStartMillis) / 1000f
                            effectAtFrame.withTime(elapsedSeconds)
                        } else {
                            animationStartMillis = 0L
                            effectAtFrame
                        }
                    val result = session.refresh(frameEffect, effectWidth, effectHeight)
                    latestReportResult(result)
                    result.onSuccess {
                        renderEffect = it
                        renderFrame = 1 - renderFrame
                    }
                }
            }
        }
    } else {
        LaunchedEffect(factory, effect, effectWidth, effectHeight) {
            if (effectWidth > 0 && effectHeight > 0) {
                val result = factory.createRenderEffect(effect, effectWidth, effectHeight)
                latestReportResult(result)
                result
                    .onSuccess { renderEffect = it }
                    .onFailure { renderEffect = null }
            } else {
                renderEffect = null
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
        // Bound the invalidation toggle so long-running animations never accumulate rotation.
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
 * val renderEffect = rememberRenderEffect(effect, width, height)
 * Box(
 *     modifier = Modifier.graphicsLayer {
 *         this.renderEffect = renderEffect
 *     }
 * )
 * ```
 *
 * @param effect The base effect to remember and potentially animate
 * @return The effect, with time updated if animating
 */
@Composable
@NonSkippableComposable
public fun <T : ShaderEffect> rememberShaderEffect(effect: T): T {
    if (effect !is AnimatedShaderEffect || !effect.isAnimating) return effect

    // Time has its own state because animated effects deliberately compare equal across frames.
    var frameTimeSeconds by remember(effect.id) { mutableFloatStateOf(effect.time) }
    LaunchedEffect(effect.id, effect.isAnimating) {
        var startTimeMillis: Long? = null
        val initialTime = effect.time
        while (isActive) {
            withFrameMillis { frameTime ->
                val start = startTimeMillis ?: frameTime.also { startTimeMillis = it }
                frameTimeSeconds = initialTime + (frameTime - start) / 1000f
            }
        }
    }
    @Suppress("UNCHECKED_CAST")
    return remember(effect, frameTimeSeconds) { effect.withTime(frameTimeSeconds) as T }
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
@NonSkippableComposable
public fun rememberRenderEffect(
    effect: ShaderEffect,
    width: Float,
    height: Float,
    factory: ShaderFactory = LocalShaderFactory.current,
): RenderEffect? =
    remember(factory, effect, (effect as? AnimatedShaderEffect)?.time, width, height) {
        if (width > 0 && height > 0) {
            factory.createRenderEffect(effect, width, height).getOrNull()
        } else {
            null
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
@NonSkippableComposable
public fun rememberRenderEffectResult(
    effect: ShaderEffect,
    width: Float,
    height: Float,
    factory: ShaderFactory = LocalShaderFactory.current,
): ShaderResult<RenderEffect>? =
    remember(factory, effect, (effect as? AnimatedShaderEffect)?.time, width, height) {
        if (width > 0 && height > 0) {
            factory.createRenderEffect(effect, width, height)
        } else {
            null
        }
    }
