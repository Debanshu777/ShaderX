package com.debanshu.shaderlab.shaderx.effect.impl

import androidx.compose.runtime.Immutable
import com.debanshu.shaderlab.shaderx.effect.AbstractRuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ParamHandler
import com.debanshu.shaderlab.shaderx.effect.floatHandler
import com.debanshu.shaderlab.shaderx.effect.toggleHandler
import com.debanshu.shaderlab.shaderx.parameter.FloatParameter
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.parameter.PixelParameter
import com.debanshu.shaderlab.shaderx.parameter.ToggleParameter
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.uniform.Uniform

/**
 * Applies a wave distortion effect that can animate over time.
 *
 * Creates a wavy, liquid-like appearance.
 *
 * ### Equality contract
 * Two [WaveEffect] instances are equal if their **configuration** parameters are equal,
 * regardless of [time]. This allows animation loops to see the effect as stable across
 * frames — only the uniforms change, not the effect identity.
 * Data-class copying and destructuring retain the API published in 0.1.3.
 *
 * @property amplitude Maximum displacement in pixels
 * @property frequency Angular frequency in radians across each normalized image axis
 * @property animate Whether the effect should animate
 * @property time Current animation time in seconds (excluded from equality / hashCode)
 */
@Immutable
public data class WaveEffect(
    public val amplitude: Float = 10f,
    public val frequency: Float = 5f,
    public val animate: Boolean = true,
    override val time: Float = 0f,
) : AbstractRuntimeShaderEffect(),
    AnimatedShaderEffect {
    override val id: String = ID
    override val displayName: String = "Wave"
    override val isAnimating: Boolean get() = animate

    override val shaderSource: String get() = SHADER_SOURCE

    override val parameterHandlers: Map<String, ParamHandler<*>> get() = PARAMETER_HANDLERS

    override fun buildUniforms(
        width: Float,
        height: Float,
    ): List<Uniform> =
        listOf(
            FloatUniform("resolution", width, height),
            FloatUniform("amplitude", amplitude),
            FloatUniform("frequency", frequency),
            FloatUniform("time", time),
        )

    override fun withTime(newTime: Float): WaveEffect = copy(time = newTime)

    override fun withTypedParameter(
        parameterId: String,
        value: ParameterValue,
    ): WaveEffect = super.withTypedParameter(parameterId, value) as WaveEffect

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WaveEffect) return false
        return amplitude == other.amplitude && frequency == other.frequency && animate == other.animate
    }

    override fun hashCode(): Int {
        var result = amplitude.hashCode()
        result = 31 * result + frequency.hashCode()
        result = 31 * result + animate.hashCode()
        return result
    }

    public companion object {
        public const val ID: String = "wave_distortion"
        public const val PARAM_AMPLITUDE: String = "amplitude"
        public const val PARAM_FREQUENCY: String = "frequency"
        public const val PARAM_ANIMATE: String = "animate"

        private val SHADER_SOURCE: String =
            """
            uniform shader content;
            uniform float2 resolution;
            uniform float amplitude;
            uniform float frequency;
            uniform float time;

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / resolution;

                // Apply wave distortion
                float xOffset = sin(uv.y * frequency + time) * amplitude;
                float yOffset = cos(uv.x * frequency + time) * amplitude;

                float2 distortedCoord = fragCoord + float2(xOffset, yOffset);

                // Clamp to valid range
                distortedCoord = clamp(distortedCoord, float2(0.0), resolution);

                return content.eval(distortedCoord);
            }
            """.trimIndent()

        private val PARAMETER_HANDLERS: Map<String, ParamHandler<*>> =
            mapOf(
                PARAM_AMPLITUDE to
                    floatHandler<WaveEffect>(
                        spec =
                            PixelParameter(
                                id = PARAM_AMPLITUDE,
                                label = "Amplitude",
                                range = 0f..50f,
                                defaultValue = 10f,
                            ),
                        read = { it.amplitude },
                        write = { e, v -> WaveEffect(v, e.frequency, e.animate, e.time) },
                    ),
                PARAM_FREQUENCY to
                    floatHandler<WaveEffect>(
                        spec =
                            FloatParameter(
                                id = PARAM_FREQUENCY,
                                label = "Frequency",
                                range = 1f..20f,
                                defaultValue = 5f,
                            ),
                        read = { it.frequency },
                        write = { e, v -> WaveEffect(e.amplitude, v, e.animate, e.time) },
                    ),
                PARAM_ANIMATE to
                    toggleHandler<WaveEffect>(
                        spec =
                            ToggleParameter(
                                id = PARAM_ANIMATE,
                                label = "Animate",
                                isEnabledByDefault = true,
                            ),
                        read = { it.animate },
                        write = { e, v -> WaveEffect(e.amplitude, e.frequency, v, e.time) },
                    ),
            )
    }
}
