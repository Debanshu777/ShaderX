package com.debanshu.shaderlab.shaderx.compose

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.debanshu.shaderlab.shaderx.authoring.DefinedAnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.createPipelineSession
import com.debanshu.shaderlab.shaderx.factory.ShaderSession
import com.debanshu.shaderlab.shaderx.playback.ShaderPlayback
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import kotlinx.coroutines.isActive

/**
 * Owns one composition-scoped clock. Share the returned state across controlled modifiers.
 * Initial arguments are read once. Set [ShaderPlayback.isPlaying], [ShaderPlayback.speed] or
 * call [ShaderPlayback.seekTo] for later changes. Position is not saveable automatically.
 *
 * [active] defaults to true: hosts must pass false when backgrounded or outside their visible
 * lifecycle. [reducedMotion] defaults to false: hosts must supply their accessibility preference;
 * true freezes automatic progression but still permits seek and live revisions. Resume rebases
 * at the next frame, with no background catch-up. Neither flag alters the user's play preference.
 * Removing this owner stops its clock; it does not close factories or clear consumer values.
 */
@Composable
public fun rememberShaderPlayback(
    initialPositionSeconds: Double = 0.0,
    initiallyPlaying: Boolean = true,
    initialSpeed: Double = 1.0,
    active: Boolean = true,
    reducedMotion: Boolean = false,
): ShaderPlayback {
    val playback = remember { ShaderPlayback(initialPositionSeconds, initiallyPlaying, initialSpeed) }
    LaunchedEffect(playback, playback.isAdvancing, active, reducedMotion) {
        playback.stopClock()
        try {
            if (active && !reducedMotion && playback.isAdvancing) {
                while (isActive) withFrameNanos { playback.onFrame(it) }
            }
        } finally { playback.stopClock() }
    }
    return playback
}

/**
 * Additive controlled path for defined or legacy animated effects. [playback] exclusively owns
 * time; the effect's time and isAnimating flags are ignored. Legacy [shaderEffect] is unchanged.
 *
 * [inputRevision] defaults to zero. Increment it after publishing a complete live input snapshot;
 * retain the same provider. A paused unchanged effect requests no continuous frames. Changing
 * revision refreshes independently of time. Images are borrowed; never mutate them concurrently
 * with rendering. Colors remain structural values in defined effects.
 *
 * [visible] false skips refreshes and retains the last filter. For a hidden shared clock owner,
 * also pass active=false to [rememberShaderPlayback] when all consumers are hidden. Native
 * replacement on values, size, source or factory changes never resets externally owned time.
 * Failures retain the last successful filter; change a key/revision to retry when paused.
 * Disposal closes sessions, not the caller's factory. Calls belong on the composition thread.
 */
@Composable
public fun Modifier.controlledShaderEffect(
    effect: AnimatedShaderEffect,
    playback: ShaderPlayback,
    inputRevision: Long = 0L,
    factory: ShaderFactory = LocalShaderFactory.current,
    knownSize: IntSize? = null,
    visible: Boolean = true,
    onError: ((ShaderError) -> Unit)? = null,
): Modifier {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val target = knownSize ?: size
    val owner = remember { ControlledSessionOwner() }
    var filter by remember { mutableStateOf<RenderEffect?>(null) }
    var frame by remember { mutableIntStateOf(0) }
    val latestError by rememberUpdatedState(onError)
    val position = playback.positionSeconds
    DisposableEffect(owner) { onDispose { owner.close() } }
    LaunchedEffect(effect, factory, target, position, inputRevision, visible) {
        if (!visible) return@LaunchedEffect
        if (target.width <= 0 || target.height <= 0) {
            filter = null
            return@LaunchedEffect
        }
        val result = try {
            val seconds = position.toFloat()
            require(seconds.isFinite()) { "Playback position exceeds finite shader Float seconds" }
            owner.refresh(factory, effect.withTime(seconds), target)
        } catch (e: Exception) {
            ShaderResult.failure(ShaderError.Unknown("Effect '${effect.id}' controlled refresh failed: ${e.message}", e))
        }
        result.onSuccess { filter = it; frame = 1 - frame }
            .onFailure { latestError?.invoke(it) }
    }
    return (if (knownSize == null) onSizeChanged { size = it } else this).graphicsLayer {
        renderEffect = filter
        // Retain the bounded legacy invalidation mechanism until both actual backends establish
        // a replacement. Rendered Compose/Skia tests cover explicit revisions while paused.
        rotationZ = frame * 0.000001f
    }
}

