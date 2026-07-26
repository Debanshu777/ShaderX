package com.debanshu.shaderlab.shaderx.factory

import androidx.compose.ui.graphics.RenderEffect
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.result.ShaderResult

/**
 * A session-scoped handle for animated shader effects that require per-frame uniform updates.
 *
 * Created via [ShaderFactory.createAnimatedSession]. Holds platform-specific mutable shader
 * state (an instance-scoped [android.graphics.RuntimeShader] on Android, a
 * [org.jetbrains.skia.RuntimeShaderBuilder] on Skia) so uniforms can be updated every frame
 * without recompiling the shader source.
 *
 * Call [refresh] each frame with the current effect state (including updated [time]), then
 * assign the returned [RenderEffect] to `graphicsLayer { renderEffect = ... }`.
 *
 * On Android, [refresh] updates [AnimatedShaderEffect.buildFrameUniforms] in-place (typically
 * just `time`) and returns a new [RenderEffect] wrapper each frame so the platform re-samples
 * the shader. Structural uniforms are applied once at session creation. On Skia, [refresh]
 * reapplies all uniforms and returns a new [RenderEffect] wrapper each frame.
 *
 * Always call [close] when the composable leaves composition (typically via [DisposableEffect]).
 */
public interface ShaderSession : AutoCloseable {
    /** The [RenderEffect] produced when this session was created. */
    public val currentRenderEffect: RenderEffect

    /**
     * Applies [effect.buildUniforms] to this session's instance shader and returns a new
     * lightweight [RenderEffect] reference (guarantees Compose repaint).
     *
     * @param effect Current effect state (e.g. with updated [AnimatedShaderEffect.time])
     * @param width Render target width in pixels
     * @param height Render target height in pixels
     */
    public fun refresh(
        effect: RuntimeShaderEffect,
        width: Float,
        height: Float,
    ): ShaderResult<RenderEffect>

    /** Releases instance-scoped GPU resources held by this session. */
    override fun close()
}
