package com.debanshu.shaderlab.shaderx.effect

import androidx.compose.runtime.Immutable
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue

/**
 * Abstract base class for [AnimatedShaderEffect] implementations.
 *
 * Eliminates the repetitive boilerplate every animated effect must write:
 * - [withTime] is routed through [copyWithTime]
 * - [equals] and [hashCode] are [final] and delegate to [structuralEquals] / [structuralHashCode]
 *
 * ## Equality contract — [time] MUST be excluded
 *
 * Subclasses implement [structuralEquals] and [structuralHashCode] using **only** configuration
 * parameters (amplitude, speed, colors, etc.). Never include [time] — otherwise Compose
 * animation loops tear down every frame.
 *
 * ## Minimal example
 *
 * ```kotlin
 * @Immutable
 * public class MyEffect(
 *     public val speed: Float = 1f,
 *     public val animate: Boolean = true,
 *     override val time: Float = 0f,
 * ) : AbstractAnimatedShaderEffect() {
 *     override val isAnimating: Boolean get() = animate
 *     override fun copyWithTime(newTime: Float) = MyEffect(speed, animate, newTime)
 *     override fun structuralEquals(other: Any?) =
 *         other is MyEffect && speed == other.speed && animate == other.animate
 *     override fun structuralHashCode() = 31 * speed.hashCode() + animate.hashCode()
 *     // ... shaderSource, buildUniforms, parameterHandlers ...
 * }
 * ```
 *
 * @see AnimatedShaderEffect
 * @see com.debanshu.shaderlab.shaderx.effect.impl.PlasmaEffect
 */
@Immutable
public abstract class AbstractAnimatedShaderEffect :
    AbstractRuntimeShaderEffect(),
    AnimatedShaderEffect {
    /**
     * Creates a new instance with an updated [time] value.
     * Subclasses return their concrete type from this hook.
     */
    protected abstract fun copyWithTime(newTime: Float): AbstractAnimatedShaderEffect

    final override fun withTime(newTime: Float): AnimatedShaderEffect = copyWithTime(newTime)

    /**
     * Structural equality — compare configuration only, **never** [time].
     */
    protected abstract fun structuralEquals(other: Any?): Boolean

    /**
     * Structural hash — must be consistent with [structuralEquals].
     */
    protected abstract fun structuralHashCode(): Int

    final override fun equals(other: Any?): Boolean = structuralEquals(other)

    final override fun hashCode(): Int = structuralHashCode()

    final override fun withTypedParameter(
        parameterId: String,
        value: ParameterValue,
    ): AbstractAnimatedShaderEffect = super.withTypedParameter(parameterId, value) as AbstractAnimatedShaderEffect
}