/** Keeps old native state until a replacement succeeds; never owns playback. */
internal class ControlledSessionOwner : AutoCloseable {
    private var factory: ShaderFactory? = null
    private var effect: AnimatedShaderEffect? = null
    private var size: IntSize? = null
    private var session: ShaderSession? = null

    fun refresh(factory: ShaderFactory, effect: AnimatedShaderEffect, size: IntSize): ShaderResult<RenderEffect> {
        val existing = session
        val canRefresh = existing != null && this.factory === factory &&
            ((this.effect is DefinedAnimatedShaderEffect && effect is DefinedAnimatedShaderEffect) ||
                (this.effect == effect && this.size == size))
        if (canRefresh) return existing!!.refresh(effect, size.width.toFloat(), size.height.toFloat()).map {
            this.effect = effect
            this.size = size
            it
        }
        return factory.createAnimatedSession(effect, size.width.toFloat(), size.height.toFloat()).map { replacement ->
            this.factory = factory
            this.effect = effect
            this.size = size
            session = replacement
            existing?.close()
            replacement.currentRenderEffect
        }
    }

    override fun close() { session?.close(); session = null }
}

/**
 * Controlled ordered pipeline. Every animated/live child samples the same
 * [playback] position. Static composites should use [shaderEffect] to avoid clock-driven rebuilds.
 * Paused unchanged inputs settle; increment [inputRevision] for live snapshots. Reorder, source,
 * values and size refresh the whole chain transactionally. Any required failure retains the last
 * successful whole chain. A nonpositive target size clears the filter until positive dimensions
 * return; hidden consumers retain it. Visibility, factory ownership and lifecycle follow the standalone overload.
 */
@Composable
public fun Modifier.controlledShaderEffect(
    effect: com.debanshu.shaderlab.shaderx.effect.CompositeEffect,
    playback: ShaderPlayback,
    inputRevision: Long = 0L,
    factory: ShaderFactory = LocalShaderFactory.current,
    knownSize: IntSize? = null,
    visible: Boolean = true,
    onError: ((ShaderError) -> Unit)? = null,
): Modifier {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val target = knownSize ?: size
    var session by remember { mutableStateOf<com.debanshu.shaderlab.shaderx.factory.ShaderPipelineSession?>(null) }
    var sessionFactory by remember { mutableStateOf<ShaderFactory?>(null) }
    var filter by remember { mutableStateOf<RenderEffect?>(null) }
    var frame by remember { mutableIntStateOf(0) }
    val latestError by rememberUpdatedState(onError)
    val position = playback.positionSeconds
    DisposableEffect(Unit) { onDispose { session?.close() } }
    LaunchedEffect(effect, factory, target, position, inputRevision, visible) {
        if (!visible) return@LaunchedEffect
        if (target.width <= 0 || target.height <= 0) {
            filter = null
            return@LaunchedEffect
        }
        val seconds = position.toFloat()
        if (!seconds.isFinite()) {
            latestError?.invoke(ShaderError.Unknown("Pipeline position exceeds finite Float seconds"))
            return@LaunchedEffect
        }
        val existing = session
        val result = if (existing != null && sessionFactory === factory) {
            existing.refresh(effect, target.width.toFloat(), target.height.toFloat(), seconds)
        } else {
            factory.createPipelineSession(effect, target.width.toFloat(), target.height.toFloat(), seconds).map { candidate ->
                session = candidate
                sessionFactory = factory
                existing?.close()
                candidate.currentRenderEffect
            }
        }
        result.onSuccess { filter = it; frame = 1 - frame }.onFailure { latestError?.invoke(it) }
    }
    return (if (knownSize == null) onSizeChanged { size = it } else this).graphicsLayer {
        renderEffect = filter
        rotationZ = frame * 0.000001f
    }
}
