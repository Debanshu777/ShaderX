package com.debanshu.shaderlab.shaderx.factory

import androidx.compose.ui.graphics.RenderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult

/** Explicit recovery for static render creation. No policy silently removes composite children. */
public sealed interface ShaderFallbackPolicy {
    /** Return successful null so callers can display original content; retain the cause in the outcome. */
    public data object PreserveContent : ShaderFallbackPolicy
    /** Propagate the original failure. */
    public data object Fail : ShaderFallbackPolicy
    /** Attempt exactly this complete replacement once. Its failure is propagated, with original cause retained. */
    public data class Substitute(public val effect: ShaderEffect) : ShaderFallbackPolicy
}

/**
 * Observable static render outcome. [fallbackUsed] indicates that recovery was selected, even if a
 * substitute fails. [cause] retains the requested operation's error. A substitute error stays in [result].
 * Successful null specifically means PreserveContent was selected after failure.
 */
public data class ShaderRenderOutcome(
    public val result: ShaderResult<RenderEffect?>,
    public val fallbackUsed: Boolean,
    public val cause: ShaderError? = null,
)

/**
 * Creates a static snapshot with deliberate, observable recovery. Animated effects use their stored
 * values; this helper does not own an animation clock or session. For normal animated Compose use,
 * keep Modifier.shaderEffect and its onError/onResult callbacks (which preserve content on failure).
 *
 * The original operation is attempted even for UNKNOWN third-party capabilities. Errors, including
 * compilation failures, follow [policy]. A replacement is attempted once, never recursively.
 */
public fun ShaderFactory.createRenderEffectWithFallback(
    effect: ShaderEffect,
    width: Float,
    height: Float,
    policy: ShaderFallbackPolicy = ShaderFallbackPolicy.PreserveContent,
): ShaderRenderOutcome {
    val requested = createRenderEffect(effect, width, height)
    if (requested is ShaderResult.Success) return ShaderRenderOutcome(requested.map { it }, false)
    val error = (requested as ShaderResult.Failure).error
    return when (policy) {
        ShaderFallbackPolicy.Fail -> ShaderRenderOutcome(ShaderResult.failure(error), false, error)
        ShaderFallbackPolicy.PreserveContent -> ShaderRenderOutcome(ShaderResult.success(null), true, error)
        is ShaderFallbackPolicy.Substitute -> ShaderRenderOutcome(
            createRenderEffect(policy.effect, width, height).map { it }, true, error,
        )
    }
}
