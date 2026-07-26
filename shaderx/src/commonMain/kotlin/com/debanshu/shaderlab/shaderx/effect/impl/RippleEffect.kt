package com.debanshu.shaderlab.shaderx.effect.impl

import androidx.compose.runtime.Immutable
import com.debanshu.shaderlab.shaderx.effect.AbstractAnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ParamHandler
import com.debanshu.shaderlab.shaderx.effect.floatHandler
import com.debanshu.shaderlab.shaderx.effect.toggleHandler
import com.debanshu.shaderlab.shaderx.parameter.FloatParameter
import com.debanshu.shaderlab.shaderx.parameter.ToggleParameter
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.uniform.Uniform

/**
 * Expanding concentric ring distortion emanating from the center.
 *
 * Uses distance fields and exponential decay to create water-ripple-like motion.
 */
@Immutable
public class RippleEffect(
    public val frequency: Float = 10f,
    public val decay: Float = 2f,
    public val speed: Float = 1.5f,
    public val amplitude: Float = 0.02f,
    public val animate: Boolean = true,
    override val time: Float = 0f,
) : AbstractAnimatedShaderEffect() {
    override val id: String = ID
    override val displayName: String = "Ripple"
    override val isAnimating: Boolean get() = animate

    override val shaderSource: String =
        """
        uniform shader content;
        uniform float2 resolution;
        uniform float frequency;
        uniform float decay;
        uniform float speed;
        uniform float amplitude;
        uniform float time;

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            float2 center = float2(0.5, 0.5);
            float dist = distance(uv, center);

            float wave = sin(dist * frequency - time * speed) * exp(-dist * decay);
            float2 offset = float2(wave * amplitude * resolution.x, wave * amplitude * resolution.y);
            float2 sampleCoord = clamp(fragCoord + offset, float2(0.0), resolution);

            return content.eval(sampleCoord);
        }
        """.trimIndent()

    override val parameterHandlers: Map<String, ParamHandler<*>> =
        mapOf(
            PARAM_FREQUENCY to
                floatHandler<RippleEffect>(
                    spec =
                        FloatParameter(
                            id = PARAM_FREQUENCY,
                            label = "Frequency",
                            range = 1f..30f,
                            defaultValue = 10f,
                        ),
                    read = { it.frequency },
                    write = { e, v ->
                        RippleEffect(
                            v,
                            e.decay,
                            e.speed,
                            e.amplitude,
                            e.animate,
                            e.time,
                        )
                    },
                ),
            PARAM_DECAY to
                floatHandler<RippleEffect>(
                    spec =
                        FloatParameter(
                            id = PARAM_DECAY,
                            label = "Decay",
                            range = 0.5f..8f,
                            defaultValue = 2f,
                        ),
                    read = { it.decay },
                    write = { e, v ->
                        RippleEffect(
                            e.frequency,
                            v,
                            e.speed,
                            e.amplitude,
                            e.animate,
                            e.time,
                        )
                    },
                ),
            PARAM_SPEED to
                floatHandler<RippleEffect>(
                    spec =
                        FloatParameter(
                            id = PARAM_SPEED,
                            label = "Speed",
                            range = 0.1f..5f,
                            defaultValue = 1.5f,
                        ),
                    read = { it.speed },
                    write = { e, v ->
                        RippleEffect(
                            e.frequency,
                            e.decay,
                            v,
                            e.amplitude,
                            e.animate,
                            e.time,
                        )
                    },
                ),
            PARAM_AMPLITUDE to
                floatHandler<RippleEffect>(
                    spec =
                        FloatParameter(
                            id = PARAM_AMPLITUDE,
                            label = "Amplitude",
                            range = 0.001f..0.1f,
                            defaultValue = 0.02f,
                        ),
                    read = { it.amplitude },
                    write = { e, v ->
                        RippleEffect(
                            e.frequency,
                            e.decay,
                            e.speed,
                            v,
                            e.animate,
                            e.time,
                        )
                    },
                ),
            PARAM_ANIMATE to
                toggleHandler<RippleEffect>(
                    spec =
                        ToggleParameter(
                            id = PARAM_ANIMATE,
                            label = "Animate",
                            isEnabledByDefault = true,
                        ),
                    read = { it.animate },
                    write = { e, v ->
                        RippleEffect(
                            e.frequency,
                            e.decay,
                            e.speed,
                            e.amplitude,
                            v,
                            e.time,
                        )
                    },
                ),
        )

    override fun buildUniforms(
        width: Float,
        height: Float,
    ): List<Uniform> =
        listOf(
            FloatUniform("resolution", width, height),
            FloatUniform("frequency", frequency),
            FloatUniform("decay", decay),
            FloatUniform("speed", speed),
            FloatUniform("amplitude", amplitude),
            FloatUniform("time", time),
        )

    override fun copyWithTime(newTime: Float): RippleEffect = RippleEffect(frequency, decay, speed, amplitude, animate, newTime)

    override fun structuralEquals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RippleEffect) return false
        return frequency == other.frequency &&
            decay == other.decay &&
            speed == other.speed &&
            amplitude == other.amplitude &&
            animate == other.animate
    }

    override fun structuralHashCode(): Int {
        var result = frequency.hashCode()
        result = 31 * result + decay.hashCode()
        result = 31 * result + speed.hashCode()
        result = 31 * result + amplitude.hashCode()
        result = 31 * result + animate.hashCode()
        return result
    }

    public companion object {
        public const val ID: String = "ripple"
        public const val PARAM_FREQUENCY: String = "frequency"
        public const val PARAM_DECAY: String = "decay"
        public const val PARAM_SPEED: String = "speed"
        public const val PARAM_AMPLITUDE: String = "amplitude"
        public const val PARAM_ANIMATE: String = "animate"
    }
}
