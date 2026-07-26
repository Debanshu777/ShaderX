package com.debanshu.shaderlab.shaderx.effect

import androidx.compose.runtime.Stable
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.uniform.Uniform

/**
 * Interface for shader effects that support animation over time.
 *
 * Animated effects receive a `time` value that can be used in shader code
 * to create dynamic, time-varying effects like waves, pulses, or transitions.
 *
 * ## Usage
 * Prefer [com.debanshu.shaderlab.shaderx.compose.shaderEffect] — it runs the animation loop
 * internally via [com.debanshu.shaderlab.shaderx.factory.ShaderSession]. For manual
 * [androidx.compose.ui.graphics.RenderEffect] wiring, call [withTime] from your own loop or
 * use [com.debanshu.shaderlab.shaderx.compose.rememberShaderEffect].
 *
 * Implementations should extend [AbstractAnimatedShaderEffect] rather than implementing this
 * interface directly — the base class enforces the equality contract automatically.
 *
 * ## Equality contract — implementations MUST exclude [time]
 *
 * [rememberShaderEffect] drives the animation coroutine via
 * `LaunchedEffect(effect.id, effect.isAnimating)`. Those keys are stable across frames.
 * If [time] is included in [equals] or [hashCode], every call to [withTime] produces a
 * new effect identity. Any caller that keys a `LaunchedEffect` on the effect instance
 * directly will tear down and recreate the coroutine every ~16 ms — causing visible
 * animation glitches and unnecessary allocations.
 *
 * Implementations must override [equals] and [hashCode] to exclude [time]:
 *
 * ```kotlin
 * // ✅ Correct — time excluded
 * override fun equals(other: Any?): Boolean {
 *     if (other !is MyAnimatedEffect) return false
 *     return speed == other.speed && amplitude == other.amplitude
 *     // time NOT included
 * }
 * override fun hashCode(): Int = 31 * speed.hashCode() + amplitude.hashCode()
 *
 * // ❌ Wrong — data class default equality includes time
 * data class MyAnimatedEffect(val speed: Float, override val time: Float = 0f) : ...
 * ```
 */
@Stable
public interface AnimatedShaderEffect : RuntimeShaderEffect {
    /**
     * Whether this effect is currently animating.
     * When false, the effect behaves as a static shader.
     */
    public val isAnimating: Boolean

    /**
     * Current animation time in seconds.
     * Used in shader code for time-based calculations.
     */
    public val time: Float

    /**
     * Creates a new effect instance with the updated time value.
     *
     * @param newTime The new time value in seconds
     * @return A new [AnimatedShaderEffect] instance with the updated time
     */
    public fun withTime(newTime: Float): AnimatedShaderEffect

    /**
     * Uniforms that change every animation frame.
     *
     * Structural uniforms (colors, speed, resolution, etc.) are applied once when the
     * animated [com.debanshu.shaderlab.shaderx.factory.ShaderSession] is created.
     * Android's session [com.debanshu.shaderlab.shaderx.factory.ShaderSession.refresh]
     * applies only these frame uniforms in-place to avoid JNI corruption from
     * re-setting [com.debanshu.shaderlab.shaderx.uniform.ColorUniform] values while the
     * render thread is sampling the shader.
     */
    public fun buildFrameUniforms(): List<Uniform> = listOf(FloatUniform(TIME_UNIFORM_NAME, time))

    override fun withTypedParameter(
        parameterId: String,
        value: ParameterValue,
    ): AnimatedShaderEffect

    public companion object {
        /** AGSL/SkSL uniform name for [time]; the only uniform updated each frame by default. */
        public const val TIME_UNIFORM_NAME: String = "time"
    }
}
