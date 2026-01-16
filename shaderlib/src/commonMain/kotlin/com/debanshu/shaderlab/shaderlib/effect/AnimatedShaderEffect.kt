package com.debanshu.shaderlab.shaderlib.effect

/**
 * Interface for shader effects that support animation over time.
 *
 * Animated effects receive a `time` value that can be used in shader code
 * to create dynamic, time-varying effects like waves, pulses, or transitions.
 *
 * ## Usage
 * The animation loop should call [withTime] with incrementing time values:
 *
 * ```kotlin
 * LaunchedEffect(effect) {
 *     if (effect is AnimatedShaderEffect && effect.isAnimating) {
 *         while (isActive) {
 *             val time = withFrameMillis { it / 1000f }
 *             viewModel.updateEffect(effect.withTime(time))
 *         }
 *     }
 * }
 * ```
 */
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

    override fun withParameter(parameterId: String, value: Float): AnimatedShaderEffect
}
